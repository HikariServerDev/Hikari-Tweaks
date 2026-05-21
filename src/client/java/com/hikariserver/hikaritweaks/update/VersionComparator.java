package com.hikariserver.hikaritweaks.update;

// セマンティックバージョンに近い形式の比較。
// 例: v1.2.0, 1.2.0, 1.2.0-beta.1, 1.2.0-beta.10
//
// プレリリース文字列は数値部分を数値として比較するため
// beta.9 < beta.10 が正しく判定される。
public final class VersionComparator {
    // インスタンス化を禁止するプライベートコンストラクタ
    private VersionComparator() {}

    // candidate が current より新しいバージョンかどうかを返す
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    // 2 つのバージョン文字列を比較する（正 = a が新しい、負 = b が新しい、0 = 同じ）
    private static int compare(String a, String b) {
        ParsedVersion va = parse(a);
        ParsedVersion vb = parse(b);

        // メジャー・マイナー・パッチの数値比較
        int max = Math.max(va.numbers.length, vb.numbers.length);
        for (int i = 0; i < max; i++) {
            int na = i < va.numbers.length ? va.numbers[i] : 0;
            int nb = i < vb.numbers.length ? vb.numbers[i] : 0;
            if (na != nb) {
                return Integer.compare(na, nb);
            }
        }

        // 数値部分が等しい場合: プレリリースなし > プレリリースあり
        if (va.preRelease == null && vb.preRelease == null) {
            return 0;
        }
        if (va.preRelease == null) {
            return 1;   // a は正式リリース → a の方が新しい
        }
        if (vb.preRelease == null) {
            return -1;  // b は正式リリース → b の方が新しい
        }
        // 両方プレリリース: トークン単位で比較（数値は数値として扱う）
        return comparePreRelease(va.preRelease, vb.preRelease);
    }

    // プレリリース文字列をドット区切りのトークンに分割し、
    // 各トークンを「両方数値なら数値比較、それ以外は文字列比較（大文字小文字無視）」で比較する。
    // 例: "beta.10" > "beta.9"
    private static int comparePreRelease(String a, String b) {
        String[] tokensA = a.split("\\.", -1);
        String[] tokensB = b.split("\\.", -1);
        int len = Math.max(tokensA.length, tokensB.length);
        for (int i = 0; i < len; i++) {
            String ta = i < tokensA.length ? tokensA[i] : "";
            String tb = i < tokensB.length ? tokensB[i] : "";

            Integer ia = tryParseInt(ta);
            Integer ib = tryParseInt(tb);

            int cmp;
            if (ia != null && ib != null) {
                // 両方数値なら数値比較する
                cmp = Integer.compare(ia, ib);
            } else if (ia != null) {
                // 数値 < 文字列 (semver 仕様に準拠)
                cmp = -1;
            } else if (ib != null) {
                // 文字列 > 数値
                cmp = 1;
            } else {
                // 両方文字列なら大文字小文字無視で比較する
                cmp = ta.compareToIgnoreCase(tb);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    // 文字列を整数に変換して返す。変換できない場合は null を返す。
    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // バージョン文字列をパースして ParsedVersion に変換する
    private static ParsedVersion parse(String raw) {
        if (raw == null) {
            return new ParsedVersion(new int[]{0}, null);
        }
        String normalized = raw.trim();
        // 先頭の 'v' または 'V' を除去する
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        // ビルドメタデータ (+xxxx) を除去する
        String[] plusParts = normalized.split("\\+", 2);
        String baseAndPre = plusParts[0];
        // プレリリース (-xxxx) を分離する
        String[] dashParts = baseAndPre.split("-", 2);
        String base = dashParts[0];
        String pre = dashParts.length > 1 ? dashParts[1] : null;

        // ドット区切りの数値部分を配列に変換する
        String[] parts = base.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = parseIntSafe(parts[i]);
        }
        return new ParsedVersion(numbers, pre);
    }

    // 数字以外を除去してから整数に変換するヘルパー（変換失敗時は 0 を返す）
    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // パース済みバージョン情報を保持するレコード（数値部分とプレリリース文字列）
    private record ParsedVersion(int[] numbers, String preRelease) {}
}
