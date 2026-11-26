# -*- coding: utf-8 -*-
"""
牙科门店管理系统 - Python 启动器
用法：
    python 启动系统.py            # 默认端口 8080
    python 启动系统.py 8090       # 指定端口
功能：
    1. 自动查找 Java 17+ 运行时（JAVA_HOME -> PATH -> IDEA 自带 JBR）
    2. 后台启动 Spring Boot jar
    3. 主动轮询探测服务就绪（而不是死等固定秒数）
    4. 就绪后自动打开浏览器（前台 + 管理后台）
    5. Ctrl+C 优雅停止服务
"""
import os
import sys
import time
import webbrowser
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

# ---------------- 配置 ----------------
BASE_DIR = Path(__file__).resolve().parent
JAR_PATH = BASE_DIR / "target" / "dental-clinic-system-1.0.0.jar"
DEFAULT_PORT = 8080

# Java 查找候选（按优先级）
JAVA_CANDIDATES = [
    os.environ.get("JAVA_HOME", "") + os.sep + "bin" + os.sep + "java.exe",
    r"C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\jbr\bin\java.exe",
    r"C:\Program Files\Java",
]
BANNER = r"""
==================================================
   牙科门店管理系统  Dental Clinic Management System
   Spring Boot 3 + MyBatis-Plus + H2（文件库）
==================================================
  患者前台 : http://localhost:{port}/index.html
  管理后台 : http://localhost:{port}/admin.html
  登录账号 : patient01 / doctor01 / store101 / hq001
  登录密码 : 123456
  数据目录 : data\   （数据库文件，重启不丢数据）
  备份目录 : backup\ （每天 02:30 自动备份）
  停止系统 : 在本窗口按 Ctrl+C
==================================================
"""


def find_java():
    """按候选顺序查找可用的 java.exe，并验证版本 >= 17。"""
    # 1) 显式候选路径
    for cand in JAVA_CANDIDATES:
        if not cand:
            continue
        p = Path(cand)
        if p.is_file() and p.suffix == ".exe":
            if java_major(p) >= 17:
                return p
    # 2) PATH 中的 java
    which = subprocess.run(["where", "java"], capture_output=True, text=True)
    if which.returncode == 0:
        for line in which.stdout.splitlines():
            p = Path(line.strip())
            if p.is_file() and java_major(p) >= 17:
                return p
    # 3) 常见安装目录（Program Files\Java、Adoptium、Zulu 等）
    roots = [
        Path(r"C:\Program Files\Java"),
        Path(r"C:\Program Files\Eclipse Adoptium"),
        Path(r"C:\Program Files\Zulu"),
        Path.home() / ".jdks",
    ]
    for root in roots:
        if root.is_dir():
            for sub in sorted(root.iterdir()):
                exe = sub / "bin" / "java.exe"
                if exe.is_file() and java_major(exe) >= 17:
                    return exe
    return None


def java_major(exe):
    """返回 java 主版本号；解析失败返回 0。"""
    try:
        out = subprocess.run(
            [str(exe), "-version"], capture_output=True, text=True, timeout=15
        )
        text = (out.stderr or "") + (out.stdout or "")
        for token in text.replace('"', " ").split():
            if token.count(".") >= 1 and token.split(".")[0].isdigit():
                return int(token.split(".")[0])
    except Exception:
        pass
    return 0


def port_in_use(port):
    """检测端口是否已被占用。"""
    import socket
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


def wait_until_ready(port, timeout=60):
    """主动轮询 /index.html 直到返回 200 或超时，返回是否就绪。"""
    url = f"http://localhost:{port}/index.html"
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as resp:
                if resp.status == 200:
                    return True
        except (urllib.error.URLError, urllib.error.HTTPError, ConnectionError, OSError):
            pass
        time.sleep(1)
    return False


def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print("[错误] 端口必须是数字，例如：python 启动系统.py 8090")
            sys.exit(1)

    print(BANNER.format(port=port))

    if not JAR_PATH.is_file():
        print(f"[错误] 未找到 jar 文件：{JAR_PATH}")
        print("       请确认项目结构完整（target 目录下应有 dental-clinic-system-1.0.0.jar）")
        input("按回车键退出...")
        sys.exit(1)

    if port_in_use(port):
        print(f"[错误] 端口 {port} 已被占用。")
        print("       可能系统已在运行（直接访问上面的网址即可），")
        print(f"       或换一个端口：python 启动系统.py {port + 1}")
        input("按回车键退出...")
        sys.exit(1)

    java = find_java()
    if not java:
        print("[错误] 未找到 Java 17 或更高版本。")
        print("       请安装 JDK 17+，或设置 JAVA_HOME 环境变量。")
        input("按回车键退出...")
        sys.exit(1)

    print(f"[1/3] 使用 Java：{java}")
    print(f"[2/3] 启动服务（端口 {port}）...")

    # 服务日志写入 logs/dental.log（按天追加），便于排查问题
    log_dir = BASE_DIR / "logs"
    log_dir.mkdir(exist_ok=True)
    log_file = log_dir / "dental.log"
    logf = open(log_file, "a", encoding="utf-8", errors="replace")
    logf.write("\n===== " + time.strftime("%Y-%m-%d %H:%M:%S") + f" 启动（端口 {port}）=====\n")
    logf.flush()

    proc = subprocess.Popen(
        [str(java), "-jar", str(JAR_PATH), f"--server.port={port}"],
        cwd=str(BASE_DIR),
        stdout=logf,
        stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )

    print("[3/3] 等待服务就绪（主动探测中，最多 60 秒）...")
    if not wait_until_ready(port):
        print("[错误] 服务在 60 秒内未就绪，正在停止...")
        print(f"       详情见日志：{log_file}")
        try:
            with open(log_file, "r", encoding="utf-8", errors="replace") as f:
                tail = f.readlines()[-15:]
            print("---- 日志末尾 ----")
            print("".join(tail))
        except Exception:
            pass
        proc.terminate()
        logf.close()
        input("按回车键退出...")
        sys.exit(1)

    print()
    print("=" * 50)
    print("  ✔ 系统已就绪！正在打开浏览器...")
    print(f"    患者前台：http://localhost:{port}/index.html")
    print(f"    管理后台：http://localhost:{port}/admin.html")
    print(f"    运行日志：{log_file}")
    print("=" * 50)
    print()
    webbrowser.open(f"http://localhost:{port}/index.html")

    try:
        proc.wait()
    except KeyboardInterrupt:
        print()
        print("正在停止系统...")
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
        logf.close()
        print("系统已停止，再见！")


if __name__ == "__main__":
    main()
