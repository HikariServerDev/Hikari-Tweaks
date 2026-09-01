<div align="center">

<img src="https://hikariserver.com/assets/logo.png" width="200" alt="Hikari-Tweaks">

# Hikari-Tweaks

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue.svg)](https://github.com/HikariServerDev/Hikari-Tweaks/blob/main/COPYING.LESSER)
[![Modrinth](https://img.shields.io/modrinth/dt/hikari-tweaks?label=Modrinth%20Downloads)](https://modrinth.com/mod/hikari-tweaks)

</div>

**Minecraft 1.17.1 〜 1.21.11 向けのクライアントサイド Fabric ユーティリティ Mod**。[Hikari Server (光鯖)](https://hikariserver.com) で開発されました。カスタマイズ可能なスコアボード HUD（HikariScoreBoard 連携）と便利機能をまとめて提供します。

## 1.2.1 の変更点

**Minecraft 1.21.6 以降で、この Mod の設定画面の文字がすべて表示されなくなっていた問題を
修正しました。** 1.21.6 からテキスト色のアルファ補完が無くなり、アルファ 0 の色で描こうとした
テキストは捨てられるようになったため、設定画面のプレイヤー名・種別ラベル・グループ見出し・
キャプションが一切描かれていませんでした。ゲーム中の HUD には影響ありません。
Minecraft 1.21.6 〜 1.21.11 で v1.1.0 / v1.2.0（`+1.21.8` / `+1.21.10` / `+1.21.11` の jar）を
お使いの場合は v1.2.1 へ更新してください。1.21.5 以前では挙動は変わりません。

## 機能


### カスタムスコアボード HUD

> [HikariScoreBoard](https://modrinth.com/mod/hikariscoreboard) のランキングを完全カスタマイズ可能な HUD として描画します。位置（X/Y アンカー 0-100%）・スケール（0.5x-3.0x）・ページング（1-50 行/ページ）・ヘッダ/本文/文字/スコア/自分行の ARGB カラーを個別に設定できます。バニラサイドバーはデフォルトで非表示。F3 デバッグ画面の表示中と、F1 で HUD 全体を隠しているあいだは描画しません。設定キーは `scoreboardCustomHud`（既定 ON）。これは `config/hikari-tweaks.json` の値で、設定画面にトグルはありません。位置・スケール・ページサイズ・色は**スコアボード**タブから変更します。
<br><br>

### スコア数値の滑らか表示（常時 ON・設定項目なし）

> カスタム HUD の数値は、更新時に飛ばずに新しい値へ滑らかに寄っていきます。処理はすべてこの Mod がクライアント側で行っており、この演出のためにネットワークは 1 バイトも使いません。以前サーバー側にあった同等の処理は削除済みなので、**滑らかに動くのは Hikari-Tweaks を入れているプレイヤーの画面だけ**です。常時 ON で設定項目はありません。実時間ベースの指数補間（時定数およそ 0.12 秒）なのでフレームレートが変わっても速さは変わらず、ウィンドウ非アクティブやワールド読み込みなどでフレームが 0.5 秒以上空いたときは、まとめて補間せず即座に実際の値へ合わせます。補間するのは表示される数字だけです。並び順と順位は常に実際の値で決めているため補間中に行が入れ替わることはなく、現れたばかりの行は 0 から数え上げずにその時点の実際の値から始まります。ランキングの各行とサーバー合計（Total）行の両方に効きます。ランキング v2 プロトコルが前提で、v1 パケットしか送らないサーバーでは演出なしの正確な値が表示されます。
<br><br>

### 耐久値警告

> 耐久アイテムの残り耐久が 1% 以下になったとき、チャット通知と効果音で警告します。手に持っているものだけでなく、インベントリの全スロットを確認します。警告は「警告状態に入った瞬間」に 1 回だけ出し、そこから**出た**ときにだけ再武装します。そのため修理してまた削れば再び警告が出ますが、最後の 1% を削り続けるあいだに連呼されることはありません。設定キーは `durabilityWarningEnabled`（**補助機能**タブ、既定 ON）。
<br><br>

### ホットバー自動補充

> 実在するコンテナブロックを開いたとき、リストのアイテムをそのコンテナからホットバーへ自動補充し、画面を閉じます。リストは「リスト」タブで編集できます。判定は「確証が無ければ動かさない」方針です。クロスヘアの先のブロックエンティティが中身を持ち、それがエンダーチェストではなく、画面のコンテナスロット数がその中身のサイズと一致したときにだけ動きます（チェストだけは隣接してダブルチェストになりうるため、ちょうど 2 倍も許可します）。したがってチェスト・トラップチェスト・樽・設置したシュルカーボックス・ホッパー・ディスペンサー・ドロッパー・かまど類・醸造台が対象になります。エンダーチェストは明示的に除外しています。チェスト付き／ホッパー付きトロッコ、チェスト付きボート、村人との取引は、ブロックエンティティではなくエンティティでクロスヘアの先に判定材料が無いため対象外です。コマンド・NPC・アイテムから開かれたプラグイン製メニューも同じ理由で対象外です。実在するコンテナを右クリックして開いた、**スロット数まで一致する**プラグイン製メニューはクライアントから区別できないので、チェストを土台にしたショップ GUI を使うサーバーでは補充リストを絞ってください。設定キーは `autoRestockHotbar`（**補助機能**タブ、既定 OFF）。
<br><br>

### トーテム補充

> 不死のトーテム発動時に、発動前に持っていた手へインベントリからトーテムを補充します。通常のホットキーによるスロット切替では発動しないよう調整済みです。設定キーは `totemRestock`（**補助機能**タブ、既定 OFF）。
<br><br>

### 手持ち自動補充

> Tweakeroo の handrestock 相当。ホットバー内の指定アイテムが **5 個以下** になったとき、インベントリの残りから自動で補充します。監視するのはホットバーだけで、インベントリは補充元としてのみ使います。補充リストはコンテナ開時のリストとは独立しているため、細かく設定できます。設定キーは `handRestock`（**補助機能**タブ、既定 OFF）。
<br><br>

### MiniHUD ビーコン補正

> MiniHUD のフリーカメラ使用時、ビーコン範囲表示の基準をカメラではなく**プレイヤー位置に補正**します。要 MiniHUD。設定キーは `fixBeaconRangeFreeCam`（**補助機能**タブ、既定 ON）。
<br><br>

### スコアボードタブ「プレイヤー」（設定項目ではなく設定画面のタブ）

> HikariScoreBoard と組み合わせて使うと、設定画面の**スコアボード**タブにサーバーが把握しているプレイヤーが「表示中」「非表示」に分かれて並び、ランキングに出すかどうかを個別に切り替えられます。サーバーが BOT と判定したエントリには `[Bot]` が付くので見分けやすくなっています。一覧は**プレイヤー**サブタブにあり、隣の**表示設定**サブタブに HUD のページサイズ・スケール・位置・色があります。ON/OFF の設定項目はありません。
<br><br>

## 動作環境

- Minecraft **1.17.1 〜 1.21.11**（Fabric）
- **Fabric Loader** >= 0.14.0（MC 1.17.1 〜 1.20.4）/ >= 0.15.10（MC 1.20.5 以降）
- **Fabric API**
- **[MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib)** — jar ごとに下限バージョンを宣言しています（下表を参照）

### 対応バージョン一覧

Minecraft のバージョン群ごとに 1 つの jar を配布しています。使用中のバージョンに合うものをダウンロードしてください。

| 配布 jar | 対応する Minecraft バージョン | MaLiLib 下限 |
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

## 設定画面の開き方

ゲーム中に **`H` + `T`** を押します（`H` を押しながら `T`）。**押す順序が重要**で、
`T` を先に押すとチャットが開きます。

キーは設定画面の**ホットキー**タブから変更できます。
[Mod Menu](https://modrinth.com/mod/modmenu) を導入していれば、そちらからも開けます。
スコアボードのページ送りは既定で未割り当てなので、必要なら同じタブで割り当ててください。

## 任意で連携できる Mod

- **[MiniHUD](https://www.curseforge.com/minecraft/mc-mods/minihud)** — ビーコン範囲補正機能を使う場合に必要
- **[Mod Menu](https://modrinth.com/mod/modmenu)** — ホットキーを使わずに設定画面へ行きたい場合の代替手段
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

Copyright (C) 2025-2026 HikariServerDev

LGPL-3.0 は GPL-3.0 に追加の許諾を重ねる形で書かれており、GPL-3.0 を参照によって取り込んでいます。
そのため両方の全文を `COPYING.LESSER`（LGPL）と `COPYING`（GPL）としてプロジェクトに同梱しており、
配布する全 jar の `META-INF/` にも両方が入っています。参照している第三者プロジェクトは
それぞれ独自のライセンスに従います（`NOTICE` 参照）。

また、配布する全 jar は Gson 2.10.1 を `META-INF/jars/` に改変せず同梱しています。Gson 自体は
Apache License 2.0 のままで、著作権表示とライセンス全文は同じ jar の
`META-INF/THIRD-PARTY-NOTICES.txt` と `META-INF/licenses/Apache-2.0.txt` に入っています。
