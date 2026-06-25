package com.example.stayfree.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.stayfree.data.local.preferences.AppPreferences
import com.example.stayfree.data.repository.BlockingRepository
import com.example.stayfree.data.repository.InAppBlockRepository
import com.example.stayfree.data.repository.UsageRepository
import com.example.stayfree.data.repository.WebsiteBlockRepository
import com.example.stayfree.domain.BlockRuleEvaluator
import com.example.stayfree.domain.content.ContentSignatures
import com.example.stayfree.domain.model.BlockType
import com.example.stayfree.ui.blocking.FocusModeState
import com.example.stayfree.ui.content.ContentInterstitialActivity
import com.example.stayfree.ui.overlay.BlockOverlayActivity
import com.example.stayfree.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class StayFreeAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockingRepository: BlockingRepository
    @Inject lateinit var websiteBlockRepository: WebsiteBlockRepository
    @Inject lateinit var inAppBlockRepository: InAppBlockRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var prefs: AppPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory cache of packages with active rules (incl. sleep sentinel) — refreshed every 30s
    @Volatile private var blockedPackagesCache = emptySet<String>()
    // Packages never blocked by global modes (focus/sleep): self, launcher, systemui, dialer, settings
    @Volatile private var exemptPackages = emptySet<String>()
    // Current continuous foreground session (for SESSION rules and DAILY_LIMIT delta)
    private var currentForegroundPackage: String? = null
    private var foregroundSince: Long = 0L
    // Debounce for TYPE_WINDOW_CONTENT_CHANGED floods (state changes always evaluate)
    private val lastContentCheckTime = mutableMapOf<String, Long>()
    // Per-package last check for in-app blocks (debounce)
    private val lastInAppCheckTime = mutableMapOf<String, Long>()
    // Per-content blocking (Reels/Shorts): exit host app + show interstitial
    @Volatile private var enabledContentIds: Set<String> = emptySet()
    private var lastContentBlockAt: Long = 0L
    // Becomes true once we've seen the host app in a NON-short-form state during
    // this foreground session. We only block after that — so opening an app that
    // restores directly into Shorts/Reels does NOT block on launch; only actively
    // navigating into the short-form surface does. Reset on every app switch.
    private var contentSurfaceArmed = false
    // Website time tracking
    private var currentBrowserDomain: String? = null
    private var domainStartTime: Long = 0L
    private var urlDebounceJob: Job? = null
    // Focus mode state (seeded from DataStore, updated via FocusModeState)
    @Volatile private var focusModeActive = false
    @Volatile private var focusModeEndTime = 0L
    @Volatile private var focusModeWhitelist = emptySet<String>()
    @Volatile private var focusModeIsWhitelist = true // true=whitelist, false=blacklist

    companion object {
        private const val TAG = "MoreMoneyA11y"
        private const val CONTENT_DEBOUNCE_MS = 500L
        private const val CACHE_REFRESH_INTERVAL_MS = 30_000L
        private const val INAPP_CHECK_DEBOUNCE_MS = 500L
        private const val URL_DEBOUNCE_MS = 500L
        // Min gap between two interstitial launches (covers CONTENT_CHANGED bursts).
        private const val CONTENT_BLOCK_COOLDOWN_MS = 3_000L

        // Browser URL bar view IDs
        private val BROWSER_URL_VIEW_IDS = mapOf(
            "com.android.chrome" to "com.android.chrome:id/url_bar",
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.brave.browser" to "com.brave.browser:id/url_bar",
            "com.opera.browser" to "com.opera.browser:id/url_field"
        )

        private val STATIC_EXEMPT = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.incallui",
            "com.android.server.telecom"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        exemptPackages = computeExemptPackages()
        startCacheRefresh()
        collectFocusModeState()
        collectContentBlockState()
    }

    private fun collectContentBlockState() {
        serviceScope.launch {
            prefs.contentBlockEnabledIds.collect { enabledContentIds = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore self

        val eventType = event.eventType
        val now = System.currentTimeMillis()

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // New foreground window — reset session tracking on app switch
            if (currentForegroundPackage != pkg) {
                currentForegroundPackage = pkg
                foregroundSince = now
                // Fresh app session — require seeing a non-short-form screen before
                // we'll block (so launching straight into Shorts won't block).
                contentSurfaceArmed = false
            }
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val last = lastContentCheckTime[pkg] ?: 0L
            if (now - last < CONTENT_DEBOUNCE_MS) return
            lastContentCheckTime[pkg] = now
        }

        val isBrowserContentEvent =
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && pkg in BROWSER_URL_VIEW_IDS

        serviceScope.launch(Dispatchers.Main) {
            // 1) Focus mode — global, with exemptions
            if (focusModeActive && now < focusModeEndTime && pkg !in exemptPackages) {
                val shouldBlock = if (focusModeIsWhitelist) {
                    pkg !in focusModeWhitelist
                } else {
                    pkg in focusModeWhitelist
                }
                if (shouldBlock) {
                    showBlockOverlay(pkg, BlockType.FOCUS.name)
                    return@launch
                }
            }

            // 2) Block rules — per-package, plus global sleep-mode sentinel
            val decision = evaluateRules(pkg, now)
            if (decision != null) {
                showBlockOverlay(pkg, decision.reason)
                return@launch
            }

            // 3) Website blocking (only for known browsers)
            if (isBrowserContentEvent) {
                handleBrowserEvent(pkg)
            }

            // 4) In-app blocking (back-kick) — Snapchat/TikTok/Twitter etc.
            val lastInAppCheck = lastInAppCheckTime[pkg] ?: 0L
            if (now - lastInAppCheck > INAPP_CHECK_DEBOUNCE_MS) {
                lastInAppCheckTime[pkg] = now
                handleInAppBlock(pkg)
            }

            // 5) Per-content overlay blocking (Reels/Shorts)
            handleContentBlock(pkg)
        }
    }

    /**
     * Per-content blocking (Reels/Shorts): the instant the short-form surface is
     * detected we send the host app to the background and launch our full-screen
     * interstitial. Cheap-skips any package that isn't an enabled content target
     * so the tree walk only runs inside Instagram/YouTube.
     */
    private fun handleContentBlock(pkg: String) {
        val target = ContentSignatures.byPackage(pkg)
        if (target == null || target.id !in enabledContentIds) return

        // Cooldown so a burst of CONTENT_CHANGED events can't relaunch the
        // interstitial repeatedly while the Short is still settling.
        val now = System.currentTimeMillis()
        if (now - lastContentBlockAt < CONTENT_BLOCK_COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        val screenH = resources.displayMetrics.heightPixels
        val bounds = Rect()
        val onContentSurface = try {
            anyNodeMatches(root) { node ->
                val id = node.viewIdResourceName ?: return@anyNodeMatches false
                if (target.viewIdSignatures.none { id.contains(it, ignoreCase = true) }) {
                    return@anyNodeMatches false
                }
                // The player view is pre-inflated in the host's view hierarchy, so
                // id presence alone fires on app open. Require it to be actually
                // presented: visible to the user AND covering most of the screen
                // (the Shorts/Reels player is full-screen).
                if (!node.isVisibleToUser) return@anyNodeMatches false
                node.getBoundsInScreen(bounds)
                bounds.height() >= screenH * 0.6
            }
        } finally {
            root.recycle()
        }

        if (!onContentSurface) {
            // Saw the host app NOT in short-form — arm blocking for when the user
            // navigates into Shorts/Reels from here.
            contentSurfaceArmed = true
            return
        }
        // Short-form surface is up. Only block if we previously saw a non-short
        // screen this session (active navigation), not on a launch that restores
        // straight into Shorts/Reels.
        if (!contentSurfaceArmed) return

        lastContentBlockAt = now
        Log.d(TAG, "Content detected: ${target.displayName} in $pkg -> interstitial")

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "No overlay permission — cannot launch interstitial")
            return
        }
        // The full-screen interstitial is its own task (NEW_TASK|CLEAR_TASK), so
        // launching it sends the host app to the background — that *is* the exit.
        // We deliberately do NOT call GLOBAL_ACTION_HOME: HOME lands asynchronously
        // and would race the launcher on top of us, and it triggers YouTube's
        // auto-PiP (a floating Short). Dismissing the interstitial goes home, so
        // the user never falls back into the Short.
        try {
            startActivity(ContentInterstitialActivity.newIntent(this, target.id, target.displayName))
        } catch (e: Exception) {
            Log.w(TAG, "Interstitial launch rejected: ${e.message}")
        }
    }

    private suspend fun evaluateRules(pkg: String, now: Long): com.example.stayfree.domain.BlockDecision? {
        val hasPackageRules = pkg in blockedPackagesCache
        val sleepRulesExist = BlockRuleEvaluator.SLEEP_MODE_PACKAGE in blockedPackagesCache
        val includeSleep = sleepRulesExist && pkg !in exemptPackages
        if (!hasPackageRules && !includeSleep) return null

        val rules = buildList {
            if (hasPackageRules) addAll(blockingRepository.getActiveRulesForPackage(pkg))
            if (includeSleep) addAll(blockingRepository.getActiveRulesForPackage(BlockRuleEvaluator.SLEEP_MODE_PACKAGE))
        }
        if (rules.isEmpty()) return null

        val schedules = rules
            .filter { it.blockType == BlockType.SCHEDULED.name || it.blockType == BlockType.SLEEP.name }
            .associate { it.id to blockingRepository.getSchedulesForRule(it.id) }

        val sessionMs = if (currentForegroundPackage == pkg) now - foregroundSince else 0L

        val usageTodayMs = if (rules.any { it.blockType == BlockType.DAILY_LIMIT.name }) {
            val resetTime = prefs.dailyResetTimeMinutes.first()
            val date = TimeUtils.getEffectiveDate(resetTime)
            usageRepository.getScreenTimeForPackageOnDate(pkg, date).first()
        } else 0L

        return BlockRuleEvaluator.evaluate(
            rules = rules,
            schedules = schedules,
            usageTodayMs = usageTodayMs,
            sessionMs = sessionMs,
            nowMinutes = TimeUtils.currentTimeMinutes(),
            day = TimeUtils.currentDayAbbreviation()
        )
    }

    private fun showBlockOverlay(pkg: String, reason: String) {
        if (!Settings.canDrawOverlays(this)) return
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, pkg)
            putExtra(BlockOverlayActivity.EXTRA_BLOCK_REASON, reason)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Activity start can be rejected (background restrictions, transient states).
        }
    }

    private fun handleBrowserEvent(pkg: String) {
        urlDebounceJob?.cancel()
        urlDebounceJob = serviceScope.launch {
            delay(URL_DEBOUNCE_MS)
            val rootNode = rootInActiveWindow ?: return@launch
            try {
                val urlViewId = BROWSER_URL_VIEW_IDS[pkg] ?: return@launch
                val urlNodes = rootNode.findAccessibilityNodeInfosByViewId(urlViewId)
                val urlText = urlNodes.firstOrNull()?.text?.toString() ?: return@launch
                urlNodes.forEach { it.recycle() }

                val domain = extractDomain(urlText) ?: return@launch
                checkWebsiteDomain(domain, pkg)
            } finally {
                rootNode.recycle()
            }
        }
    }

    private suspend fun checkWebsiteDomain(domain: String, browserPkg: String) {
        val resetTime = prefs.dailyResetTimeMinutes.first()
        val date = TimeUtils.getEffectiveDate(resetTime)
        val now = System.currentTimeMillis()

        // Update time for previous domain
        if (currentBrowserDomain != null && currentBrowserDomain != domain) {
            val elapsed = now - domainStartTime
            val prevBlock = websiteBlockRepository.getByDomain(currentBrowserDomain!!)
            if (prevBlock != null) {
                websiteBlockRepository.updateTimeUsed(prevBlock.id, prevBlock.timeUsedTodayMs + elapsed, date)
            }
        }

        if (currentBrowserDomain != domain) {
            currentBrowserDomain = domain
            domainStartTime = now
        }

        val websiteBlock = websiteBlockRepository.getByDomain(domain) ?: return
        if (!websiteBlock.isActive) return

        if (websiteBlock.dailyCapMs == null) {
            // Always block
            showBlockOverlay(browserPkg, "WEBSITE_BLOCKED")
        } else {
            val elapsed = now - domainStartTime
            val totalUsed = websiteBlock.timeUsedTodayMs + elapsed
            if (totalUsed >= websiteBlock.dailyCapMs) {
                showBlockOverlay(browserPkg, "WEBSITE_CAP_REACHED")
            }
        }
    }

    private fun handleInAppBlock(pkg: String) {
        serviceScope.launch {
            val targets = inAppBlockRepository.getActiveForPackage(pkg)
            if (targets.isEmpty()) return@launch

            val rootNode = rootInActiveWindow ?: return@launch
            try {
                for (target in targets) {
                    if (checkInAppStrategy(rootNode, target.detectionStrategy)) {
                        withContext(Dispatchers.Main) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
                        break
                    }
                }
            } finally {
                rootNode.recycle()
            }
        }
    }

    private fun checkInAppStrategy(rootNode: AccessibilityNodeInfo, strategyJson: String): Boolean {
        return try {
            val strategy = JSONObject(strategyJson)
            val matched = matchNodeStrategy(rootNode, strategy)
            if (!matched) {
                val fallback = strategy.optJSONObject("fallback")
                if (fallback != null) matchNodeStrategy(rootNode, fallback) else false
            } else matched
        } catch (e: Exception) {
            false
        }
    }

    private fun matchNodeStrategy(rootNode: AccessibilityNodeInfo, strategy: JSONObject): Boolean {
        val value = strategy.optString("value")
        return when (strategy.optString("type")) {
            "anyOf" -> {
                val arr = strategy.optJSONArray("strategies") ?: return false
                (0 until arr.length()).any { i ->
                    arr.optJSONObject(i)?.let { matchNodeStrategy(rootNode, it) } == true
                }
            }
            // Exact, fully-qualified resource id (fast path).
            "viewId" -> {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(value)
                val found = nodes.isNotEmpty()
                nodes.forEach { it.recycle() }
                found
            }
            // Version-resilient: any node whose resource id contains the token.
            "viewIdContains" ->
                anyNodeMatches(rootNode) { it.viewIdResourceName?.contains(value, ignoreCase = true) == true }
            "contentDescription" ->
                anyNodeMatches(rootNode) { it.contentDescription?.toString()?.contains(value, ignoreCase = true) == true }
            "text" ->
                anyNodeMatches(rootNode) { it.text?.toString()?.contains(value, ignoreCase = true) == true }
            else -> false
        }
    }

    /** Depth-bounded DFS over the live node tree; recycles every node it visits except [root]. */
    private fun anyNodeMatches(
        root: AccessibilityNodeInfo,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (predicate(root)) return true
        if (depth >= 80) return false
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            try {
                if (anyNodeMatches(child, depth + 1, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun extractDomain(url: String): String? {
        return try {
            val cleaned = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            val domain = cleaned.substringBefore("/").substringBefore("?").substringBefore("#")
            if (domain.contains(".") && domain.length > 3) domain else null
        } catch (e: Exception) {
            null
        }
    }

    private fun computeExemptPackages(): Set<String> {
        val set = mutableSetOf(packageName)
        set.addAll(STATIC_EXEMPT)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { set.add(it) }
        val dial = Intent(Intent.ACTION_DIAL)
        packageManager.resolveActivity(dial, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { set.add(it) }
        return set
    }

    private fun startCacheRefresh() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val rules = blockingRepository.getActiveRulesOnce()
                    blockedPackagesCache = rules.map { it.packageName }.toSet()
                    exemptPackages = computeExemptPackages()
                } catch (e: Exception) { /* continue */ }
                delay(CACHE_REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun collectFocusModeState() {
        serviceScope.launch {
            // Seed from persisted state so an active focus session survives process restarts.
            try {
                val active = prefs.focusActive.first()
                val endTime = prefs.focusEndTime.first()
                val isWhitelist = prefs.focusIsWhitelist.first()
                if (active && endTime > System.currentTimeMillis() && !FocusModeState.state.value.active) {
                    FocusModeState.update(true, endTime, emptySet(), isWhitelist)
                }
            } catch (e: Exception) { /* fall through to live state */ }

            FocusModeState.state.collect { snapshot ->
                focusModeActive = snapshot.active
                focusModeEndTime = snapshot.endTime
                focusModeWhitelist = snapshot.whitelist
                focusModeIsWhitelist = snapshot.isWhitelist
            }
        }
    }

    override fun onInterrupt() {
        // No persistent UI to tear down — interstitial is a normal activity.
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
