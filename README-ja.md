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

- **実在するコンテナブロック**を開いたとき、補充リストのアイテムをそのコンテナからホットバーへ自動補充
- ON/OFF 切り替え可能（ホットキー対応）
- 判定は「確証が無ければ動かさない」方針です。次の条件を**すべて**満たしたときだけ補充します。
  1. クロスヘアの先がブロックで、そこにクライアントから見えているブロックエンティティがあり、それが中身（インベントリ）を持っている
  2. そのブロックエンティティがエンダーチェストでない
  3. 開いている画面のコンテナスロット数が、そのブロックエンティティの中身のサイズと一致する（チェストだけは隣接でダブルチェストになりうるため、ちょうど 2 倍も許可）

実際にどうなるか:

| 開いたもの | 補充 | 理由 |
|---|---|---|
| チェスト／トラップチェスト（単・ダブル） | される | 実在するブロックエンティティ。チェストだけは自身のサイズのちょうど 2 倍も許可される |
| 樽、**設置した**シュルカーボックス | される | 実在するブロックエンティティで、スロット数も一致する |
| ホッパー、ディスペンサー、ドロッパー、かまど類、醸造台 | される | 同じ規則。中身のサイズが画面のスロット数と一致するブロックエンティティなら対象になる |
| エンダーチェスト | されない | 明示的に除外。中身がプレイヤーごとで、プラグインが差し替えていることもあるため |
| チェスト付き／ホッパー付きトロッコ、チェスト付きボート、村人との取引 | されない | これらはエンティティであってブロックエンティティではなく、クロスヘアの先に判定材料が無い |
| 作業台、エンチャントテーブル、金床 | されない | 中身を持つブロックエンティティが無い |
| 自分のインベントリ画面 | されない | 判定に入る前に除外している |
| コマンド・NPC・アイテムから開かれたプラグイン製メニュー | されない | クロスヘアの先に確証となるブロックが無い |
| 実在するコンテナを右クリックして開いた、**スロット数まで一致する**プラグイン製メニュー | されてしまう（検出不可） | クライアントからは本物のブロックと完全に同じに見えるため。下の注意を参照 |

注意: スロット数の判定は「27 スロットのチェストを右クリックしたら 54 スロットのショップ GUI が開いた」というよくある食い違いは弾けますが、開いたブロックとサイズがたまたま一致する仮想 GUI はクライアント側から区別できません。チェストを土台にしたメニューを使うサーバーでは、補充リストを絞ってください。

補足:

- 補充後は画面を自動で閉じます

### 1.4 不死のトーテム自動補充

- トーテム発動時に、発動前に持っていたスロットへトーテムを補充
- 通常のスロット切り替えでは補充が発動しないように調整済み
- ON/OFF 切り替え可能（ホットキー対応）

### 1.5 手持ち自動補充（v1.0.6 新機能）

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
- スコアの数値が飛ばずに滑らかに動く（1.7 を参照）

### 1.7 スコア数値の滑らか表示（カスタム HUD）

カスタムスコアボード HUD の数値は、更新時に飛ばずに新しい値へ滑らかに寄っていきます。
処理はすべてこの Mod がクライアント側で行っており（この演出のためにネットワークは 1 バイトも使いません）、
以前サーバー側にあった同等の処理は削除済みです。
そのため、**滑らかに動くのは Hikari-Tweaks を入れているプレイヤーの画面だけ**です。

- 常時 ON で、設定項目はありません。意図的に削除しました（現在の更新レートでは変化幅がほぼ常に +1 で、整数表示では N と N+1 の間に描ける中間値が無く、ON と OFF の区別が付かないため）
- 実時間ベースの指数補間（時定数およそ 0.12 秒）なので、フレームレートが変わっても速さは変わりません。ウィンドウ非アクティブやワールド読み込みなどでフレームが 0.5 秒以上空いたときは、まとめて補間せず即座に実際の値へ合わせます
- ランキングの各行と、サーバー合計（Total）行の**両方**に効きます
- 補間するのは**表示される数字だけ**です。並び順と順位は常に実際の値で決めているため、補間中に行が入れ替わってちらつくことはありません
- 現れたばかりの行（新規エントリ、見ていなかったページへのページ送り）は、その時点の実際の値から始まります。0 から数え上げる演出はしません
- ランキング v2 プロトコルが前提です。v1 パケットしか送らないサーバーでは、行を追跡するための UUID が無いため、値はそのまま（演出なしで正確に）表示されます
- 表示している統計が別のものに切り替わったときは状態を捨てるので、無関係な数値のあいだで数え下げ演出が出ることはありません

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

1. 使用中の Minecraft バージョンに合う jar をクライアント側 `mods/` に配置
   （ローカルビルドの出力先は
   `build/libs/<mod version>/hikari-tweaks-<version>+<minecraft version>.jar`。§9 を参照。
   配布物は Minecraft バージョン群ごとに 1 つなので、どれを選ぶかは §2 の表を参照）
2. 依存 Mod（Fabric API / malilib）も同様に配置
3. ゲーム起動
4. 初回起動後、`config/hikari-tweaks.json` が生成されます

---

## 4. 使い方

### 4.1 設定画面を開く

- デフォルトホットキー: `H` + `T`（`H` を押しながら `T`。設定ファイル上は `H,T`）
- または Mod Menu 経由で `Hikari-Tweaks` の設定画面を開く

### 4.2 設定タブ

- `Tweaks`: 各機能の ON/OFF
- `Lists`: アイテムIDリスト 2 種（**ホットバー補充対象リスト** / **手持ち補充対象リスト**）
- `Hotkeys`: 機能トグルや設定画面オープンのキー設定
- `Scoreboard`: スコアボード連携・表示設定・プレイヤー管理

---

## 5. 設定一覧（デフォルト値）

`config/hikari-tweaks.json` の全フィールドです（これ以外の項目はありません）。
`scoreboard*` の表示設定は設定画面の `Scoreboard` タブから編集する項目で、
手で書き換えることは想定していません。

| 設定キー | 既定値 | 説明 |
|---|---:|---|
| `configVersion` | `7` | 設定スキーマのバージョン。Mod が書き込み・移行するので編集しないこと |
| `fixBeaconRangeFreeCam` | `true` | MiniHUDのビーコン範囲補正 |
| `fixBeaconRangeFreeCamHotkey` | `""` | 上記のトグルホットキー（初期未割当） |
| `durabilityWarningEnabled` | `true` | 耐久1%警告 |
| `durabilityWarningEnabledHotkey` | `""` | 上記のトグルホットキー（初期未割当） |
| `autoRestockHotbar` | `false` | ホットバー自動補充 |
| `autoRestockHotbarHotkey` | `""` | 上記のトグルホットキー（初期未割当） |
| `totemRestock` | `false` | トーテム自動補充 |
| `totemRestockHotkey` | `""` | 上記のトグルホットキー（初期未割当） |
| `handRestock` | `false` | 手持ち自動補充 |
| `handRestockHotkey` | `""` | 上記のトグルホットキー（初期未割当） |
| `hotbarRestockList` | `minecraft:firework_rocket`, `minecraft:golden_carrot` | ホットバー自動補充の対象リスト（「リスト」タブの **ホットバー補充対象リスト**） |
| `handRestockList` | *(空)* | 手持ち自動補充の対象リスト（「リスト」タブの **手持ち補充対象リスト**） |
| `openConfigHotkey` | `H,T` | 設定画面を開くキー（`H` を押しながら `T`） |
| `scoreboardNextPageHotkey` | `""` | カスタムHUD: 次ページ（初期未割当） |
| `scoreboardPrevPageHotkey` | `""` | カスタムHUD: 前ページ（初期未割当） |
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

色は Gson が符号付き 10 進整数として書き出すため、ファイル上は上記の 16 進と同じ値が
10 進で入ります（`0xFFFFFFFF` は `-1`）。範囲外の数値は読み込み時に丸められ、
古いファイルに無いフィールドは起動時に補完されます。

---

## 6. ホットキー

ホットキーは設定画面から編集します。機能トグルのホットキーは `Tweaks`（補助機能）タブの
その機能の行にあり、下 3 つは `Hotkeys`（ホットキー）タブにあります。
同時押しはカンマ区切りで保存されます（`H,T` は画面上 "H + T" と表示）。

| ホットキー | 設定キー | 既定値 | 動作 |
|---|---|---|---|
| 設定画面を開く | `openConfigHotkey` | `H,T`（`H` を押しながら `T`） | Hikari-Tweaks の設定画面を開く |
| スコアボード 次ページ | `scoreboardNextPageHotkey` | 未割当 | カスタムHUDを次のページへ |
| スコアボード 前ページ | `scoreboardPrevPageHotkey` | 未割当 | カスタムHUDを前のページへ |
| MiniHUD ビーコン補正 | `fixBeaconRangeFreeCamHotkey` | 未割当 | `fixBeaconRangeFreeCam` を切り替え |
| 耐久値警告 | `durabilityWarningEnabledHotkey` | 未割当 | `durabilityWarningEnabled` を切り替え |
| ホットバー自動補充 | `autoRestockHotbarHotkey` | 未割当 | `autoRestockHotbar` を切り替え |
| トーテム補充 | `totemRestockHotkey` | 未割当 | `totemRestock` を切り替え |
| 手持ち自動補充 | `handRestockHotkey` | 未割当 | `handRestock` を切り替え |

トグル系のホットキーを押すと、切り替え後の状態がアクションバーに表示されます。

---

## 7. HikariScoreBoard 連携仕様

`Hikari-Tweaks` は以下チャネルで `HikariScoreBoard` と通信します。

受信:

- `hikariscoreboard:ranking_v2` — 差分ランキングプロトコル。サーバーが対応していればこちらが使われます
- `hikariscoreboard:ranking_data` — 旧い全件送信プロトコル。旧版のサーバー向けに残してあります
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
- カスタムスコアボードHUDは、次の**いずれか**に当てはまるフレームでは描画されません
  - `scoreboardCustomHud` が OFF
  - 描くものが無い（サーバーからボードがまだ届いていない／サーバーが非表示を指示している）
  - 届いたボードのエントリ数が 0
  - F1 で HUD 全体を非表示にしている（`hudHidden`）。カスタムHUDは `InGameHud.render` の
    `TAIL` から描いており、バニラ側の F1 処理をすり抜けるため、ここで明示的に弾いています
  - F3 デバッグ画面の表示中。判定は **「F3 が ON かどうか」だけ** です。1.21.9 以降は
    バニラの `shouldShowDebugHud()` が「ピン留め項目があるだけ」でも true を返すため、
    F3 を押していないのに HUD が消えないよう、それらのターゲットでは F3 フラグを直接見ています
- バニラサイドバーの非表示（`scoreboardHideVanilla`）は上記とは独立した設定で、
  カスタムHUDが OFF でも適用されます

---

## 11. ライセンス

**GNU Lesser General Public License v3.0 (LGPL-3.0-or-later)**

Copyright (C) 2025-2026 HikariServerDev

LGPL-3.0 は GPL-3.0 に追加の許諾を重ねる形で書かれており、GPL-3.0 を参照によって取り込んでいます。
そのため、このプロジェクトでは両方の全文を同梱しています:

- [COPYING.LESSER](COPYING.LESSER) — GNU Lesser General Public License v3
- [COPYING](COPYING) — GNU General Public License v3

どちらも `NOTICE` とあわせて、配布する全 jar の `META-INF/` にも同梱しています。

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