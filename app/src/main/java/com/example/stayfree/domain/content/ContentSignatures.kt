package com.example.stayfree.domain.content

/**
 * THE single source of content-detection signatures. Per-content blocking
 * relies on resource-ids inside other apps, which change with every app
 * update — when Reels/Shorts stop triggering, edit ONLY this file.
 *
 * v1 scope: Instagram Reels + YouTube Shorts (per the product plan).
 * - TikTok is entirely short-form → use ordinary app blocking, not this.
 * - Facebook Reels dropped from v1 (most unstable ids, too many misses).
 */
object ContentSignatures {

    const val INSTAGRAM_REELS = "instagram_reels"
    const val INSTAGRAM_STORIES = "instagram_stories"
    const val YOUTUBE_SHORTS = "youtube_shorts"
    const val TIKTOK = "tiktok"

    val ALL: List<ContentBlockTarget> = listOf(
        ContentBlockTarget(
            id = INSTAGRAM_REELS,
            displayName = "Reels",
            packageName = "com.instagram.android",
            viewIdSignatures = listOf(
                "clips_viewer",
                "clips_video_container",
                "clips_tab"
            ),
            blockMode = ContentBlockMode.REWARD_UNLOCK
        ),
        ContentBlockTarget(
            id = INSTAGRAM_STORIES,
            displayName = "Stories",
            packageName = "com.instagram.android",
            // Verified against a live story-viewer dump. Instagram's internal
            // name for Stories is "reel" (the disappearing 24h kind), so the
            // viewer is full of reel_viewer_* ids — distinct from the TikTok-style
            // Reels (clips_*) and from YouTube Shorts (reel_watch_*), so the three
            // never collide. reel_viewer_root is the single "we're in a Story" marker.
            viewIdSignatures = listOf(
                "reel_viewer_root",
                "reel_viewer_media_container",
                "reel_viewer_content_layout",
                "reel_viewer_media_layout"
            ),
            blockMode = ContentBlockMode.REWARD_UNLOCK
        ),
        ContentBlockTarget(
            id = YOUTUBE_SHORTS,
            displayName = "Shorts",
            packageName = "com.google.android.youtube",
            // Verified against a live uiautomator dump of the Shorts player —
            // the whole surface is full of reel_* ids; reel_watch_fragment_root
            // is the single most specific "we are watching a Short" marker.
            viewIdSignatures = listOf(
                "reel_watch_fragment_root",
                "reel_recycler",
                "reel_player_page_container",
                "reel_watch_player",
                "reel_player_overlay",
                "shorts"
            ),
            blockMode = ContentBlockMode.REWARD_UNLOCK
        ),
        ContentBlockTarget(
            id = TIKTOK,
            displayName = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            // TikTok is entirely short-form and its resource-ids are obfuscated +
            // version-unstable (e.g. the For You video is just "hni"), so id-matching
            // is useless. Treat the whole app as the surface, and don't press Back
            // (TikTok has no safe screen behind the feed — Back just exits the app).
            viewIdSignatures = emptyList(),
            blockMode = ContentBlockMode.REWARD_UNLOCK,
            matchWholeApp = true,
            pressBackBeforeBlock = false
        )
    )

    /** Packages we ever care about — used to cheaply skip non-target foregrounds. */
    val targetPackages: Set<String> = ALL.map { it.packageName }.toSet()

    fun byId(id: String): ContentBlockTarget? = ALL.firstOrNull { it.id == id }

    fun byPackage(pkg: String): ContentBlockTarget? = ALL.firstOrNull { it.packageName == pkg }

    /** All targets for a package — a host app can expose several surfaces
     *  (e.g. Instagram has both Reels and Stories). */
    fun allByPackage(pkg: String): List<ContentBlockTarget> = ALL.filter { it.packageName == pkg }
}
