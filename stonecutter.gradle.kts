plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.18.2"

tasks.register("chiseledBuild") {
    group = "project"
    description = "全ターゲットをビルドして build/libs/<mod version>/ に集約する"
    dependsOn(stonecutter.versions.map { ":${it.project}:buildAndCollect" })
}
