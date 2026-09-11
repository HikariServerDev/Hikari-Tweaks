# GitHub Release に添付した jar を Modrinth へ上げるスクリプト。
#
#   python scripts/publish-modrinth.py --tag v1.2.3 --jar-dir jars --dry-run
#   MODRINTH_TOKEN=... python scripts/publish-modrinth.py --tag v1.2.3 --jar-dir jars
#
# 普段は .github/workflows/release.yml から呼ばれる。
# 手元で叩くのは、送る内容を確かめる --dry-run だけでよい（トークン不要）。
#
# 1 ターゲット = Modrinth の 1 バージョン。17 個を 1 個ずつ作成する。
# 送る内容はすべてリポジトリから組み立てる。v1.1.0 / v1.2.2 を手で上げたときの規則をそのまま写した。
#   バージョン番号   <mod.version>+<ターゲット>            … jar のバージョンと同じ
#   名前             <mod.name> <mod.version> (MC <最初> - <最後>)
#   対応 MC          stonecutter.properties.toml の mod.mc_releases
#   変更履歴         README.md の「### v<mod.version>」の節（英語版。Modrinth の利用者向け）
#   依存             DEPENDENCIES（全ターゲット共通）
#
# ★ トークンに要る権限は「Create versions」だけ。
#   作成後の編集・削除・説明文の変更はブラウザから手で行う運用なので、このスクリプトは一切しない。
#
# ★ 何度実行しても安全。Modrinth に同じバージョン番号が既にあるターゲットは飛ばす。
#   途中で失敗したら、そのままワークフローを再実行すれば残りだけが上がる。
#
# ★ 1 個でも送れない理由（jar が無い・中身が違う・変更履歴が無い…）があれば、
#   1 個も送らずに止まる。17 個のうち一部だけ公開された状態を作らないため。
#
# 終了コード 0 = 全て上げた（または既に上がっていた）

import argparse
import email.parser
import email.policy
import hashlib
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
import zipfile

try:
    import tomllib
except ImportError:  # Python 3.10 以前
    sys.exit("Python 3.11 以降が必要です（tomllib を使うため）")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

API = "https://api.modrinth.com/v2"
# Modrinth は API の利用者を識別できる User-Agent を求めている
USER_AGENT = "HikariServerDev/Hikari-Tweaks (scripts/publish-modrinth.py)"

# modrinth.com/mod/hikari-tweaks のプロジェクト ID。slug ではなく ID を送る必要がある。
PROJECT_ID = "gqePQwPY"

# 全ターゲット共通の依存。fabric.mod.json の depends / suggests と対応させること。
DEPENDENCIES = [
    ("P7dR8mSH", "required"),  # fabric-api
    ("GcWjdA9I", "required"),  # malilib
    ("UMxybHE8", "optional"),  # minihud（ビーコン補正でのみ使う）
    ("mOgUt4GM", "optional"),  # modmenu（設定画面の入口）
]


def read(path):
    return io.open(os.path.join(ROOT, path), encoding="utf-8").read()


def targets():
    """settings.gradle.kts の versions(...) からビルド対象を読む（verify-jars.py と同じ）"""
    m = re.search(r"versions\(([^)]*)\)", read("settings.gradle.kts"))
    if not m:
        sys.exit("settings.gradle.kts から versions(...) を読めませんでした")
    return re.findall(r'"([^"]+)"', m.group(1))


def load_properties():
    with open(os.path.join(ROOT, "stonecutter.properties.toml"), "rb") as f:
        return tomllib.load(f)


def changelog(version):
    """README.md の「### v<version>」の節を、次の見出しか区切り線の手前まで切り出す"""
    lines = read("README.md").splitlines()
    head = re.compile(r"^###\s+v%s\s*$" % re.escape(version))
    for i, line in enumerate(lines):
        if head.match(line):
            body = []
            for rest in lines[i + 1:]:
                if re.match(r"^(#{1,3}\s|---)", rest):
                    break
                body.append(rest)
            text = "\n".join(body).strip()
            if text:
                return text
    return None


def version_name(mod_name, version, releases):
    span = releases[0] if len(releases) == 1 else "%s - %s" % (releases[0], releases[-1])
    return "%s %s (MC %s)" % (mod_name, version, span)


def check_jar(path, expected_version):
    """Release から落とした jar が、名前どおりの中身かを確かめる"""
    try:
        with zipfile.ZipFile(path) as z:
            fmj = json.loads(z.read("fabric.mod.json").decode("utf-8"))
    except (zipfile.BadZipFile, KeyError, ValueError) as e:
        return "jar として読めません (%s)" % e
    if fmj.get("version") != expected_version:
        return "fabric.mod.json の version が %r です（期待値 %r）" % (fmj.get("version"), expected_version)
    if fmj.get("environment") != "client":
        return "fabric.mod.json の environment が %r です" % fmj.get("environment")
    return None


def request(method, url, body=None, headers=None):
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={"User-Agent": USER_AGENT, **(headers or {})})
    with urllib.request.urlopen(req, timeout=120) as res:
        return json.loads(res.read().decode("utf-8"))


def existing_version_numbers():
    """Modrinth に既にあるバージョン番号（公開 API なのでトークン不要）"""
    return {v["version_number"] for v in request("GET", "%s/project/%s/version" % (API, PROJECT_ID))}


def multipart(data, filename, content):
    """POST /version の本体を作る。data 部（JSON）とファイル部の 2 つだけ"""
    boundary = "----hikari-tweaks-%s" % uuid.uuid4().hex
    out = io.BytesIO()

    def part(headers, payload):
        out.write(("--%s\r\n" % boundary).encode("ascii"))
        for h in headers:
            out.write((h + "\r\n").encode("utf-8"))
        out.write(b"\r\n")
        out.write(payload)
        out.write(b"\r\n")

    part(['Content-Disposition: form-data; name="data"', "Content-Type: application/json"],
         json.dumps(data, ensure_ascii=False).encode("utf-8"))
    part(['Content-Disposition: form-data; name="file"; filename="%s"' % filename,
          "Content-Type: application/java-archive"],
         content)
    out.write(("--%s--\r\n" % boundary).encode("ascii"))
    return out.getvalue(), "multipart/form-data; boundary=%s" % boundary


def self_check(body, content_type, data, content):
    """組み立てた multipart を読み戻して、送るつもりの内容と一致するかを確かめる（--dry-run 用）"""
    msg = email.parser.BytesParser(policy=email.policy.HTTP).parsebytes(
        b"Content-Type: " + content_type.encode("ascii") + b"\r\n\r\n" + body)
    parts = list(msg.iter_parts())
    if len(parts) != 2:
        return "multipart の部品が %d 個です" % len(parts)
    if json.loads(parts[0].get_content()) != data:
        return "data 部が一致しません"
    if parts[1].get_payload(decode=True) != content:
        return "ファイル部が一致しません"
    return None


def main():
    ap = argparse.ArgumentParser(description="GitHub Release の jar を Modrinth へ上げる")
    ap.add_argument("--tag", required=True, help="上げるリリースのタグ（例: v1.2.3）")
    ap.add_argument("--jar-dir", required=True, help="Release から落とした jar の置き場所")
    ap.add_argument("--dry-run", action="store_true", help="送らずに送信内容だけを表示する")
    args = ap.parse_args()

    props = load_properties()
    mod = props["mod"]
    version = mod["version"]
    if args.tag != "v" + version:
        sys.exit("タグ %s と stonecutter.properties.toml の mod.version=%s が一致しません"
                 % (args.tag, version))

    # ── 送る前に全部そろっているかを確かめる。1 個でも欠けていたら 1 個も送らない ──
    problems = []
    notes = changelog(version)
    if notes is None:
        problems.append("README.md に「### v%s」の変更履歴がありません" % version)

    plan = []
    for target in targets():
        releases = props.get(target, {}).get("mod", {}).get("mc_releases")
        if not releases:
            problems.append("%s: stonecutter.properties.toml に mod.mc_releases がありません" % target)
            continue
        number = "%s+%s" % (version, target)
        filename = "%s-%s.jar" % (mod["id"], number)
        path = os.path.join(args.jar_dir, filename)
        if not os.path.exists(path):
            problems.append("%s: %s がありません" % (target, filename))
            continue
        err = check_jar(path, number)
        if err:
            problems.append("%s: %s" % (target, err))
            continue
        plan.append((target, number, filename, path, releases))

    if problems:
        print("送れない理由があるので、1 個も送らずに止めます:")
        for p in problems:
            print("  - " + p)
        sys.exit(1)

    existing = existing_version_numbers()
    todo = [p for p in plan if p[1] not in existing]

    print("Modrinth: hikari-tweaks (%s)   mod version: %s   タグ: %s" % (PROJECT_ID, version, args.tag))
    print()
    print("%-9s %-18s %-40s %-26s %8s  %-12s %s"
          % ("target", "version_number", "name", "game_versions", "size", "sha1", "状態"))
    print("-" * 140)
    for target, number, filename, path, releases in plan:
        content = open(path, "rb").read()
        print("%-9s %-18s %-40s %-26s %7dK  %-12s %s"
              % (target, number, version_name(mod["name"], version, releases), ",".join(releases),
                 len(content) // 1024, hashlib.sha1(content).hexdigest()[:12],
                 "既にある（飛ばす）" if number in existing else "上げる"))
    print()
    print("依存: " + ", ".join("%s(%s)" % d for d in DEPENDENCIES) + "   ローダー: fabric   環境: client_only")
    print()
    print("── 変更履歴（全バージョン共通）" + "─" * 60)
    print(notes)
    print("─" * 90)
    print()

    def payload(number, releases):
        return {
            "project_id": PROJECT_ID,
            "name": version_name(mod["name"], version, releases),
            "version_number": number,
            "changelog": notes,
            "dependencies": [{"project_id": pid, "dependency_type": kind} for pid, kind in DEPENDENCIES],
            "game_versions": releases,
            "version_type": "release",
            "loaders": ["fabric"],
            "environment": "client_only",
            "featured": False,
            "status": "listed",
            "file_parts": ["file"],
            "primary_file": "file",
        }

    if args.dry_run:
        for target, number, filename, path, releases in todo:
            content = open(path, "rb").read()
            data = payload(number, releases)
            err = self_check(*multipart(data, filename, content), data, content)
            if err:
                sys.exit("%s: 送信データの組み立てに失敗しました: %s" % (target, err))
        print("dry-run: %d 個を上げる予定、%d 個は既にあります。送信データの組み立ても確認しました。"
              % (len(todo), len(plan) - len(todo)))
        return

    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        sys.exit("環境変数 MODRINTH_TOKEN がありません")

    done = []
    for target, number, filename, path, releases in todo:
        body, content_type = multipart(payload(number, releases), filename, open(path, "rb").read())
        try:
            res = request("POST", "%s/version" % API, body,
                          {"Authorization": token, "Content-Type": content_type})
        except urllib.error.HTTPError as e:
            print("失敗: %s → HTTP %d %s" % (number, e.code, e.read().decode("utf-8", "replace")))
            if e.code == 401:
                print("  トークンが無効か、Create versions の権限がありません。期限切れなら発行し直してください。")
            print("上げ終わったもの: %s" % (", ".join(done) or "なし"))
            print("このまま再実行すれば、上げ終わったものは飛ばして続きから上げます。")
            sys.exit(1)
        done.append(number)
        print("上げました: %-18s → https://modrinth.com/mod/hikari-tweaks/version/%s" % (number, res["id"]))

    print()
    print("完了: %d 個を上げました（%d 個は既にありました）" % (len(done), len(plan) - len(todo)))


if __name__ == "__main__":
    main()
