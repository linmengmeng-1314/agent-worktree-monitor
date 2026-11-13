package com.linmengmeng.worktreemonitor.git

import com.linmengmeng.worktreemonitor.domain.FileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StatusParser / NumstatParser 单测:固定 -z 样本断言,不依赖真实仓库。
 * 重点覆盖 -z 模式重命名记录的【新路径在前】顺序与 numstat 的 {old => new} 展开。
 */
class StatusParserTest {

    @Test
    fun `parse mixed status entries`() {
        val output = (
            "MM config/app.properties\u0000" +
                " M src/App.java\u0000" +
                "A  src/EconomicMapper.java\u0000" +
                "R  src/Helper2.java\u0000src/Helper.java\u0000" +
                " D src/Legacy.java\u0000" +
                "?? notes/todo.txt\u0000"
            ).toByteArray(Charsets.UTF_8)

        val files = StatusParser.parse(output)

        assertEquals(6, files.size)

        val mm = files[0]
        assertEquals("config/app.properties", mm.path)
        assertTrue(mm.staged && mm.unstaged)
        assertEquals(FileStatus.MODIFIED, mm.status)

        val unstagedModified = files[1]
        assertTrue(!unstagedModified.staged && unstagedModified.unstaged)

        val stagedAdded = files[2]
        assertTrue(stagedAdded.staged && !stagedAdded.unstaged)
        assertEquals(FileStatus.ADDED, stagedAdded.status)

        val rename = files[3]
        assertEquals("src/Helper2.java", rename.path)
        assertEquals("src/Helper.java", rename.oldPath)
        assertEquals(FileStatus.RENAMED, rename.status)

        val deleted = files[4]
        assertEquals(FileStatus.DELETED, deleted.status)
        assertNull(deleted.oldPath)

        val untracked = files[5]
        assertEquals("notes/todo.txt", untracked.path)
        assertTrue(untracked.untracked)
        assertEquals(FileStatus.UNTRACKED, untracked.status)
    }

    @Test
    fun `parse chinese path entry`() {
        val output = "?? 说明 文档/备注.txt\u0000".toByteArray(Charsets.UTF_8)
        val files = StatusParser.parse(output)
        assertEquals(1, files.size)
        assertEquals("说明 文档/备注.txt", files[0].path)
    }

    @Test
    fun `parse empty output returns empty list`() {
        assertTrue(StatusParser.parse(ByteArray(0)).isEmpty())
    }

    @Test
    fun `numstat rename braces expanded to new path`() {
        val text = "1\t0\tsrc/App.java\n" +
            "0\t0\tsrc/{Helper.java => Helper2.java}\n" +
            "12\t3\tprefix{old => new}suffix.java\n"

        val map = NumstatParser.parse(text)

        assertEquals(3, map.size)
        assertEquals(1 to 0, map["src/App.java"])
        assertEquals(0 to 0, map["src/Helper2.java"])
        assertEquals(12 to 3, map["prefixnewsuffix.java"])
    }

    @Test
    fun `numstat binary entries skipped`() {
        val map = NumstatParser.parse("-\t-\tlogo.png\n5\t2\ta.java\n")
        assertEquals(1, map.size)
        assertEquals(5 to 2, map["a.java"])
    }
}
