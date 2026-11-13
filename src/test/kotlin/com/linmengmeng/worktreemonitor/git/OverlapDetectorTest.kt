package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.FileChange
import com.linmengmeng.worktreemonitor.domain.WorktreeChange
import com.linmengmeng.worktreemonitor.domain.WorktreeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨 worktree 文件重叠检测单测:未提交 + 已提交文件集合求交集,单 worktree 内去重。
 */
class OverlapDetectorTest {

    private fun worktree(name: String, main: Boolean = false) = WorktreeInfo(
        path = "D:/wt/$name",
        branch = "refs/heads/$name",
        headCommit = "1111111111111111111111111111111111111111",
        isMainWorktree = main,
        isDetached = false,
        isLocked = false,
        isPrunable = false
    )

    private fun modified(path: String) = FileChange(path, null, ' ', 'M')

    @Test
    fun `overlap detector finds files modified by multiple worktrees`() {
        val changeA = WorktreeChange(
            worktree("agent/a"),
            files = listOf(modified("src/App.java"), modified("src/Only.java")),
            addedLines = 0, deletedLines = 0,
            committedFiles = mapOf("src/Economic.java" to (10 to 0))
        )
        val changeB = WorktreeChange(
            worktree("agent/b"),
            files = listOf(modified("src/App.java")),
            addedLines = 0, deletedLines = 0,
            committedFiles = mapOf("src/Economic.java" to (3 to 1))
        )
        val changeMain = WorktreeChange(worktree("develop", main = true), emptyList(), 0, 0)

        val overlaps = OverlapDetector.detect(listOf(changeMain, changeA, changeB))

        assertEquals(2, overlaps.size)
        val app = overlaps.first { it.filePath == "src/App.java" }
        assertEquals(2, app.worktrees.size)
        val economic = overlaps.first { it.filePath == "src/Economic.java" }
        assertEquals(2, economic.worktrees.size)
        assertTrue(overlaps.none { it.filePath == "src/Only.java" })
    }

    @Test
    fun `same file in both layers of one worktree counts once`() {
        val changeA = WorktreeChange(
            worktree("agent/a"),
            files = listOf(modified("src/App.java")),
            addedLines = 0, deletedLines = 0,
            committedFiles = mapOf("src/App.java" to (10 to 0))
        )
        val changeB = WorktreeChange(worktree("agent/b"), listOf(modified("src/App.java")), 0, 0)

        val overlaps = OverlapDetector.detect(listOf(changeA, changeB))

        assertEquals(1, overlaps.size)
        assertEquals(2, overlaps[0].worktrees.size)
    }
}
