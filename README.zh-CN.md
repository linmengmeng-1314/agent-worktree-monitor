# Agent Worktree Monitor(中文说明)

IntelliJ IDEA 插件:多 Agent Git Worktree 代码变更监控面板。

多个 AI 编程代理(Claude Code、Codex、OpenCode 等)并行开发时,每个 Agent 各自在独立的 Git Worktree 中工作,很难快速掌握每个 Agent 的改动。本插件提供统一的审查入口:自动发现当前仓库关联的全部 Worktree,展示分支、已提交与未提交变更、增删行数、被多个 Worktree 同时修改的文件,双击打开 IDEA 原生 Diff。

**只读、本地优先**:不执行任何 Git 写操作,无网络请求,无遥测。

[English README](README.md)

## 功能

- 自动发现当前仓库的全部 Worktree(含 detached / locked / 中文与空格路径)
- 总览:分支、HEAD 短 SHA、变更文件数、增删行数、未跟踪数
- **基准分支与已提交变更**:每个 worktree 可右键配置基准分支(默认按主 worktree 分支推断),统计 `base...HEAD` 已提交变更与提交数,与未提交变更分层合并展示、行数不重复累计
- 变更文件列表:区分暂存 / 未暂存 / 未跟踪层级与"已提交/未提交"来源;文件名优先显示,目录灰色后缀,悬浮显示完整路径;重命名显示 `新文件名 ← 旧路径`
- **跨 worktree 文件重叠检测**:汇总未提交 + 已提交变更,列出被多个 worktree 修改的文件及涉及方(重叠 Tab),双击直接打开对应 Diff
- 双击文件打开 IDEA 原生 Diff(未提交文件对比 HEAD,仅已提交文件对比基准分支)
- **自动刷新**:默认 10 秒轮询(5/10/30/60 可选),轻量签名比对、状态未变化不执行详细扫描;IDEA 窗口激活时触发一次;可关闭

## 环境要求

- IntelliJ IDEA 2026.2+(build 262+),Community / Ultimate 均可
- Git ≥ 2.19(需支持 `--porcelain -z`)

## 安装

从 [Releases](../../releases) 下载 `agent-worktree-monitor-<version>.zip` → Settings → Plugins → ⚙ → Install Plugin from Disk → 选择 zip → 重启 IDEA。打开任意 Git 项目,右侧工具栏出现 "Agent Worktree Monitor"。

## 隐私

本地优先。所有数据通过本地只读 git 命令获取,无遥测、无统计上报、无网络请求。

## 开发

```bash
# 构建与测试(需 JDK 21;若本机默认 JDK 非 21,先设置 JAVA_HOME)
./gradlew build

# 启动沙箱 IDEA 验证(自动打开测试工程)
./gradlew runIde

# 打包可安装 zip:build/distributions/*.zip
./gradlew buildPlugin

# 兼容性验证(plugin verifier)
./gradlew runPluginVerifier
```

复用本机已安装 IDEA 作为平台依赖:设环境变量 `LOCAL_IDE_PATH`(或 `./gradlew build -PlocalIdePath=<IDEA 路径>`),免远程下载。

### 测试仓库

`scripts/make_test_repo.py` 一键构造含 4 个 worktree 的测试仓库(中文空格路径、detached、locked、重命名、暂存并存、未跟踪、跨 worktree 重叠等全部验证场景):

```bash
python scripts/make_test_repo.py --base D:/tmp
```

## 问题反馈

[GitHub Issues](../../issues)

## 许可

[Apache-2.0](LICENSE) · [EULA](EULA.md) · [更新日志](CHANGELOG.md)
