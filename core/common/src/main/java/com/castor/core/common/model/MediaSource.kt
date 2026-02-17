package com.castor.core.common.model

enum class MediaSource {
    SPOTIFY, YOUTUBE, AUDIBLE, KINDLE, NETFLIX, PRIME_VIDEO, CHROME_BROWSER;

    companion object {
        /** Maps well-known package names to their corresponding [MediaSource]. */
        private val packageMap = mapOf(
            "com.spotify.music" to SPOTIFY,
            "com.google.android.youtube" to YOUTUBE,
            "com.audible.application" to AUDIBLE,
            "com.amazon.kindle" to KINDLE,
            "com.netflix.mediaclient" to NETFLIX,
            "com.amazon.avod" to PRIME_VIDEO,
            "com.android.chrome" to CHROME_BROWSER
        )

        /** Returns the [MediaSource] for a given package name, or `null` if unknown. */
        fun fromPackageName(packageName: String): MediaSource? = packageMap[packageName]

        /** Returns the package name for a given [MediaSource], or `null` if not applicable. */
        fun MediaSource.toPackageName(): String? = packageMap.entries
            .firstOrNull { it.value == this }?.key
    }
}
