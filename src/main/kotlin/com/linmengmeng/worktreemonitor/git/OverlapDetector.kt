package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.WorktreeChange
import com.linmengmeng.worktreemonitor.domain.WorktreeInfo

/** 一个被多个 worktree 修改的文件 */
data class OverlapItem(
    val filePath: String,
    val worktrees: List<WorktreeInfo>
)

/**
 * 跨 worktree 文件重叠检测:
 * 汇总每个 worktree 的变更文件集合(未提交 status + 已提交 base...HEAD),求交集,
 * 被 >=2 个 worktree 修改的文件即重叠。文件重叠不等于合并冲突,仅提示人工审查。
 */
object OverlapDetector {

    fun detect(changes: List<WorktreeChange>): List<OverlapItem> {
        val fileOwners = LinkedHashMap<String, MutableList<WorktreeInfo>>()
        for (change in changes) {
            val paths = mutableSetOf<String>()
            paths += change.files.map { it.path }
            paths += change.committedFiles.keys
            for (path in paths) {
                fileOwners.getOrPut(path) { mutableListOf() }.add(change.worktree)
            }
        }
        return fileOwners
            .filter { it.value.size >= 2 }
            .map { (path, owners) -> OverlapItem(path, owners) }
            .sortedBy { it.filePath }
    }
}
