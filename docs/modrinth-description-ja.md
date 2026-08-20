<div align="center">

<img src="https://hikariserver.com/assets/logo.png" width="200" alt="Hikari-Tweaks">

# Hikari-Tweaks

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](https://github.com/Tamago0314/Hikari-Tweaks/blob/main/LICENSE)
[![Modrinth](https://img.shields.io/modrinth/dt/hikari-tweaks?label=Modrinth%20Downloads)](https://modrinth.com/mod/hikari-tweaks)

</div>

**Minecraft 1.17 〜 1.21.11 向けのクライアントサイド Fabric ユーティリティ Mod**。[Hikari Server (光鯖)](https://hikariserver.com) で開発されました。カスタマイズ可能なスコアボード HUD（HikariScoreBoard 連携）と便利機能をまとめて提供します。

## 機能


### tweakCustomScoreboardHud

> [HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard) のランキングを完全カスタマイズ可能な HUD として描画。位置（X/Y アンカー 0-100%）・スケール（0.5x-3.0x）・ページング（1-50 行/ページ）・ヘッダ/本文/文字/スコア/自分行の ARGB カラーを個別設定可能。バニラサイドバーはデフォルトで非表示。
<br><br>

### tweakDurabilityWarning

> 装備中の耐久アイテムが**残り 1%** になったときにチャット通知と効果音で警告。同一状態での連続通知は抑制されます。ホットキーで ON/OFF 切替可能。
<br><br>

### tweakAutoRestockHotbar

> コンテナ（チェスト・シュルカーボックス等）を開いたとき、指定リストのアイテムをコンテナからホットバーへ自動補充。リストは「リスト」タブで編集可能。プレイヤーインベントリ・エンダーチェストは対象外。補充後は画面を自動で閉じます。
<br><br>

### tweakTotemRestock

> 不死のトーテム発動時に、発動前に持っていた手（スロット）へインベントリからトーテムを補充。通常のホットキーによるスロット切替では発動しないよう調整済み。
<br><br>

### tweakHandRestock

> Tweakeroo の handrestock 相当。ホットバー内の指定アイテムが **5 個以下** になったとき、インベントリから自動で補充します。コンテナ開時の補充リストとは独立して細かく設定可能。
<br><br>

### tweakBeaconRangeFreeCamFix

> MiniHUD のフリーカメラ使用時、ビーコン範囲表示の基準をカメラではなく**プレイヤー位置に補正**します。要 MiniHUD。
<br><br>

### tweakScoreboardPlayerManagement

> HikariScoreBoard と組み合わせて使用すると、チェスト型 GUI からランキングへの表示/非表示を切り替えできます。Carpet BOT も一覧で視覚的に区別されます。
<br><br>

## 動作環境

- Minecraft **1.17 〜 1.21.11**（Fabric）
- **Fabric Loader** >= 0.14.0
- **Fabric API**
- **[MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)**

## 任意で連携できる Mod

- **[MiniHUD](https://www.curseforge.com/minecraft/mc-mods/minihud)** — ビーコン範囲補正機能を使う場合に必要
- **[Mod Menu](https://modrinth.com/mod/modmenu)** — ゲーム内から設定画面にアクセスできるようになる
- **[HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard)**（サーバー側） — カスタムスコアボード HUD のデータソースを提供。未導入でも動作しますが、ランキングは表示されません。

## Credits

開発元: **[Hikari Server (光鯖)](https://hikariserver.com)** — Minecraft Java Edition のコミュニティサーバー

- **Maintainer**: [Tamago0314](https://github.com/Tamago0314)

### 依存 Mod・参考実装

**masa** (fi.dy.masa)
- [MaLiLib](https://github.com/maruohon/malilib) — LGPLv3
- [MiniHUD](https://github.com/maruohon/minihud) — LGPLv3
- [Tweakeroo](https://github.com/maruohon/tweakeroo) — LGPLv3

**Sim-hu** (ASTRAL-SMP) — [AST-Tweaks](https://github.com/ASTRAL-SMP/AST-Tweaks) (Apache-2.0)

**pugur** — [ama-tweaks](https://github.com/pugur523/ama-tweaks) (MIT)

## ライセンス

**GNU Lesser General Public License v3 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 Hikari Server

詳細は同梱の `LICENSE` を参照してください。参照している第三者プロジェクトはそれぞれ独自のライセンスに従います（`NOTICE` 参照）。
