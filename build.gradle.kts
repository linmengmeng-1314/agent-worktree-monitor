plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // 平台依赖:优先复用本机已安装 IDEA(localIdePath 属性或 LOCAL_IDE_PATH 环境变量),
        // 否则从远程仓库下载 IntelliJ IDEA Community。
        val localIde = providers.gradleProperty("localIdePath").orNull
            ?: System.getenv("LOCAL_IDE_PATH")
        if (localIde != null) {
            local(localIde)
        } else {
            intellijIdeaCommunity("2026.2.2")
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Agent Worktree Monitor"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    test {
        useJUnit()
    }
    runIde {
        // 沙箱启动后直接打开指定项目(默认测试仓库;-PrunIdeProject=<path> 覆盖)
        val projectPath = providers.gradleProperty("runIdeProject")
            .orElse("D:/Data/claude-auto/worktree-test-repo")
        args = listOf(projectPath.get())
        // 全新沙箱跳过 Trust Project / Tips 弹窗,避免阻塞自动化验证
        jvmArgs("-Didea.trust.all.projects=true", "-Dide.show.tips.on.startup.default.value=false")
    }
}
