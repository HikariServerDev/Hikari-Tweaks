[🇯🇵 日本語 (README-ja.md)](README-ja.md) | **🇬🇧 English**

---

# Hikari-Tweaks

> **A client-side Fabric utility mod for Minecraft 1.17.1 - 1.21.11**, developed at [Hikari Server (光鯖)](https://hikariserver.com).

### Requirements
| Mod | Type |
|---|---|
| [Fabric Loader](https://fabricmc.net/) `>=0.14.0` (MC 1.17.1 - 1.20.4) / `>=0.15.10` (MC 1.20.5 and newer) | **Required** |
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

- When you open a **real container block**, automatically restocks the items on your restock list from that container into your hotbar
- Toggleable (hotkey supported)
- Detection deliberately fails closed. Restocking only runs when **all** of the following hold:
  1. Your crosshair is on a block that the client knows has a block entity, and that block entity holds an inventory
  2. That block entity is not an ender chest
  3. The number of container slots in the open screen equals that block entity's inventory size — a chest (and only a chest) may also be exactly double, so double chests work

What that means in practice:

| Opened by | Restocks? | Why |
|---|---|---|
| Chest / trapped chest, single or double | Yes | Real block entity; a chest is allowed to be exactly 2x its own size |
| Barrel, shulker box **placed as a block** | Yes | Real block entity, slot count matches |
| Hopper, dispenser, dropper, furnace family, brewing stand | Yes | Same rule — any block entity whose inventory size matches the screen qualifies |
| Ender chest | No | Excluded explicitly: its contents are per-player and servers often swap them out |
| Chest / hopper minecart, chest boat, villager trading | No | Entities, not block entities — there is no block entity under the crosshair |
| Crafting table, enchanting table, anvil | No | No block entity holding an inventory |
| Your own inventory screen | No | Rejected before detection runs |
| Plugin menu opened by a command, an NPC or an item | No | Nothing under the crosshair to confirm it against |
| Plugin menu opened by right-clicking a real container, **with a matching slot count** | Yes — not detectable | Identical to the real block from the client's point of view; see below |

Caveat: the slot-count check catches the common mismatch (right-clicking a 27-slot chest and getting a 54-slot shop GUI), but a virtual GUI that happens to use exactly the size of the block it was opened from cannot be told apart on the client. On servers with chest-backed menus, keep the restock list narrow.

Notes:

- Automatically closes the screen after restocking

### 1.4 Totem of Undying Auto-Restock

- When a totem is used, restocks another one into the slot it popped from
- Adjusted so normal slot switches don't trigger it
- Toggleable (hotkey supported)

### 1.5 Hand Auto-Restock (new in v1.0.6)

- When a **listed** item on your hotbar drops to **5 or fewer**, automatically restocks from your inventory
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
- Score numbers animate smoothly instead of jumping (see 1.7)

### 1.7 Smooth Score Animation (custom HUD)

Numbers on the custom scoreboard HUD ease towards their new value instead of jumping.
This is done entirely on the client by this mod — no extra data is sent over the network
for it, and the server-side smoother that used to do this was removed — so **scores only
animate for players who have Hikari-Tweaks installed**.

- Always on; there is no config option. One was deliberately removed: at the current update rate the steps are almost always +1, and an integer display has nothing to draw between N and N+1, so the toggle made no visible difference either way
- Real-time exponential easing (time constant ~0.12 s), so the speed is the same at any frame rate. If more than 0.5 s passes between frames (window unfocused, world loading) the value snaps straight to the real one instead of replaying the animation
- Applies to the ranking rows **and** to the server total line
- Only the displayed digits are eased. Sorting and ranks always come from the real values, so rows never swap places mid-animation
- A row that has just appeared (new entry, or turning to a page you were not looking at) shows its real value immediately — it never counts up from zero
- Requires the v2 ranking protocol. A server still sending the v1 packet gets exact values with no animation, because v1 rows carry no UUID to track a row by
- The animation state is dropped when the board switches to a different statistic, so you never see a count-down between two unrelated numbers

---

## 2. Requirements

- Minecraft **1.17.1 - 1.21.11** (Fabric)
- Fabric Loader `>= 0.14.0` (MC 1.17.1 - 1.20.4) / `>= 0.15.10` (MC 1.20.5 and newer)
  - The 1.20.5+ jars declare `compatibilityLevel = JAVA_21` for Mixin, which Fabric Loader
    only understands from 0.15.10 onwards (the first release bundling sponge-mixin 0.13.3).
- Fabric API
- malilib

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

Recommended (optional):

- Mod Menu (for easier access to the config screen)
- MiniHUD (for the beacon range fix feature)
- HikariScoreBoard (for the custom scoreboard integration)

---

## 3. Installation

1. Place the jar matching your Minecraft version in the client's `mods/`.
   A local build writes it to
   `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>.jar` (see §9);
   released jars are published one per Minecraft version group (see the table in §2)
2. Place the required mods (Fabric API / malilib) similarly
3. Launch the game
4. `config/hikari-tweaks.json` is generated on first launch

---

## 4. Usage

### 4.1 Open the config screen

- Default hotkey: `H` + `T` (hold `H` and press `T`; stored as `H,T`)
- Alternatively, open the `Hikari-Tweaks` config screen via Mod Menu

### 4.2 Config tabs

- `Tweaks`: on/off toggles for each feature
- `Lists`: the two item ID lists — **Hotbar Restock List** (hotbar auto-restock) and **Hand Restock List** (hand auto-restock)
- `Hotkeys`: key bindings for feature toggles and opening the config screen
- `Scoreboard`: scoreboard integration / display settings / player management

---

## 5. Settings (defaults)

Every field of `config/hikari-tweaks.json` is listed below — there are no others.
The `scoreboard*` display fields are edited from the `Scoreboard` tab of the config
screen rather than typed by hand.

| Key | Default | Description |
|---|---:|---|
| `configVersion` | `7` | Config schema version. Written and migrated by the mod — do not edit |
| `fixBeaconRangeFreeCam` | `true` | MiniHUD beacon range fix |
| `fixBeaconRangeFreeCamHotkey` | `""` | Toggle hotkey for the above (unassigned) |
| `durabilityWarningEnabled` | `true` | Durability 1% warning |
| `durabilityWarningEnabledHotkey` | `""` | Toggle hotkey for the above (unassigned) |
| `autoRestockHotbar` | `false` | Hotbar auto-restock |
| `autoRestockHotbarHotkey` | `""` | Toggle hotkey for the above (unassigned) |
| `totemRestock` | `false` | Totem auto-restock |
| `totemRestockHotkey` | `""` | Toggle hotkey for the above (unassigned) |
| `handRestock` | `false` | Hand auto-restock |
| `handRestockHotkey` | `""` | Toggle hotkey for the above (unassigned) |
| `hotbarRestockList` | `minecraft:firework_rocket`, `minecraft:golden_carrot` | Hotbar auto-restock target list (**Hotbar Restock List**, Lists tab) |
| `handRestockList` | *(empty)* | Hand auto-restock target list (**Hand Restock List**, Lists tab) |
| `openConfigHotkey` | `H,T` | Key to open the config screen (hold `H` and press `T`) |
| `scoreboardNextPageHotkey` | `""` | Custom HUD: next page (unassigned) |
| `scoreboardPrevPageHotkey` | `""` | Custom HUD: previous page (unassigned) |
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

Colors are stored by Gson as signed decimal integers, so the hex values above appear in
the file in decimal (`0xFFFFFFFF` is written as `-1`). Out-of-range numbers are clamped
on load, and fields missing from an older file are filled in on startup.

---

## 6. Hotkeys

All hotkeys are edited from the config screen. A feature's toggle hotkey sits on that
feature's own row in the `Tweaks` tab; the three below it live in the `Hotkeys` tab.
Key combinations are stored comma-separated (`H,T` is displayed as `H + T`).

| Hotkey | Config key | Default | Action |
|---|---|---|---|
| Open Config Screen | `openConfigHotkey` | `H,T` (hold `H`, press `T`) | Opens the Hikari-Tweaks config screen |
| Scoreboard Next Page | `scoreboardNextPageHotkey` | unassigned | Custom HUD: next page |
| Scoreboard Previous Page | `scoreboardPrevPageHotkey` | unassigned | Custom HUD: previous page |
| MiniHUD Beacon Fix | `fixBeaconRangeFreeCamHotkey` | unassigned | Toggles `fixBeaconRangeFreeCam` |
| Durability Warning | `durabilityWarningEnabledHotkey` | unassigned | Toggles `durabilityWarningEnabled` |
| Hotbar Auto-Restock | `autoRestockHotbarHotkey` | unassigned | Toggles `autoRestockHotbar` |
| Totem Restock | `totemRestockHotkey` | unassigned | Toggles `totemRestock` |
| Hand Auto-Restock | `handRestockHotkey` | unassigned | Toggles `handRestock` |

Pressing a toggle hotkey prints the resulting state in the action bar.

---

## 7. HikariScoreBoard Integration Specification

`Hikari-Tweaks` communicates with `HikariScoreBoard` via the following channels.

Receive:

- `hikariscoreboard:ranking_v2` — the delta ranking protocol; used whenever the server supports it
- `hikariscoreboard:ranking_data` — the older full-snapshot protocol, kept for servers still on it
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

This project uses [Stonecutter](https://stonecutter.kikugie.dev/) to build every supported
Minecraft version from a single source tree. Java 21 is enough to build all of them
(each target sets its own `--release` level).

Build every version:

```bash
./gradlew chiseledBuild
```

Outputs land in `build/libs/<mod version>/`, one jar (plus sources jar) per target:

- `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>.jar`
- `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>-sources.jar`

Build a single version while developing:

```bash
./gradlew "1.21.10:build"
```

After building, verify the produced jars:

```bash
python scripts/verify-jars.py
```

This checks the Java level, `depends.minecraft`, and — importantly — that the mixin
refmap resolves to the intended target methods. A green build does **not** by itself
prove the mixins are correct, so run this before publishing.

### Adding a new Minecraft version

1. Add a `["<version>"]` block to `stonecutter.properties.toml` with its dependency versions.
2. Add the version to `versions(...)` in `settings.gradle.kts`.
3. Run `./gradlew "<version>:build"` and fix whatever breaks.
4. Run `python scripts/verify-jars.py`.

Version differences are kept inside `com.hikariserver.hikaritweaks.compat` wherever possible;
only signatures that are themselves version boundaries (screen `render`, mouse events, mixin
targets) use Stonecutter comment branches. See `docs/multiversion/PLAN.md` for the design.

---

## 10. Known Behaviors / Notes

- Auto-restock features perform slot clicks as client-side operations
- Hotbar auto-restock runs when a container is opened, closing the screen after completion
- The custom scoreboard HUD is skipped for that frame when **any** of the following holds:
  - `scoreboardCustomHud` is off
  - There is nothing to draw — the server has not sent a board yet, or it told the client to hide it
  - The board it did send has zero entries
  - The HUD is hidden entirely with F1 (`hudHidden`). The custom HUD is drawn from a `TAIL`
    injection on `InGameHud.render`, which runs past vanilla's own F1 handling, so this is
    checked explicitly instead of being inherited
  - The F3 debug screen is shown. The condition is *"is F3 on"* and nothing else: on 1.21.9+
    vanilla's `shouldShowDebugHud()` also returns true when debug entries are merely pinned,
    so those targets read the F3 flag directly and the HUD does not vanish without F3
- Hiding the vanilla sidebar (`scoreboardHideVanilla`) is independent of all of the above —
  it applies even when the custom HUD is off

---

## 11. License

**GNU Lesser General Public License v3.0 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 HikariServerDev

LGPL-3.0 is written as a set of additional permissions layered on top of GPL-3.0 and
incorporates it by reference, so both texts ship with this project:

- [COPYING.LESSER](COPYING.LESSER) — GNU Lesser General Public License v3
- [COPYING](COPYING) — GNU General Public License v3

Both are also bundled in `META-INF/` inside every published jar, together with `NOTICE`.

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
