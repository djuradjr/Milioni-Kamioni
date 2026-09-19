package com.example.stayfree.data.billing

import android.content.Context
import java.io.File

/**
 * Debug builds only: `files/premium_override` holding 1 or 0 forces the premium state
 * without Play — `adb shell run-as <pkg> sh -c 'echo 1 > files/premium_override'`.
 * The release source set ships a no-op, so this code does not exist in release builds.
 */
internal object DebugPremium {
    fun override(context: Context): Boolean? =
        File(context.filesDir, "premium_override").takeIf { it.isFile }
            ?.readText()?.trim()?.let { it == "1" }
}
