package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.WorktreeInfo

/**
 * 解析 `git worktree list --porcelain -z` 输出。
 *
 * -z 模式下每个属性行以 NUL 终止(替代换行),stanza 之间由一个空记录(单独 NUL)分隔。
 * 每个 stanza 的属性行:worktree <path> / HEAD <sha> / branch <ref> /
 * detached / locked [reason] / prunable [reason] / bare。
 * 输出中第一个 worktree 恒为主工作目录。
 */
object WorktreeListParser {

    fun parse(output: ByteArray): List<WorktreeInfo> {
        val tokens = String(output, Charsets.UTF_8)
            .split('\u0000')
            .map { it.trimEnd('\n', '\r') }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<WorktreeInfo>()
        var path: String? = null
        var branch: String? = null
        var head = ""
        var detached = false
        var locked = false
        var prunable = false
        var bare = false

        fun flush() {
            val p = path ?: return
            result += WorktreeInfo(
                path = p,
                branch = branch,
                headCommit = head,
                isMainWorktree = result.isEmpty(),
                isDetached = detached || bare,
                isLocked = locked,
                isPrunable = prunable
            )
            path = null
            branch = null
            head = ""
            detached = false
            locked = false
            prunable = false
            bare = false
        }

        for (token in tokens) {
            when {
                token.startsWith("worktree ") -> {
                    flush()
                    path = token.removePrefix("worktree ")
                }
                token.startsWith("HEAD ") -> head = token.removePrefix("HEAD ")
                token.startsWith("branch ") -> branch = token.removePrefix("branch ")
                token == "detached" -> detached = true
                token == "locked" || token.startsWith("locked ") -> locked = true
                token == "prunable" || token.startsWith("prunable ") -> prunable = true
                token == "bare" -> bare = true
            }
        }
        flush()
        return result
    }
}
