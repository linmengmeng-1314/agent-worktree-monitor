package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.FileChange
import com.linmengmeng.worktreemonitor.domain.WorktreeChange
import com.linmengmeng.worktreemonitor.domain.WorktreeInfo
import com.linmengmeng.worktreemonitor.domain.mergedFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 已提交与未提交变更的合并视图单测:按文件合并、行数两层相加不重复累计。
 */
class MergedFilesTest {

    private fun worktree(name: String) = WorktreeInfo(
        path = "D:/wt/$name",
        branch = "refs/heads/$name",
        headCommit = "1111111111111111111111111111111111111111",
        isMainWorktree = false,
        isDetached = false,
        isLocked = false,
        isPrunable = false
    )

    @Test
    fun `merged files combine committed and uncommitted without double counting`() {
        val change = WorktreeChange(
            worktree = worktree("agent/a"),
            files = listOf(
                FileChange("src/App.java", null, ' ', 'M'),
                FileChange("src/New.java", null, ' ', 'M')
            ),
            addedLines = 3,
            deletedLines = 1,
            lineStats = mapOf("src/App.java" to (2 to 1), "src/New.java" to (1 to 0)),
            baseBranch = "develop",
            committedFiles = mapOf("src/App.java" to (10 to 2), "src/Committed.java" to (5 to 0)),
            committedAdded = 15,
            committedDeleted = 2,
            commitCount = 2
        )

        val merged = change.mergedFiles().sortedBy { it.path }

        assertEquals(3, merged.size)

        val app = merged.first { it.path == "src/App.java" }
        assertTrue(app.committed && app.change != null)
        assertEquals(12, app.added)   // 已提交 10 + 未提交 2
        assertEquals(3, app.deleted)  // 已提交 2 + 未提交 1

        val committedOnly = merged.first { it.path == "src/Committed.java" }
        assertTrue(committedOnly.committed && committedOnly.change == null)
        assertEquals(5, committedOnly.added)

        val uncommittedOnly = merged.first { it.path == "src/New.java" }
        assertTrue(!uncommittedOnly.committed && uncommittedOnly.change != null)
    }
}
