# -*- coding: utf-8 -*-
"""
构造 Agent Worktree Monitor 的测试仓库(自包含,可重复执行,先清后建)。

用法:
    python scripts/make_test_repo.py                 # 默认建在 ~/agent-worktree-test
    python scripts/make_test_repo.py --base D:/tmp   # 指定根目录

产物布局(<base> 下):
    worktree-test-repo/            主仓库(develop 分支)
    worktree-test-area/economic/   agent/economic worktree
    worktree-test-area/api/        agent/api worktree
    worktree-test-area/仪表盘 demo/ detached worktree(路径含中文与空格)

覆盖场景:主 worktree + 3 个 agent worktree、中文空格路径、修改/暂存新增/删除/重命名/
未跟踪、同一文件暂存与未暂存并存、已 commit 未合并、detached HEAD、locked worktree。
"""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path

MAIN_REPO_NAME = "worktree-test-repo"
AREA_NAME = "worktree-test-area"


def run(cmd, cwd):
    r = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print("FAIL:", " ".join(cmd), file=sys.stderr)
        print(r.stderr, file=sys.stderr)
        sys.exit(1)
    return r.stdout


def w(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def init_main_repo(main_repo: Path):
    run(["git", "init", "-b", "develop"], main_repo)
    run(["git", "config", "user.name", "test"], main_repo)
    run(["git", "config", "user.email", "test@test.local"], main_repo)
    w(main_repo / "README.md", "# 测试仓库\n")
    w(main_repo / "src" / "App.java", "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"hello\");\n    }\n}\n")
    w(main_repo / "src" / "Util.java", "public class Util {\n    public static int add(int a, int b) {\n        return a + b;\n    }\n}\n")
    w(main_repo / "src" / "Legacy.java", "public class Legacy {\n    // 待删除\n}\n")
    w(main_repo / "config" / "app.properties", "name=demo\nversion=1\n")
    run(["git", "add", "."], main_repo)
    run(["git", "commit", "-m", "init: 基础代码"], main_repo)


def add_worktrees(main_repo: Path, economic: Path, api: Path, dashboard: Path):
    run(["git", "worktree", "add", str(economic), "-b", "agent/economic"], main_repo)
    run(["git", "worktree", "add", str(api), "-b", "agent/api"], main_repo)
    run(["git", "worktree", "add", str(dashboard), "--detach"], main_repo)
    run(["git", "worktree", "lock", str(dashboard)], main_repo)


def setup_economic(economic: Path):
    """agent/economic:场景最全的一个 worktree"""
    w(economic / "src" / "EconomicConfig.java", "public class EconomicConfig {\n    private int rate = 5;\n}\n")
    run(["git", "add", "."], economic)
    run(["git", "commit", "-m", "feat: 新增 EconomicConfig"], economic)
    w(economic / "src" / "EconomicService.java", "public class EconomicService {\n    public int calc(int x) {\n        return x * 2;\n    }\n}\n")
    run(["git", "add", "src/EconomicService.java"], economic)
    run(["git", "commit", "-m", "feat: 新增 EconomicService"], economic)

    # 重命名(暂存区 R 状态)
    w(economic / "src" / "Helper.java", "public class Helper {\n}\n")
    run(["git", "add", "src/Helper.java"], economic)
    run(["git", "commit", "-m", "feat: 新增 Helper"], economic)
    run(["git", "mv", "src/Helper.java", "src/Helper2.java"], economic)

    # 新增且已暂存(A 状态)
    w(economic / "src" / "EconomicMapper.java", "public class EconomicMapper {\n}\n")
    run(["git", "add", "src/EconomicMapper.java"], economic)

    # 修改未暂存(M 工作区)
    w(economic / "src" / "App.java", "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"economic changed\");\n    }\n}\n")

    # 删除未暂存(D 工作区)
    (economic / "src" / "Legacy.java").unlink()

    # 同一文件暂存+未暂存并存:先 add 再改
    w(economic / "config" / "app.properties", "name=demo\nversion=2\n")
    run(["git", "add", "config/app.properties"], economic)
    w(economic / "config" / "app.properties", "name=demo\nversion=3\n")

    # 未跟踪
    w(economic / "notes" / "todo.txt", "未跟踪文件\n")


def setup_api(api: Path):
    """agent/api:与 economic 修改同一文件(重叠检测素材)"""
    w(api / "src" / "ApiController.java", "public class ApiController {\n}\n")
    run(["git", "add", "."], api)
    run(["git", "commit", "-m", "feat: 新增 ApiController"], api)
    w(api / "src" / "App.java", "public class App {\n    public static void main(String[] args) {\n        System.out.println(\"api changed\");\n    }\n}\n")
    w(api / "api-notes.txt", "api 未跟踪\n")


def main():
    parser = argparse.ArgumentParser(description="构造多 worktree 测试仓库")
    parser.add_argument("--base", type=Path, default=Path.home() / "agent-worktree-test",
                        help="测试仓库根目录(默认 ~/agent-worktree-test)")
    args = parser.parse_args()

    base: Path = args.base
    main_repo = base / MAIN_REPO_NAME
    area = base / AREA_NAME
    economic = area / "economic"
    api = area / "api"
    dashboard = area / "仪表盘 demo"

    for d in (base, area):
        if d.exists():
            shutil.rmtree(d)
            print("已清理:", d)
    base.mkdir(parents=True)
    area.mkdir(parents=True)

    init_main_repo(main_repo)
    add_worktrees(main_repo, economic, api, dashboard)
    setup_economic(economic)
    setup_api(api)

    print("\n===== git worktree list =====")
    print(run(["git", "worktree", "list", "--porcelain"], main_repo))
    print("===== agent/economic status =====")
    print(run(["git", "status", "--porcelain=v1"], economic))
    print("===== agent/api status =====")
    print(run(["git", "status", "--porcelain=v1"], api))
    print("测试仓库构造完成:", main_repo)
    print("用 IDEA 打开该目录即可验证插件。")


if __name__ == "__main__":
    main()
