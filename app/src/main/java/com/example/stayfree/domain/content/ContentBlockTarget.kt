package com.example.stayfree.domain.content

/**
 * How a detected content surface is handled.
 */
enum class ContentBlockMode {
    /** Cover the surface with a full-screen block the user can't easily bypass. */
    HARD_BLOCK,
    /** Offer a rewarded unlock: watch an ad → a short window of free access. */
    REWARD_UNLOCK
}

/**
 * A short-form content surface that can be blocked *within* a host app
 * (e.g. Reels inside Instagram) without blocking the whole app.
 *
 * @param viewIdSignatures resource-id substrings of the *player/viewer* surface
 *        (NOT the always-present nav tab) — matched case-insensitively against
 *        the live accessibility tree. App-version specific; keep them all here.
 *        Ignored when [matchWholeApp] is true.
 * @param blockMode whether the surface is hard-blocked or offers a rewarded unlock.
 * @param matchWholeApp treat the WHOLE app as the blocked surface (no tree walk).
 *        For apps that are entirely short-form (TikTok) where every screen is the
 *        feed and resource-ids are obfuscated/unstable, so id-matching is useless.
 * @param pressBackBeforeBlock press Back to leave the surface before showing the
 *        block (so the host saves a non-short last state). Set false for apps with
 *        no safe screen behind the feed (TikTok) — there Back would just exit the app.
 */
data class ContentBlockTarget(
    val id: String,
    val displayName: String,   // shown in the overlay title, e.g. "Reels"
    val packageName: String,
    val viewIdSignatures: List<String>,
    val blockMode: ContentBlockMode = ContentBlockMode.HARD_BLOCK,
    val matchWholeApp: Boolean = false,
    val pressBackBeforeBlock: Boolean = true
)
