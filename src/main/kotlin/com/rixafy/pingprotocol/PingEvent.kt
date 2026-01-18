package com.rixafy.pingprotocol

import com.hypixel.hytale.event.ICancellable
import com.hypixel.hytale.event.IEvent
import java.net.InetSocketAddress

/**
 * Event fired when a client pings the server.
 * Server owners can modify the response properties before it's sent.
 *
 * Kotlin:
 * ```kotlin
 * eventRegistry.registerGlobal(PingEvent::class.java) { event ->
 *     event.motd = "Welcome to my server!"
 *     event.maxPlayers = 500
 * }
 * ```
 *
 * Java:
 * ```java
 * eventRegistry.registerGlobal(PingEvent.class, event -> {
 *     event.setMotd("Welcome to my server!");
 *     event.setMaxPlayers(500);
 * });
 * ```
 */
class PingEvent(
    /** The client's remote address */
    val clientAddress: InetSocketAddress?,

    /** The protocol version reported by the client (0 for legacy pings) */
    val protocolVersion: Int,

    /** Whether this is a legacy ping (Minecraft 1.6 or older) */
    val isLegacy: Boolean
) : IEvent<Void>, ICancellable {

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

    private var cancelled: Boolean = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancelled: Boolean) {
        this.cancelled = cancelled
    }

    /**
     * Represents a player in the sample list
     */
    data class PlayerInfo(
        val name: String,
        val uuid: String
    )
}
