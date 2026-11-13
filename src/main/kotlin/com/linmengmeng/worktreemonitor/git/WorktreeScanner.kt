package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.WorktreeChange
import com.linmengmeng.worktreemonitor.domain.WorktreeInfo
import java.nio.file.Path
import kotlin.io.path.readLines

/** 已提交变更统计(base...HEAD) */
data class CommittedChange(
    val files: Map<String, Pair<Int, Int>>,
    val added: Int,
    val deleted: Int,
    val commitCount: Int
)

/**
 * Worktree 扫描编排:worktree 发现 + 单 worktree 变更汇总(未提交 + 已提交)。
 * 全部方法执行 git 命令,必须在后台线程调用。
 */
class WorktreeScanner(private val executor: GitCommandExecutor = GitCommandExecutor()) {

    /** 发现 projectDir 所属仓库的全部 worktree */
    fun scanWorktrees(projectDir: Path): List<WorktreeInfo> {
        val result = executor.execute(projectDir, "worktree", "list", "--porcelain", "-z")
        if (!result.isSuccess) {
            throw IllegalStateException("git worktree list 执行失败: ${result.stderr.trim()}")
        }
        return WorktreeListParser.parse(result.stdoutBytes)
    }

    /**
     * 汇总单个 worktree 的变更:
     * 1. `git status --porcelain=v1 -z --untracked-files=all` 得文件清单与状态;
     * 2. `git diff --numstat HEAD` 得已跟踪文件相对 HEAD 的合并增删行(不与暂存/未暂存重复累计);
     * 3. 未跟踪文件按文件行数计为全增。
     */
    fun scanChanges(worktree: WorktreeInfo, baseBranch: String? = null): WorktreeChange {
        val workDir = Path.of(worktree.path)
        val status = executor.execute(workDir, "status", "--porcelain=v1", "-z", "--untracked-files=all")
        if (!status.isSuccess) {
            throw IllegalStateException("git status 执行失败(${worktree.branchDisplayName}): ${status.stderr.trim()}")
        }
        val files = StatusParser.parse(status.stdoutBytes)

        val numstat = executor.execute(workDir, "diff", "--numstat", "HEAD")
        val lineMap = if (numstat.isSuccess) NumstatParser.parse(numstat.stdoutText) else emptyMap()

        val lineStats = mutableMapOf<String, Pair<Int, Int>>()
        var added = 0
        var deleted = 0
        for (file in files) {
            if (file.untracked) {
                val lines = runCatching { workDir.resolve(file.path).readLines().size }.getOrDefault(0)
                lineStats[file.path] = lines to 0
                added += lines
            } else {
                val stat = lineMap[file.path]
                if (stat != null) {
                    lineStats[file.path] = stat
                    added += stat.first
                    deleted += stat.second
                }
            }
        }

        val committed = baseBranch?.let { scanCommitted(workDir, it) } ?: CommittedChange(emptyMap(), 0, 0, 0)

        return WorktreeChange(
            worktree = worktree,
            files = files,
            addedLines = added,
            deletedLines = deleted,
            lineStats = lineStats,
            baseBranch = baseBranch,
            committedFiles = committed.files,
            committedAdded = committed.added,
            committedDeleted = committed.deleted,
            commitCount = committed.commitCount
        )
    }

    /**
     * 已提交变更:base...HEAD(自共同分叉点)的 numstat 与提交数。
     * 基准分支不存在时返回 null(不当作错误,静默视为未配置)。
     */
    fun scanCommitted(workDir: Path, baseBranch: String): CommittedChange? {
        val verify = executor.execute(workDir, "rev-parse", "--verify", "--quiet", "$baseBranch^{commit}")
        if (!verify.isSuccess) return null

        val numstat = executor.execute(workDir, "diff", "$baseBranch...HEAD", "--numstat")
        val files = if (numstat.isSuccess) NumstatParser.parse(numstat.stdoutText) else emptyMap()

        val log = executor.execute(workDir, "log", "$baseBranch..HEAD", "--oneline")
        val commitCount = if (log.isSuccess) log.stdoutText.lines().count { it.isNotBlank() } else 0

        return CommittedChange(
            files = files,
            added = files.values.sumOf { it.first },
            deleted = files.values.sumOf { it.second },
            commitCount = commitCount
        )
    }

    /** 本地 + 远程分支列表(供基准分支选择),按名称排序 */
    fun listBranches(projectDir: Path): List<String> {
        val result = executor.execute(projectDir, "branch", "--all", "--format=%(refname:short)")
        if (!result.isSuccess) return emptyList()
        return result.stdoutText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "HEAD" }
            .distinct()
            .sorted()
    }
}
