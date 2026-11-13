# Agent Worktree Monitor

IntelliJ IDEA 插件:多 Agent Git Worktree 代码变更监控面板。

为多 Agent 并行开发(Claude Code / Codex / OpenCode 等基于 Git Worktree 的工作方式)提供统一的变更审查入口:自动发现当前仓库关联的全部 Worktree,展示各 Worktree 的分支、修改文件、增删行数,双击文件打开 IDEA 原生 Diff。

**只读工具**:不执行任何 Git 写操作,不会修改任何 Worktree 的代码与状态。

## 功能

- 自动发现当前仓库的全部 Worktree(含 detached / locked / 中文与空格路径)
- 总览:分支、HEAD 短 SHA、路径、变更文件数、增删行数、未跟踪数
- **基准分支与已提交变更**:每个 worktree 可右键配置基准分支(默认按主 worktree 分支推断),统计 `base...HEAD` 已提交变更与提交数,与未提交变更分层合并展示、行数不重复累计
- 变更文件列表:区分暂存 / 未暂存 / 未跟踪层级与"已提交/未提交"来源,重命名显示 `old → new`
- **跨 worktree 文件重叠检测**:汇总未提交 + 已提交变更,列出被多个 worktree 修改的文件及涉及方,双击直接打开对应 Diff(重叠 Tab)
- 双击文件打开 IDEA 原生 Diff(未提交文件对比 HEAD,仅已提交文件对比基准分支)
- **自动刷新**:默认 10 秒轮询(5/10/30/60 可选),轻量签名比对、状态未变化不执行详细扫描;IDEA 窗口激活时触发一次;可关闭

## 环境要求

- IntelliJ IDEA 2026.2+(build 262+),Community / Ultimate 均可
- Git ≥ 2.19(需支持 `--porcelain -z`)

## 安装

从 [Releases](../../releases) 下载 `agent-worktree-monitor-<version>.zip` → Settings → Plugins → ⚙ → Install Plugin from Disk → 选择 zip → 重启 IDEA。打开任意 Git 项目,右侧工具栏出现 "Agent Worktree Monitor"。

## 开发

```bash
# 构建与测试(需 JDK 21;若本机默认 JDK 非 21,先设置 JAVA_HOME)
./gradlew build

# 启动沙箱 IDEA 验证(自动打开测试工程)
./gradlew runIde

# 打包可安装 zip:build/distributions/*.zip
./gradlew buildPlugin
```

可选本机配置(二选一,免远程下载 IDE 依赖):

- 环境变量 `LOCAL_IDE_PATH=D:/Program Files/JetBrains/IntelliJ IDEA 2026.2.2`(推荐,不落仓库);
- 构建时传参 `./gradlew build -PlocalIdePath=<本机 IDEA 路径>`。

### 测试仓库

`scripts/make_test_repo.py` 一键构造含 4 个 worktree 的测试仓库(中文空格路径、detached、locked、重命名、暂存并存、未跟踪、跨 worktree 重叠等全部验证场景):

```bash
python scripts/make_test_repo.py --base D:/tmp
```

## 路线图

- [x] v0.1.0:Worktree 发现 + 总览 + 变更列表 + Diff + 手动刷新
- [x] v0.2.0:基准分支与已提交变更统计、跨 Worktree 文件重叠检测、自动刷新、独立图标
- [ ] 文件列表文件名优先显示(路径灰色后缀/悬浮提示,对齐 IDEA Git 窗口)
- [ ] 设置页、搜索过滤、重命名/删除文件 Diff 特判、汇总统计栏
- [ ] merge-tree 合并冲突预测、Agent 会话关联

## License

[Apache-2.0](LICENSE)
