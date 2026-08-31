# `hikariscoreboard:ranking_v2` プロトコル仕様

対象ブランチ: 両リポジトリとも `feature/ranking-v2`

- サーバー: `HikariScoreBoard`
- クライアント: `Hikari-Tweaks`

**この文書が唯一の契約である。** 両側はここに書かれたバイト列だけを信じて実装すること。
片側だけを変更してはならない。相手側の実装を読んで合わせるのではなく、
両方がこの文書に合わせる。

---

## 0. なぜ v2 が要るのか

Phase 1 で「計算」の遅延は消えたが、「送信」の遅延が残っている。

旧チャンネル `hikariscoreboard:ranking_data` は**毎回リスト全体**を送る。
`topEntries`（既定 100 件）と `fullEntries`（最大 200 件）で 1 パケット約 6KB。
30 人が同時に採掘すると毎 tick 30 × 6KB = 秒間約 2.4MB になり、
同じ tick で送ろうとすると帯域が破綻する。
結果、送信間隔を空ける以外に手が無い ＝ **プロトコルが遅延を強制している。**

バニラのスコアボードが即時なのは、変わった 1 行だけ（約 20 バイト）を
同じ tick で送るからである。v2 はこれと同じ構造にする。

差分 1 件あたり約 30 バイト。**200 分の 1** になるので、
最小送信間隔そのものを撤廃できる。

---

## 1. チャンネルと切り替え

| チャンネル | 用途 |
|---|---|
| `hikariscoreboard:ranking_data` | **v1。削除しない。** 旧 Hikari-Tweaks 用 |
| `hikariscoreboard:ranking_v2` | 新規。差分対応クライアント用 |

サーバーは `NetworkChannel.canSend(player, RANKING_V2)` が true のクライアントへ
**v2 だけ**を送る。false なら従来どおり v1 か、Tweaks 非導入ならバニラサイドバー。

**同じプレイヤーに v1 と v2 を両方送ってはならない。** HUD が二重に更新される。

クライアントがチャンネルを登録しているかどうかが唯一の判定材料である
（Hikari-Tweaks はサーバーへ自分のバージョンを名乗らない）。

---

## 2. ワイヤフォーマット

すべて Minecraft の `PacketByteBuf` の標準エンコーディングを使う。

- `varint` … `readVarInt` / `writeVarInt`
- `string(N)` … `readString(N)` / `writeString(s, N)`（N は最大文字数）
- `long` … `readLong` / `writeLong`（8 バイト・ビッグエンディアン）
- `uuid` … `readUuid` / `writeUuid`（**16 バイト固定**。most significant long → least significant long の順）

先頭 1 バイトがメッセージ種別。**将来の拡張のためここを必ず読むこと。**

### 0x00 HIDE

```
byte  0x00
```

以降に**何も書かない**。1 バイトで完結する。
クライアントは表示中のボードを消し、保持しているテーブルを捨てる。

### 0x01 SNAPSHOT

```
byte    0x01
varint  boardId
string  title           (最大 256)
long    serverTotal
varint  count
count 回 {
    uuid    playerId    (16 バイト)
    string  name        (最大 64)
    long    value
}
```

クライアントはテーブルを**丸ごと置き換える**（マージではない）。

### 0x02 DELTA

```
byte    0x02
varint  boardId
long    serverTotal
varint  upsertCount
upsertCount 回 {
    uuid    playerId    (16 バイト)
    string  name        (最大 64)
    long    value
}
varint  removeCount
removeCount 回 {
    uuid    playerId    (16 バイト)
}
```

`upsert` は「その uuid の行を name / value で作る or 上書きする」。
`remove` は「その uuid の行をテーブルから消す」。

---

## 3. 不変条件

**破ると表示が壊れる。実装中に迷ったらここへ戻ること。**

1. **`boardId` が一致しない DELTA はクライアントが捨てる。**
   サーバーは `boardId` を変えたら、その値での最初のメッセージを
   **必ず SNAPSHOT にする**こと。DELTA を先に送ってはならない。

2. **`boardId` を変える契機**（サーバー側の責任）
   - 視聴者が表示 stat を切り替えた
   - JOIN / 再接続（クライアントのテーブルが空の可能性がある）
   - 送信経路が v1 → v2 に切り替わった
   - `configEpoch` が変わった（blocked / FakePlayer / topLimit / aggregate 再読込）
   - サーバーがその視聴者の差分を作れない（変更ログから溢れた）

3. **並び順はクライアントが決める。**
   規則は **`value` 降順、同値は `playerId.toString()` の辞書順昇順**。
   サーバー側の `Ranking` と**同じ規則**であること。
   `UUID.compareTo` は符号付き long 比較で**結果が異なる**ので使わないこと。

4. **順位は並べ替えた後の添字 + 1。** サーバーは順位を送らない。

5. **`serverTotal < 0` は「Total 行を出すな」のセンチネル。** v1 と同じ意味。

6. **自分の行の判定は uuid で行う。** クライアントは自分の UUID を知っている
   (`MinecraftClient.getInstance().player.getUuid()`)。
   v1 は表示名の一致で判定していたため、別名が付くと強調が効かない不具合があった。
   v2 では名前を一切使わない。

7. **値 0 の行は送らない。** サーバーは値が 0 になった uuid を
   `upsert value=0` ではなく **`remove`** として送る。
   （v1 の `entries` が値 0 を除外していた挙動に合わせる。
   `serverTotal` には 0 の人も含める、というのも v1 と同じ。）

8. **`count` / `upsertCount` / `removeCount` の上限。**
   SNAPSHOT の `count` は `CLIENT_FULL_LIMIT`（200）以下。
   クライアントは上限を超える値を受け取ったら**接続を切らずに**打ち切ること
   （壊れたサーバーで落とされないため）。

9. **末尾に余分なバイトを書かない。** v1 と違い `isReadable()` をセンチネルには
   使っていないが、将来フィールドを足すときのために末尾は常にぴったり終わらせる。

---

## 4. サーバー側の実装メモ

### 4.1 差分の作り方

視聴者ごとに「最後に送った版」を持ち、`StatIndex` の**変更ログ**から差分を組む。

- `StatIndex` に「版 → その版で値が変わった uuid の集合」のリングバッファを持たせる。
  長さは 256 版ぶんもあれば足りる（視聴者は全員最新版付近にいる）。
- 視聴者の `lastSentVersion` から現在版までの集合の**和**を取り、
  その uuid ぶんだけ `upsert` / `remove` を組む。
- `lastSentVersion` が古すぎてリングから溢れていたら **SNAPSHOT にフォールバック**する
  （`boardId` を上げる）。

**視聴者ごとに「送った内容のコピー」を持たない。** 30 人 × 400 件のマップを
抱えるのは無駄で、リングバッファのほうがはるかに小さい。

### 4.2 送信タイミング

Phase 1 と同じく**変化駆動**。クライアントにキャッシュ TTL は無いので定期再送はしない。

**最小送信間隔を置かない。** 差分が小さいので同じ tick で送ってよい。
これが「バニラ同等の即時性」の意味である。
`SendGate` は v2 経路では間隔判定を通さないこと。

### 4.3 やってはいけないこと

- v1 の送信経路を削る（旧クライアントが切断される）
- v1 のバイト配置を変える（同上）
- 同じプレイヤーへ v1 と v2 を両方送る

---

## 5. クライアント側の実装メモ

### 5.1 状態

`Map<UUID, Row(name, value)>` と `boardId` と `title` と `serverTotal` を保持する。
描画時に「`value` 降順、同値は `uuid.toString()` 昇順」で並べる。

**現行 v1 の実装は「サーバーが並べたものをそのまま描く」ので、
ソート処理は新規追加になる。** v1 の受信経路は残すこと
（サーバーが旧版のままの構成があり得る）。

### 5.2 数値の補間（ここで実装する）

Phase 1 でサーバー側の `ScoreSmoother` は削除済み。
サーバーでやると送信レートに従属して破綻することが分かったため、
**クライアントで実時間ベースの補間を行う。**

- `MixinInGameHud` は `RenderTickCounter` / `tickDelta` を受け取りながら
  `ScoreboardHudRenderer.render(DrawCtx)` へ渡さずに捨てている。まずそこを繋ぐ。
- 行ごとに `displayed → target` をフレーム時間ベースで補間する。
  **tick 数やパケット数ではなく実時間で**進めること。
- 並び順は**実値**で決める。補間するのは表示される数字だけ。
  そうしないと補間中に行が入れ替わってちらつく。
- 既定 ON、設定で切れるようにする。

これでネットワークを 1 バイトも使わず 60fps で滑らかに動く。
今回のラグの根本原因が構造的に再発しなくなる。

### 5.3 壊れたパケットで落ちないこと

現行 v1 のパースには try/catch が無く、不正なパケットは
netty スレッドで例外 → **クライアント切断**になる。
v2 の受信は例外を捕まえてログに落とし、そのパケットを捨てるだけにすること。

---

## 6. テスト

サーバー側・クライアント側とも、コーデックは MC に依存しない純ロジックに
切り出してユニットテストを書くこと。最低限:

- SNAPSHOT / DELTA / HIDE の書き込み → 読み込みの往復で内容が一致する
- `boardId` 不一致の DELTA が捨てられる
- 並び順が「値降順・同値は uuid 文字列昇順」になる（`UUID.compareTo` との差が出る値で）
- 値 0 が `remove` として送られる
- `serverTotal < 0` で Total 行が出ない
- 変更ログから溢れた視聴者に SNAPSHOT が送られる

サーバー側は既存の 97 ケースを壊さないこと。
