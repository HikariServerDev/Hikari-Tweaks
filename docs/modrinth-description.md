<div align="center">

<img src="https://hikariserver.com/assets/logo.png" width="200" alt="Hikari-Tweaks">

# Hikari-Tweaks

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](https://github.com/HikariServerDev/Hikari-Tweaks/blob/main/COPYING.LESSER)
[![Modrinth](https://img.shields.io/modrinth/dt/hikari-tweaks?label=Modrinth%20Downloads)](https://modrinth.com/mod/hikari-tweaks)

</div>

**A client-side Fabric utility mod for Minecraft 1.17.1 - 1.21.11**, developed at [Hikari Server (光鯖)](https://hikariserver.com). Provides a fully customizable custom scoreboard HUD (paired with HikariScoreBoard) plus a set of quality-of-life features.

## What changed in 1.2.1

**All text in this mod's config screens was invisible on Minecraft 1.21.6 and newer.** 1.21.6
stopped filling in the alpha channel of text colours and now discards any text drawn with an
alpha of 0, so the player names, type labels, group headers and captions in the config screens
were never drawn at all. The in-game HUD was unaffected. If you play Minecraft 1.21.6 - 1.21.11
and run v1.1.0 or v1.2.0 — the `+1.21.8`, `+1.21.10` and `+1.21.11` jars — update to v1.2.1.
Nothing changes on 1.21.5 and older.

## Features


### Custom Scoreboard HUD

> Renders [HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard) rankings as a fully customizable HUD. Configurable position (anchor X/Y 0-100%), scale (0.5x-3.0x), pagination (1-50 rows per page), and independent ARGB colors for header, body, text, score, and self-highlight rows. Hides the vanilla sidebar by default. Not drawn while the F3 debug screen is open or the HUD is hidden with F1. Config key `scoreboardCustomHud`, on by default: it lives in `config/hikari-tweaks.json` and has no toggle in the config screen. Position, scale, page size and colors are edited from the **Scoreboard** tab.
<br><br>

### Smooth Score Animation (always on, no setting)

> Numbers on the custom HUD ease towards their new value instead of jumping. The smoothing runs entirely on the client inside this mod — nothing extra goes over the network for it, and the old server-side smoother has been removed — so **scores only animate for players who have Hikari-Tweaks installed**. It is always on and has no setting. Real-time exponential easing (~0.12 s time constant) keeps the speed identical at any frame rate, and a gap of more than 0.5 s between frames (window unfocused, world loading) snaps straight to the real value instead of replaying the animation. Only the drawn digits are eased: sorting and ranks always come from the real values, so rows never swap places mid-animation, and a row that has just appeared shows its real value immediately rather than counting up from zero. Applies to the ranking rows and to the server total line. Requires the v2 ranking protocol — a server still sending v1 packets gets exact values with no animation.
<br><br>

### Durability Warning

> Warns via chat notification and sound when a damageable item drops to 1% durability or lower. Every slot of your inventory is checked, not just the item in your hand. The warning fires once when an item enters the warning state and re-arms only after it leaves it, so repairing and re-damaging warns again while grinding down the last percent does not spam. Config key `durabilityWarningEnabled` (**Tweaks** tab, on by default).
<br><br>

### Hotbar Auto-Restock

> When you open a real container block, automatically restocks the items on your list from that container into your hotbar, then closes the screen. The list is configurable in the Lists tab. Detection deliberately fails closed: it only fires when your crosshair is on a block whose block entity holds an inventory, that block entity is not an ender chest, and the screen's container slot count matches that inventory's size — a chest, and only a chest, may also be exactly double, so double chests work. Chests, trapped chests, barrels, placed shulker boxes, hoppers, dispensers, droppers, furnaces and brewing stands therefore qualify. Ender chests are excluded outright. Chest and hopper minecarts, chest boats and villager trades are excluded because they are entities rather than block entities, so there is no block entity under the crosshair. Plugin menus opened from a command, an NPC or an item are excluded for the same reason. A plugin menu opened by right-clicking a real container **with the same slot count** cannot be told apart on the client, so keep the list narrow on servers with chest-backed shop GUIs. Config key `autoRestockHotbar` (**Tweaks** tab, off by default).
<br><br>

### Totem Restock

> When a Totem of Undying is used, restocks another one from your inventory into the same hand that popped it. Does not trigger on normal hotkey slot switches. Config key `totemRestock` (**Tweaks** tab, off by default).
<br><br>

### Hand Auto-Restock

> Tweakeroo-style handrestock: when a listed item on your hotbar drops to 5 or fewer, automatically pulls a replacement from the rest of your inventory. Only the hotbar is watched; the inventory is used as a source only. The restock list is separate from the container-open list for granular control. Config key `handRestock` (**Tweaks** tab, off by default).
<br><br>

### MiniHUD Beacon Fix

> When using MiniHUD's freecam, corrects the beacon range display to use the player's actual position instead of the camera position. Requires MiniHUD. Config key `fixBeaconRangeFreeCam` (**Tweaks** tab, on by default).
<br><br>

### Scoreboard Tab: Players (a config-screen tab, not a setting)

> When paired with HikariScoreBoard, the **Scoreboard** tab of the in-game config screen lists the players the server knows about, split into Shown and Hidden groups, and lets you toggle whether each one appears in the rankings. Entries the server flags as bots are labelled `[Bot]` so they are easy to pick out. The list sits on the **Players** sub-tab; the **Display** sub-tab beside it holds the HUD's page size, scale, position and colors. There is no on/off setting for it.
<br><br>

## Requirements

- Minecraft **1.17.1 - 1.21.11** (Fabric)
- **Fabric Loader** >= 0.14.0 (MC 1.17.1 - 1.20.4) / >= 0.15.10 (MC 1.20.5 and newer)
- **Fabric API**
- **[MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)** — each jar declares its own minimum, listed in the table below

### Supported versions

One jar is published per Minecraft version group. Download the one matching your game.

| Jar | Supported Minecraft versions | Minimum MaLiLib |
|---|---|---|
| `hikari-tweaks-<version>+1.17.1.jar` | 1.17.1 | 0.10.0-dev.26 |
| `hikari-tweaks-<version>+1.18.1.jar` | 1.18, 1.18.1 | 0.11.8 |
| `hikari-tweaks-<version>+1.18.2.jar` | 1.18.2 | 0.12.0 |
| `hikari-tweaks-<version>+1.19.2.jar` | 1.19, 1.19.1, 1.19.2 | 0.13.0 |
| `hikari-tweaks-<version>+1.19.3.jar` | 1.19.3 | 0.14.1-pre.1 |
| `hikari-tweaks-<version>+1.19.4.jar` | 1.19.4 | 0.15.4 |
| `hikari-tweaks-<version>+1.20.1.jar` | 1.20, 1.20.1 | 0.16.3 |
| `hikari-tweaks-<version>+1.20.2.jar` | 1.20.2 | 0.17.0 |
| `hikari-tweaks-<version>+1.20.4.jar` | 1.20.3, 1.20.4 | 0.18.4-alpha.1 |
| `hikari-tweaks-<version>+1.20.6.jar` | 1.20.5, 1.20.6 | 0.19.2 |
| `hikari-tweaks-<version>+1.21.1.jar` | 1.21, 1.21.1 | 0.21.10 |
| `hikari-tweaks-<version>+1.21.3.jar` | 1.21.2, 1.21.3 | 0.22.8 |
| `hikari-tweaks-<version>+1.21.4.jar` | 1.21.4 | 0.23.5 |
| `hikari-tweaks-<version>+1.21.5.jar` | 1.21.5 | 0.24.3 |
| `hikari-tweaks-<version>+1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 | 0.25.7 |
| `hikari-tweaks-<version>+1.21.10.jar` | 1.21.9, 1.21.10 | 0.26.8 |
| `hikari-tweaks-<version>+1.21.11.jar` | 1.21.11 | 0.27.17 |

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

Copyright (C) 2025-2026 HikariServerDev

LGPL-3.0 layers additional permissions on top of GPL-3.0 and incorporates it by
reference, so both texts are distributed with the project as `COPYING.LESSER` (LGPL)
and `COPYING` (GPL); every jar carries a copy of both in `META-INF/`. Referenced
third-party projects retain their own licenses (see `NOTICE`).

Every jar also embeds Gson 2.10.1 under `META-INF/jars/`, unmodified and under its own
Apache License 2.0. Its copyright notices and the full license text ship in the same jar as
`META-INF/THIRD-PARTY-NOTICES.txt` and `META-INF/licenses/Apache-2.0.txt`.
