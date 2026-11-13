package com.linmengmeng.worktreemonitor.service

import com.linmengmeng.worktreemonitor.git.GitCommandExecutor
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * 打开 IDEA 原生 Diff:
 * 左侧 = 基准版本内容(默认 HEAD;已提交文件用基准分支版本),git show 提取字符串创建,不依赖 VirtualFile;
 * 右侧 = 工作区当前文件(refreshAndFindFileByNioFile 主动刷新,兼容项目外 worktree)。
 */
class DiffOpener(
    private val project: Project,
    private val executor: GitCommandExecutor = GitCommandExecutor()
) {

    fun openDiff(worktreePath: Path, filePath: String, untracked: Boolean, leftRef: String = "HEAD") {
        ApplicationManager.getApplication().executeOnPooledThread {
            val workFile = LocalFileSystem.getInstance()
                .refreshAndFindFileByNioFile(worktreePath.resolve(filePath))
            val baseContent = if (untracked) "" else loadRefContent(worktreePath, leftRef, filePath)
            val fileType: FileType = workFile?.fileType ?: PlainTextFileType.INSTANCE

            ApplicationManager.getApplication().invokeLater {
                val right = workFile?.let { DiffContentFactory.getInstance().createDocument(project, it) }
                    ?: return@invokeLater
                val left = DiffContentFactory.getInstance().create(baseContent, fileType)
                val request = SimpleDiffRequest(
                    "Diff: $filePath",
                    left,
                    right,
                    leftRef,
                    "工作区"
                )
                DiffManager.getInstance().showDiff(project, request)
            }
        }
    }

    private fun loadRefContent(worktreePath: Path, ref: String, filePath: String): String {
        val result = executor.execute(worktreePath, "show", "$ref:$filePath")
        return if (result.isSuccess) String(result.stdoutBytes, StandardCharsets.UTF_8) else ""
    }
}
