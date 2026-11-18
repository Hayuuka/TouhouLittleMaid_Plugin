plugins {
    java
    // 蓝图写的 8.3.6 只支持 Gradle 8.x；本机只有 JDK 26，必须用 Gradle 9（见 docs/api-facts.md 第 9 节）
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.touhoulittlemaid"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.citizensnpcs.co/")
}

dependencies {
    // 坐标以 docs/api-facts.md 第 2 节实测结果为准（蓝图 0.4 原值已在 v1 Phase 0 修正）
    compileOnly("io.papermc.paper:paper-api:26.2.build.135-stable")
    // citizens-main 内含全部 net.citizensnpcs.api.* 类，但它的传递依赖（libby-bukkit 等）
    // 在公开仓库里不存在；编译只需要 API 类，关掉传递解析。
    compileOnly("net.citizensnpcs:citizens-main:2.0.44-SNAPSHOT") { isTransitive = false }

    // BetterModel / CraftEngine 不做编译期依赖：二者是 paper-plugin（classloader 隔离），
    // 对它们的全部调用集中在 platform/impl 的适配器内走反射（blueprint 0.4 环3）。
}

// 本机只有 JDK 26，toolchain 25 需联网自动下载（不稳定）。
// 改用 --release 25 产出 Java 25 字节码，运行期与蓝图一致。
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.shadowJar {
    archiveClassifier.set("")
}
