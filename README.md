# PlayerLog

A lightweight Minecraft server-side mod that monitors player events and sends notifications to server operators and spectators.

## Features

- **Damage notifications**
- **Low-health notifications**
- **Drowning detection**
- **Creeper threat detection**
- **Fire, fall, and projectile damage notifications**
- Configurable notification recipients
- Configurable cooldowns
- Clickable teleport button
- JSON configuration

## Commands

| Command | Description |
| :--- | :--- |
| `/pl` | Show PlayerLog status |
| `/pl help` | Show available commands |
| `/pl enable` | Enable PlayerLog |
| `/pl disable` | Disable PlayerLog |
| `/pl reload` | Reload the configuration |
| `/pl tp <player>` | Teleport to a player |

## Configuration

The configuration file is located at `config/PlayerLog.json`.

Configuration options include:

- Notification types
- Minimum damage
- Low-health threshold
- Creeper detection range
- Drowning health threshold
- Notification recipients
- Notification cooldowns
- Coordinates
- Teleport buttons

## Requirements

- **Minecraft:** 26.2
- **Loader:** Fabric Loader
- **API:** Fabric API

## License

This project is licensed under the [MIT License](LICENSE).
