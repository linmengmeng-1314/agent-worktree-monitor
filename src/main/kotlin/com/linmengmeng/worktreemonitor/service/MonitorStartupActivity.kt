package com.linmengmeng.worktreemonitor.service

import com.linmengmeng.worktreemonitor.git.OverlapDetector
import com.linmengmeng.worktreemonitor.git.WorktreeScanner
import com.linmengmeng.worktreemonitor.settings.MonitorSettings
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * 项目打开时的完整预扫描:发现 worktree、按基准分支规则统计已提交/未提交变更、检测重叠,
 * 结果写日志(为 ToolWindow 展示预热,同时作为启动自检)。
 */
class MonitorStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val projectDir = project.basePath?.let { Path.of(it) } ?: return
        withContext(Dispatchers.IO) {
            try {
                val scanner = WorktreeScanner()
                val settings = MonitorSettings.getInstance(project)
                val worktrees = scanner.scanWorktrees(projectDir)
                val defaultBase = worktrees.firstOrNull()?.takeIf { !it.isDetached }?.branchDisplayName

                val changes = worktrees.map { wt ->
                    val base = settings.baseBranchFor(wt.path)
                        ?: if (wt.isMainWorktree) null else defaultBase
                    runCatching { scanner.scanChanges(wt, base) }.getOrNull()
                }.filterNotNull()

                val overlaps = OverlapDetector.detect(changes)
                val summary = changes.joinToString { c ->
                    "${c.worktree.branchDisplayName}: 未提交=${c.files.size} 已提交=${c.commitCount}c/${c.committedFiles.size}f"
                }
                val overlapPart = if (overlaps.isEmpty()) "" else
                    ", overlaps=${overlaps.size} [${overlaps.joinToString { it.filePath }}]"
                thisLogger().info(
                    "AgentWorktreeMonitor startup: ${worktrees.size} worktrees ($summary$overlapPart)"
                )
            } catch (e: Exception) {
                thisLogger().warn("AgentWorktreeMonitor startup scan failed: ${e.message}")
            }
        }
    }
}
