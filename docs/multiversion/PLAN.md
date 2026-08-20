# Hikari-Tweaks マルチバージョン対応 設計書

対象: **Minecraft 1.17 〜 1.21.11（Fabric）**
起点: 1.18.2 単一ターゲット（fabric-loom 1.6.12 / Gradle 8.6 / Java 17）

---

## 1. 採用する仕組み: Stonecutter

[Stonecutter](https://stonecutter.kikugie.dev/) (`dev.kikugie.stonecutter`, 最新 **0.9.7**) を採用する。

理由:

- ソースツリーは 1 本のまま、コメント形式のプリプロセッサ（`//? if >=1.19.3 {`）でバージョン差分を吸収できる。
- ブランチ分割方式と違い、機能追加が全バージョンへ自動的に反映される。
- `chiseledBuild` で全ターゲットのビルドを一括実行でき、CI に載せやすい。
- Fabric 系マルチバージョン Mod の事実上の標準。

### ディレクトリ構成（移行後）

```
settings.gradle(.kts)        # stonecutter プラグイン + ターゲット一覧
stonecutter.gradle(.kts)     # 共通の chiseled タスク定義
build.gradle                 # 全ターゲット共通のビルドスクリプト（バージョンごとに評価される）
stonecutter.properties.toml  # 全バージョンの依存を一括定義（実際に採用した方式）
gradle.properties            # Gradle 自体のオプションのみ
versions/                    # Stonecutter が生成するバージョンノード（.gitignore 済み）
src/main/java                # 単一ソースツリー（プリプロセッサコメント入り）
scripts/verify-jars.py       # ビルド後の jar 検証スクリプト
```

---

## 2. ビルドターゲット一覧

malilib のビルド区切りに合わせて **18 ターゲット**。1 ターゲット = 1 jar。
括弧内はその jar が `fabric.mod.json` で対応宣言する MC バージョン。

| # | ターゲット | カバー範囲 | yarn | Fabric API | malilib | MiniHUD | ModMenu | Java |
|---|---|---|---|---|---|---|---|---|
| 1 | 1.17 | 1.17 | 1.17+build.13 | 0.36.0+1.17 | 0.10.0-dev.22+beta.1 † | 0.19.0-dev.20210626.193015 | 2.0.17 | 16 |
| 2 | 1.17.1 | 1.17.1 | 1.17.1+build.65 | 0.46.1+1.17 | 0.10.0-dev.26 † | 0.20.0-beaconoverlay.1 | 2.0.17 | 16 |
| 3 | 1.18.1 | 1.18, 1.18.1 | 1.18.1+build.22 | 0.46.6+1.18 | 0.11.8 | 0.21.5 | 3.0.1 | 17 |
| 4 | 1.18.2 | 1.18.2 | 1.18.2+build.4 | 0.77.0+1.18.2 | 0.12.0 | 0.22.1 | 3.2.5 | 17 |
| 5 | 1.19.2 | 1.19, 1.19.1, 1.19.2 | 1.19.2+build.28 | 0.77.0+1.19.2 | 0.13.0 | 0.23.3-test.1 | 4.2.0-beta.2 | 17 |
| 6 | 1.19.3 | 1.19.3 | 1.19.3+build.5 | 0.76.1+1.19.3 | 0.14.1-pre.1 | 0.25.0 | 5.1.0 | 17 |
| 7 | 1.19.4 | 1.19.4 | 1.19.4+build.2 | 0.87.2+1.19.4 | 0.15.4 | 0.26.2 | 6.3.1 | 17 |
| 8 | 1.20.1 | 1.20, 1.20.1 | 1.20.1+build.10 | 0.92.11+1.20.1 | 0.16.3 | 0.27.1 | 7.2.2 | 17 |
| 9 | 1.20.2 | 1.20.2 | 1.20.2+build.4 | 0.91.6+1.20.2 | 0.17.0 | 0.29.1 | 8.0.1 | 17 |
| 10 | 1.20.4 | 1.20.3, 1.20.4 | 1.20.4+build.3 | 0.97.3+1.20.4 | 0.18.4-alpha.1 | 0.30.2 | 9.2.0 | 17 |
| 11 | 1.20.6 | 1.20.5, 1.20.6 | 1.20.6+build.3 | 0.100.8+1.20.6 | 0.19.2 | 0.31.0 | 10.0.0 | **21** |
| 12 | 1.21.1 | 1.21, 1.21.1 | 1.21.1+build.3 | 0.116.15+1.21.1 | 0.21.10 | 0.32.60 | 11.0.4 | 21 |
| 13 | 1.21.3 | 1.21.2, 1.21.3 | 1.21.3+build.2 | 0.114.1+1.21.3 | 0.22.8 | 0.33.10 | 12.0.1 | 21 |
| 14 | 1.21.4 | 1.21.4 | 1.21.4+build.8 | 0.119.4+1.21.4 | 0.23.5 | 0.34.8 | 13.0.4 | 21 |
| 15 | 1.21.5 | 1.21.5 | 1.21.5+build.1 | 0.128.2+1.21.5 | 0.24.3 | 0.35.4 | 14.0.2 | 21 |
| 16 | 1.21.8 | 1.21.6, 1.21.7, 1.21.8 | 1.21.8+build.1 | 0.136.1+1.21.8 | 0.25.7 | 0.36.7 | 15.0.2 | 21 |
| 17 | 1.21.10 | 1.21.9, 1.21.10 | 1.21.10+build.3 | 0.138.4+1.21.10 | 0.26.8 | 0.37.6 | 16.0.1 | 21 |
| 18 | 1.21.11 | 1.21.11 | 1.21.11+build.6 | 0.141.6+1.21.11 | 0.27.17 | 0.38.14 | 17.0.0 | 21 |

- masa の maven は malilib が **1.21.1 までしか無い**ため、原則 **Modrinth maven
  (`maven.modrinth:malilib:<version>`)** を使う。
- MiniHUD / ModMenu は全ターゲットで Modrinth maven（どちらも `modCompileOnly`）。
- **†** の付いた malilib（1.17 系）は **Modrinth maven が配信していない**ため、
  masa の maven（`https://masa.dy.fi/maven`）から
  `fi.dy.masa.malilib:malilib-fabric-1.17.x:<version>` として取得している。
  そのため `deps.malilib` はバージョン文字列ではなく**完全な座標**を書く。
- 表の内容は `stonecutter.properties.toml` が正。この表は読み物用の写しなので、
  食い違ったら TOML を信じること。

### バージョン確認用コマンド

```bash
curl -s "https://api.modrinth.com/v2/project/malilib/version" | python -c "import json,sys;[print(v['version_number'],v['game_versions']) for v in json.load(sys.stdin)]"
```

### 採用したツールチェイン（実測）

| ツール | バージョン | 備考 |
|---|---|---|
| Stonecutter | 0.9.7 | `registerChiseled` API は廃止済み。`chiseledBuild` は自前定義 |
| fabric-loom | 1.17.19 | 1.17〜1.21.11 の全バージョンで動作した |
| Gradle | 9.7.0 | Stonecutter 0.9.7 / loom 1.17 の要求 |
| JDK | 21 | `options.release` で 16 / 17 / 21 を切り替えるため 1 つで足りる |

**`splitEnvironmentSourceSets()` は使えない。** MC 1.17.x は bundled server jar が無く
Loom が split 構成を拒否するため、クライアント専用コードも `src/main/java` に置いている。

---

## 3. バージョン間 API 差分と対応方針

差分は原則 **`compat` パッケージのファサード**に閉じ込め、呼び出し側のコードは 1 本に保つ。
どうしても閉じ込められない箇所のみ Stonecutter のコメントを直接使う。

### 3.1 Text（1.19 で `LiteralText` 削除）

- 影響: `ColorPickerScreen`, `PositionEditorScreen`, `DurabilityWarningHandler`
- 対応: `compat/TextCompat.literal(String)`
  - `<1.19`: `new LiteralText(s)`
  - `>=1.19`: `Text.literal(s)`

### 3.2 Registry（1.19.3 で `Registry.ITEM` → `Registries.ITEM`）

- 影響: `AutoRestockHotbarHandler:109`, `HandRestockHandler:58,99`
- 対応: `compat/RegistryCompat.itemId(Item)`
  - `<1.19.3`: `net.minecraft.util.registry.Registry.ITEM.getId(...)`
  - `>=1.19.3`: `net.minecraft.registry.Registries.ITEM.getId(...)`

### 3.3 Identifier（1.21 で `new Identifier(...)` → `Identifier.of(...)`）

- 影響: `ScoreboardPacketClient:16-19`
- 対応: `compat/IdCompat.of(String, String)`

### 3.4 描画 API（1.20 で `MatrixStack` + `DrawableHelper` → `DrawContext`）

最大の差分。影響: `ScoreboardHudRenderer`, `ScoreboardTab`, `ColorPickerScreen`,
`PositionEditorScreen`, `HikariTweaksConfigScreen`, `MixinInGameHud`

- 対応: `compat/DrawCtx` ラッパークラスを新設し、描画コードは全て `DrawCtx` を受け取る形に書き換える。
  - `<1.20`: `MatrixStack` を保持。`fill` は `DrawableHelper.fill`、`drawText` は `textRenderer.draw(matrices, ...)`
  - `>=1.20`: `DrawContext` を保持。`fill` / `drawText` / `drawTextWithShadow` に委譲
  - `push/translate/scale/pop` は `<1.20` は `MatrixStack`、`>=1.20` は `ctx.getMatrices()`
  - **1.21.6+ 注意**: `getMatrices()` が `Matrix3x2fStack`（2D）に変わり `translate(x,y,z)` / `scale(x,y,z)` が無い。
    1.21.9/1.21.10 の描画パイプライン刷新も含め、ここは個別に検証が必要。
  - `RenderSystem.enableScissor` (`ScoreboardTab:425,526`) は `>=1.20` では `ctx.enableScissor(...)` へ。

### 3.5 GUI ウィジェット

- `new ButtonWidget(x,y,w,h,text,onPress)` は **1.19.3 で `ButtonWidget.builder()` に変更**（実測。
  当初 1.19.4 と見積もっていたが 1 バージョン早かった）。
  - 影響: `ColorPickerScreen:95,135,143,152`, `PositionEditorScreen:68,82`
  - 対応: `compat/WidgetCompat.button(x,y,w,h,text,onPress)`
- `SliderWidget` のサブクラス（`ComponentSlider`, `ScaleSlider`）は
  `renderTrack`/`applyValue`/`updateMessage` のシグネチャがバージョンで変わるため、
  該当メソッドのみ Stonecutter コメントで分岐する。
- `Screen.render(MatrixStack|DrawContext, int, int, float)` のシグネチャ変更
  → 各 Screen の `render` を分岐させ、内部で `DrawCtx` に包んで共通処理へ渡す。
- **1.20.2+**: `Screen.renderBackground` のシグネチャ変更、1.21.x でさらに変更あり。

### 3.6 ネットワーキング（1.20.5 で CustomPayload 必須化）

影響: `ScoreboardPacketClient` 全体（サーバー側 HikariScoreBoard は **生の PluginMessage** を送る）

- `<1.20.5`: 現行どおり `ClientPlayNetworking.registerGlobalReceiver(Identifier, ...)` / `send(Identifier, buf)`
- `>=1.20.5`: 生バイト列をそのまま運ぶ `RawPayload implements CustomPayload` を実装する。
  - `CustomPayload.Id<RawPayload>` をチャンネルごとに定義
  - codec は **残りバイト全部を読む**もの（`PacketByteBuf` を丸ごと保持）にする。
    `PacketCodec.of(write, read)` で自前実装すること。長さプレフィックス付きの
    `PacketCodecs.BYTE_ARRAY` を使うとサーバー側とバイト列が食い違うので **不可**。
  - `PayloadTypeRegistry.playS2C().register(id, codec)` / `playC2S()` を初期化時に呼ぶ
  - 受信は `ClientPlayNetworking.registerGlobalReceiver(Id, (payload, context) -> ...)`
- 対応: `compat/NetCompat` にチャンネル登録・送信・受信コールバックを寄せる。
  受信ハンドラの引数は共通の `(MinecraftClient, PacketByteBuf)` に正規化する。

### 3.7 Mixin

> ⚠️ **`method` には必ず完全な記述子を書くこと。**
> 1.20 以降 `InGameHud` には `renderScoreboardSidebar` のオーバーロードが 2 つあり
> （呼び出し元と実描画）、メソッド名だけを書くと Mixin AP が**呼び出し元の方**を選ぶ。
> **ハンドラ側の引数不一致は AP が検出しない**ので、ビルドは通るのに実行時に
> mixin 適用が失敗する。`scripts/verify-jars.py` で refmap を必ず確認すること。

| Mixin | 差分（実測） |
|---|---|
| `MixinInGameHud.renderScoreboardSidebar` | 目的の対象は常に `(…, ScoreboardObjective)` 版。第1引数が `<1.20`: `MatrixStack` / `>=1.20`: `DrawContext` |
| `MixinInGameHud.render` | `<1.20`: `(MatrixStack, float)` / `1.20〜1.20.6`: `(DrawContext, float)` / `>=1.21`: `(DrawContext, RenderTickCounter)` |
| `MixinClientPlayNetworkHandler.onEntityStatus` | `packet.getEntity(world)` はほぼ不変。ステータス 35 も不変。低リスク |
| `MixinOverlayRenderer` | MiniHUD 側の `renderOverlays` シグネチャ・`EntityUtils.getCameraEntity()` の記述子がバージョンで変わる。`@Redirect` の `target` に intermediary 名 (`Lnet/minecraft/class_1297;`) を直書きしているため、**MiniHUD のバージョンごとに確認必須**。最悪 `require = 0` で任意化する |

`hikari-tweaks.mixins.json` の `compatibilityLevel` も `JAVA_17` / `JAVA_21` で分岐させる。

### 3.8 その他

- **Java リリースターゲット**: 1.20.4 以下は 17、1.20.5 以上は 21。
  `options.release` と `fabric.mod.json` の `depends.java` を分岐。
- **`fabric.mod.json`**: `depends.minecraft` をターゲットごとに書き換える必要があるため、
  `processResources` の `expand` に `minecraft_dependency` プロパティを渡す方式へ変更する。
- **`splitEnvironmentSourceSets()`**: 全ターゲットで維持する（クライアント専用 Mod のため）。
- **refmap**: Loom 1.17 は Mixin AP が既定オフのため `useLegacyMixinAp = true` を明示している。
  ソース構成を `src/main` 一本にしたため refmap 名は `hikari-tweaks.refmap.json`。

---

## 4. 作業順序

ベースラインの 1.18.2 を壊さないことを最優先に、**古い側と新しい側へ交互に広げず、
API 変更点の少ない順**に進める。

1. **Phase 0** — Stonecutter 導入。ターゲットは 1.18.2 のみ。`./gradlew build` が通ることを確認
2. **Phase 1** — `compat` ファサード新設（Text / Registry / Identifier / Draw / Widget / Net）。
   1.18.2 のまま挙動が変わらないことを確認
3. **Phase 2** — 1.19.2 → 1.19.3 → 1.19.4 を追加
4. **Phase 3** — 1.20.1 → 1.20.2 → 1.20.4 を追加（描画 API 移行の山場）
5. **Phase 4** — 1.20.6 を追加（Java 21 / CustomPayload の山場）
6. **Phase 5** — 1.21.1 → 1.21.3 → 1.21.4 → 1.21.5 を追加
7. **Phase 6** — 1.21.8 → 1.21.10 を追加（描画パイプライン刷新の山場）
8. **Phase 7** — 1.18.1 → 1.17.1 → 1.17 を追加（後方拡張）
9. **Phase 8** — CI / リリースワークフローを `chiseledBuild` 対応に更新、README 更新

---

## 5. 完了条件

- `./gradlew chiseledBuild` が **18 ターゲット全て**で成功する
- `build/libs/` に 18 個（+sources）の jar が出力される
- 各 jar の `fabric.mod.json` の `depends.minecraft` が §2 のカバー範囲と一致する
- 1.18.2 の既存挙動にリグレッションが無い
- `.github/workflows/ci.yml` / `release.yml` が全ターゲットをビルド・公開する
- README / README-ja に対応バージョン表が載っている

---

## 6. 既知のリスク

| リスク | 影響 | 緩和策 |
|---|---|---|
| MiniHUD の `OverlayRenderer` mixin が新バージョンで一致しない | ビルドは通るが実行時に mixin 適用失敗 | `require = 0` にして機能を任意化し、ログに警告を出す |
| malilib の GUI API（`GuiConfigsBase`, `WidgetSlider` 等）が 1.21.x で変更 | コンパイルエラー | malilib の該当バージョンの jar をデコンパイルして確認。差分は `compat` へ |
| 1.21.9/1.21.10 の描画刷新 | `ScoreboardHudRenderer` / 各 Screen が動かない | 最後のフェーズに回し、必要なら描画層を丸ごとバージョン別ソースに分離 |
| 初回ビルドで 17 バージョン分の MC/mappings をダウンロード | 非常に時間がかかる | ターゲットを 1 つずつ追加し、都度ビルドを確認する |
| 実機動作確認ができない | 実行時バグの見逃し | コンパイル + mixin 適用チェックまでを自動検証範囲とし、実機確認は別途 |
