package com.rixafy.pingprotocol

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.util.logging.Level
import java.util.logging.Logger

class PingServer(
    private val plugin: PingProtocolPlugin,
    private val logger: Logger,
    private val port: Int
) {
    private var bossGroup: NioEventLoopGroup? = null
    private var workerGroup: NioEventLoopGroup? = null
    private var channelFuture: ChannelFuture? = null

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast("ping-handler", MinecraftPingHandler(plugin, logger))
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.TCP_NODELAY, true)

            channelFuture = bootstrap.bind(port).sync()
            logger.log(Level.INFO, "Minecraft ping protocol enabled on TCP port $port")

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to start ping server on port $port: ${e.message}")
            shutdown()
            throw e
        }
    }

    fun shutdown() {
        try {
            channelFuture?.channel()?.close()?.sync()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error closing channel: ${e.message}")
        }

        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()

        workerGroup = null
        bossGroup = null
        channelFuture = null

        logger.log(Level.INFO, "Ping server stopped")
    }
}
