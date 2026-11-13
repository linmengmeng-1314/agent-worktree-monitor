package com.linmengmeng.worktreemonitor.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WorktreeListParser 单测:固定 porcelain -z 样本断言,不依赖真实仓库。
 * 样本覆盖:主 worktree、中文与空格路径、detached、locked、prunable。
 */
class WorktreeListParserTest {

    private val sha1 = "1111111111111111111111111111111111111111"
    private val sha2 = "2222222222222222222222222222222222222222"
    private val sha3 = "3333333333333333333333333333333333333333"

    private fun porcelain(vararg stanzas: String): ByteArray =
        stanzas.joinToString("\u0000\u0000", postfix = "\u0000").toByteArray(Charsets.UTF_8)

    @Test
    fun `parse basic main and agent worktrees`() {
        val output = porcelain(
            "worktree D:/Git/repo\nHEAD $sha1\nbranch refs/heads/develop".replace("\n", "\u0000"),
            "worktree D:/Git/repo agent/economic\nHEAD $sha2\nbranch refs/heads/agent/economic".replace("\n", "\u0000")
        )
        val worktrees = WorktreeListParser.parse(output)

        assertEquals(2, worktrees.size)
        val main = worktrees[0]
        assertEquals("D:/Git/repo", main.path)
        assertEquals("refs/heads/develop", main.branch)
        assertEquals("develop", main.branchDisplayName)
        assertEquals(sha1, main.headCommit)
        assertEquals("1111111", main.headShort)
        assertTrue(main.isMainWorktree)
        assertFalse(main.isDetached)

        val agent = worktrees[1]
        assertEquals("D:/Git/repo agent/economic", agent.path)
        assertEquals("agent/economic", agent.branchDisplayName)
        assertFalse(agent.isMainWorktree)
    }

    @Test
    fun `parse chinese and space path`() {
        val output = porcelain(
            "worktree D:/Git/仓库 主目录\nHEAD $sha1\nbranch refs/heads/main".replace("\n", "\u0000")
        )
        val worktrees = WorktreeListParser.parse(output)
        assertEquals("D:/Git/仓库 主目录", worktrees[0].path)
    }

    @Test
    fun `parse detached locked prunable flags`() {
        val output = porcelain(
            "worktree D:/Git/repo\nHEAD $sha1\nbranch refs/heads/main".replace("\n", "\u0000"),
            "worktree D:/Git/wt1\nHEAD $sha2\ndetached\nlocked".replace("\n", "\u0000"),
            "worktree D:/Git/wt2\nHEAD $sha3\nbranch refs/heads/x\nprunable foo bar".replace("\n", "\u0000")
        )
        val worktrees = WorktreeListParser.parse(output)

        assertEquals(3, worktrees.size)
        val wt1 = worktrees[1]
        assertTrue(wt1.isDetached)
        assertTrue(wt1.isLocked)
        assertFalse(wt1.isPrunable)
        assertEquals("(detached)", wt1.branchDisplayName)

        val wt2 = worktrees[2]
        assertFalse(wt2.isDetached)
        assertTrue(wt2.isPrunable)
    }

    @Test
    fun `parse empty output returns empty list`() {
        assertTrue(WorktreeListParser.parse(ByteArray(0)).isEmpty())
    }
}
