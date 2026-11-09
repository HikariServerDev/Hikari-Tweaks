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

MIXIN_PREFIX = "com/hikariserver/hikaritweaks/mixin/"

REQUIRED_REFMAP_ENTRIES = [
    ("MixinInGameHud",                     "displayScoreboardSidebar"),
    ("MixinInGameHud",                     "render("),
    ("MixinClientPlayerInteractionManager", "handleInventoryMouseClick"),
    ("MixinClientPlayNetworkHandler",      "handleEntityEvent"),
    ("MerchantScreenHandlerAccessor",      "tradeContainer"),
    ("MerchantInventoryAccessor",          "selectionHint"),
]

MEMBER_RE = re.compile(r";([A-Za-z_$][A-Za-z0-9_$]*)[:(]")

def resolved_member(value):
    m = MEMBER_RE.search(value)
    if m:
        return m.group(1)
    return value.split(":")[0]

def unresolved_entries(mappings):
    bad = []
    for cls, entries in sorted(mappings.items()):
        for key, value in sorted(entries.items()):
            member = resolved_member(value)
            if not (member.startswith("method_") or member.startswith("field_")):
                bad.append("%s.%s -> %s" % (cls.rsplit("/", 1)[-1], key, value))
    return bad

def missing_entries(mappings):
    missing = []
    for mixin, prefix in REQUIRED_REFMAP_ENTRIES:
        entries = mappings.get(MIXIN_PREFIX + mixin, {})
        if not any(k.startswith(prefix) for k in entries):
            missing.append("%s.%s" % (mixin, prefix))
    return missing

def uses_refmap(target):
    return target.startswith("1.")

def main():
    version = mod_version()
    libs = os.path.join(ROOT, "build", "libs", version)
    if not os.path.isdir(libs):
        sys.exit("%s がありません。先に ./gradlew chiseledBuild を実行してください" % libs)

    bad = []
    skipped = []
    print("mod version: %s" % version)
    print()
    print("%-9s %-5s %-22s %-8s %-9s %s"
          % ("target", "java", "depends.minecraft", "refmap", "entries", "mixin 解決状況"))
    print("-" * 100)

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

        mappings = refmap.get("mappings", {})
        env_ok = fmj.get("environment") == "client"
        java = mixins["compatibilityLevel"].replace("JAVA_", "")
        depends = fmj["depends"]["minecraft"]

        if not uses_refmap(target):
            if has_refmap:
                print("%-9s %-5s %-22s %-8s %-9s %s"
                      % (target, java, depends, "想定外", "-", "NG!! 26.x に refmap がある"))
                bad.append(target)
                continue
            skipped.append(target)
            print("%-9s %-5s %-22s %-8s %-9s %s"
                  % (target, java, depends, "なし", "-", "未検証（実機起動で確認すること）"))
            if not env_ok:
                bad.append(target)
            continue

        unresolved = unresolved_entries(mappings)
        missing = missing_entries(mappings)
        entry_count = sum(len(v) for v in mappings.values())

        if not (has_refmap and env_ok) or unresolved or missing:
            bad.append(target)

        print("%-9s %-5s %-22s %-8s %-9s %s" % (
            target, java, depends,
            "OK" if has_refmap else "MISSING",
            "%d 件" % entry_count,
            "OK" if not (unresolved or missing) else "NG!!",
        ))
        for u in unresolved:
            print("          └ intermediary へ解決されていない: %s" % u)
        for m in missing:
            print("          └ refmap にエントリが無い: %s" % m)

    print()
    if skipped:
        print("未検証（refmap 無し）: %s" % ", ".join(skipped))
    if bad:
        print("NG: %s" % ", ".join(bad))
        return 1
    print("全 %d ターゲット OK（うち %d ターゲットは未検証）"
          % (len(targets()), len(skipped)))
    return 0

if __name__ == "__main__":
    sys.exit(main())
