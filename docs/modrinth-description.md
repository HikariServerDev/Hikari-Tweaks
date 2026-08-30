<div align="center">

<img src="https://hikariserver.com/assets/logo.png" width="200" alt="Hikari-Tweaks">

# Hikari-Tweaks

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](https://github.com/HikariServerDev/Hikari-Tweaks/blob/main/LICENSE)
[![Modrinth](https://img.shields.io/modrinth/dt/hikari-tweaks?label=Modrinth%20Downloads)](https://modrinth.com/mod/hikari-tweaks)

</div>

**A client-side Fabric utility mod for Minecraft 1.17.1 - 1.21.11**, developed at [Hikari Server (光鯖)](https://hikariserver.com). Provides a fully customizable custom scoreboard HUD (paired with HikariScoreBoard) plus a set of quality-of-life features.

## Features


### tweakCustomScoreboardHud

> Renders [HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard) rankings as a fully customizable HUD. Configurable position (anchor X/Y 0-100%), scale (0.5x-3.0x), pagination (1-50 rows per page), and independent ARGB colors for header, body, text, score, and self-highlight rows. Hides the vanilla sidebar by default.
<br><br>

### tweakDurabilityWarning

> Warns via chat notification and sound when any equipped tool drops to 1% durability. Duplicate warnings for the same item state are suppressed. Toggle via hotkey.
<br><br>

### tweakAutoRestockHotbar

> When you open a container (chest, shulker-box, etc.), automatically restocks the specified items from the container into your hotbar. Configurable list in the Lists tab. Excludes player inventory / ender chest to avoid interference. Auto-closes the screen after restocking.
<br><br>

### tweakTotemRestock

> When a Totem of Undying is used, restocks another one from your inventory into the same hand that popped it. Does not trigger on normal hotkey slot switches.
<br><br>

### tweakHandRestock

> Tweakeroo-style handrestock: when any item on your hotbar drops to 5 or fewer, automatically pulls a replacement from your inventory. The restock list is separate from the container-open list for granular control.
<br><br>

### tweakBeaconRangeFreeCamFix

> When using MiniHUD's freecam, corrects the beacon range display to use the player's actual position instead of the camera position. Requires MiniHUD.
<br><br>

### tweakScoreboardPlayerManagement

> When paired with HikariScoreBoard, provides a chest-based GUI to manage which players are shown in rankings (block/unblock). Bot players from Carpet are also visually distinguished in the list.
<br><br>

## Requirements

- Minecraft **1.17.1 - 1.21.11** (Fabric)
- **Fabric Loader** >= 0.14.0 (MC 1.17.1 - 1.20.4) / >= 0.15.10 (MC 1.20.5 and newer)
- **Fabric API**
- **[MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)** — a per-jar minimum version is declared; the build target version of MaLiLib (or newer) is required

### Supported versions

One jar is published per Minecraft version group. Download the one matching your game.

| Jar | Supported Minecraft versions |
|---|---|
| `hikari-tweaks-<version>+1.17.1.jar` | 1.17.1 |
| `hikari-tweaks-<version>+1.18.1.jar` | 1.18, 1.18.1 |
| `hikari-tweaks-<version>+1.18.2.jar` | 1.18.2 |
| `hikari-tweaks-<version>+1.19.2.jar` | 1.19, 1.19.1, 1.19.2 |
| `hikari-tweaks-<version>+1.19.3.jar` | 1.19.3 |
| `hikari-tweaks-<version>+1.19.4.jar` | 1.19.4 |
| `hikari-tweaks-<version>+1.20.1.jar` | 1.20, 1.20.1 |
| `hikari-tweaks-<version>+1.20.2.jar` | 1.20.2 |
| `hikari-tweaks-<version>+1.20.4.jar` | 1.20.3, 1.20.4 |
| `hikari-tweaks-<version>+1.20.6.jar` | 1.20.5, 1.20.6 |
| `hikari-tweaks-<version>+1.21.1.jar` | 1.21, 1.21.1 |
| `hikari-tweaks-<version>+1.21.3.jar` | 1.21.2, 1.21.3 |
| `hikari-tweaks-<version>+1.21.4.jar` | 1.21.4 |
| `hikari-tweaks-<version>+1.21.5.jar` | 1.21.5 |
| `hikari-tweaks-<version>+1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 |
| `hikari-tweaks-<version>+1.21.10.jar` | 1.21.9, 1.21.10 |
| `hikari-tweaks-<version>+1.21.11.jar` | 1.21.11 |

## Opening the config screen

Press **`H` + `T`** in game — hold `H`, then press `T`. The order matters.

The key can be changed from the **Hotkeys** tab of the config screen, or through
[Mod Menu](https://modrinth.com/mod/modmenu) if you prefer to reach it that way.
Scoreboard page-turning hotkeys are unbound by default and can be assigned there too.

## Optional

- **[MiniHUD](https://www.curseforge.com/minecraft/mc-mods/minihud)** — Required for the beacon range fix feature
- **[Mod Menu](https://modrinth.com/mod/modmenu)** — An alternative way to reach the config screen, if you would rather not use the hotkey
- **[HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard)** (server-side) — Enables the custom scoreboard HUD data source. Also works without it, but with no ranking data displayed.

## Credits

Developed at: **[Hikari Server (光鯖)](https://hikariserver.com)** — a Minecraft Java Edition community server

- **Maintainer**: [Tamago0314](https://github.com/Tamago0314)

### Dependencies / Reference Implementations

**masa** (fi.dy.masa)
- [MaLiLib](https://github.com/maruohon/malilib) — LGPLv3
- [MiniHUD](https://github.com/maruohon/minihud) — LGPLv3
- [Tweakeroo](https://github.com/maruohon/tweakeroo) — LGPLv3

**Sim-hu** (ASTRAL-SMP) — [AST-Tweaks](https://github.com/ASTRAL-SMP/AST-Tweaks) (Apache-2.0)

**pugur** — [ama-tweaks](https://github.com/pugur523/ama-tweaks) (MIT)

## License

**GNU Lesser General Public License v3 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 Hikari Server

See the bundled `LICENSE` file for details. Referenced third-party projects retain their own licenses (see `NOTICE`).
