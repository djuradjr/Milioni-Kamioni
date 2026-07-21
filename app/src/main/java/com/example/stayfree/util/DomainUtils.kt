package com.example.stayfree.util

object DomainUtils {

    /** "https://www.M.YouTube.com/watch?v=x" → "m.youtube.com"; null when not a host. */
    fun normalize(input: String): String? {
        val cleaned = input.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .trimEnd('.')
        return if (cleaned.contains('.') && cleaned.length > 3 && !cleaned.contains(' ')) cleaned else null
    }

    /** True when [host] is [blockedDomain] itself or any of its subdomains. */
    fun matches(host: String, blockedDomain: String): Boolean {
        val blocked = normalize(blockedDomain) ?: return false
        return host == blocked || host.endsWith(".$blocked")
    }
}
