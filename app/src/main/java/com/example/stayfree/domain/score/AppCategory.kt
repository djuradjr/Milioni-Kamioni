package com.example.stayfree.domain.score

/**
 * How an app counts toward the focus score. The user can change any app's
 * category from the Apps screen; [defaultFor] only seeds the first guess.
 */
enum class AppCategory {
    /** Costs score per minute. */
    DISTRACTION,

    /** Costs nothing, still counts toward screen time. */
    NEUTRAL,

    /** Costs nothing and is never proposed as a block target. */
    PRODUCTIVE;

    companion object {
        /**
         * Seed categories for apps most people install. Everything unknown is
         * NEUTRAL — guessing DISTRACTION on an unknown app would punish the
         * user for something they never chose.
         */
        fun defaultFor(packageName: String): AppCategory = when (packageName) {
            "com.instagram.android",
            "com.instagram.lite",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.tinder",
            "com.netflix.mediaclient",
            "tv.twitch.android.app",
            "com.tumblr" -> DISTRACTION

            "com.duolingo",
            "com.google.android.keep",
            "com.google.android.calendar",
            "com.microsoft.office.outlook",
            "com.todoist",
            "com.anydo",
            "com.notion.id" -> PRODUCTIVE

            else -> NEUTRAL
        }
    }
}
