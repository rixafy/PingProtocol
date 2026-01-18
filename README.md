# PingProtocol - Minecraft Server List Ping for Hytale

A Hytale plugin that implements the Minecraft Server List Ping protocol, allowing Minecraft clients to query your Hytale server information and display it in their server list.

## ✨ Features

- 🔌 **Full Protocol Support** — Modern (1.7+), Legacy (1.6), and Beta (1.8-1.3) protocols
- 👥 **Live Player Info** — Real-time player count, max slots, and player list with UUIDs
- 🎨 **Customizable Responses** — MOTD, version name, player sample, and favicon
- 🎯 **Event API** — Intercept and modify ping responses dynamically via `PingEvent`
- ⚡ **High Performance** — Smart caching with sub-10ms response times
- 🔧 **Zero Port Config** — Runs on same port as game server (TCP alongside UDP)

## How It Works

This plugin creates a TCP server that runs alongside Hytale's QUIC (UDP) game server. The key insight is that **TCP and UDP are separate protocols**, so they can both listen on the same port number without conflicts:

- **Hytale Server**: Uses UDP/QUIC protocol on port 5520 (or configured port) for game traffic
- **PingProtocol Plugin**: Uses TCP on the same port number for Minecraft ping requests

This allows Minecraft clients to ping your Hytale server without any port forwarding or additional network configuration.

## Installation

1. Download `PingProtocol-1.0.0.jar` from releases
2. Place it in your Hytale server's `mods/` directory
3. Start your server or type `/plugin load Hytalist:PingProtocol`
4. Configure in `mods/Hytalist_PingProtocol/config.json` if needed

## Configuration

The plugin creates a `config.json` file in `mods/Hytalist_PingProtocol/`:

```json
{
  "motd": "",
  "showPlayerList": true
}
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `motd` | `""` | Message of the Day displayed to Minecraft clients (empty string if not set) |
| `showPlayerList` | `true` | Show player list when hovering over player count |

The plugin automatically:
- Uses the same port as your Hytale server (TCP/UDP can share the same port)
- Reports protocol version 0 to indicate a non-Minecraft server
- Reads the server version from Hytale's manifest and strips build hash (e.g., "2026.01.13-dcad8778f" becomes "2026.01.13")

## 🎯 PingEvent API

The `PingEvent` allows you to intercept ping requests and customize the response dynamically. This is useful for:

- 🎭 Showing different MOTDs based on time of day or server state
- 📊 Displaying custom player counts or fake slots
- 👤 Adding custom entries to the player sample list
- 🖼️ Setting a dynamic favicon
- 🚫 Hiding certain players from the list

### Event Properties

| Property | Type | Description |
|----------|------|-------------|
| `clientAddress` | `InetSocketAddress?` | Client's IP address and port (read-only) |
| `protocolVersion` | `Int` | Protocol version from client (read-only) |
| `isLegacy` | `Boolean` | Whether this is a legacy ping (read-only) |
| `motd` | `String` | Message of the Day |
| `onlinePlayers` | `Int` | Number of online players |
| `maxPlayers` | `Int` | Maximum player slots |
| `versionName` | `String` | Server version displayed to clients |
| `versionProtocol` | `Int` | Protocol version (0 = non-Minecraft) |
| `playerSample` | `MutableList<PlayerInfo>` | Players shown on hover |
| `favicon` | `String?` | Base64-encoded 64x64 PNG |

### Kotlin Example

```kotlin
import com.rixafy.pingprotocol.PingEvent

class MyPlugin : JavaPlugin(init) {

    override fun start() {
        // Register a ping event listener
        PingEvent.addListener { event ->
            // Customize the MOTD
            event.motd = "§aWelcome to My Server!\n§7Currently ${event.onlinePlayers} players online"

            // Modify player count display
            event.maxPlayers = 500

            // Add a custom entry to the player sample (shown on hover)
            event.playerSample.add(
                PingEvent.PlayerInfo(
                    name = "§eNext restart: 2 hours",
                    uuid = "00000000-0000-0000-0000-000000000000"
                )
            )

            // Set favicon (base64 PNG, 64x64)
            event.favicon = "data:image/png;base64,iVBORw0KGgo..."

            // Access client info
            val clientIp = event.clientAddress?.address?.hostAddress
            println("Ping from: $clientIp (protocol: ${event.protocolVersion})")
        }
    }

    override fun shutdown() {
        // Clean up listeners
        PingEvent.clearListeners()
    }
}
```

### Java Example

```java
import com.rixafy.pingprotocol.PingEvent;

public class MyPlugin extends JavaPlugin {

    private PingEvent.Listener pingListener;

    @Override
    public void start() {
        // Create and register a ping event listener
        pingListener = event -> {
            // Customize the MOTD
            event.setMotd("§aWelcome to My Server!\n§7Currently " + event.getOnlinePlayers() + " players online");

            // Modify player count display
            event.setMaxPlayers(500);

            // Add a custom entry to the player sample (shown on hover)
            event.getPlayerSample().add(
                new PingEvent.PlayerInfo(
                    "§eNext restart: 2 hours",
                    "00000000-0000-0000-0000-000000000000"
                )
            );

            // Set favicon (base64 PNG, 64x64)
            event.setFavicon("data:image/png;base64,iVBORw0KGgo...");

            // Access client info
            if (event.getClientAddress() != null) {
                String clientIp = event.getClientAddress().getAddress().getHostAddress();
                System.out.println("Ping from: " + clientIp + " (protocol: " + event.getProtocolVersion() + ")");
            }
        };

        PingEvent.Companion.addListener(pingListener);
    }

    @Override
    public void shutdown() {
        // Remove the listener
        PingEvent.Companion.removeListener(pingListener);
    }
}
```

### 💡 Tips

- **Legacy pings** (`event.isLegacy == true`) only support `motd`, `onlinePlayers`, `maxPlayers`, and `versionName`. Player sample and favicon are ignored.
- **Player sample** entries can use Minecraft color codes (§) for colored text in the hover tooltip.
- **Favicon** must be a base64-encoded PNG image exactly 64x64 pixels.
- **Multiple listeners** are supported — all listeners are called in registration order.

## Protocol Support

### Modern Protocol (Minecraft 1.7+)

The plugin implements the full modern Server List Ping protocol:

1. **Handshake Packet** (0x00): Client sends protocol version, server address, port, and next state
2. **Status Request** (0x00): Client requests server status
3. **Status Response** (0x00): Server sends JSON with version, players, description
4. **Ping Request** (0x01): Client sends timestamp
5. **Pong Response** (0x01): Server echoes timestamp and closes connection

Example JSON response:
```json
{
  "version": {
    "name": "2026.01.13",
    "protocol": 0
  },
  "players": {
    "max": 100,
    "online": 5,
    "sample": [
      {
        "name": "PlayerName",
        "id": "uuid-here"
      }
    ]
  },
  "description": {
    "text": "My Hytale Server"
  }
}
```

### Legacy Protocol (Minecraft 1.6)

The plugin also supports legacy ping protocol for older clients:

- Detects `0xFE 0x01 0xFA` magic bytes
- Responds with `0xFF` kick packet
- Format: `§1\0protocol\0version\0motd\0online\0max`

### Very Old Protocol (Beta 1.8 - 1.3)

Supports the oldest ping protocol:

- Detects `0xFE` magic byte
- Responds with `0xFF` kick packet
- Format: `motd§online§max`

## Performance

The plugin is optimized for low-latency responses:

- **Response Caching**: Status responses are cached for 1 second
- **Smart Invalidation**: Cache is invalidated when player count changes
- **Typical Response Times**:
  - First request (cache miss): ~10-50ms
  - Cached requests: <10ms
  - Cache avoids expensive operations like fetching full player list and JSON serialization on every ping

## Technical Details

### Architecture

The plugin uses Netty (already available in Hytale server) to create a TCP server:

1. **PingServer**: Bootstrap and manages the Netty server
2. **MinecraftPingHandler**: Stateful handler that processes packets
3. **StatusCache**: Shared cache for status responses to minimize expensive operations
4. **MinecraftProtocol**: Utilities for VarInt encoding/decoding and packet reading/writing
5. **StatusResponse**: Data classes for JSON serialization

### Why This Works

Minecraft Server List Ping uses **TCP**, while Hytale uses **QUIC** which runs over **UDP**. Since these are different transport protocols, they can both bind to the same port number:

- TCP packets → Handled by PingProtocol plugin
- UDP packets → Handled by Hytale's QUIC server

This is similar to how the HyQuery plugin (shown in `EXAMPLE_INTERCEPT.md`) works, except:
- HyQuery intercepts UDP packets *before* the QUIC codec
- PingProtocol runs a separate TCP server alongside Hytale

## Building from Source

```bash
./gradlew jar
```

The JAR will be created in `build/libs/PingProtocol-1.0.0.jar`.

## Troubleshooting

### "Failed to start ping server on port X"

**Cause**: Another TCP service is using the port

**Solution**:
- Check if another service is bound to the TCP port
- Ensure your firewall allows TCP connections on the port
- Note: The plugin uses the same port number as Hytale but on TCP (Hytale uses UDP)

### Minecraft client shows "Can't connect to server"

**Cause**: Firewall blocking TCP connections or plugin not started

**Solution**:
- Verify the plugin started successfully in server logs
- Check firewall allows TCP traffic on the server port
- Try adding server using IP:port format in Minecraft

### Player list not showing

**Cause**: Config setting or no players online

**Solution**:
- Set `"showPlayerList": true` in config
- Ensure players are actually online on the Hytale server
- Player list is only shown when `currentPlayerCount > 0`

### Slow ping response times

**Cause**: Cache not working or first request

**Solution**:
- First request builds the cache and may take 10-50ms
- Subsequent requests should be <10ms
- Cache invalidates every second or when player count changes
- If consistently slow, check Hytale server performance

## Credits

- Implementation based on [Minecraft Server List Ping specification](https://minecraft.wiki/w/Java_Edition_protocol/Server_List_Ping)
