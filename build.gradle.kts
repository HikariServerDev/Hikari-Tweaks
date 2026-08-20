plugins {
    id("fabric-loom")
}

// stonecutter.properties.toml の値を読む唯一の入口。
// Stonecutter の API が変わったらここだけ直せば済むようにしておく。
fun prop(key: String): String = sc.properties.get<String>(key)

// group は Stonecutter のバージョンノードでは設定しない（テンプレートの指示に従う）
version = "${prop("mod.version")}+${sc.current.version}"
base.archivesName = prop("mod.id")

val javaRelease: Int = prop("mod.java").toInt()

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    // 1.17 系の malilib は Modrinth maven が配信していないため masa の maven を併用する
    exclusiveContent {
        forRepository { maven("https://masa.dy.fi/maven") { name = "masa" } }
        filter { includeGroup("fi.dy.masa.malilib") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    mappings("net.fabricmc:yarn:${prop("deps.yarn")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    implementation("com.google.code.gson:gson:${prop("deps.gson")}")
    include("com.google.code.gson:gson:${prop("deps.gson")}")

    // malilib / MiniHUD / ModMenu は全ターゲットで Modrinth maven に統一する
    // （masa の maven は 1.21.1 までしか無いため）
    // deps.malilib はバージョンではなく完全な座標（group:artifact:version）。
    // 1.17 系だけ masa の maven を指しているため。
    modImplementation(prop("deps.malilib")) { isTransitive = false }
    modCompileOnly("maven.modrinth:minihud:${prop("deps.minihud")}") { isTransitive = false }
    // runClient で MixinOverlayRenderer を実際に検証するため、開発実行時のみ MiniHUD を読み込む
    modLocalRuntime("maven.modrinth:minihud:${prop("deps.minihud")}") { isTransitive = false }
    modCompileOnly("maven.modrinth:modmenu:${prop("deps.modmenu")}") { isTransitive = false }
}

loom {
    // splitEnvironmentSourceSets() は使わない。
    // MC 1.17.x は bundled server jar が無く Loom が split 構成をサポートしないため、
    // クライアント専用コードも src/main/java に置いて全バージョンで同じ構成にしている。
    // クライアント限定であることは fabric.mod.json の environment=client と
    // mixins.json の client セクションで担保している。

    mixin {
        // Loom 1.17 で Mixin AP が既定オフになったが、古い Loader でも確実に
        // mixin が解決できるよう refmap 方式を維持する。
        useLegacyMixinAp = true
        defaultRefmapName.set("hikari-tweaks.refmap.json")
    }

    mods {
        register("hikari-tweaks") {
            sourceSet(sourceSets["main"])
        }
    }

    runConfigs.all {
        // run ディレクトリはバージョンごとに分ける。
        // options.txt やワールドの形式が MC バージョン間で互換でないため、
        // 共有すると古いバージョンの起動が壊れる。
        runDirectory = rootProject.file("run/${sc.current.version}")
    }
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaRelease)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf(
        "id" to prop("mod.id"),
        "name" to prop("mod.name"),
        "version" to project.version.toString(),
        "minecraft" to prop("mod.mc_compat"),
        // 依存の下限。根拠は stonecutter.properties.toml のヘッダコメントを参照。
        "loader_min" to prop("mod.loader_min"),
        "malilib_min" to prop("mod.malilib_min"),
    )
    props.forEach { (k, v) -> inputs.property(k, v) }

    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaRelease") }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "jar をビルドして build/libs/<mod version>/ に集約する"

    dependsOn(tasks.build)
    from(tasks.remapJar.flatMap { it.archiveFile })
    from(tasks.remapSourcesJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}
