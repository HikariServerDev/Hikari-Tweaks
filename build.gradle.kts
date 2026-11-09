import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.jvm.tasks.ProcessResources

fun prop(key: String): String = sc.properties.get<String>(key)

val mojangNative = !sc.current.version.startsWith("1.")

apply(plugin = if (mojangNative) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")

val loom = extensions.getByType<LoomGradleExtensionAPI>()

version = "${prop("mod.version")}+${sc.current.version}"
extensions.configure<BasePluginExtension>("base") {
    archivesName.set(prop("mod.id"))
}

val javaRelease: Int = prop("mod.java").toInt()

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://masa.dy.fi/maven") { name = "masa" } }
        filter { includeGroup("fi.dy.masa.malilib") }
    }
}

val modImplementation = if (mojangNative) "implementation" else "modImplementation"
val modCompileOnly = if (mojangNative) "compileOnly" else "modCompileOnly"
val modLocalRuntime = if (mojangNative) "runtimeOnly" else "modLocalRuntime"

dependencies {
    "minecraft"("com.mojang:minecraft:${sc.current.version}")
    if (!mojangNative) {
        "mappings"(loom.officialMojangMappings())
    }
    modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    "implementation"("com.google.code.gson:gson:${prop("deps.gson")}")
    "include"("com.google.code.gson:gson:${prop("deps.gson")}")

    modImplementation(prop("deps.malilib")) { isTransitive = false }
    modCompileOnly("maven.modrinth:minihud:${prop("deps.minihud")}") { isTransitive = false }
    modLocalRuntime("maven.modrinth:minihud:${prop("deps.minihud")}") { isTransitive = false }
    modCompileOnly("maven.modrinth:modmenu:${prop("deps.modmenu")}") { isTransitive = false }
}

val TEST_TARGET = "1.18.2"

val sourceSets = extensions.getByType<SourceSetContainer>()

if (sc.current.version == TEST_TARGET) {
    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
} else {
    sourceSets["test"].java.setSrcDirs(emptyList<String>())
    sourceSets["test"].resources.setSrcDirs(emptyList<String>())
}

if (!mojangNative) {
    loom.mixin {
        useLegacyMixinAp.set(true)
        defaultRefmapName.set("hikari-tweaks.refmap.json")
    }
}

loom.mods {
    register("hikari-tweaks") {
        sourceSet(sourceSets["main"])
    }
}

loom.runConfigs.all {
    runDirectory = rootProject.file("run/${sc.current.version}")
}

extensions.configure<JavaPluginExtension>("java") {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaRelease)
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "id" to prop("mod.id"),
        "name" to prop("mod.name"),
        "version" to project.version.toString(),
        "minecraft" to prop("mod.mc_compat"),
        "loader_min" to prop("mod.loader_min"),
        "malilib_min" to prop("mod.malilib_min"),
        "fabric_api_id" to prop("mod.fabric_api_id"),
    )
    props.forEach { (k, v) -> inputs.property(k, v) }

    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to "JAVA_$javaRelease") }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("COPYING.LESSER")) { into("META-INF") }
    from(rootProject.file("COPYING")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
    from(rootProject.file("THIRD-PARTY-NOTICES.txt")) { into("META-INF") }
    from(rootProject.file("licenses")) { into("META-INF/licenses") }
}

val distJar = if (mojangNative) "jar" else "remapJar"
val distSourcesJar = if (mojangNative) "sourcesJar" else "remapSourcesJar"

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "jar をビルドして build/libs/<mod version>/ に集約する"

    dependsOn(tasks.named("build"))
    from(tasks.named(distJar).map { it.outputs.files })
    from(tasks.named(distSourcesJar).map { it.outputs.files })
    into(rootProject.layout.buildDirectory.dir("libs/${prop("mod.version")}"))
}
