package com.rixafy.pingprotocol

import java.net.InetSocketAddress

/**
 * Event fired when a client pings the server.
 * Server owners can modify the response properties before it's sent.
 */
class PingEvent(
    /** The client's remote address */
    val clientAddress: InetSocketAddress?,

    /** The protocol version reported by the client (0 for legacy pings) */
    val protocolVersion: Int,

    /** Whether this is a legacy ping (Minecraft 1.6 or older) */
    val isLegacy: Boolean
) {
    /** Message of the day displayed in server list */
    var motd: String = ""

    /** Number of online players */
    var onlinePlayers: Int = 0

    /** Maximum player slots */
    var maxPlayers: Int = 100

    /** Server version name displayed to clients */
    var versionName: String = "Hytale"

    /** Protocol version to report (0 indicates non-Minecraft server) */
    var versionProtocol: Int = 0

    /** List of players to show in the player sample (hover list) */
    var playerSample: MutableList<PlayerInfo> = mutableListOf()

    /** Base64-encoded PNG favicon (64x64), null to not send */
    var favicon: String? = null

    /**
     * Represents a player in the sample list
     */
    data class PlayerInfo(
        val name: String,
        val uuid: String
    )

    /**
     * Functional interface for PingEvent listeners
     */
    fun interface Listener {
        fun onPing(event: PingEvent)
    }

    companion object {
        private val listeners = mutableListOf<Listener>()

        /**
         * Register a listener to be called when ping events occur
         */
        fun addListener(listener: Listener) {
            listeners.add(listener)
        }

        /**
         * Remove a previously registered listener
         */
        fun removeListener(listener: Listener) {
            listeners.remove(listener)
        }

        /**
         * Clear all registered listeners
         */
        fun clearListeners() {
            listeners.clear()
        }

        /**
         * Fire the event to all registered listeners
         */
        internal fun fire(event: PingEvent) {
            for (listener in listeners) {
                try {
                    listener.onPing(event)
                } catch (e: Exception) {
                    // Log but don't break the ping response
                    e.printStackTrace()
                }
            }
        }
    }
}
