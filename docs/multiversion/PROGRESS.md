# マルチバージョン対応 進捗

> このファイルはループ作業の**唯一の状態ソース**。
> 各イテレーションで必ずこのファイルを最初に読み、最後に更新すること。
> 設計の詳細は [PLAN.md](PLAN.md) を参照。

**現在のフェーズ**: ✅ **完了**（Phase 0〜8 全て完了 / 2026-08-14）

MC **1.17.1 〜 1.21.11** の全 17 ターゲットがビルド・検証を通過し、CI とドキュメントも更新済み。
ループ作業はここで終了。

**再開・保守のときに読むもの**:

- 設計と対応表 → [PLAN.md](PLAN.md)
- 新しい MC バージョンの追加手順 → README の「9. ビルド」節
- ビルド後の検証 → `python scripts/verify-jars.py`

**残っている唯一の大きな宿題は実機動作確認**（下の「未解決の課題」参照）。

---

## フェーズ進捗

| Phase | 内容 | 状態 |
|---|---|---|
| 0 | Stonecutter 導入（1.18.2 のみ） | ✅ 完了 |
| 1 | `compat` ファサード新設 | ✅ 完了 |
| 2 | 1.19.2 / 1.19.3 / 1.19.4 | ✅ 完了 |
| 3 | 1.20.1 / 1.20.2 / 1.20.4 | ✅ 完了 |
| 4 | 1.20.6（Java 21 + CustomPayload） | ✅ 完了 |
| 5 | 1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 | ✅ 完了 |
| 6 | 1.21.8 / 1.21.10 | ✅ 完了 |
| 7 | 1.18.1 / 1.17.1 / 1.17 | ✅ 完了 |
| 8 | CI・リリース・README 更新 | ✅ 完了 |

状態記号: ⬜ 未着手 / 🔄 作業中 / ✅ 完了 / ⚠️ ブロック中

---

## ターゲット別ビルド状況

`./gradlew "<target>:build"` が成功したら ✅ にする。

| ターゲット | 状態 | 最終確認 | 備考 |
|---|---|---|---|
| 1.17.1 | ✅ | 2026-08-14 | |
| 1.18.1 | ✅ | 2026-08-14 | 1.18・1.18.1 をカバー |
| 1.18.2 | ✅ | 2026-08-14 | Stonecutter 移行後もビルド成功。jar の中身は移行前と一致 |
| 1.19.2 | ✅ | 2026-08-14 | |
| 1.19.3 | ✅ | 2026-08-14 | |
| 1.19.4 | ✅ | 2026-08-14 | |
| 1.20.1 | ✅ | 2026-08-14 | |
| 1.20.2 | ✅ | 2026-08-14 | |
| 1.20.4 | ✅ | 2026-08-14 | |
| 1.20.6 | ✅ | 2026-08-14 | Java 21 / CustomPayload 移行済み |
| 1.21.1 | ✅ | 2026-08-14 | |
| 1.21.3 | ✅ | 2026-08-14 | |
| 1.21.4 | ✅ | 2026-08-14 | |
| 1.21.5 | ✅ | 2026-08-14 | |
| 1.21.8 | ✅ | 2026-08-14 | 1.21.6〜1.21.8 をカバー |
| 1.21.10 | ✅ | 2026-08-14 | 1.21.9〜1.21.10 をカバー |
| 1.21.11 | ✅ | 2026-08-20 | 追加リクエストで後から対応 |

---

## 作業ログ

新しいエントリは**この見出しの直下**に追記する（新しいものが上）。
1 イテレーション 1 エントリ。何をしたか・結果・次の一手を 3〜6 行で。

### 2026-08-20 — 対応範囲を 1.17.1 以降に変更 / 実機検証の着手

- **1.17（無印）を対応対象から除外**し、対応範囲を **1.17.1 〜 1.21.11（28 バージョン / 17 ターゲット）** に変更。
  `settings.gradle.kts` の `versions(...)` と TOML の `["1.17"]` ブロックを削除。
  再追加は両方を戻すだけでよい（`ScreenCompat` の `openScreen` 分岐は残してある）。
- `./gradlew chiseledBuild` + `verify-jars.py` で **17 ターゲット OK** を再確認。
- README（日英）・Modrinth 説明文・PLAN.md の対応表も 1.17.1 起点に更新。

**実機検証について（重要）:**

- 開発実行で MiniHUD を読み込ませるため `modLocalRuntime` を追加した。
  これが無いと `MixinOverlayRenderer` が実行時に一度も検証されない。
- `run/` はバージョンごとに分けるようにした。options.txt やワールドの形式が
  MC バージョン間で互換でないため、共有すると古いバージョンの起動が壊れる。
- **1.18.2 で起動スモークテストを実施し PASS。**
  `hikari-tweaks` / `malilib` / `minihud` の 3 つが読み込まれ、
  メインメニュー（`Sound engine started`）まで到達、mixin エラー無し。
  `mixins.json` が `required: true` なので、**起動できた＝ 3 つの mixin が全て正しく適用された証明**になる。
- 手順:

  ```bash
  timeout -k 15 420 ./gradlew "<バージョン>:runClient" > run-<バージョン>.log 2>&1
  grep -iE "Loading [0-9]+ mods|- hikari|- minihud|- malilib" run-<バージョン>.log
  grep -iE "Sound engine started" run-<バージョン>.log
  grep -iE "mixin apply failed|InvalidInjectionException|Critical injection" run-<バージョン>.log
  ```

- **残り 16 ターゲットの起動確認は未実施。** GUI の目視確認とサーバー接続確認は
  自動化できないため、引き続き人手が必要。

### 2026-08-20 — 1.21.11 を追加（18 ターゲット目）

- ユーザーからの追加リクエストで 1.21.11 に対応。
- **ソースコードの変更は一切不要だった。** `stonecutter.properties.toml` に
  `["1.21.11"]` ブロックを足し、`settings.gradle.kts` の `versions(...)` に
  追記しただけでビルドが通った。compat 層が 1.21.9 以降の変更
  （2D 行列 / `Click` レコード）を既に吸収していたため。
- 依存バージョン: yarn `1.21.11+build.6` / Fabric API `0.141.6+1.21.11` /
  malilib `0.27.17` / MiniHUD `0.38.14` / ModMenu `17.0.0`。
  ModMenu は 1.21.11 向けに beta（17.0.1-beta.1）もあるが、
  他ターゲットと揃えて stable の 17.0.0 を採用した。
- `./gradlew chiseledBuild` + `python scripts/verify-jars.py` で
  **全 18 ターゲット OK**。1.21.11 の refmap も確認し、
  `renderScoreboardSidebar` → `method_1757(class_332, class_266)`、
  `render` → `method_1753(class_332, class_9779)` と正しく解決されていた。
- README（日英）・Modrinth 説明文・PLAN.md の対応表も 1.21.11 まで更新済み。

**この回で分かったこと**: 新しい MC バージョンの追加コストは、
API 破壊が無ければ**設定 2 箇所の追記だけ**。マルチバージョン基盤が
意図どおり機能している。

### 2026-08-14 — Phase 8 完了 — 🎉 **全フェーズ完了**

- **CI** (`.github/workflows/ci.yml`): JDK 21 + Python をセットアップし、
  `./gradlew chiseledBuild` → `python scripts/verify-jars.py` を実行するよう更新。
  アーティファクトのパスも `build/libs/*/*.jar` に修正。
- **リリース** (`.github/workflows/release.yml`): 同様に更新し、
  17 個の jar を全てリリースに添付するようにした。
  タグと `mod.version` がずれていたら失敗させるガードも追加。
- **`scripts/verify-jars.py`**: リポジトリ相対パスで動くよう書き直し、
  ビルド対象は `settings.gradle.kts` の `versions(...)` から、
  mod バージョンは `stonecutter.properties.toml` から読むようにした（CI で実行できる）。
- **README.md / README-ja.md**: 対応バージョン表（jar ↔ MC バージョン）を追加。
  「9. ビルド」節をマルチバージョン前提に全面改稿し、
  新バージョン追加手順と検証手順を明記。
- **docs/modrinth-description*.md**: 対応バージョン表記を 1.17〜1.21.10 に更新。
- **PLAN.md**: 「要確認」だった欄を実測値で埋め、採用したツールチェイン表を追加。
  当初の想定と違っていた点（ButtonWidget は 1.19.3、split source set は使えない、
  1.17 系 malilib は masa maven など）を反映。
- 最終確認: `./gradlew chiseledBuild` 成功 + `python scripts/verify-jars.py` が
  **全 17 ターゲット OK**（終了コード 0）。

### 2026-08-14 — Phase 7 完了（1.18.1 / 1.17.1 / 1.17）— **全 17 ターゲット達成**

- `./gradlew chiseledBuild` で **17 ターゲット全て**がビルド成功。
- 検証スクリプト `scripts/verify-jars.py` を追加し、全 jar について
  **Java バージョン / depends.minecraft / refmap の有無 / mixin のターゲット**を
  機械チェックした。**NG 0 件**。

**最大の構造変更: split source set の廃止**

- MC 1.17.x は bundled server jar が無いため、Loom が `splitEnvironmentSourceSets()` を
  サポートしない（`Only Minecraft versions using a bundled server jar can be split`）。
- そのため **`src/client/java` を廃止し、全コードを `src/main/java` へ移動**した。
  クライアント限定であることは `fabric.mod.json` の `environment: client` と
  `mixins.json` の `client` セクションで担保している。
- 副作用として refmap 名が `client-hikari-tweaks.refmap.json` から
  **`hikari-tweaks.refmap.json`** に変わったので `mixins.json` も更新済み。

**依存関係の問題:**

- **Modrinth maven は malilib の `0.10.0-dev.*`（1.17 系）を配信していない**（404）。
  masa の maven には存在するため、`deps.malilib` を**バージョン文字列から
  完全な座標（group:artifact:version）に変更**し、1.17 系だけ
  `fi.dy.masa.malilib:malilib-fabric-1.17.x:...` を指すようにした。
  `build.gradle.kts` には masa maven を `fi.dy.masa.malilib` 限定で追加している。
- MiniHUD / ModMenu は 1.17 系でも Modrinth maven で問題なく解決できた。

**1.18.1 で判明した差分:**

- `Screen.close()` は **1.18.2 で `onClose()` からリネーム**されたもの。
  `ColorPickerScreen` / `PositionEditorScreen` のオーバーライドを分岐し、
  内部からの呼び出しは共通の `returnToParent()` に置き換えた。

**1.17.1 で判明した差分:**

- malilib 0.10 には **`BooleanHotkeyGuiWrapper` が存在しない**。
  `HikariTweaksConfigScreen.wrapConfig` を分岐し、1.17 系では設定オブジェクトを
  そのまま返す（boolean とホットキーが別行で表示されるだけで機能は同じ）。
- `Screen.shouldPause()` は 1.18 でのリネームで、1.17 系は **`isPauseScreen()`**。

**1.17 で判明した差分:**

- `MinecraftClient.setScreen(Screen)` は **1.17.1 でのリネーム**で、1.17 は `openScreen(Screen)`。
  `compat/ScreenCompat.setScreen(client, screen)` を新設し、7 箇所の呼び出しを差し替えた。

### 2026-08-14 — Phase 6 完了（1.21.8 / 1.21.10）

- `./gradlew chiseledBuild` で **14 ターゲット**が揃ってビルド成功。
  全ターゲットの refmap も再検証済み（`renderScoreboardSidebar` は全て `method_1757(..., class_266)`）。
- 残りは Phase 7（1.18.1 / 1.17.1 / 1.17）と Phase 8（CI・README）のみ。

**1.21.6 で判明した差分（描画パイプライン刷新）:**

- **`DrawContext.getMatrices()` の戻り値が `MatrixStack`（3D）から
  JOML の `Matrix3x2fStack`（2D）に変わった。**
  - `push()` / `pop()` → **`pushMatrix()` / `popMatrix()`**
  - `translate(x, y, z)` → **`translate(x, y)`**（z なし）
  - `scale(x, y, z)` → **`scale(x, y)`**（z なし）
- `DrawCtx` の `push` / `pop` / `translate` / `scale` を `>=1.21.6` / `>=1.20` / else の 3 分岐にした。
  z 成分は捨てているが、この Mod の用途（HUD のスケーリング）では translate の z は常に 0、
  scale の z は常に 1 なので**描画結果は変わらない**。
- 戻り値型が変わる `matrices()` は**どこからも使われていなかった**ので削除した
  （`raw()` だけ残し、こちらは `>=1.20` / else の 2 分岐）。
- `fill` / `drawTextWithShadow` / `enableScissor` / `disableScissor` は 1.21.8 でも無変更。

**1.21.9 で判明した差分（マウス入力 API の刷新）:**

- `Element` / `ParentElement` のマウス系メソッドが **`Click` レコード**を取る形に変わった。
  - `mouseClicked(double, double, int)` → **`mouseClicked(Click, boolean doubled)`**
  - `mouseReleased(double, double, int)` → **`mouseReleased(Click)`**
  - `mouseDragged(double, double, int, double, double)` → **`mouseDragged(Click, double, double)`**
  - `Click` から座標とボタンは `click.x()` / `click.y()` / `click.button()` で取れる。
  - `mouseScrolled(double, double, double, double)` は 1.20.2 以降のまま変更なし。
- malilib 0.26.8 の `GuiBase.onMouseClicked` も `(Click, boolean)` に変わっている。
- `PositionEditorScreen` と `HikariTweaksConfigScreen` のオーバーライドを分岐。
  `PositionEditorScreen` は処理本体を `handleDragStart` / `handleDrag` に切り出して共通化した。
- `ScoreboardTab` は自前クラス（`Element` 非実装）なので**従来のシグネチャのまま**。
  分岐側から `click.x()` などを渡している。

**作業メモ:**

- ソースの一括置換スクリプトをヒアドキュメントで渡すとシェルのクォートで壊れることがある。
  スクラッチパッドに `.py` として書いてから実行する方が確実。
- 全角ダッシュ（`–`）などの文字を含む行をアンカーにすると照合に失敗しやすい。

### 2026-08-14 — Phase 5 完了（1.21.3 / 1.21.4 / 1.21.5）

- `./gradlew chiseledBuild` で **12 ターゲット**が揃ってビルド成功。
- **全 12 ターゲットの refmap を機械的に検証**し、`renderScoreboardSidebar` が
  すべて `method_1757(..., class_266)`（ScoreboardObjective 版）に解決されていることを確認。
- 1.21.3 と 1.21.4 は**無修正で通った**。

**1.21.5 で新たに判明した差分:**

- `PlayerInventory` の `main` / `offHand` / `selectedSlot` が**すべて private 化**された。
  - `inventory.main.size()` → `inventory.getMainStacks().size()`
  - `inventory.offHand.get(0)` → `inventory.getStack(PlayerInventory.OFF_HAND_SLOT)`
    （`OFF_HAND_SLOT = 40` で、コード内の自前定数 `OFFHAND_SLOT = 40` と一致することを確認済み）
  - `inventory.selectedSlot` → `inventory.getSelectedSlot()`
- `compat/InventoryCompat` を新設して `TotemRestockHandler` を差し替え。

### 2026-08-14 — 1.21.1 追加 + ⚠️ mixin 誤ターゲットの重大バグを発見・修正

**1.21.1 はコンパイルエラー 0 件で通ったが、それが罠だった。**
refmap を確認したところ、**1.20.1 以降ずっと mixin が意図と違うメソッドを掴んでいた**。

- `InGameHud` には **1.20 以降 `renderScoreboardSidebar` のオーバーロードが 2 つある**:
  - `renderScoreboardSidebar(DrawContext, float | RenderTickCounter)` … 呼び出し元
  - `renderScoreboardSidebar(DrawContext, ScoreboardObjective)` … 実際の描画（**こちらが目的**）
- `@Inject(method = "renderScoreboardSidebar")` のように**メソッド名だけ**を書いていたため、
  Mixin AP が前者を選び、refmap には `method_55803(class_332, F)` が出力されていた。
- 同様に 1.21.1 では `render` が `(DrawContext, RenderTickCounter)` に変わっているのに、
  ハンドラは `(DrawContext, float, CallbackInfo)` のままで**ビルドが通った**。
- つまり **Mixin AP はハンドラ側の引数不一致を検出しない**。
  1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 の jar は
  **実行すると mixin 適用に失敗して（`required: true` なので）クラッシュする**状態だった。

**修正内容:**

- `MixinInGameHud` の `method` を全て**完全な記述子付き**に変更し、3 分岐にした
  （`>=1.21` / `>=1.20` / else）。
- 全ターゲットの refmap を再確認し、`renderScoreboardSidebar` が
  **`method_1757(..., class_266)`** に、`render` が各バージョン正しい記述子に
  解決されていることを確認済み。
- `./gradlew chiseledBuild` で **9 ターゲット**成功。

**教訓:** ビルドが通ることは mixin が正しいことを意味しない。
バージョンを足すたびに refmap を目視確認すること（手順は冒頭の「次にやること」に記載）。

### 2026-08-14 — Phase 4 完了（1.20.6 / Java 21 + CustomPayload）

- `./gradlew chiseledBuild` で **8 ターゲット**が揃ってビルド成功。
- Java 21 と mixin の `compatibilityLevel = JAVA_21` は
  `stonecutter.properties.toml` の `mod.java` から自動で切り替わった。生成 jar で確認済み。
- **`compat/NetCompat` を新設し、`ScoreboardPacketClient` を全面的にその経由へ書き換えた。**
  公開 API は `registerReceiver` / `registerSendChannel` / `canSend` / `createBuf` / `send` の 5 つで、
  受信コールバックは全バージョン `(MinecraftClient, PacketByteBuf)` に正規化してある。
  - `>=1.20.5`: 生バイト列を運ぶ `RawPayload implements CustomPayload` を実装。
    codec は `PacketCodec.of` で自作し、**長さプレフィックスを付けず残りバイトを全部読む**
    形にした（サーバー側 HikariScoreBoard は素の PluginMessage を送るため）。
    `PayloadTypeRegistry.playS2C()` / `playC2S()` にチャンネルごとに登録している。
  - `<1.20.5`: 従来どおり `ClientPlayNetworking.registerGlobalReceiver(Identifier, ...)`。
  - この書き換えは **1.20.6 で一発で通った**（ネットワーキング関連のコンパイルエラーはゼロ）。

**1.20.6 で新たに判明した差分:**

- `ItemStack.canCombine(a, b)` → **`ItemStack.areItemsAndComponentsEqual(a, b)`**
- `Rarity.formatting` フィールドが private 化 → **`Rarity.getFormatting()`**
- 上記 2 つを `compat/ItemCompat` に閉じ込め、`AutoRestockHotbarHandler` を差し替え。

**要注意（次フェーズ以降）:**

- `MixinInGameHud` の `@Inject(method = "render")` は**メソッド名だけ**で対象を指定している。
  1.20.6 では `render(DrawContext, float)` のままだったので通ったが、
  1.21 で `render(DrawContext, RenderTickCounter)` に変わる。
  **Mixin AP がシグネチャ不一致を必ずコンパイルエラーにしてくれるかは未検証**なので、
  1.21 系では javap で実シグネチャを確認してから分岐を書くこと。

### 2026-08-14 — Phase 3 完了（1.20.1 / 1.20.2 / 1.20.4）

- 3 バージョンを追加し、`./gradlew chiseledBuild` で **7 ターゲット**が揃ってビルド成功。
- **`DrawCtx` の `>=1.20` ブランチは一発で通った。** `DrawContext` の
  `fill` / `drawTextWithShadow` / `enableScissor` / `disableScissor` / `getMatrices` が
  そのまま使えることを確認。`ScoreboardHudRenderer` と `ScoreboardTab` は
  DrawCtx 経由にしてあったため**無修正**で 1.20 系に載った。ファサード方式の狙いどおり。
- バージョン境界そのものである 4 箇所だけ Stonecutter のコメント分岐を入れた:
  `ColorPickerScreen.render` / `PositionEditorScreen.render` /
  `HikariTweaksConfigScreen.render` / `MixinInGameHud`（2 つのインジェクト）。
  いずれも「薄いラッパーだけ分岐させ、描画本体は `renderContent(DrawCtx, ...)` に集約」
  という形にしたので、分岐部分は数行で済んでいる。

**1.20.2 で新たに判明した差分:**

- `Screen.renderBackground(DrawContext)` → **`renderBackground(DrawContext, int, int, float)`**。
  render ラッパーを `>=1.20.2` / `>=1.20` / else の 3 分岐にした。
- `mouseScrolled(double, double, double)` → **`(double, double, double, double)`**
  （水平スクロール量が追加）。`HikariTweaksConfigScreen` の override を分岐。
  `ScoreboardTab.mouseScrolled` は自前メソッドなので 3 引数のまま、垂直量だけ渡している。
- `GameOptions.debugEnabled` が廃止され **`InGameHud.getDebugHud().shouldShowDebugHud()`** に移動。
  `compat/HudCompat.isDebugHudShown(MinecraftClient)` を新設。
- 1.20.4 は 1.20.2 の修正だけで**無修正で通った**。

**作業上の注意（ヒヤリハット）:**

- ラッパー導入を一括置換でやった際、`super.render(...)` の行が新旧どちらのブロックにも
  一致してしまい、置換対象を取り違えて 1.18.2 側のラッパーから `super.render` が
  消えた。ビルドエラーで検出できたが、**分岐ブロックを機械置換するときは
  置換後の文字列が自分自身にマッチしないか必ず確認すること。**

### 2026-08-14 — Phase 2 完了（1.19.2 / 1.19.3 / 1.19.4）

- `settings.gradle.kts` の `versions(...)` に 3 バージョンを追加し、いずれもビルド成功。
  `./gradlew chiseledBuild` で **4 ターゲット**の jar が `build/libs/1.0.11/` に揃うことを確認。
- **Stonecutter のコメント分岐が実地で動作することを確認。** 切り替え時に `src/` が
  書き換わることも無かった（アクティブは 1.18.2 のまま。生成物は
  `versions/<ver>/build/generated/stonecutter/` に出力される）。
  事前に取った `src/` のバックアップと差分ゼロであることを `diff -rq` で確認済み。
- 1.19.2 は無修正で通った（`TextCompat` の `>=1.19` 分岐が正しく効いた）。

**1.19.3 で新たに判明した差分（PLAN の想定より 1 バージョン早かった）:**

- `ButtonWidget` の public コンストラクタは **1.19.4 ではなく 1.19.3 で廃止**されていた。
  1.19.3 時点で `ButtonWidget.builder(...)` が使える。`WidgetCompat` の条件を
  `>=1.19.4` → **`>=1.19.3`** に修正。
- `SoundEvents` の定数が **1.19.3 で `RegistryEntry.Reference<SoundEvent>` になった**。
  `compat/SoundCompat.noteBlockPling()` を新設し、`>=1.19.3` では `.value()` を呼ぶ。
  `DurabilityWarningHandler` の呼び出しを差し替え。
- `player.sendMessage(Text, boolean)` は 1.19.x でもそのまま使えた。

**Stonecutter の記法メモ:**

- 3 分岐以上は `//?} elif <条件> {` が使える（`else if` ではなく **`elif`**）。
- 分岐ブロックの書き方は下記が正しい。無効な側がコメントアウトされた状態でソースに置かれる。

  ```java
  //? if >=1.19 {
  /*return Text.literal(value);
  *///?} else {
  return new LiteralText(value);
  //?}
  ```

### 2026-08-14 — Phase 1 完了（DrawCtx）

- `compat/DrawCtx` を新設。`<1.20` は `MatrixStack` + `DrawableHelper`、
  `>=1.20` は `DrawContext` を内部に持つ。公開 API は
  `fill` / `drawTextWithShadow` / `push` / `pop` / `translate` / `scale` /
  `enableScissor` / `disableScissor` / `matrices()` / `raw()`。
- 描画コードを全て `DrawCtx` 経由に置換：
  `ScoreboardHudRenderer.render(DrawCtx)`、`ScoreboardTab` の
  `render` / `renderSubTabButton` / `renderPlayersContent` / `renderDisplayContent` / `drawCentered`、
  `ColorPickerScreen` / `PositionEditorScreen`（`renderPreview` 含む）。
  `HikariTweaksConfigScreen` と `MixinInGameHud` は受け取った `MatrixStack` を
  `new DrawCtx(...)` で包んで渡す形にした。
- `ScoreboardTab` のシザーは `RenderSystem.enableScissor` の直呼び（GL 座標・原点左下）を
  やめ、画面座標を取る `DrawCtx.enableScissor(left, top, right, bottom)` に統一。
  1.20 未満の実装内で従来と同じ GL 座標計算をしているので描画結果は変わらない。
- `./gradlew build` 成功。生の描画 API が残っているのは `compat/DrawCtx` の中だけ
  （grep で確認）。例外は `Screen.render(...)` のオーバーライドと `MixinInGameHud` の
  インジェクト対象シグネチャで、これらは**バージョン境界そのもの**なので
  Phase 2 以降で Stonecutter のコメント分岐を入れる。

**判明した制約:**

- `DrawContext.drawTextWithShadow` は int 座標しか受け付けないため、
  `DrawCtx.drawTextWithShadow` は float を受けて `>=1.20` では切り捨てる。
  中央揃えなどで最大 1px ずれる可能性がある（実害は無いと判断）。

### 2026-08-14 — Phase 1 前半（小物ファサード 4 種）

- `compat/` に **TextCompat** / **RegistryCompat** / **IdCompat** / **WidgetCompat** を新設。
  それぞれ Stonecutter のコメント分岐（`>=1.19` / `>=1.19.3` / `>=1.21` / `>=1.19.4`）を
  あらかじめ書き込んである。
- 呼び出し側を全てファサード経由に置換：
  `ColorPickerScreen` / `PositionEditorScreen`（LiteralText + ButtonWidget）、
  `DurabilityWarningHandler`（LiteralText）、
  `AutoRestockHotbarHandler` / `HandRestockHandler`（Registry.ITEM）、
  `ScoreboardPacketClient`（new Identifier）。
  生の旧 API は compat パッケージ内にしか残っていないことを grep で確認済み。
- `./gradlew build` 成功。
- 残りは `DrawCtx`。次のイテレーションで着手する。

### 2026-08-14 — Phase 0 完了（Stonecutter 導入）

- ビルドスクリプトを Groovy → Kotlin DSL へ移行。`settings.gradle` / `build.gradle` を削除し、
  `settings.gradle.kts` / `stonecutter.gradle.kts` / `build.gradle.kts` / `stonecutter.properties.toml` を新設。
- Gradle wrapper を 8.6 → **9.7.0** に更新（Stonecutter 0.9.7 + fabric-loom 1.17.19 の要求）。
- 依存バージョンは `stonecutter.properties.toml` に **17 ターゲット分すべて**先行して記載済み。
  以降のフェーズは `settings.gradle.kts` の `versions(...)` に足すだけで良い。
- `./gradlew build` / `./gradlew chiseledBuild` ともに成功。生成 jar を検証し、
  `fabric.mod.json`（id/name/version/depends.minecraft）・`mixins.json` の `compatibilityLevel`・
  refmap がいずれも移行前と同等であることを確認。
- 次は Phase 1（compat ファサード）。

**このフェーズで入れた設計判断:**

- Stonecutter 0.9.7 では `registerChiseled` / `stonecutter.chiseled` API が**廃止**されている。
  代わりに `stonecutter.versions` を回して各ノードの `buildAndCollect` に依存する
  `chiseledBuild` タスクを `stonecutter.gradle.kts` で自前定義した。
- Loom 1.17 は Mixin AP が既定オフになったため `useLegacyMixinAp = true` を明示。
  古い Loader でも refmap 方式で確実に mixin が解決できるようにするため。
- Java リリースターゲットは toolchain ではなく `options.release` で切る（`mod.java` プロパティ）。
  JDK の追加ダウンロードを避けるため。1.17/1.17.1 は 16、1.18〜1.20.4 は 17、1.20.5 以降は 21。
- malilib/MiniHUD/ModMenu を Modrinth maven へ統一。1.18.2 は malilib **0.12.0** を指定し、
  移行前（masa maven の 0.12.0）と同じバージョンを維持した。
- **`maven-publish` を削除した**。Stonecutter のバージョンノードでは `group` を設定できず、
  CI も `build` しか使っていないため。maven 公開が必要になったら別途組み直すこと。

### 2026-08-14 — 調査完了

- 依存関係の入手性を Modrinth API / Fabric meta で確認。**1.17〜1.21.10 の全バージョンで
  malilib・MiniHUD・ModMenu・Fabric API・yarn が揃っている**ことを確認済み。
- malilib は masa の maven が 1.21.1 止まりのため、全ターゲットで Modrinth maven に統一する方針。
- Stonecutter 最新版 0.9.7、fabric-loom 最新版 1.18.0 を確認。
- ベースライン `./gradlew build`（1.18.2）はオフラインで成功することを確認。
- 設計を [PLAN.md](PLAN.md) にまとめた。次は Phase 0。

---

## 未解決の課題

- ~~Mixin AP がインジェクト対象のシグネチャ不一致をコンパイルエラーにするか未検証~~
      → **検証済み。検出しない。** メソッド名だけの指定は実際に誤ターゲットを引き起こした。
      `MixinInGameHud` は記述子付きに修正済み。`MixinClientPlayNetworkHandler.onEntityStatus` と
      `MixinOverlayRenderer.renderOverlays` はオーバーロードが無いため名前のみで問題ないが、
      新バージョン追加時は refmap を確認すること
- [ ] **実機での動作確認が一度も行われていない。** コンパイルと refmap の確認までしか
      自動検証できていないため、最終的には各バージョンで実際に起動テストが必要
- ~~fabric-loom 1.17.19 が MC 1.17 のビルドをサポートするか未検証~~
      → **解決。サポートしている。** ただし `splitEnvironmentSourceSets()` は使えないため
      ソース構成を `src/main` 一本に統合した
- ~~`.github/workflows/*.yml` が旧構成のまま~~ → 解決（Phase 8 で更新済み）
- [ ] `.idea/runConfigurations/*.xml` が旧 Gradle 構成を指しているため IDE から起動できない可能性あり。
      Stonecutter はアクティブバージョンごとに run 設定を生成するので、
      IDE 側は Gradle プロジェクトを再インポートするのが早い
- [ ] **MiniHUD `MixinOverlayRenderer` は全バージョンでビルドが通っているが実行時未検証。**
      `@Redirect` の `target` に intermediary 名 (`Lnet/minecraft/class_1297;`) を直書きしており、
      MiniHUD 側の `renderOverlays` が変わっていると実行時に適用失敗する。
      ビーコン補正機能が効かないバージョンが見つかったら `require = 0` で任意化することを検討する
- ~~malilib の GUI API が 1.21.x で変更されていないか未検証~~
      → 解決。1.21.9 の `onMouseClicked(Click, boolean)` 以外に破壊的変更は無かった
- ~~1.21.9 以降の描画パイプライン刷新の影響範囲が未調査~~
      → 解決。`DrawCtx` の行列操作（1.21.6 の 2D 化）と
      マウスイベントの `Click` レコード化（1.21.9）を吸収済み
- ~~1.17 / 1.18.1 / 1.19.3 / 1.20.2 向けの MiniHUD・ModMenu のバージョンが未確定~~
      → 解決。全 17 ターゲット分を `stonecutter.properties.toml` に記載済み
