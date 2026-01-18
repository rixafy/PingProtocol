package com.rixafy.pingprotocol

import com.google.gson.Gson
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.universe.Universe
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.net.InetSocketAddress
import java.util.logging.Level
import java.util.logging.Logger

class MinecraftPingHandler(
    private val plugin: PingProtocolPlugin,
    private val logger: Logger
) : ChannelInboundHandlerAdapter() {

    private val gson = Gson()
    private var state = State.HANDSHAKE
    private var protocolVersion = 0

    enum class State {
        HANDSHAKE,
        STATUS,
        LEGACY
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg !is ByteBuf) {
            ctx.fireChannelRead(msg)
            return
        }

        try {
            if (state == State.HANDSHAKE && msg.isReadable && msg.getByte(msg.readerIndex()).toInt() == 0xFE) {
                handleLegacyPing(ctx, msg)
                return
            }

            when (state) {
                State.HANDSHAKE -> handleHandshake(ctx, msg)
                State.STATUS -> handleStatus(ctx, msg)
                State.LEGACY -> {}
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error handling ping from ${ctx.channel().remoteAddress()}: ${e.message}")
            ctx.close()
        } finally {
            msg.release()
        }
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, buf: ByteBuf) {
        val length = MinecraftProtocol.readVarInt(buf)
        val packetId = MinecraftProtocol.readVarInt(buf)

        if (packetId != 0x00) {
            ctx.close()
            return
        }

        protocolVersion = MinecraftProtocol.readVarInt(buf)
        val serverAddress = MinecraftProtocol.readString(buf)
        val serverPort = buf.readUnsignedShort()
        val nextState = MinecraftProtocol.readVarInt(buf)

        if (nextState == 1) {
            state = State.STATUS
            logger.log(Level.FINE, "Handshake from ${ctx.channel().remoteAddress()}: protocol=$protocolVersion")
        } else {
            ctx.close()
        }
    }

    private fun handleStatus(ctx: ChannelHandlerContext, buf: ByteBuf) {
        val length = MinecraftProtocol.readVarInt(buf)
        val packetId = MinecraftProtocol.readVarInt(buf)

        when (packetId) {
            0x00 -> handleStatusRequest(ctx)
            0x01 -> handlePingRequest(ctx, buf)
            else -> ctx.close()
        }
    }

    private fun handleStatusRequest(ctx: ChannelHandlerContext) {
        val config = plugin.config
        val universe = Universe.get()
        val clientAddress = ctx.channel().remoteAddress() as? InetSocketAddress

        // Create and populate the event with default values
        val event = PingEvent(
            clientAddress = clientAddress,
            protocolVersion = protocolVersion,
            isLegacy = false
        ).apply {
            motd = config.motd
            onlinePlayers = universe.playerCount
            maxPlayers = getMaxPlayers()
            versionName = getServerVersion()
            versionProtocol = 0

            if (config.showPlayerList && universe.playerCount > 0) {
                playerSample = universe.players.map { player ->
                    PingEvent.PlayerInfo(
                        name = player.username,
                        uuid = player.uuid.toString()
                    )
                }.toMutableList()
            }
        }

        // Fire event to allow listeners to modify the response
        PingEvent.fire(event)

        // Build response from event data
        val response = StatusResponse(
            version = StatusResponse.Version(
                name = event.versionName,
                protocol = event.versionProtocol
            ),
            players = StatusResponse.Players(
                max = event.maxPlayers,
                online = event.onlinePlayers,
                sample = if (event.playerSample.isEmpty()) null else event.playerSample.map {
                    StatusResponse.PlayerSample(name = it.name, id = it.uuid)
                }
            ),
            description = StatusResponse.Description(text = event.motd),
            favicon = event.favicon
        )

        val json = gson.toJson(response)

        val responseBuf = Unpooled.buffer()

        val jsonBuf = Unpooled.buffer()
        MinecraftProtocol.writeVarInt(jsonBuf, 0x00) // Packet ID
        MinecraftProtocol.writeString(jsonBuf, json)

        MinecraftProtocol.writeVarInt(responseBuf, jsonBuf.readableBytes())
        responseBuf.writeBytes(jsonBuf)
        jsonBuf.release()

        ctx.writeAndFlush(responseBuf)
        logger.log(Level.FINE, "Sent status response to ${ctx.channel().remoteAddress()}")
    }

    private fun handlePingRequest(ctx: ChannelHandlerContext, buf: ByteBuf) {
        val payload = buf.readLong()

        val responseBuf = Unpooled.buffer()
        val pongBuf = Unpooled.buffer()

        MinecraftProtocol.writeVarInt(pongBuf, 0x01)
        pongBuf.writeLong(payload)

        MinecraftProtocol.writeVarInt(responseBuf, pongBuf.readableBytes())
        responseBuf.writeBytes(pongBuf)
        pongBuf.release()

        ctx.writeAndFlush(responseBuf).addListener(ChannelFutureListener.CLOSE)
        logger.log(Level.FINE, "Sent pong response to ${ctx.channel().remoteAddress()}")
    }

    private fun handleLegacyPing(ctx: ChannelHandlerContext, buf: ByteBuf) {
        state = State.LEGACY

        if (!buf.isReadable) {
            ctx.close()
            return
        }

        val packetId = buf.readUnsignedByte().toInt()
        if (packetId != 0xFE) {
            ctx.close()
            return
        }

        val isExtended = buf.isReadable && buf.getByte(buf.readerIndex()).toInt() == 0x01

        if (isExtended) {
            buf.readByte()
            handleLegacyExtendedPing(ctx)
        } else {
            handleLegacyBasicPing(ctx)
        }

        buf.release()
    }

    private fun handleLegacyExtendedPing(ctx: ChannelHandlerContext) {
        val config = plugin.config
        val universe = Universe.get()
        val clientAddress = ctx.channel().remoteAddress() as? InetSocketAddress

        // Create and populate the event with default values
        val event = PingEvent(
            clientAddress = clientAddress,
            protocolVersion = 0,
            isLegacy = true
        ).apply {
            motd = config.motd
            onlinePlayers = universe.playerCount
            maxPlayers = getMaxPlayers()
            versionName = getServerVersion()
            versionProtocol = 0
        }

        // Fire event to allow listeners to modify the response
        PingEvent.fire(event)

        val response = "\u00A7\u0031\u0000" +
                "${event.versionProtocol}\u0000" +
                "${event.versionName}\u0000" +
                "${event.motd}\u0000" +
                "${event.onlinePlayers}\u0000" +
                "${event.maxPlayers}"

        val responseBuf = Unpooled.buffer()
        responseBuf.writeByte(0xFF)
        MinecraftProtocol.writeStringUTF16BE(responseBuf, response)

        ctx.writeAndFlush(responseBuf).addListener(ChannelFutureListener.CLOSE)
        logger.log(Level.FINE, "Sent legacy extended ping response to ${ctx.channel().remoteAddress()}")
    }

    private fun handleLegacyBasicPing(ctx: ChannelHandlerContext) {
        val config = plugin.config
        val universe = Universe.get()
        val clientAddress = ctx.channel().remoteAddress() as? InetSocketAddress

        // Create and populate the event with default values
        val event = PingEvent(
            clientAddress = clientAddress,
            protocolVersion = 0,
            isLegacy = true
        ).apply {
            motd = config.motd
            onlinePlayers = universe.playerCount
            maxPlayers = getMaxPlayers()
        }

        // Fire event to allow listeners to modify the response
        PingEvent.fire(event)

        val response = "${event.motd}\u00A7${event.onlinePlayers}\u00A7${event.maxPlayers}"

        val responseBuf = Unpooled.buffer()
        responseBuf.writeByte(0xFF)
        MinecraftProtocol.writeStringUTF16BE(responseBuf, response)

        ctx.writeAndFlush(responseBuf).addListener(ChannelFutureListener.CLOSE)
        logger.log(Level.FINE, "Sent legacy basic ping response to ${ctx.channel().remoteAddress()}")
    }

    private fun getMaxPlayers(): Int {
        return try {
            HytaleServer.get().config.maxPlayers
        } catch (e: Exception) {
            100
        }
    }

    private fun getServerVersion(): String {
        return try {
            val manifest = com.hypixel.hytale.common.util.java.ManifestUtil.getImplementationVersion()
            if (manifest != null) {
                // Strip build number: "2026.01.13-dcad8778f" -> "2026.01.13"
                manifest.substringBefore('-')
            } else {
                "Hytale"
            }
        } catch (e: Exception) {
            "Hytale"
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        logger.log(Level.FINE, "Exception in ping handler: ${cause.message}")
        ctx.close()
    }
}
