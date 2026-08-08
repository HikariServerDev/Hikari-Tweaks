[🇯🇵 日本語 (README-ja.md)](README-ja.md) | **🇬🇧 English**

---

# Hikari-Tweaks

> **A client-side Fabric utility mod for Minecraft 1.18.2**, developed at [Hikari Server (光鯖)](https://hikariserver.com).

### Requirements
| Mod | Type |
|---|---|
| [Fabric Loader](https://fabricmc.net/) `>=0.14.0` | **Required** |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **Required** |
| [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) | **Required** |
| [MiniHUD](https://www.curseforge.com/minecraft/mc-mods/minihud) | Optional (beacon fix feature) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Optional (GUI config screen) |

---


## 1. Main Features

### 1.1 MiniHUD Fix (Freecam Beacon Fix)

- While MiniHUD's freecam is active, corrects the beacon range display to use the player's actual position instead of the camera position
- Toggleable (hotkey supported)

### 1.2 Durability 1% Warning

- When a durable item drops to 1% or lower, notifies via chat + sound
- Suppresses duplicate notifications for the same item state
- Toggleable (hotkey supported)

### 1.3 Hotbar Auto-Restock

- When you open a container, automatically restocks specified items from the container into your hotbar
- Toggleable (hotkey supported)

Notes:

- Doesn't run on the player inventory screen
- Doesn't run when opening an ender chest
- Automatically closes the screen after restocking

### 1.4 Totem of Undying Auto-Restock

- When a totem is used, restocks another one into the slot it popped from
- Adjusted so normal slot switches don't trigger it
- Toggleable (hotkey supported)

### 1.7 Hand Auto-Restock (new in v1.0.6)

- When any hotbar item drops to **5 or fewer**, automatically restocks from your inventory
- Behaves like Tweakeroo's handrestock; the target list is managed in the **Hand Restock List** on the Lists tab
- Monitors only the hotbar; the inventory is used only as a restock source
- Toggleable (hotkey supported)

### 1.6 Custom Scoreboard HUD (HikariScoreBoard integration) — rendered as a client HUD

- Toggle to hide the vanilla scoreboard
- Change page size (1–50)
- Next / previous page / reset
- HUD position (X/Y %) and scale (0.5x–3.0x) adjustment
- Header / body / text / score / self-highlight colors adjustable in ARGB
- Toggle for server total (Total) display
- Player management tab (block-toggle for display)

---

## 2. Requirements

- Minecraft `1.18.2`
- Fabric Loader `>= 0.14.0`
- Fabric API
- malilib

Recommended (optional):

- Mod Menu (for easier access to the config screen)
- MiniHUD (for the beacon range fix feature)
- HikariScoreBoard (for the custom scoreboard integration)

---

## 3. Installation

1. Place `build/libs/hikari-tweaks-<version>.jar` in the client's `mods/`
2. Place the required mods (Fabric API / malilib) similarly
3. Launch the game
4. `config/hikari-tweaks.json` is generated on first launch

---

## 4. Usage

### 4.1 Open the config screen

- Default hotkey: `RIGHT_SHIFT`
- Alternatively, open the `Hikari-Tweaks` config screen via Mod Menu

### 4.2 Config tabs

- `Tweaks`: on/off toggles for each feature
- `Lists`: item ID list for hotbar auto-restock
- `Hotkeys`: key bindings for feature toggles and opening the config screen
- `Scoreboard`: scoreboard integration / display settings / player management

---

## 5. Main Settings (defaults)

| Key | Default | Description |
|---|---:|---|
| `fixBeaconRangeFreeCam` | `true` | MiniHUD beacon range fix |
| `durabilityWarningEnabled` | `true` | Durability 1% warning |
| `autoRestockHotbar` | `false` | Hotbar auto-restock |
| `totemRestock` | `false` | Totem auto-restock |
| `handRestock` | `false` | Hand auto-restock |
| `hotbarRestockList` | `minecraft:firework_rocket`, `minecraft:golden_carrot` | Auto-restock target list |
| `openConfigHotkey` | `RIGHT_SHIFT` | Key to open the config screen |
| `scoreboardCustomHud` | `true` | Show custom HUD |
| `scoreboardHideVanilla` | `true` | Hide the vanilla right-side scoreboard |
| `scoreboardPageSize` | `10` | Rows per page (1–50) |
| `scoreboardPositionX` | `100` | HUD anchor X (0–100%) |
| `scoreboardPositionY` | `50` | HUD anchor Y (0–100%) |
| `scoreboardScale` | `1.0` | HUD scale (0.5–3.0) |
| `scoreboardHeaderColor` | `0x66000000` | Header background color (ARGB) |
| `scoreboardBodyColor` | `0x4D000000` | Body background color (ARGB) |
| `scoreboardTextColor` | `0xFFFFFFFF` | Text color (ARGB) |
| `scoreboardScoreColor` | `0xFFFF5555` | Score color (ARGB) |
| `scoreboardSelfColor` | `0xFFFFFF55` | Self-row highlight color (ARGB) |
| `scoreboardShowServerTotal` | `true` | Show server total |

---

## 6. Hotkeys

- `Open Config`: default `RIGHT_SHIFT`
- `fixBeaconRangeFreeCam`: unassigned (set as needed)
- `durabilityWarningEnabled`: unassigned (set as needed)
- `autoRestockHotbar`: unassigned (set as needed)
- `totemRestock`: unassigned (set as needed)
- `handRestock`: unassigned (set as needed)

---

## 7. HikariScoreBoard Integration Specification

`Hikari-Tweaks` communicates with `HikariScoreBoard` via the following channels.

Receive:

- `hikariscoreboard:ranking_data`
- `hikariscoreboard:player_list_response`

Send:

- `hikariscoreboard:player_list_request`
- `hikariscoreboard:block_toggle`

---

## 8. Config File

Location: `config/hikari-tweaks.json`

- Saved in JSON format
- Missing fields are auto-supplemented on startup
- Auto-saved when settings or hotkeys change

---

## 9. Build

```bash
./gradlew build
```

Outputs:

- `build/libs/hikari-tweaks-<version>.jar`
- `build/libs/hikari-tweaks-<version>-sources.jar`

---

## 10. Known Behaviors / Notes

- Auto-restock features perform slot clicks as client-side operations
- Hotbar auto-restock runs when a container is opened, closing the screen after completion
- Custom scoreboard HUD is not drawn while the F3 debug screen is shown

---

## 11. License

**GNU Lesser General Public License v3.0 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 Hikari Server


See the bundled [LICENSE](LICENSE) file and the following URLs for details:
- https://www.gnu.org/licenses/lgpl-3.0.txt
- https://www.gnu.org/licenses/gpl-3.0.txt

---

## 12. Credits

Developed at: **[Hikari Server (光鯖)](https://hikariserver.com)** — a Minecraft Java Edition community server

- **Maintainer**: [Tamago0314](https://github.com/Tamago0314)


### Dependencies / Reference Implementations

**masa** (fi.dy.masa)
- [MaLiLib](https://github.com/maruohon/malilib) — LGPLv3
- [MiniHUD](https://github.com/maruohon/minihud) — LGPLv3
- [Tweakeroo](https://github.com/maruohon/tweakeroo) — LGPLv3


**Sim-hu** (ASTRAL-SMP) — [AST-Tweaks](https://github.com/ASTRAL-SMP/AST-Tweaks) (Apache-2.0)


**pugur** — [ama-tweaks](https://github.com/pugur523/ama-tweaks) (MIT)
