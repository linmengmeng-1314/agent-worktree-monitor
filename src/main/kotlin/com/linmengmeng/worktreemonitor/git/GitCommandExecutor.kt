package com.linmengmeng.worktreemonitor.git

import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.charset.StandardCharsets
import java.nio.file.Path

data class GitCommandResult(
    val exitCode: Int,
    /** stdout 原始字节。porcelain -z 输出以 NUL 分隔,必须按字节保留,不能按行读文本 */
    val stdoutBytes: ByteArray,
    val stderr: String
) {
    val stdoutText: String get() = String(stdoutBytes, StandardCharsets.UTF_8)
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * 统一 Git 命令执行收口:
 * 1. 参数数组方式执行进程,不拼接 shell 字符串,杜绝命令注入;
 * 2. 只允许只读子命令(白名单),插件定位为只读监控工具;
 * 3. 必须在后台线程调用,禁止 EDT。
 */
class GitCommandExecutor {

    companion object {
        /** 只读子命令白名单,新增命令须评审 */
        private val ALLOWED_SUBCOMMANDS = setOf(
            "worktree", "status", "diff", "show", "rev-parse", "merge-base", "ls-files", "log", "branch"
        )
    }

    fun execute(workDir: Path, vararg args: String): GitCommandResult {
        val subcommand = args.firstOrNull() ?: throw IllegalArgumentException("git 参数为空")
        require(subcommand in ALLOWED_SUBCOMMANDS) { "git 子命令不在只读白名单内: $subcommand" }

        val commandLine = GeneralCommandLine("git", *args)
            .withWorkDirectory(workDir.toFile())
            .withCharset(StandardCharsets.UTF_8)
        val process = commandLine.createProcess()
        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
        val exitCode = process.waitFor()
        return GitCommandResult(exitCode, stdout, stderr)
    }
}
