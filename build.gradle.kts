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
    pluginVerification {
        // 兼容性验证目标:优先用本机已安装 IDEA;无本机 IDE 的环境(如 CI)跳过 verifier
        val localIde = providers.gradleProperty("localIdePath")
            .orElse(providers.environmentVariable("LOCAL_IDE_PATH"))
        if (localIde.isPresent) {
            ides {
                local(localIde.get())
            }
        }
    }
    publishing {
        // Marketplace 发布令牌,仅 publishPlugin 任务使用;通过 MARKETPLACE_TOKEN 环境变量注入,禁止落盘
        token = providers.environmentVariable("MARKETPLACE_TOKEN")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    test {
        useJUnit()
    }
    verifyPlugin {
        // verifier 启动时需在线拉取已知 API 变更清单,直连受限网络会失败;
        // 需要时传 -PverifierProxy=host:port(如本地代理 127.0.0.1:7897),CI 无需传
        providers.gradleProperty("verifierProxy").orNull?.let { proxy ->
            val (host, port) = proxy.split(":")
            jvmArgs(
                "-Dhttps.proxyHost=$host", "-Dhttps.proxyPort=$port",
                "-Dhttp.proxyHost=$host", "-Dhttp.proxyPort=$port",
            )
        }
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
