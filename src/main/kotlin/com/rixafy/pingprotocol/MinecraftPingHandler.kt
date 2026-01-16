package com.rixafy.pingprotocol

import com.google.gson.Gson
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.universe.Universe
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
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
        val currentPlayerCount = universe.playerCount

        val json = StatusCache.getCachedOrBuild(currentPlayerCount) {
            val playerSample = if (config.showPlayerList && currentPlayerCount > 0) {
                universe.players.map { player ->
                    StatusResponse.PlayerSample(
                        name = player.username,
                        id = player.uuid.toString()
                    )
                }
            } else {
                emptyList()
            }

            val response = StatusResponse(
                version = StatusResponse.Version(
                    name = getServerVersion(),
                    protocol = 0
                ),
                players = StatusResponse.Players(
                    max = getMaxPlayers(),
                    online = currentPlayerCount,
                    sample = if (playerSample.isEmpty()) null else playerSample
                ),
                description = StatusResponse.Description(text = config.motd)
            )

            gson.toJson(response)
        }

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

        val response = "\u00A7\u0031\u0000" +
                "0\u0000" +
                "${getServerVersion()}\u0000" +
                "${config.motd}\u0000" +
                "${universe.playerCount}\u0000" +
                "${getMaxPlayers()}"

        val responseBuf = Unpooled.buffer()
        responseBuf.writeByte(0xFF)
        MinecraftProtocol.writeStringUTF16BE(responseBuf, response)

        ctx.writeAndFlush(responseBuf).addListener(ChannelFutureListener.CLOSE)
        logger.log(Level.FINE, "Sent legacy extended ping response to ${ctx.channel().remoteAddress()}")
    }

    private fun handleLegacyBasicPing(ctx: ChannelHandlerContext) {
        val config = plugin.config
        val universe = Universe.get()

        val response = "${config.motd}\u00A7${universe.playerCount}\u00A7${getMaxPlayers()}"

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
