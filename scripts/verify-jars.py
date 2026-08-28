# chiseledBuild で生成した全 jar を検証するスクリプト。
#
#   ./gradlew chiseledBuild
#   python scripts/verify-jars.py
#
# 確認内容:
#   - settings.gradle.kts に列挙した全ターゲットの jar が揃っているか
#   - mixins.json の compatibilityLevel（Java バージョン）
#   - fabric.mod.json の environment と depends.minecraft
#   - refmap が jar に含まれているか
#   - MixinInGameHud の renderScoreboardSidebar が
#     ScoreboardObjective 版（class_266）に解決されているか
#     ※ メソッド名だけで指定すると別のオーバーロードを掴んでしまい、
#       ビルドは通るのに実行時に mixin 適用が失敗する
#
# 終了コード 0 = 全て OK

import io
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(path):
    return io.open(os.path.join(ROOT, path), encoding="utf-8").read()


def targets():
    """settings.gradle.kts の versions(...) からビルド対象を読む"""
    m = re.search(r"versions\(([^)]*)\)", read("settings.gradle.kts"))
    if not m:
        sys.exit("settings.gradle.kts から versions(...) を読めませんでした")
    return re.findall(r'"([^"]+)"', m.group(1))


def mod_version():
    m = re.search(r'^mod\.version\s*=\s*"([^"]+)"',
                  read("stonecutter.properties.toml"), re.M)
    if not m:
        sys.exit("stonecutter.properties.toml から mod.version を読めませんでした")
    return m.group(1)


def main():
    version = mod_version()
    libs = os.path.join(ROOT, "build", "libs", version)
    if not os.path.isdir(libs):
        sys.exit("%s がありません。先に ./gradlew chiseledBuild を実行してください" % libs)

    bad = []
    print("mod version: %s" % version)
    print()
    print("%-9s %-5s %-22s %-8s %s"
          % ("target", "java", "depends.minecraft", "refmap", "sidebar mixin target"))
    print("-" * 108)

    for target in targets():
        jar = os.path.join(libs, "hikari-tweaks-%s+%s.jar" % (version, target))
        if not os.path.exists(jar):
            print("%-9s jar が見つかりません" % target)
            bad.append(target)
            continue

        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            fmj = json.loads(z.read("fabric.mod.json").decode("utf-8"))
            mixins = json.loads(z.read("hikari-tweaks.mixins.json").decode("utf-8"))

            refmap_name = mixins.get("refmap")
            has_refmap = refmap_name in names
            refmap = json.loads(z.read(refmap_name).decode("utf-8")) if has_refmap else {}

        hud = refmap.get("mappings", {}).get(
            "com/hikariserver/hikaritweaks/mixin/MixinInGameHud", {})
        found = [t for k, t in hud.items() if k.startswith("renderScoreboardSidebar")]
        sidebar = found[0] if found else "(none)"

        sidebar_ok = "class_266" in sidebar
        env_ok = fmj.get("environment") == "client"
        if not (has_refmap and sidebar_ok and env_ok):
            bad.append(target)

        print("%-9s %-5s %-22s %-8s %s%s" % (
            target,
            mixins["compatibilityLevel"].replace("JAVA_", ""),
            fmj["depends"]["minecraft"],
            "OK" if has_refmap else "MISSING",
            "OK  " if sidebar_ok else "NG!! ",
            sidebar.split(";", 1)[1] if ";" in sidebar else sidebar,
        ))

    print()
    if bad:
        print("NG: %s" % ", ".join(bad))
        return 1
    print("全 %d ターゲット OK" % len(targets()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
