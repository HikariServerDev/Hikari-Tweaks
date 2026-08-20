plugins {
    id("dev.kikugie.stonecutter")
}

// IDE が開くアクティブバージョン。切り替えは `./gradlew "Set active project to <ver>"`
stonecutter active "1.18.2"

// Stonecutter 0.9 で chiseled タスク API が廃止されたため、
// 全バージョンノードの buildAndCollect に依存する集約タスクを自前で用意する。
tasks.register("chiseledBuild") {
    group = "project"
    description = "全ターゲットをビルドして build/libs/<mod version>/ に集約する"
    dependsOn(stonecutter.versions.map { ":${it.project}:buildAndCollect" })
}
