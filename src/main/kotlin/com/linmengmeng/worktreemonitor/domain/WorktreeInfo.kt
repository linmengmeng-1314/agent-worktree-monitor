package com.linmengmeng.worktreemonitor.domain

/**
 * 一个 Git Worktree 的元信息,来自 `git worktree list --porcelain -z` 解析结果。
 */
data class WorktreeInfo(
    val path: String,
    val branch: String?,
    val headCommit: String,
    val isMainWorktree: Boolean,
    val isDetached: Boolean,
    val isLocked: Boolean,
    val isPrunable: Boolean
) {
    /** 展示用分支名:去掉 refs/heads/ 前缀;detached 时显示占位 */
    val branchDisplayName: String
        get() = branch?.substringAfter("refs/heads/") ?: "(detached)"

    /** HEAD 短 SHA */
    val headShort: String
        get() = if (headCommit.length >= 7) headCommit.substring(0, 7) else headCommit
}
