package com.rixafy.pingprotocol

/**
 * Shared cache for status responses to avoid expensive operations on every ping
 */
object StatusCache {
    @Volatile private var cachedJson: String? = null
    @Volatile private var cachedPlayerCount: Int = -1
    @Volatile private var lastCacheTime: Long = 0
    private const val CACHE_VALIDITY_MS = 1000L // Cache for 1 second

    fun getCachedOrBuild(playerCount: Int, builder: () -> String): String {
        val currentTime = System.currentTimeMillis()

        // Check if cache is valid
        if (cachedJson != null &&
            cachedPlayerCount == playerCount &&
            (currentTime - lastCacheTime) < CACHE_VALIDITY_MS) {
            return cachedJson!!
        }

        // Rebuild and cache
        val newJson = builder()
        cachedJson = newJson
        cachedPlayerCount = playerCount
        lastCacheTime = currentTime

        return newJson
    }

    fun invalidate() {
        cachedJson = null
        cachedPlayerCount = -1
        lastCacheTime = 0
    }
}
