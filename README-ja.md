**🇯🇵 日本語** | [🇬🇧 English](README.md)

---

# Hikari-Tweaks

> **Minecraft 1.17.1 〜 1.21.11 向けのクライアントサイド Fabric ユーティリティ Mod**。[Hikari Server (光鯖)](https://hikariserver.com) で開発されました。

### Requirements
| Mod | Type |
|---|---|
| [Fabric Loader](https://fabricmc.net/) `>=0.14.0`（MC 1.17.1 〜 1.20.4）/ `>=0.15.10`（MC 1.20.5 以降） | **Required** |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **Required** |
| [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) | **Required** |
| [MiniHUD](https://www.curseforge.com/minecraft/mc-mods/minihud) | Optional (beacon fix feature) |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Optional (GUI config screen) |

---


## 1. 主な機能

### 1.1 MiniHUD 補正（Freecam Beacon Fix）

- MiniHUD のフリーカメラ中、ビーコン範囲表示の基準をカメラではなくプレイヤー位置に補正
- ON/OFF 切り替え可能（ホットキー対応）

### 1.2 耐久値 1% 警告

- 耐久アイテムが「残り1%以下」になったとき、チャット通知 + 効果音で警告
- 同一状態での連続通知を抑制
- ON/OFF 切り替え可能（ホットキー対応）

### 1.3 ホットバー自動補充

- コンテナを開いたとき、指定リストのアイテムをホットバーへ自動補充
- ON/OFF 切り替え可能（ホットキー対応）

補足:

- プレイヤーインベントリ画面では動作しません
- エンダーチェストを開いたときは動作しません
- 補充後は画面を自動で閉じます

### 1.4 不死のトーテム自動補充

- トーテム発動時に、発動前に持っていたスロットへトーテムを補充
- 通常のスロット切り替えでは補充が発動しないように調整済み
- ON/OFF 切り替え可能（ホットキー対応）

### 1.7 手持ち自動補充（v1.0.6 新機能）

- ホットバー内の指定アイテムが **5 個以下** になったとき、インベントリから自動で補充します
- Tweakeroo の handrestock 機能と同様の動作で、補充対象は「リスト」タブの **手持ち補充対象リスト** で管理します
- ホットバーのみを監視し、インベントリ内は補充元としてのみ使用します
- ON/OFF 切り替え可能（ホットキー対応）

### 1.6 カスタムスコアボードHUD（HikariScoreBoard 連携）、クライアントHUDとして表示します。

- バニラスコアボードの非表示切替
- ページサイズ変更（1〜50）
- ページ送り / 戻し / リセット
- HUD位置（X/Y %）とスケール（0.5x〜3.0x）調整
- ヘッダー / 本文 / 文字色 / スコア色 / 自己強調色を ARGB で調整
- サーバー合計値（Total）表示切替
- プレイヤー管理タブ（表示ブロック切替）

---

## 2. 動作環境

- Minecraft **1.17.1 〜 1.21.11**（Fabric）
- Fabric Loader `>= 0.14.0`（MC 1.17.1 〜 1.20.4）/ `>= 0.15.10`（MC 1.20.5 以降）
  - 1.20.5 以降の jar は Mixin の `compatibilityLevel = JAVA_21` を宣言しており、
    これを解釈できるのは sponge-mixin 0.13.3 を同梱した Fabric Loader 0.15.10 以降のためです。
- Fabric API
- malilib

### 対応バージョン一覧

Minecraft のバージョン群ごとに 1 つの jar を配布しています。使用中のバージョンに合うものをダウンロードしてください。

| 配布 jar | 対応する Minecraft バージョン |
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

推奨（任意）:

- Mod Menu（設定画面を開きやすくする）
- MiniHUD（ビーコン補正機能を使う場合）
- HikariScoreBoard（カスタムスコアボード連携を使う場合）

---

## 3. 導入手順

1. `build/libs/hikari-tweaks-<version>.jar` をクライアント側 `mods/` に配置
2. 依存 Mod（Fabric API / malilib）も同様に配置
3. ゲーム起動
4. 初回起動後、`config/hikari-tweaks.json` が生成されます

---

## 4. 使い方

### 4.1 設定画面を開く

- デフォルトホットキー: `RIGHT_SHIFT`
- または Mod Menu 経由で `Hikari-Tweaks` の設定画面を開く

### 4.2 設定タブ

- `Tweaks`: 各機能の ON/OFF
- `Lists`: ホットバー自動補充対象アイテムIDリスト
- `Hotkeys`: 機能トグルや設定画面オープンのキー設定
- `Scoreboard`: スコアボード連携・表示設定・プレイヤー管理

---

## 5. 主要設定（デフォルト値）

| 設定キー | 既定値 | 説明 |
|---|---:|---|
| `fixBeaconRangeFreeCam` | `true` | MiniHUDのビーコン範囲補正 |
| `durabilityWarningEnabled` | `true` | 耐久1%警告 |
| `autoRestockHotbar` | `false` | ホットバー自動補充 |
| `totemRestock` | `false` | トーテム自動補充 |
| `handRestock` | `false` | 手持ち自動補充 |
| `hotbarRestockList` | `minecraft:firework_rocket`, `minecraft:golden_carrot` | 自動補充対象リスト |
| `openConfigHotkey` | `RIGHT_SHIFT` | 設定画面を開くキー |
| `scoreboardCustomHud` | `true` | カスタムHUD表示 |
| `scoreboardHideVanilla` | `true` | バニラ右側スコアボードを隠す |
| `scoreboardPageSize` | `10` | 1ページの表示件数（1〜50） |
| `scoreboardPositionX` | `100` | HUD基準X（0〜100%） |
| `scoreboardPositionY` | `50` | HUD基準Y（0〜100%） |
| `scoreboardScale` | `1.0` | HUDスケール（0.5〜3.0） |
| `scoreboardHeaderColor` | `0x66000000` | ヘッダー背景色（ARGB） |
| `scoreboardBodyColor` | `0x4D000000` | 本文背景色（ARGB） |
| `scoreboardTextColor` | `0xFFFFFFFF` | 文字色（ARGB） |
| `scoreboardScoreColor` | `0xFFFF5555` | スコア色（ARGB） |
| `scoreboardSelfColor` | `0xFFFFFF55` | 自己行強調色（ARGB） |
| `scoreboardShowServerTotal` | `true` | サーバー合計表示 |

---

## 6. ホットキー

- `Open Config`: 既定 `RIGHT_SHIFT`
- `fixBeaconRangeFreeCam`: 初期未割当（任意で設定）
- `durabilityWarningEnabled`: 初期未割当（任意で設定）
- `autoRestockHotbar`: 初期未割当（任意で設定）
- `totemRestock`: 初期未割当（任意で設定）
- `handRestock`: 初期未割当（任意で設定）

---

## 7. HikariScoreBoard 連携仕様

`Hikari-Tweaks` は以下チャネルで `HikariScoreBoard` と通信します。

受信:

- `hikariscoreboard:ranking_data`
- `hikariscoreboard:player_list_response`

送信:

- `hikariscoreboard:player_list_request`
- `hikariscoreboard:block_toggle`

---

## 8. 設定ファイル

配置先: `config/hikari-tweaks.json`

- JSON形式で保存
- 起動時に不足項目を補完
- 設定画面やホットキー変更時に自動保存

---

## 9. ビルド

[Stonecutter](https://stonecutter.kikugie.dev/) を使い、単一のソースツリーから
対応する全 Minecraft バージョンをビルドします。ビルドに必要な JDK は 21 だけで足ります
（ターゲットごとに `--release` を切り替えているため）。

全バージョンをビルド:

```bash
./gradlew chiseledBuild
```

生成物は `build/libs/<mod version>/` にターゲットごとに出力されます:

- `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>.jar`
- `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>-sources.jar`

開発中に 1 バージョンだけビルドする場合:

```bash
./gradlew "1.21.10:build"
```

ビルド後は生成された jar を検証してください:

```bash
python scripts/verify-jars.py
```

Java バージョン・`depends.minecraft`・そして **mixin の refmap が意図したメソッドに
解決されているか** を確認します。**ビルドが通ることは mixin が正しいことを意味しません**
（メソッド名だけの指定だと別のオーバーロードを掴んでもビルドは成功してしまう）。
公開前に必ず実行してください。

### 新しい Minecraft バージョンを追加する手順

1. `stonecutter.properties.toml` に `["<version>"]` ブロックを足し、依存バージョンを書く
2. `settings.gradle.kts` の `versions(...)` にバージョンを足す
3. `./gradlew "<version>:build"` を実行してエラーを潰す
4. `python scripts/verify-jars.py` で検証する

バージョン差分は可能な限り `com.hikariserver.hikaritweaks.compat` の中に閉じ込めています。
シグネチャそのものがバージョン境界になっている箇所（画面の `render`、マウスイベント、
mixin のインジェクト対象）だけ Stonecutter のコメント分岐を使っています。
設計の詳細は `docs/multiversion/PLAN.md` を参照してください。

---

## 10. 既知の挙動・注意点

- 自動補充系はクライアント操作としてスロットクリックを行います
- ホットバー自動補充はコンテナを開いたタイミングで実行され、完了後に画面を閉じます
- F3デバッグ表示中はカスタムスコアボードHUDを描画しません

---

## 11. ライセンス

**GNU Lesser General Public License v3.0 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 Hikari Server


詳細は同梱の [LICENSE](LICENSE) ファイルおよび以下 URL を参照してください:
- https://www.gnu.org/licenses/lgpl-3.0.txt
- https://www.gnu.org/licenses/gpl-3.0.txt

---

## 12. Credits

開発元: **[Hikari Server (光鯖)](https://hikariserver.com)** — Minecraft Java Edition のコミュニティサーバー

- **Maintainer**: [Tamago0314](https://github.com/Tamago0314)


### 依存 Mod・参考実装

**masa** (fi.dy.masa)
- [MaLiLib](https://github.com/maruohon/malilib) — LGPLv3
- [MiniHUD](https://github.com/maruohon/minihud) — LGPLv3
- [Tweakeroo](https://github.com/maruohon/tweakeroo) — LGPLv3


**Sim-hu** (ASTRAL-SMP) — [AST-Tweaks](https://github.com/ASTRAL-SMP/AST-Tweaks) (Apache-2.0)


**pugur** — [ama-tweaks](https://github.com/pugur523/ama-tweaks) (MIT)