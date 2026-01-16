package com.rixafy.pingprotocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hypixel.hytale.server.core.Options
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import javax.annotation.Nonnull

class PingProtocolPlugin(@Nonnull init: JavaPluginInit) : JavaPlugin(init) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var configData: PingProtocolConfig
    private var pingServer: PingServer? = null

    val config: PingProtocolConfig
        get() = configData

    override fun setup() {
        logger.at(Level.INFO).log("PingProtocol plugin loading...")
        loadConfig()
    }

    override fun start() {
        try {
            val port = getGamePort()

            // Start ping server
            pingServer = PingServer(this, java.util.logging.Logger.getLogger("PingProtocol"), port)
            pingServer?.start()

            logger.at(Level.INFO).log("PingProtocol started successfully")
            logger.at(Level.INFO).log("  - Protocol: Minecraft Server List Ping (TCP)")
            logger.at(Level.INFO).log("  - Port: $port (same as game server)")
            logger.at(Level.INFO).log("  - MOTD: ${if (configData.motd.isEmpty()) "(empty)" else configData.motd}")
            logger.at(Level.INFO).log("  - Show player list: ${configData.showPlayerList}")

        } catch (e: Exception) {
            logger.at(Level.SEVERE).log("Failed to start PingProtocol: ${e.message}", e)
        }
    }

    override fun shutdown() {
        try {
            pingServer?.shutdown()
            pingServer = null
            logger.at(Level.INFO).log("PingProtocol plugin stopped")
        } catch (e: Exception) {
            logger.at(Level.WARNING).log("Error during shutdown: ${e.message}")
        }
    }

    private fun loadConfig() {
        val configPath: Path = dataDirectory.resolve("config.json")
        val defaults = PingProtocolConfig()

        try {
            if (Files.exists(configPath)) {
                val json = Files.readString(configPath)
                configData = gson.fromJson(json, PingProtocolConfig::class.java)
                logger.at(Level.INFO).log("Loaded configuration from $configPath")
            } else {
                configData = defaults
                Files.createDirectories(configPath.parent)
                Files.writeString(configPath, gson.toJson(configData))
                logger.at(Level.INFO).log("Created default configuration at $configPath")
            }
        } catch (e: Exception) {
            logger.at(Level.WARNING).log("Failed to load/save config, using defaults: ${e.message}")
            configData = defaults
        }
    }

    private fun getGamePort(): Int {
        return try {
            Options.getOptionSet().valueOf(Options.BIND).port
        } catch (e: Exception) {
            25565 // Default Minecraft port
        }
    }

    companion object {
        private lateinit var instance: PingProtocolPlugin

        fun get(): PingProtocolPlugin = instance
    }

    init {
        instance = this
    }
}
