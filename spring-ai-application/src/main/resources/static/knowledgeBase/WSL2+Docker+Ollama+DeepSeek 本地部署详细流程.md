# WSL2 + Docker + Ollama + DeepSeek 本地部署详细流程

> 适用环境：Windows 11 + AMD 显卡（RX 7600M XT，RDNA3）+ 国内网络。
> 目标：在 WSL2 的 Docker 里运行 Ollama，加载 `deepseek-r1:8b`，供本机 Spring Ai 项目本地推理。
> 本文基于**实际部署过程**整理，所有坑都按「现象 → 原因 → 解决」记录，已实测跑通。
>
> **最近一次全量复核：2026-09-28**（对照真机 `docker ps` / `docker inspect`、`.wslconfig` 实际内容、项目当前代码与 `application-*.yaml` 逐项核对，并实跑 `/ollama/chat` 打通端到端）。文中所有版本号、端口、模型名、目录均以该次实测为准。

---

## 概述

### 本机环境

| 项目 | 值 |
|------|----|
| 系统 | Windows 11（build 22621 / 22H2） |
| CPU | AMD Ryzen 7 7840H（8 核 16 线程，含 Radeon 780M 核显） |
| 独显 | AMD Radeon RX 7600M XT（8GB 显存） |
| 内存 | 32GB |
| 用户名 | YU（`C:\Users\YU`） |

### 最终效果

- WSL2 版本 **2.7.14.0**（内核 `6.18.33.2-2`），Ubuntu **24.04.4 LTS**（noble），发行版导入在 `D:\WSL\Ubuntu`
- Docker Engine **29.7.1**（由 systemd 托管，开机自启）
- Ollama **0.32.5** 容器 + `deepseek-r1:8b` 模型（**5.2GB**，实测 5225376047 字节）
- **Open WebUI** 图形界面已部署（`http://localhost:3000`，浏览器网页对话）
- **Portainer 2.39.7** 容器管理界面已部署（`https://localhost:9443`，可视化管理容器/镜像/卷）
- **Milvus 2.6.22** 向量数据库已部署（`localhost:19530` gRPC / `localhost:9091` WebUI），供 Spring AI RAG 检索
- **MySQL 8.0**（实测 `8.0.46-1.el9`）已部署（`localhost:3306`，库 `sap_db`，Spring 项目使用）
- **Attu v2.6.5**（Milvus 图形管理界面）已部署（`http://localhost:8000`）
- 由于 WSL 内缺 `/dev/kfd`，**ROCm GPU 加速不可用，走 CPU 推理**，实测约 **10 token/s**（`deepseek-r1:8b`，含 `think=high` 思考输出）
- `.wslconfig` 启用 **`networkingMode=mirrored`**（镜像网络）：Windows 侧 `localhost` 直达 WSL 内服务，容器 `-p` 端口无需手动做端口转发
- 全部 6 个容器统一 `--restart unless-stopped`；其中 ollama/open-webui/mysql8/milvus 带**健康检查**（`docker ps` 显示 `(healthy)`），**portainer 与 attu 官方镜像均无 HealthCheck**，`Up` 即属正常
- **有开机自启链路**：`Docker-AutoStart.vbs`（用户级 Startup 文件夹，延时 60s）→ 拉起 WSL → systemd 启 Docker → `/root/.docker-autostart.sh` 等 docker 就绪后 `docker start` 全部容器。注意 `--restart unless-stopped` **只自动拉起"意外退出"的容器**，优雅关机 `docker stop` 会把容器标记为手动停止、不会自动恢复，恢复全靠自启脚本里的显式 `docker start`
- **Spring 应用侧还有 2 个非 Docker 进程是启动硬前置**：MCP Server（`spring-ai-mcp-server-webflux`，`localhost:8081`）与主应用（`spring-ai-application`，`localhost:8080`），详见第七章

### 国内网络要点速览

| 资源 | 可达性 | 解决方案 |
|------|--------|----------|
| 微软商店 / `wsl --update` | 卡 0% | ghfast.top 代理手动下载 MSI |
| `wsl --install -d Ubuntu` | 卡死 | 清华 TUNA LXC rootfs 导入 |
| Docker Hub | 极慢/不通 | daemon.json 配置 4 个国内镜像加速 |
| Ollama 模型源 | 慢但可下载 | 断点续传，中断重跑即可 |
| GitHub Releases 直连 | 超时 | ghfast.top 代理（实测直连 `github.com` 下载超时，代理返回 200） |
| Spring AI 侧外网依赖 | 必需 | DeepSeek / OpenRouter API、npm（stdio MCP 走 `npx`）都要外网，**Ollama 离线不等于项目能离线** |

---

## 一、安装 WSL2（国内网络方案）

### 1. 前置检查

1. 任务管理器 → 性能 → CPU，确认「虚拟化」为**已启用**；若禁用，重启进 BIOS 开启 **SVM（AMD）/ VT-x（Intel）**。
2. 若从未启用过 WSL 功能（旧机器），管理员 PowerShell 执行：

```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
# 然后重启电脑
```

### 2. 坑①：`wsl --update` 卡在 0%

**现象**：执行 `wsl --update` 后进度永远停在 0%，`wsl --version` 无输出或提示更新失败。
**原因**：WSL 的 MSI 从微软服务器分发，国内网络不通（商店 CDN 被墙/超时）。
**解决**：改用 GitHub 镜像代理手动下载安装包。

```powershell
# 下载 WSL 安装包（约 247MB），经 ghfast.top 代理转发 GitHub 资源
# 本机当前版本 2.7.14.0（2026-09-28 实测，URL 已验证可下载）
Invoke-WebRequest -Uri "https://ghfast.top/https://github.com/microsoft/WSL/releases/download/2.7.14/wsl.2.7.14.0.x64.msi" -OutFile "$env:TEMP\wsl.2.7.14.0.x64.msi"
# 双击安装，或：
Start-Process msiexec -ArgumentList '/i', "$env:TEMP\wsl.2.7.14.0.x64.msi", '/quiet'
```

**验证**：

```powershell
wsl --version
# WSL 版本：2.7.14.0
# 内核版本：6.18.33.2-2
```

> 该 URL 是「GitHub Releases 下载地址 + ghfast.top 前缀」的固定套路：直连 `https://github.com/microsoft/WSL/releases/download/<版本>/wsl.<版本>.x64.msi` 在国内超时，加 `https://ghfast.top/` 前缀即可。版本号以 Releases 页为准，已安装的可直接 `wsl --update`（能走通就不用手动下 MSI）。

---

## 二、安装 Ubuntu（LXC rootfs 导入）

### 3. 坑②：`wsl --install -d Ubuntu-24.04` 卡死

**现象**：走商店或 web 下载方式安装 Ubuntu 时长期卡住/超时失败。
**原因**：发行版 rootfs 从微软服务器下载，国内不通。
**解决**：从清华 TUNA 的 LXC 镜像站直接下载 rootfs，用 `wsl --import` 导入。

```powershell
# 1) 去 TUNA LXC 镜像目录选一个 Ubuntu noble 版本：
#    https://mirrors.tuna.tsinghua.edu.cn/lxc-images/images/ubuntu/noble/amd64/default/
#    例：rootfs.tar.xz（约 128MB）

# 2) 下载
Invoke-WebRequest -Uri "https://mirrors.tuna.tsinghua.edu.cn/lxc-images/images/ubuntu/noble/amd64/default/20260801_07%3A42/rootfs.tar.xz" -OutFile "$env:TEMP\rootfs.tar.xz"

# 3) 导入（WSL 2.7 支持直接导入 xz 压缩 tar；若报错先 `xz -dk rootfs.tar.xz` 再导入 rootfs.tar）
wsl --import Ubuntu "D:\WSL\Ubuntu" "$env:TEMP\rootfs.tar.xz"

# 4) 验证
wsl -l -v
#   NAME      VERSION
#   Ubuntu    2
```

> `--import` 到自定义目录 `D:\WSL\Ubuntu`，避免占满 C 盘。rootfs 默认 root 用户登录。

### 4. 给 WSL2 分配内存 + 开启镜像网络

编辑 `C:\Users\YU\.wslconfig`（不存在则新建），**本机实际内容如下**：

```ini
[general]
# 会话全部关闭后，实例空闲多久自动终止。-1 = 永不自动终止（默认 15000 毫秒）
instanceIdleTimeout=-1

[wsl2]
# 镜像网络:Windows 与 WSL 共享网络,localhost 可直达 WSL 内服务(含 Docker 映射端口)
networkingMode=mirrored
memory=24GB
swap=4GB
processors=8
# 实例全部终止后，虚拟机空闲多久自动关闭。-1 = 永不自动关闭（默认 60000 毫秒）
vmIdleTimeout=-1
```

- `networkingMode=mirrored`（**必加**）：镜像网络模式下 Windows 与 WSL 共享同一网络命名空间，容器 `-p` 发布的端口直接在 Windows 侧 `localhost` 可达，项目 yaml 里的 `localhost:11434` / `localhost:19530` / `localhost:3306` 都能直接用（见第 26.1 条）；不开启也能靠 NAT 转发，但镜像网络更稳、更快。
- `memory=24GB`：给 Ollama CPU 推理 + Milvus + MySQL 留足（宿主 32GB）。
- `instanceIdleTimeout` / `vmIdleTimeout` 见第 35 条（关窗口不关机）。

生效需重启 WSL：

```powershell
wsl --shutdown
```

### 5. 配置 systemd 与默认用户

进入 Ubuntu：`wsl -d Ubuntu`，编辑 `/etc/wsl.conf`：

```bash
vim /etc/wsl.conf
```

写入：

```
[boot]
systemd=true

[user]
default=root
```

- `systemd=true`：让 WSL 用 systemd 管理服务（Docker 需要）。
- `[user] default=root`：免去每次 sudo。

PowerShell 执行 `wsl --shutdown` 后重新进入生效。可用 `systemctl is-system-running` 确认 systemd 已启动。

### 6. apt 换清华源

Ubuntu 24.04 使用 `.sources` 格式：

```bash
cp /etc/apt/sources.list.d/ubuntu.sources /etc/apt/sources.list.d/ubuntu.sources.bak
sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list.d/ubuntu.sources
sed -i 's|http://security.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list.d/ubuntu.sources
apt update && apt upgrade -y
```

---

## 三、安装 Docker（踩坑最多）

### 7. 添加清华 docker-ce 源

```bash
apt update
apt install -y ca-certificates curl gnupg
mkdir -p /etc/apt/keyrings
```

### 8. 坑③：TUNA `/gpg` 返回 ASCII 文本

**现象**：直接执行 `curl .../gpg | gpg --dearmor -o .../docker.gpg` 后，apt update 报签名格式错误。
**原因**：清华镜像返回的是 **ASCII armored** 文本（`-----BEGIN PGP PUBLIC KEY BLOCK-----`），而 `.gpg` 是二进制格式；若不 dearmor 直接存盘，apt 无法识别。
**解决**：必须用 `gpg --dearmor` 转成二进制：

```bash
curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list
apt update
```

### 9. 坑④：`NO_PUBKEY 7EA0A9C3F273FCD8`

**现象**：`apt update` 报 `The following signatures couldn't be verified because the public key is not available: NO_PUBKEY 7EA0A9C3F273FCD8`。
**原因**：key 未正确安装（未 dearmor 或 keyring 路径不对）。`7EA0A9C3F273FCD8` 是 Docker 官方 key 的主 key。
**解决**：确认 keyring 文件存在且为二进制（`file /etc/apt/keyrings/docker.gpg` 应显示 `PGP public key block`）；若 dkg 仍缺，把 ASCII key 追加进 apt 自带 keyring：

```bash
# 备用：导入到 /usr/share/keyrings（apt 默认信任）
curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker.gpg] https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list
apt update
```

### 10. 坑⑤：systemd 卡死导致 dpkg configure 挂起

**现象**：安装 docker-ce 时，`apt` 在触发服务启动的 dpkg configure 阶段**长时间挂起**；`systemctl status docker` 也卡死。
**原因**：此时 WSL 的 systemd 正卡在 "initializing"（详见第四章），`systemctl start` 排队等不到响应，apt 触发服务启动就死等。
**解决**：先屏蔽服务自启动，让 apt 跳过服务启停：

```bash
printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d
chmod +x /usr/sbin/policy-rc.d
```

> `policy-rc.d` 返回 101 = 「禁止自动启动服务」。只影响安装阶段，不影响后续手动/systemd 启动。

然后安装（版本实测为 5:29.7.1）：

```bash
apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
docker --version   # Docker version 29.7.1
```

### 11. 配置镜像加速

新建 `/etc/docker/daemon.json`（顺带限制容器日志大小）：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.net",
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

> 镜像地址可能随时间失效，失效时替换为当时可用镜像即可。改完必须重启 dockerd 才生效。

### 12. 手动启动 dockerd

systemd 卡死时先用 nohup 手动拉起（第四章修复 systemd 后会交给 systemd 托管）：

```bash
nohup dockerd > /var/log/dockerd.log 2>&1 &
sleep 5
docker info   # Server Version: 29.7.1 即成功
```

### 13. 坑⑥：LXC rootfs 缺 `/run/user/0`

**现象**：部分命令报 `/run/user/0` 不存在，或容器/工具启动异常。
**原因**：LXC rootfs 是精简版，未创建该目录。
**解决**：

```bash
mkdir -p /run/user/0
```

### 14. hello-world 验证

```bash
docker run --rm hello-world
# 打印 "Hello from Docker!" 即安装成功（首次会自动从镜像加速拉取）
```

---

## 四、修复 systemd（卡 initializing）

### 15. 现象与排查

`systemctl is-system-running` 一直停在 `initializing`，任何 `systemctl start/status` 都排队挂起。

排查过程：

```bash
systemctl list-jobs --all          # 找所有 "activating"/"running" 的 job
# 结果锁定：systemd-firstboot.service  start  running  ← 就是它一直不放
systemctl --failed                 # 无失败单元，问题不是崩溃而是挂起
journalctl -b --no-pager | tail -20   # 大量 WSL "WaitForBootProcess ... Timed out"
```

### 16. 根因

rootfs 是直接导入的，相当于「从零首次启动」。`systemd-firstboot` 等待**交互式输入**（语言/键盘/时区等），在无人值守的 WSL 里永远等不到输入，把整个启动队列堵死。

### 17. 解决

屏蔽 firstboot 并驱动启动队列走完：

```bash
systemctl mask systemd-firstboot.service    # 创建 /dev/null 软链，永久禁用
systemctl start multi-user.target           # 让排队的服务全部启动
systemctl is-system-running                 # 返回 degraded 即恢复（degraded=有小问题但可用）
```

> 不要取消 mask，否则下次重启又卡死。

### 18. dockerd 交接给 systemd 托管

systemd 恢复后，把第十二步手动起的 dockerd 换成 systemd 管理，实现开机自启：

```bash
systemctl enable docker     # 开机自启
pkill dockerd               # 停掉手动实例（⚠️ 见第八章教训：禁 kill -9）
sleep 3
systemctl reset-failed docker
systemctl start docker
systemctl is-active docker  # active
docker info                 # 正常
```

> **警告**：此处本流程曾用 `pkill -9` 强杀 dockerd 导致数据损坏，务必改用正常停止方式（详见第八章事故记录）。

---

## 五、安装 Ollama + deepseek-r1:8b

### 19. 拉取镜像

```bash
docker pull ollama/ollama    # 约 8GB，走镜像加速；中断重跑可续传
```

### 20. 运行容器

```bash
docker run -d --name ollama --restart unless-stopped \
  --device /dev/dxg \
  -e OLLAMA_HOST=0.0.0.0:11434 \
  -e HSA_OVERRIDE_GFX_VERSION=11.0.2 \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  --health-cmd 'OLLAMA_HOST=127.0.0.1:11434 ollama list >/dev/null 2>&1' \
  --health-interval 30s --health-timeout 10s --health-start-period 30s --health-retries 3 \
  ollama/ollama
```

要点解释：

- `--device /dev/dxg`：WSL2 的 GPU paravirtualization 设备，**不是** Docker Desktop 的 `--gpus all`（后者在此场景无效）。
- `-e HSA_OVERRIDE_GFX_VERSION=11.0.2`：给 AMD RDNA3（RX 7600M XT）强制指定 ROCm 架构。
- `-v ollama:/root/.ollama`：**模型数据持久化卷，必须保留**，删容器不丢模型。
- `--restart unless-stopped`：Docker 重启时容器自动拉起，配合 systemd 实现开机自愈。
- `-e OLLAMA_HOST=0.0.0.0:11434`：显式监听 `0.0.0.0`（新版 Ollama 默认绑 `127.0.0.1`，会拒绝跨容器/外部访问）。
- `-p 11434:11434`：暴露 Ollama API 端口供 Windows 侧访问。
- `--health-cmd 'OLLAMA_HOST=127.0.0.1:11434 ollama list ...'`：镜像无 curl/wget，用自带 `ollama list`（能连上本地服务即返回 0）做健康检查；`docker ps` 显示 `(healthy)` 才算就绪。

### 21. 下载模型（后台执行 + 断点续传）

国内访问 Ollama 模型源较慢（实测约 400KB/s，5.2GB 全程约 2 小时），建议后台执行并写日志：

```bash
nohup docker exec ollama ollama pull deepseek-r1:8b > /var/log/ollama-pull.log 2>&1 &
# 查看进度
tail -f /var/log/ollama-pull.log
docker exec ollama ollama list    # 出现 deepseek-r1:8b  5.2GB 即完成
```

- 中途断了**直接重跑 pull 即可**，Ollama 自动断点续传。
- ⚠️ WSL 后台任务会随「最后一个前台进程退出」而被终止：长时间任务要么保持窗口打开，要么用 `tmux`/`screen` 挂起。
- 想换更小的模型（8GB 显存 / CPU 推理更快）就 `ollama pull deepseek-r1:7b`，**但要同步改配置**，见第 26.2 条。

### 22. GPU 确认（实测：走 CPU）

```bash
ls -l /dev/dxg /dev/kfd    # 实测：dxg 存在，kfd 不存在
zcat /proc/config.gz | grep HSA_AMD   # 实测：# CONFIG_HSA_AMD is not set
docker exec ollama ollama ps
# NAME            PROCESSOR
# deepseek-r1:8b  100% CPU
```

- `/dev/dxg`（WSL GPU 半虚拟化）存在 → 基础可探测 GPU；
- `/dev/kfd`（ROCm 需要）缺失 → **ROCm GPU 加速不可用**，Ollama 自动退回 CPU；
- 7840H 8 核 CPU 实测约 **10 token/s**（`deepseek-r1:8b`，2026-09-28 复测 `eval_count 296 / eval_duration 37.6s ≈ 9.95 t/s`），日常问答够用；**首次提问会多花 20~40 秒把模型从内存载入**。

**为什么没有 GPU（根因链）**：

1. Windows AMD 显卡驱动偏旧（实测内部版本 `31.0.14060.0`，约 Adrenalin 23.9.x），不含「ROCm for WSL」组件；
2. **关键根因**：WSL2 官方内核编译时 `CONFIG_HSA_AMD` 为**关闭**（`zcat /proc/config.gz | grep HSA_AMD` 显示 `# CONFIG_HSA_AMD is not set`），即内核**根本没有 `amdkfd` 模块，永远不会生成 `/dev/kfd`**——这是驱动之外的硬性前提；
3. 当前 `ollama/ollama` 镜像默认**不含 ROCm 运行时**（只带 CPU + NVIDIA CUDA），必须改用 `ollama/ollama:rocm`。

**启用 GPU 加速三步方案**：

**① 更新 Windows AMD 驱动**：去 AMD 官网下载支持 ROCm WSL 的版本（Adrenalin 24.1.1 之后，内部版本号 ≥ `31.0.24000`），安装后重启电脑。

**② 让 WSL 提供 `/dev/kfd`**：先试 `wsl --update`（走 ghfast.top 代理）后用新内核再查 `zcat /proc/config.gz | grep HSA_AMD` 是否变为 `=m`/`=y`；若官方内核仍关闭，需**自行编译带 `CONFIG_HSA_AMD=m` 的 WSL2 内核**，并在 `.wslconfig` 的 `[wsl2]` 段加 `kernel=<内核文件路径>`，然后 `wsl --shutdown`。确认 `ls /dev/kfd` 出现。

**③ 换 ROCm 版 Ollama 容器**（模型在 volume 里，不用重下）：

```bash
docker rm -f ollama
docker pull ollama/ollama:rocm
docker run -d --name ollama --restart unless-stopped \
  --device /dev/dxg --device /dev/kfd \
  -e HSA_OVERRIDE_GFX_VERSION=11.0.2 \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  ollama/ollama:rocm
docker exec ollama ollama ps    # PROCESSOR 显示 GPU 即成功
```

> 注意事项：
> - 笔记本是「核显 780M + 独显 7600M XT」混合架构，ROCm 可能绑定到 iGPU（780M 为 gfx1103，需 `HSA_OVERRIDE_GFX_VERSION=11.0.3`）而非独显（gfx1102）；若 `ollama ps` 显示 GPU 但速度异常，尝试调整该值。
> - 7600M XT 仅 8GB 显存，8b 模型默认量化（≈5.2GB）刚好装下；更大的模型需更低量化或换模型。
> - 若更新驱动后 `/dev/kfd` 仍未出现，优先怀疑内核 HSA_AMD 未编译，而不是驱动问题。

---

## 六、测试对话（Windows 侧两个坑）

### 23. 坑⑦：PowerShell 的 `curl` 不是 curl

**现象**：

```powershell
curl http://localhost:11434/api/chat -d "..."
# Invoke-WebRequest : 找不到接受实际参数"-d"的位置形式参数
```

**原因**：PowerShell 里 `curl` 是 **`Invoke-WebRequest` 的别名**，它没有 `-d` 参数。
**解决**：用真实 curl 的完整名 **`curl.exe`**（Windows 10/11 自带），或直接用 `Invoke-RestMethod`。

### 24. 坑⑧：PowerShell 5.1 破坏 curl.exe 参数内的双引号

**现象**：

```powershell
curl.exe http://localhost:11434/api/chat -d '{"model":"deepseek-r1:8b",...}'
# 返回 {"error":"invalid character 'm' looking for beginning of object key string"}
```

**原因**：PowerShell 5.1 把参数传给原生程序时会**吃掉内部双引号**，JSON 变成 `{model:...}`（key 无引号），Ollama 的 Go 解析器在 `{` 后第一个字符 `m` 处报错。单引号只在 PowerShell 解析层有效，救不了传给 curl.exe 的引号。
**解决**：见下一条推荐命令。

### 25. 正确的测试命令（首选）

```powershell
Invoke-RestMethod -Uri http://localhost:11434/api/chat -Method Post -ContentType 'application/json' -Body '{"model":"deepseek-r1:8b","messages":[{"role":"user","content":"1+1等于几？"}],"stream":false}' | Select-Object -ExpandProperty message
```

**备选**：

```powershell
# 方案 B：curl.exe + --% 停止 PowerShell 解析，之后原样传给 curl
curl.exe http://localhost:11434/api/chat -d --% "{\"model\":\"deepseek-r1:8b\",\"messages\":[{\"role\":\"user\",\"content\":\"1+1等于几？\"}],\"stream\":false}"

# 方案 C：JSON 写入文件再让 curl 读文件
'{"model":"deepseek-r1:8b","messages":[{"role":"user","content":"1+1等于几？"}],"stream":false}' | Out-File -Encoding ascii $env:TEMP\body.json
curl.exe http://localhost:11434/api/chat -d "@$env:TEMP\body.json"

# 方案 D：进 WSL 跑 Linux 原生 curl（无引号问题）
wsl -d Ubuntu -- bash -lc 'curl -s http://localhost:11434/api/chat -d "{\"model\":\"deepseek-r1:8b\",\"messages\":[{\"role\":\"user\",\"content\":\"1+1等于几？\"}],\"stream\":false}"'
```

返回 JSON 的 `message.content` 字段就是模型回答。

---

## 七、对接 Spring Ai Practice 项目

> 本章全部内容按 **2026-09-28 的项目代码**（HEAD = `3012ed8`「新增 内容安全审核，OpenRouter调用」）核对过，并**实跑验证过端到端**（MCP Server + 主应用起来 → `GET /ollama/chat?message=...` 返回 HTTP 200，耗时约 25s）。

### 26.1 应用启动的硬依赖（先看这张表）

项目是 **多模块 Maven 工程**（根聚合 pom 本身不可运行），有两个独立 Spring Boot 应用：

| 组件 | 端口 | 是否 Docker | 缺了会怎样 |
|------|------|------------|-----------|
| `ollama` 容器 | 11434 | ✅ | 只有 `/ollama/*` 接口不可用，**应用仍能启动** |
| `milvus` 容器 | 19530 | ✅ | **启动失败**：`MilvusVectorStore` 启动即建 `vector_store` 集合，连不上直接抛异常 |
| `mysql8` 容器 | 3306 | ✅ | **启动失败**：DataSource + `JdbcChatMemoryRepository` 建不出来 |
| `spring-ai-mcp-server-webflux` 应用 | 8081 | ❌ 本地跑 | **启动失败**：`Client failed to initialize by explicit API call` ← `Connection refused: localhost:8081` |
| `.env`（7 个 key） | — | — | **启动失败**：`ChatClientConfig` 里 `@Value("${MYSQL_HOST}")` 等直接缺属性 |
| 外网（DeepSeek / OpenRouter API、npm） | — | — | 启动能过，但 DeepSeek/OpenAI/RAG/工具调用类接口全部报错 |

**最容易踩的一条**：`application-dev.yaml` 里 `spring.ai.mcp.client` 配了 streamable-http 指向 `http://localhost:8081/mcp`，而 MCP 客户端是**启动期强制初始化**的（不是懒加载）。8081 没进程时，`mcpSyncClients` bean 创建失败 → 级联到 `deepSeekChatModel` → `codeReviewAgent` → **`Failed to load ApplicationContext`**。所以「先起 8081，再起 8080」是硬顺序。

> 补充：另有两个 **stdio 型** MCP（`mcp/mcp-servers.json` 里 `cmd.exe /c npx -y bing-cn-mcp` 和 `npx -y @f4ww4z/mcp-mysql-server`），它们跑在 **Windows 宿主机**上，需要本机装 **Node.js / npx** 且能访问 npm 源。首次启动会现场 `npx` 下载包，慢属正常。

### 26.2 配置 `.env`（项目根目录，git 忽略）

当前 `.env` 实际包含 **7 个 key**（2026-09-28 实测）：

```
DEEPSEEK_API_KEY=...
OPENROUTER_API_KEY=...
MYSQL_HOST=localhost
MYSQL_USER=sap
MYSQL_PASSWORD=sap123
MYSQL_DATABASE=sap_db
PEXELS_API_KEY=...
```

- `MYSQL_*` 四个**不能少**：`ChatClientConfig` 用 `@Value("${MYSQL_HOST}")` 等拼 MySQL MCP 工具的 system prompt，`application-dev.yaml` 的 `datasource.password` 也取 `${MYSQL_PASSWORD}`，缺任何一项都会启动失败。值必须与 `mysql8` 容器一致（`sap` / `sap123` / `sap_db`）。
- `PEXELS_API_KEY` 供 `spring-ai-mcp-server-webflux` 的 `PexelsMcpTools` 用（两个模块各自有 `DotenvEnvironmentPostProcessor`，都会回退查找项目根的 `.env`）。
- **Ollama 相关的 `OLLAMA_BASE_URL` / `OLLAMA_CHAT_MODEL` 当前 `.env` 里没有，也不需要**：`application-dev/sit/prod.yaml` 的默认值就是 `http://localhost:11434` + `deepseek-r1:8b`，与本地已装模型一致，**2026-09-28 实测不加也能跑通 `/ollama/chat`**。

```
# 可选：只有换了端口 / 换了模型才需要写进 .env
# OLLAMA_BASE_URL=http://localhost:11434
# OLLAMA_CHAT_MODEL=deepseek-r1:8b
```

> `localhost` 在 Windows 侧即可直达 WSL2 的 11434 端口（`.wslconfig` 已开 `networkingMode=mirrored` 镜像网络，见第 4 条）。
> ⚠️ 反向坑：若本机 `ollama pull` 换了模型（如换成 7b），**必须**在 `.env` 加 `OLLAMA_CHAT_MODEL`，否则 yaml 默认的 8b 不存在，接口报「model not found」。另外 `spring.ai.ollama.init.pull-model-strategy: never` 表示**应用不会自动帮你拉模型**，缺模型就是直接报错。

### 26.3 初始化 MySQL 表（一次性）

`mysql8` 容器已带 `sap_db` 库，但业务表要自己导入。脚本在仓库里：

```powershell
# 建表：sys_user、user_address（IF NOT EXISTS，可重复执行）
docker exec -i mysql8 mysql -usap -psap123 sap_db < "D:\JavaProjects\SpringAiPractice\spring-ai-application\src\main\resources\sql\sys_user.sql"
docker exec -i mysql8 mysql -usap -psap123 sap_db < "D:\JavaProjects\SpringAiPractice\spring-ai-application\src\main\resources\sql\user_address.sql"
```

> `SPRING_AI_CHAT_MEMORY`（聊天记忆表）由 `spring.ai.chat.memory.repository.jdbc.initialize-schema: always` **自动建**，不用管。
> 校验：`docker exec -it mysql8 mysql -usap -psap123 sap_db -e "show tables;"` 应看到 `SPRING_AI_CHAT_MEMORY`、`sys_user`、`user_address`（2026-09-28 实测三张表都在）。

### 26.4 启动两个应用（顺序不能反）

```powershell
$env:JAVA_HOME="D:\softwarework\JDK\ms-25.0.3"   # 必须 JDK 25，系统默认 1.8 会构建失败

# ① 先起 MCP Server（端口 8081），另开一个窗口跑
.\mvnw.cmd spring-boot:run -pl spring-ai-mcp-server-webflux

# ② 确认 8081 起来了，再起主应用（端口 8080）
.\mvnw.cmd spring-boot:run -pl spring-ai-application
```

- **必须带 `-pl <模块>`**：根 pom 是 `packaging=pom` 的聚合器，直接 `.\mvnw.cmd spring-boot:run` 会在两个模块上依次执行，第二个模块必然失败。
- 切 profile：`.\mvnw.cmd spring-boot:run -pl spring-ai-application "-Dspring-boot.run.arguments=--spring.profiles.active=sit"`。
- `--enable-native-access=ALL-UNNAMED` 已在根 pom 的 `spring-boot-maven-plugin` 里配好（仅 `mvn spring-boot:run` 生效）；用 IDEA 启动要自己加到 VM options。
- 用 IDEA 运行时建议仍从**多模块根目录**启动（`spring-boot-maven-plugin` 的 `workingDirectory` 就是 `${maven.multiModuleProjectDirectory}`，`.env` 在根目录）。

### 26.5 验证 `/ollama/chat`

浏览器打开 `http://localhost:8080/swagger-ui.html`，用 `/ollama/chat` 接口提问。参数：`message`（必填，问题内容）、`voice`（可选，口吻，默认「聊天AI助手」）。直接在浏览器地址栏访问：

```
http://localhost:8080/ollama/chat?message=你好
```

走 `OllamaController` → `ollamaChatClient` → 本地 CPU 推理。**2026-09-28 实测**：ASCII 问题 HTTP 200 约 25s；中文问题浏览器访问正常返回中文。

> 坑（PowerShell 专有）：**别用 `Invoke-RestMethod` 直接传中文/百分号编码的 URL**。PowerShell 5.1 会把 URI 再解码一次，Tomcat 抛
> `InvalidParameterException: Character decoding failed ... MalformedInputException` → **接口返回 500**（这不是应用 bug）。
> 命令行测中文要用 `curl.exe` + `[uri]::EscapeDataString()`：
>
> ```powershell
> $q = [uri]::EscapeDataString("用一句话介绍你自己")
> curl.exe "http://localhost:8080/ollama/chat?message=$q"
> ```
>
> 输出里的中文乱码只是控制台代码页问题（先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` 或重定向到文件再看）。

### 26.6 RAG 用的嵌入模型不在 Ollama（容易误解）

`spring.ai.model.embedding: openai` —— 项目把 **OpenAI 兼容接口指向 OpenRouter 网关**（`spring.ai.openai.base-url: https://openrouter.ai/api/v1`），RAG 的向量由 **`voyageai/voyage-4`（1024 维）** 生成，与本地 Ollama 无关：

- 结论：**`/rag/*` 接口需要外网 + `OPENROUTER_API_KEY`**，Ollama 挂了不影响它；
- `spring.ai.vectorstore.milvus.embedding-dimension: 1024` 必须与嵌入模型维度一致，改嵌入模型要同步改这个值，否则集合建不出来；
- 灌知识库：先 `GET /rag/initVectorStore`（会读 `static/knowledgeBase/` 下的 `products.json` 和 2 个 PDF，**向量化走外网**），再 `GET /rag/ragChat?message=...&conversationId=...`。
- 想彻底离线跑 RAG，可把 `spring.ai.model.embedding` 换成 `ollama` + `spring.ai.ollama.embedding`（需另 `ollama pull` 一个 embedding 模型，维度要与 `embedding-dimension` 对齐）。

### 27. 一键自检（起应用前跑一遍）

```powershell
# Docker 侧：6 个容器是否都 Up
wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'

# 端口侧：容器映射的 6 个端口
foreach ($p in 11434,3000,8000,9091,9443,19530,3306) {
  $c = New-Object System.Net.Sockets.TcpClient
  $t = $c.BeginConnect("localhost",$p,$null,$null)
  "{0,-6} {1}" -f $p, (($t.AsyncWaitHandle.WaitOne(2000)) -and $c.Connected)
  $c.Close()
}

# Spring 侧：先确认 8081 有进程，再启 8080
foreach ($p in 8081,8080) {
  $c = New-Object System.Net.Sockets.TcpClient
  $t = $c.BeginConnect("localhost",$p,$null,$null)
  "{0,-6} {1}" -f $p, (($t.AsyncWaitHandle.WaitOne(2000)) -and $c.Connected)
  $c.Close()
}

# 依赖面：模型在不在、MySQL 表建了没
wsl -d Ubuntu -- docker exec ollama ollama list
wsl -d Ubuntu -- docker exec -it mysql8 mysql -usap -psap123 sap_db -e "show tables;"
Test-Path "D:\JavaProjects\SpringAiPractice\.env"   # 必须 True
```

**判定标准**（2026-09-28 实测基线）：

- 端口 11434 / 3000 / 8000 / 9091 / 9443 / 19530 / 3306 全部 `True`；
- `ollama list` 有 `deepseek-r1:8b`；
- `show tables` 有 `SPRING_AI_CHAT_MEMORY`、`sys_user`、`user_address`；
- `.env` 存在（7 个 key）；
- 8081 在起 MCP Server 之后才应为 `True`；8080 只有起完主应用才为 `True`。

---

## 八、Open WebUI 图形界面（使用与维护）

> Open WebUI 是浏览器网页版对话界面，直接连本地 Ollama，免去敲命令。访问地址 **`http://localhost:3000`**。

### 28. 部署命令（已部署，实测 healthy）

```bash
docker run -d --name open-webui --restart unless-stopped \
  -p 3000:8080 \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -e HF_HUB_OFFLINE=1 \
  -v open-webui:/app/backend/data \
  --add-host host.docker.internal:host-gateway \
  ghcr.io/open-webui/open-webui:main
```

参数说明：

- `-p 3000:8080`：浏览器访问 `localhost:3000`，容器内服务跑在 8080。
- `OLLAMA_BASE_URL=http://host.docker.internal:11434`：让 Open WebUI 通过 `host.docker.internal` 指向宿主（WSL）的 Ollama 端口 11434。
- `-e HF_HUB_OFFLINE=1`：**必须加（坑⑨）**。RAG 嵌入向量模型 `sentence-transformers/all-MiniLM-L6-v2` 的缓存就在 volume 里（`/app/backend/data/cache/embedding/models`），但 huggingface_hub 即使命中缓存仍会**联网校验 huggingface.co**，国内网络下连接卡死 → 启动永远停在 "Waiting for application startup"，3000 端口无响应、healthcheck 一直 `starting`。离线模式直接从本地缓存加载、不做任何联网，秒启动。
- `--add-host host.docker.internal:host-gateway`：WSL 下必须加，否则容器内无法解析 `host.docker.internal`。
- `-v open-webui:/app/backend/data`：**持久化卷**（账号、会话、上传文件、RAG 知识库、嵌入模型缓存都存这里），升级/重建不丢。
- `--restart unless-stopped`：配合 systemd，WSL 启动后自动拉起。

验证：

```powershell
curl http://localhost:3000        # 返回 HTML 即正常
wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'    # 应显示 open-webui Up (healthy)
```

### 29. 访问与使用

1. 浏览器打开 `http://localhost:3000`；
2. 首次访问按提示**注册账号**（数据存在本机 volume，仅本机可见）；
3. 登录后在顶部/侧栏**模型下拉框选择 `deepseek-r1:8b`**，即可网页对话；
4. 常用功能：
   - 多会话管理（左栏历史会话，可重命名/删除/搜索）；
   - Markdown / 代码高亮 / LaTeX 渲染；
   - **RAG 知识库**：左侧「知识库」→ 新建并上传文档（PDF/Word/TXT 等）→ 在聊天页顶部的 `+` 里勾选知识库，模型即可基于文档回答；
   - 图片/音频上传（图片走视觉模型，8b 纯文本模型不支持看图，音频需 Whisper 配置，默认基础可用）；
   - 多模型切换（后续 `ollama pull` 装了别的模型，刷新页面即可在列表中选择）。

> deepseek-r1:8b 是**纯文本推理模型**，上传图片无法"看图"，但 RAG 文档问答（上传文字类文件）完全可用。

### 30. 日常维护

| 用途 | 命令 |
|------|------|
| 看状态 | `wsl -d Ubuntu -- docker ps --filter name=open-webui` |
| 重启（改配置后） | `wsl -d Ubuntu -- docker restart open-webui` |
| 看日志 | `wsl -d Ubuntu -- docker logs -f open-webui` |
| 看数据占用 | `wsl -d Ubuntu -- du -sh /var/lib/docker/volumes/open-webui/_data` |

**升级**（保留全部数据，volumes 不丢）：

```bash
wsl -d Ubuntu -- docker rm -f open-webui
wsl -d Ubuntu -- docker pull ghcr.io/open-webui/open-webui:main
# 然后重跑第 28 条部署命令即可
```

**备份数据**（volume → 当前目录 tgz）：

```bash
wsl -d Ubuntu -- bash -lc 'docker run --rm -v open-webui:/data -v $(pwd):/backup alpine tar czf /backup/openwebui-$(date +%F).tar.gz -C /data .'
```

### 31. Open WebUI 故障排查

| 症状 | 检查与修复 |
|------|-----------|
| `localhost:3000` 打不开 | `docker ps` 看 open-webui 是否 healthy；不在则 `docker start open-webui`；看 `docker logs open-webui` |
| 启动卡在 "Waiting for application startup"，端口无响应、healthcheck 一直 `starting` | **HF 联网校验卡死（坑⑨）**：重建容器时漏了 `-e HF_HUB_OFFLINE=1`。加上即从 volume 缓存离线加载秒启动；若缓存损坏再删 `/app/backend/data/cache/embedding/models` 后重启让其重建 |
| 对话报「无法连接 Ollama」 | 确认 ollama 容器在跑；确认 env：`docker inspect open-webui | grep OLLAMA_BASE_URL` 应为 `host.docker.internal:11434`；容器内测 `docker exec open-webui curl http://host.docker.internal:11434` |
| 第一次提问特别慢 | 首次要加载 embedding 模型 + 8b CPU 推理（实测约 25s），属正常 |
| 升级后数据没了 | 检查升级命令是否漏了 `-v open-webui:/app/backend/data`；旧卷还在就补挂载 |
| 磁盘越来越大 | 上传附件/会话数据在 volume 里，先 `du` 确认，再删无用的知识库/会话 |

### 32. Open WebUI 注意事项

- 3000 端口界面**带账号体系**，但默认只应在本机使用，勿直接映射到公网；
- 浏览器侧要**手动注册第一个账号**（admin），后续账号也由该界面管理；
- 升级频率可参考官方 GitHub Releases，`main` 镜像滚动更新，重拉即可；
- Open WebUI 与 Spring 项目的 `OllamaController` 互不影响，两套入口都能用同一个 Ollama；
- **容器必须保留 `-e HF_HUB_OFFLINE=1`**，一旦去掉，国内网络下重启必卡启动（见第 28 条坑⑨）；
- **务必定期备份**：`kill -9` 事故曾导致本卷数据库被清空，账号/会话不可找回（见第 34.1 条）。

### 32.1 Portainer（Docker 图形管理界面）

> 浏览器端管理容器/镜像/卷/网络/日志的图形界面（Docker CE 默认无 UI）。当前使用**官方镜像** `portainer/portainer-ce:2.39.7`。官方镜像不带 `HEALTHCHECK`，因此 `docker ps` 里 portainer 只显示 `Up` 而没有 `(healthy)`——**这是官方镜像的正常行为，不是故障**。

**⚠️ 曾踩过的坑（历史，供参考）：**

1. **端口占用 → 永久 Exited(2)**：旧部署发布 `9000:9000 + 9443:9443` 且 `--restart always`，某次重启时 WSL 内 9000 被临时占用，docker 网络绑定失败导致容器一直死着；`restart=always` 对"启动期网络错误"不会重试，所以会一直 `Exited(2)`。
2. **scratch 镜像无 shell / 自建健康检查方案已废弃**：`portainer-ce` 是纯静态 Go 单文件镜像（无 `/bin/sh`）；早期曾尝试在 `portainer/portainer-ce:latest` 基础上拷贝 busybox + glibc（动态链接 `ld-linux-x86-64.so.2`）自建 `portainer-ce:2.39.5` 加健康检查，但**官方镜像本就不定义 HEALTHCHECK**，自建方案维护成本高且 `Dockerfile` 已随清理删掉——**已改回官方镜像，无健康检查属正常**。

**当前部署（官方镜像，只发布 9443，缩小端口冲突面）：**

```bash
docker run -d --name portainer --restart unless-stopped \
  -p 9443:9443 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v portainer_data:/data \
  portainer/portainer-ce:2.39.7
```

- 只有 **9443** 对外发布（HTTPS），容器内 8000/9000 不对外映射；
- 访问 **`https://localhost:9443`**（自签证书，浏览器提示"不安全"属正常，点继续）；首次访问设置 admin 密码；
- 数据卷 `portainer_data` 存自身配置，**重建不丢**（沿用旧账号）;
- `docker ps` 中 portainer **没有 `(healthy)` 标记属正常**，只要 `Up` 且 `https://localhost:9443` 能打开即可（API 自检见第 36/38 条）；
- 仅本地管理用，勿映射公网。

### 32.2 Milvus 向量数据库（v2.6.22 单容器 embed 部署）

> 供 Spring AI 2.0.1 做向量检索（RAG）。**版本必须 2.6.x**：`spring-ai-starter-vector-store-milvus:2.0.1` 编译依赖 `io.milvus:milvus-sdk-java:2.6.21`（v2 协议），官方兼容表是 2.6.x 服务器 ↔ 2.6.x SDK；Milvus 3.x 需 v3 SDK（v2/v3 proto 不互通），所以**不能用 3.0.x**。

**部署**（单容器内嵌 etcd + 本地存储，约 1~2GB 内存）：

```bash
# ① 配置文件（官方 standalone_embed.sh 同款参数）
mkdir -p /root/milvus
# /root/milvus/embedEtcd.yaml：
#   listen-client-urls: http://0.0.0.0:2379
#   quota-backend-bytes: 4294967296        # etcd 配额 4GB
#   auto-compaction-retention: '120000000'
# /root/milvus/user.yaml：占位文件（挂载需文件存在，缺失会挂成空目录导致解析失败）

# ② 启动
docker run -d --name milvus --restart unless-stopped \
  --security-opt seccomp:unconfined \
  -e ETCD_USE_EMBED=true \
  -e ETCD_DATA_DIR=/var/lib/milvus/etcd \
  -e ETCD_CONFIG_PATH=/milvus/configs/embedEtcd.yaml \
  -e COMMON_STORAGETYPE=local \
  -e DEPLOY_MODE=STANDALONE \
  -p 19530:19530 -p 9091:9091 \
  -v milvus:/var/lib/milvus \
  -v /root/milvus/embedEtcd.yaml:/milvus/configs/embedEtcd.yaml \
  -v /root/milvus/user.yaml:/milvus/configs/user.yaml \
  --health-cmd 'curl -f http://localhost:9091/healthz' \
  --health-interval 30s --health-timeout 10s --health-start-period 60s --health-retries 5 \
  milvusdb/milvus:v2.6.22 \
  milvus run standalone
```

- `19530`：gRPC 主链路（Spring AI 应用连这个）；`9091`：WebUI（`http://localhost:9091`）；
- `-v milvus:/var/lib/milvus`：**命名卷持久化**，删容器重建不丢向量数据；
- 镜像自带 curl，健康检查直接查 `:9091/healthz`，返回 `OK` 即 ready；
- 验证：`wsl -d Ubuntu -- docker ps` 显示 `(healthy)`，`curl http://localhost:9091/healthz` 返回 `OK`（2026-09-28 实测返回 `OK`）；
- **项目侧已接入**（不是"后续阶段"）：`spring-ai-application/pom.xml` 已引入 `spring-ai-starter-vector-store-milvus`（BOM 2.0.1），`application-dev/sit/prod.yaml` 已配 `spring.ai.vectorstore.milvus.*`（`localhost:19530`、库 `default`、集合 `vector_store`、`IVF_FLAT` + `COSINE`、1024 维）；**Milvus 容器不 healthy，应用就起不来**，见第 26.1 条；
- 国内拉镜像约 1.3GB，报 `connection reset` 直接重跑 `docker pull` 即可（已实测成功）。

### 32.3 MySQL 8.0（Spring AI 项目数据库）

```bash
docker run -d --name mysql8 --restart unless-stopped \
  -p 3306:3306 \
  -v /root/docker/mysql/logs:/var/log/mysql \
  -v /root/docker/mysql/conf/mysql.cnf:/etc/mysql/conf.d/mysql.cnf \
  -v /root/docker/mysql/data:/var/lib/mysql \
  -e MYSQL_USER=sap -e MYSQL_PASSWORD=sap123 \
  -e MYSQL_ROOT_PASSWORD=root123 -e MYSQL_DATABASE=sap_db \
  --health-cmd 'mysqladmin ping -h 127.0.0.1 -uroot -proot123 --silent' \
  --health-interval 30s --health-timeout 5s --health-start-period 60s --health-retries 3 \
  mysql:8.0
```

- 数据在**绑定挂载** `/root/docker/mysql/data`（不是命名卷），备份直接拷该目录；
- `/root/docker/mysql/conf/mysql.cnf` 关键项：`utf8mb4` / `utf8mb4_unicode_ci`、时区 `+08:00`、`max_connections=200`、`mysql_native_password`、慢查询日志 `>2s`（落在挂载的 `/var/log/mysql/slow.log`）；
- 健康检查 `mysqladmin ping`：服务起来且 root 可连通才显示 `(healthy)`；
- 重建必须带齐 `-v` 三个挂载与四个 `MYSQL_*` 环境变量，否则数据/账号不完整；
- **建表脚本**（`sys_user.sql` / `user_address.sql`）导入方式见第 26.3 条；`SPRING_AI_CHAT_MEMORY` 由应用自动建。

### 32.4 Attu（Milvus 图形管理界面）

> 官方推荐的可视化工具，用来浏览集合、看 schema/索引/数据。本机已部署（`http://localhost:8000`），容器内跑在 3000。

```bash
docker run -d --name attu --restart unless-stopped \
  -p 8000:3000 \
  -e MILVUS_URL=host.docker.internal:19530 \
  --add-host host.docker.internal:host-gateway \
  zilliz/attu:v2.6.5
```

- `-p 8000:3000`：浏览器 `localhost:8000`，容器内 3000；
- `MILVUS_URL=host.docker.internal:19530` + `--add-host host.docker.internal:host-gateway`：容器去连 Milvus。**注意它连的是宿主映射端口，不是容器名**——Milvus 与 Attu 都在默认 `bridge` 网络上，理论上可以 `http://milvus:19530` 直连，但用宿主端口更省事（换网络模式也不用改）；
- **无数据卷**：Attu 不存业务数据（连接信息存浏览器 localStorage），删了重建不影响 Milvus；
- **官方镜像无 HEALTHCHECK**，`docker ps` 里只有 `Up` 没有 `(healthy)`，属正常；
- 打开后在界面里填 Milvus 地址（`localhost:19530`）即可看到 `vector_store` 集合。

> 当前六个容器：`ollama`(11434)、`open-webui`(3000)、`portainer`(9443, HTTPS)、`mysql8`(3306)、`milvus`(19530/9091)、`attu`(8000)，全部 `--restart unless-stopped`；其中 ollama / open-webui / mysql8 / milvus 带健康检查，**portainer 与 attu 官方镜像无健康检查，`Up` 即正常**。

---

## 九、事故记录与恢复（kill -9 的教训）

### 33. 事故经过

为了把 dockerd 从手动实例切到 systemd 托管，执行了 `pkill -9 dockerd` **强杀**守护进程。

**后果**：

1. `docker start ollama` 报错：
   ```
   Error response from daemon: RWLayer of container 45c3c956131f... is unexpectedly nil
   ```
2. `docker images` 镜像记录全部丢失（虽然磁盘上数据仍在），需要重新 `docker pull`（8GB）。

### 34. 恢复流程

```bash
docker rm -f ollama                 # 删掉损坏容器（模型在 volume 里，不丢）
docker pull ollama/ollama           # 重拉镜像（约 8GB）
docker run -d --name ollama --restart unless-stopped \
  --device /dev/dxg \
  -e OLLAMA_HOST=0.0.0.0:11434 \
  -e HSA_OVERRIDE_GFX_VERSION=11.0.2 \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  --health-cmd 'OLLAMA_HOST=127.0.0.1:11434 ollama list >/dev/null 2>&1' \
  --health-interval 30s --health-timeout 10s --health-start-period 30s --health-retries 3 \
  ollama/ollama
docker exec ollama ollama list      # 确认 deepseek-r1:8b 还在（5.2GB，未重下）
```

**经验**：`-v ollama:/root/.ollama` 卷保住了 5.2GB 模型，重建容器零损失。Docker 正常停启**必须**用 `systemctl stop/restart docker`，禁用 `kill -9 dockerd`。

### 34.1 二次事故：Open WebUI 数据库被清空（kill -9 的间接后果）

**现象**：强杀恢复后发现 open-webui 的 `webui.db`（/app/backend/data 卷内）里的 `user`/`auth`/`chat`/`message` 等表**全部为空**，账号与历史会话丢失，只能重新注册。

**原因推测**：`kill -9 dockerd` 导致卷内 SQLite 数据库文件损坏/被重置（`webui.db-wal` 为 0 字节，无回滚数据可恢复）。

**恢复**：无备份无法找回，直接访问 `http://localhost:3000` 重新注册即可（本地界面）。

**教训**：
1. `kill -9` 不仅坏容器 RWLayer，还可能损坏**卷内数据库**——危害比预想更大；
2. 账号/会话类数据**务必定期备份**：`wsl -d Ubuntu -- bash -lc 'docker run --rm -v open-webui:/data -v $(pwd):/backup alpine tar czf /backup/openwebui-$(date +%F).tar.gz -C /data .'`
3. Ollama 模型在 `-v ollama:/root/.ollama` 卷中未受损（本次零损失），Open WebUI 数据则没那么幸运，两者备份策略不同。

---

## 十、日常使用

### 35. WSL 空闲自动停机与启动方式（坑⑩：关窗口就关机）

**现象**：WSL 有两个独立的"空闲计时器"，关掉最后一个终端/控制台窗口后，实例会按各自的超时被终止（默认仅 15 秒/60 秒），容器随之中断。之前只改 `vmIdleTimeout` 无效，因为真正先触发的是会话级 `instanceIdleTimeout`。

**根因**：两个计时器必须**同时关闭**（`C:\Users\YU\.wslconfig`，与第 4 条是同一份文件）：

```ini
[general]
instanceIdleTimeout=-1

[wsl2]
networkingMode=mirrored
memory=24GB
swap=4GB
processors=8
vmIdleTimeout=-1
```

改完执行 `wsl --shutdown` 生效。已实测：关闭所有终端后等 90 秒以上，实例与全部容器仍保持 Running（空闲保活与 `--restart unless-stopped` 配合）。

**开机自启链路（已配置）**——电脑开机自动拉起 WSL + 全部容器，无需手动：

1. `C:\Users\YU\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Startup\Docker-AutoStart.vbs`：开机延时 60 秒待网络就绪，非阻塞触发 `wsl.exe -d Ubuntu -- bash /root/.docker-autostart.sh`
2. WSL 启动后 systemd 自动拉起 docker（`systemctl is-enabled docker` = enabled）
3. `/root/.docker-autostart.sh`：轮询等 docker 就绪（最多 120 秒）后执行 `docker ps -aq | xargs -r docker start`，一次拉起全部 6 个容器

> 手动启动全栈命令（与自启脚本等效）：`wsl -d Ubuntu -u root bash /root/.docker-autostart.sh`

**为什么自启脚本必须显式 `docker start`（关键坑⑪）**：`--restart unless-stopped` 只自动恢复**意外退出**（崩溃/OOM）的容器；而优雅关机流程用的是 `docker stop`，会先把容器标记为"手动停止"，Docker 再启动时**不会**碰它们。若自启脚本只等 docker 就绪就退出（旧版脚本的缺陷），优雅关机后再开机会发现容器全停着、`docker ps` 为空。已实测修复：2026-09-25 优雅关机 → 关电脑 → 开机只有 docker.service 起来、6 容器全 Exited；在脚本追加 `docker start` 后一次全恢复。

### 35.1 优雅关机（推荐，防 Milvus 崩溃）

**为什么必须优雅关**：直接 `wsl --shutdown`（或断电/强关电脑）会**硬杀 WSL 虚拟机**，内嵌 Docker 里的容器来不及收尾。Milvus 的 embed etcd 在写 WAL 时被打断会损坏数据 → 下次开机 `--restart unless-stopped` 自动拉起后崩溃循环（`Restarting (134)`、`etcdserver: leader changed` panic），应用侧报 `milvusClient` 初始化 `DEADLINE_EXCEEDED`；元数据损坏严重时只能清空 `/var/lib/milvus/etcd` 重建（向量集合丢失）。已实测出坑，2026-09-24 断电重启必现。

**原则**：先停容器（SIGTERM 等它优雅收尾）→ 停 Docker daemon（防 socket 拉活）→ 再关 WSL VM（电源在 Windows 侧，`systemctl poweroff` 无效）。

**Windows PowerShell（已验证）**：

```powershell
# ① 优雅停所有容器（逐个 SIGTERM，默认等 10 秒收尾，超时才强杀）
wsl -d Ubuntu -u root sh -c "docker ps -aq | xargs -r docker stop"
# ② 停 Docker daemon（防 docker.socket 又把服务拉活）
wsl -d Ubuntu -u root systemctl stop docker
# ③ 从 Windows 侧优雅关 WSL 虚拟机
wsl --terminate Ubuntu
```

**terminal / cmd**：

```cmd
wsl -d Ubuntu -u root sh -c "docker ps -aq | xargs -r docker stop && systemctl stop docker"
wsl --terminate Ubuntu
```

**开机恢复**：

```powershell
# WSL 已关闭：冷启动 + 等 docker 就绪 + 拉起全部容器（推荐，与开机自启同款）
wsl -d Ubuntu -u root bash /root/.docker-autostart.sh

# WSL / docker 已在运行、只容器停了：免等 docker，直接拉起
wsl -d Ubuntu -u root sh -c "docker ps -aq | xargs -r docker start"
```

> 优雅关机后**不要**以为只执行 `wsl -d Ubuntu` 就能自动恢复容器（`unless-stopped` 不管手动停止的容器，见坑⑪）。要么跑上面的自启脚本，要么直接重启电脑走 `Docker-AutoStart.vbs` 自启链路。跑完后 `docker ps` 的 6 个容器应在 30 秒内全部 `Up (healthy)`。

**坑（均已实测）**：
- **`$(docker ps -aq)` 写法在 PowerShell 会被宿主抢先求值**（Windows 无 docker 命令 → CommandNotFoundException，命令变形），在 cmd 里多容器时 `$()` 换行输出会被 sh 拆开执行（只停第一个容器）。**必须用 `docker ps -aq | xargs -r docker stop`**（逐行喂参，无容器时 `-r` 直接跳过）。
- **`systemctl poweroff` 在 WSL 内无效**：执行后 `wsl -l -v` 仍 `Running`（uptime 递增）。WSL VM 电源只能在 Windows 侧控制，用 `wsl --terminate Ubuntu`。
- `wsl --terminate` 后**不要再立刻执行任何 `wsl -d Ubuntu ...` 命令**——会触发冷启动把 VM 拉回来。
- 校验关机结果：`wsl -l -v` 应显示 `Stopped`（而非 `Running`）。

### 36. 快速验证

```powershell
curl http://localhost:11434        # 返回 "Ollama is running" 即正常
curl http://localhost:3000         # 返回 HTML 即 Open WebUI 正常
curl http://localhost:9091/healthz # Milvus：返回 OK 即正常
curl.exe -sk https://localhost:9443/api/status   # Portainer：返回 {"Version":"2.39.7",...} 即正常
curl http://localhost:8000         # Attu：返回 HTML 即正常
wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'   # ollama/mysql8/milvus/open-webui 应为 Up (healthy)，portainer/attu 为 Up（官方镜像无健康检查）
```

2026-09-28 实测输出（供对照）：

```
portainer   portainer/portainer-ce:2.39.7            Up 7 hours              8000/tcp, 9000/tcp, 0.0.0.0:9443->9443/tcp
attu        zilliz/attu:v2.6.5                      Up 7 hours              0.0.0.0:8000->3000/tcp
ollama      ollama/ollama                           Up 7 hours (healthy)    0.0.0.0:11434->11434/tcp
mysql8      mysql:8.0                               Up 7 hours (healthy)    0.0.0.0:3306->3306/tcp
milvus      milvusdb/milvus:v2.6.22                 Up 7 hours (healthy)    0.0.0.0:9091->9091/tcp, 0.0.0.0:19530->19530/tcp
open-webui  ghcr.io/open-webui/open-webui:main      Up 7 hours (healthy)    0.0.0.0:3000->8080/tcp
```

| Spring 应用侧（**非 Docker，需单独启动**） | `curl.exe -sk http://localhost:8081/health` → MCP Server 起了才有响应；`curl http://localhost:8080/swagger-ui.html` → 主应用；一键自检脚本见第 27 条 |

### 37. 日常对话

```powershell
# 推荐：浏览器网页对话（Open WebUI）
#   打开 http://localhost:3000，选模型 deepseek-r1:8b 即可

# 命令行非交互（一条命令）
Invoke-RestMethod -Uri http://localhost:11434/api/chat -Method Post -ContentType 'application/json' -Body '{"model":"deepseek-r1:8b","messages":[{"role":"user","content":"你好"}],"stream":false}' | Select-Object -ExpandProperty message

# 交互模式（进入对话界面，输入 /bye 退出）
wsl -d Ubuntu -- docker exec -it ollama ollama run deepseek-r1:8b
```

### 38. 常用管理命令

| 用途 | 命令 |
|------|------|
| 手动恢复全栈（WSL 已关,冷启动+容器） | `wsl -d Ubuntu -u root bash /root/.docker-autostart.sh`（开机自启同款，等 docker 就绪后 start 全部） |
| 手动恢复容器（WSL/docker 已在运行） | `wsl -d Ubuntu -- sh -c "docker ps -aq \| xargs -r docker start"`（等价自启脚本后半段，不用等 docker） |
| 开机后 `docker ps` 无容器（空列表） | 可能是优雅关机 `docker stop` 把容器标记为手动停止，`unless-stopped` 不恢复 → 执行 `wsl -d Ubuntu -u root bash /root/.docker-autostart.sh` 或 `wsl -d Ubuntu -u root sh -c "docker ps -aq \| xargs -r docker start"`（见坑⑪） |
| 看 WSL 实例状态 | `wsl -l -v`（Running / Stopped） |
| 看容器状态/健康 | `wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'` |
| 看全部（含退出） | `wsl -d Ubuntu -- docker ps -a` |
| 手动启容器 | `wsl -d Ubuntu -- docker start ollama` |
| 启 Milvus / MySQL / Portainer / Attu | `wsl -d Ubuntu -- docker start milvus`（同理 `mysql8` / `portainer` / `attu`） |
| 启 Open WebUI | `wsl -d Ubuntu -- docker start open-webui` |
| 看容器日志 | `wsl -d Ubuntu -- docker logs ollama` |
| 看 Open WebUI 日志 | `wsl -d Ubuntu -- docker logs open-webui` |
| Milvus 健康检查 | `wsl -d Ubuntu -- bash -lc 'curl -s http://localhost:9091/healthz'` |
| Portainer API 检查 | `curl.exe -sk https://localhost:9443/api/status` |
| 看模型列表 | `wsl -d Ubuntu -- docker exec ollama ollama list` |
| 下载/换模型 | `wsl -d Ubuntu -- docker exec ollama ollama pull <模型名>` |
| 删模型 | `wsl -d Ubuntu -- docker exec ollama ollama rm deepseek-r1:8b` |
| 看推理设备 | `wsl -d Ubuntu -- docker exec ollama ollama ps` |
| 看内存占用 | `wsl -d Ubuntu -- docker stats` |
| 清理悬空镜像 | `wsl -d Ubuntu -- docker image prune -f` |
| 清理孤立卷 | `wsl -d Ubuntu -- docker volume prune -f` |
| 清理构建缓存 | `wsl -d Ubuntu -- docker builder prune -f` |
| 一键清理全部无用资源 | `wsl -d Ubuntu -- docker system prune -af --volumes`（⚠️ 慎用，会删掉所有无容器引用的镜像/卷/缓存） |
| 全面优雅关机（推荐） | PowerShell：`wsl -d Ubuntu -u root sh -c "docker ps -aq \| xargs -r docker stop"` → `systemctl stop docker` → `wsl --terminate Ubuntu`（详见 35.1） |
| 查看 WSL 状态 | `wsl -l -v`（Graceful 关后应为 `Stopped`；`--shutdown` 是硬杀，避免） |

### 39. 常见故障排查表

| 症状 | 检查与修复 |
|------|-----------|
| `localhost:11434` 连不上 | `wsl -d Ubuntu -- docker ps`；容器不在就 `docker start ollama`；报错先看 `docker logs ollama` |
| `curl` 报 "找不到位置形式参数" | 把 `curl` 换成 `curl.exe` 或 `Invoke-RestMethod` |
| 对话返回 JSON 解析错误 | 双引号被 PowerShell 吃掉，改用 `Invoke-RestMethod` 或 `curl.exe --%` |
| 模型缺失 | `wsl -d Ubuntu -- docker exec ollama ollama list`；缺则重 `ollama pull deepseek-r1:8b`；注意 `pull-model-strategy: never`，应用不会自动拉 |
| 端口 11434 被占 | `netstat -ano | findstr 11434` 查占用，检查是否有第二个 ollama 容器 |
| `localhost:9091`（Milvus）不通 | `wsl -d Ubuntu -- docker start milvus`；确认 19530/9091 已映射；仍不行看 `docker logs milvus` |
| portainer `Exited(2)` | 端口被占导致网络绑定失败 → 按第 32.1 条重建（`docker run` 只发布 9443，`portainer_data` 卷不丢） |
| portainer / attu 没有 `(healthy)` | 官方镜像无 HEALTHCHECK，属正常；portainer 用 `curl.exe -sk https://localhost:9443/api/status` 自检，attu 直接开 `http://localhost:8000` |
| milvus `docker pull` 报 connection reset | 镜像源单层重置，直接重跑 `docker pull milvusdb/milvus:v2.6.22`（已实测重试成功） |
| 容器重建后模型不见了 | 检查是否用了 `-v ollama:/root/.ollama`；若用了旧卷，`ollama pull` 秒回因为已有缓存则正常 |
| 项目 `/ollama/chat` 报模型不存在 | yaml 默认已是 `deepseek-r1:8b`（与本地一致）；若你换过模型（如 7b），要在 `.env` 加 `OLLAMA_CHAT_MODEL`，见第 26.2 条 |
| 项目 `/ollama/chat` 返回 500 且日志是 `MalformedInputException` | PowerShell 把中文 URI 又解码了一次，不是应用 bug；改用浏览器访问，或 `curl.exe` + `[uri]::EscapeDataString()`，见第 26.5 条 |
| **应用启动失败 `Client failed to initialize by explicit API call` / `Connection refused: localhost:8081`** | **MCP Server（`spring-ai-mcp-server-webflux`）没起**。先 `.\mvnw.cmd spring-boot:run -pl spring-ai-mcp-server-webflux` 起 8081，再起主应用。详见第 26.1 条 |
| 应用启动报 `Could not resolve placeholder 'MYSQL_HOST'` | `.env` 缺 `MYSQL_HOST/MYSQL_USER/MYSQL_PASSWORD/MYSQL_DATABASE`，见第 26.2 条 |
| 应用启动报 MySQL 连不上 | `docker start mysql8` 等 `Up (healthy)`；确认 `.env` 里 `MYSQL_USER/PASSWORD/DATABASE` 与容器一致（`sap`/`sap123`/`sap_db`） |
| `/rag/*` 报嵌入/维度错误 | 嵌入走 OpenRouter `voyageai/voyage-4`（1024 维），需要外网 + `OPENROUTER_API_KEY`；换模型要同步改 `embedding-dimension`，见第 26.6 条 |
| MCP stdio 工具（bing/mysql）初始化失败 | 需宿主机有 Node.js/npx 且能访问 npm 源；检查 `mcp/mcp-servers.json` 与 `npx -y` 包名 |
| milvus `Restarting (134)` 崩溃循环 | 意外断电/硬关 WSL 损坏 embed etcd WAL（日志：`etcdserver: leader changed` / `context canceled` panic）。先 `wsl -d Ubuntu -- docker stop milvus`，备份 `/var/lib/milvus/etcd` 后删除该目录让元数据重建（向量集合需重灌） |
| 项目启动报 `milvusClient` DEADLINE_EXCEEDED | 通常不是配置问题，是 Milvus 容器起不来（崩循环/未启动）→ `docker ps` 看 milvus 状态，先 `docker start milvus` 等 `Up (healthy)` 再启应用 |
| WSL 内存不足 | 改 `.wslconfig` 后 `wsl --shutdown` 生效 |
| 关掉终端后 WSL 自动关机 | `.wslconfig` 里 `[general] instanceIdleTimeout=-1` 与 `[wsl2] vmIdleTimeout=-1` 都要设，见第 35 条 |
| Windows 侧访问不到容器端口 | 确认 `.wslconfig` 有 `networkingMode=mirrored`（改完要 `wsl --shutdown`）；否则靠 WSL NAT 转发，先 `wsl -l -v` 确认实例是 `Running` |

---

## 十一、注意事项

1. **PowerShell 命令纪律**：调 HTTP 一律 `curl.exe`（或 `Invoke-RestMethod`），**永远不要**用 `curl`（别名 `Invoke-WebRequest`，参数不同）。
2. **禁用 `kill -9 dockerd`**：会损坏容器 RWLayer 与镜像记录。停/启 Docker 统一用 `systemctl stop|restart docker`。
3. **重建容器必须保留 `-v ollama:/root/.ollama`**，否则 5.2GB 模型要重新下载。
4. **项目默认模型已是 `deepseek-r1:8b`**：`application-dev/sit/prod.yaml` 里 `OLLAMA_CHAT_MODEL` 默认值就是 `deepseek-r1:8b`，与本地已装模型一致，**当前 `.env` 无需加任何 Ollama 配置**（2026-09-28 实测跑通）。只有换模型才要显式设 `OLLAMA_CHAT_MODEL`，否则报模型不存在。
5. **WSL 后台任务会被杀**：`wsl -d Ubuntu -- bash -lc "nohup ... &"` 的进程随会话退出而终止。长时间任务（模型下载等）要**前台保持窗口**，或用 `tmux`。
6. **GPU 现状与加速**：`/dev/dxg` 有、`/dev/kfd` 无（官方 WSL 内核 `CONFIG_HSA_AMD` 关闭）→ 当前 CPU 推理（约 10 token/s）。要 GPU 需：更新 AMD 驱动（≥ Adrenalin 24.1.1）→ 让 WSL 出现 `/dev/kfd`（新内核或自编译带 `HSA_AMD` 的内核）→ 换 `ollama/ollama:rocm` 并挂载两个设备，详见第 22 条。
7. **国内网络**：Docker 镜像加速列表（daemon.json）可能失效需更换；Ollama 模型走自身源，慢但可断点续传，重跑 `ollama pull` 即可。
8. **内存与磁盘**：8b 推理时 `.wslconfig` 分配 24GB 足够（`docker stats` 可观察实际占用）。2026-09-28 实测占用：镜像合计 **18.36GB**（ollama 镜像 8.04GB + open-webui 7.16GB + mysql 1.1GB + milvus 1.3GB + portainer 188MB + attu 565MB），卷合计 **5.67GB**（ollama 4.9G / open-webui 267M / milvus 301M / portainer 500K），WSL 根分区（D 盘）已用 32GB。清理用 `docker system prune`。
9. **端口安全**：11434（Ollama）只作本机使用，勿暴露公网（无鉴权）；3000（Open WebUI）有账号体系，但也只在本机使用，勿映射公网。
10. **systemd-firstboot 已 mask**：不要取消，否则重启再次卡死。
11. **配置生效**：`.wslconfig`、`/etc/wsl.conf` 改动后必须 `wsl --shutdown` 再重进；daemon.json 改动后 `systemctl restart docker`。
12. **中文乱码**：PowerShell 看中文输出先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`；**中文 URL 不要用 `Invoke-RestMethod` 直传**（会被二次解码导致 Tomcat 500，见第 26.5 条）。
13. **日志位置**：dockerd 手动启动日志 `/var/log/dockerd.log`（systemd 托管后走 `journalctl -u docker`）；容器日志 `docker logs ollama` / `docker logs open-webui`；模型下载日志 `/var/log/ollama-pull.log`；MCP Server 日志 `D:\JavaProjects\SpringAiPractice\logs\mcp-server-webflux.log`（`mvn spring-boot:run` 时工作目录是多模块根）。
14. **不要用 PowerShell `Set-Content`/`Out-File` 写带中文注释的 Java 文件**：UTF-8 中文会被破坏，Java 源码一律用编辑器/IDE 写入。
15. **Open WebUI 容器必须带 `-e HF_HUB_OFFLINE=1`**：国内网络下 huggingface.co 的联网校验会卡死启动（坑⑨，见第 28 条）；去掉后重建容器必复现。
16. **WSL 空闲保活**：`.wslconfig` 里 `[general] instanceIdleTimeout` 与 `[wsl2] vmIdleTimeout` 都必须设 `-1`，只改一个无效（坑⑩，见第 35 条）。
17. **定期备份 open-webui 卷**：`kill -9` 事故曾清空其数据库（账号/会话不可找回，见第 34.1 条）；备份命令见第 30 条。Ollama 模型卷含 5.2GB 模型，一般无需备份。
18. **Milvus 版本锁定 2.6.x**：Spring AI 2.0.1 的 milvus starter 依赖 v2 SDK（`milvus-sdk-java:2.6.21`），服务端只能用 2.6.x，勿升 3.x（第 32.2 条）。
19. **portainer 用官方镜像** `portainer/portainer-ce`（当前 2.39.7）：官方镜像不带 HEALTHCHECK，`docker ps` 无 `(healthy)` 标记**属正常**；此前的自建健康检查镜像方案已废弃。重建命令见第 32.1 条。
20. **portainer 只发布 9443**：容器内 9000/8000 不对外映射；再出 `Exited(2)` 先查端口占用（第 32.1 条）。主机 8000 端口现由 Milvus 管理界面 attu 占用，勿与 portainer 混淆。
21. **定期清理 Docker 垃圾**：`docker image prune -f`、`docker volume prune -f`、`docker builder prune -f`；`docker system prune -af --volumes` 会删所有无引用的镜像/卷/缓存，先确认不需要。
22. **关机一律走优雅流程**（见 35.1）：先 `docker stop` 全部容器 → `systemctl stop docker` → `wsl --terminate Ubuntu`。**禁止**直接 `wsl --shutdown` / 断电关电脑，否则 Milvus embed etcd WAL 损坏 → 崩循环 → 元数据需重建（向量数据丢失）。`systemctl poweroff` 在 WSL 内无效。

23. **`--restart unless-stopped` 只恢复"意外退出"的容器（坑⑪）**：容器被 `docker stop` 优雅停止后带"手动停止"标记，Docker 重启**不会**自动拉起它。所以优雅关机后的"开机自动恢复"由 `/root/.docker-autostart.sh` 里的 `docker ps -aq | xargs -r docker start` 完成（VBS 自启链路见第 35 节），不要指望 `unless-stopped`。已实测：优雅关机 → 关机 → 开机只有 docker.service 起来、6 容器全 Exited、`docker ps` 为空；脚本修复后一次全恢复。
24. **Spring 应用不属于 Docker 自启链路**：开机自启只拉起 6 个容器，**两个 Spring 应用（8081 MCP Server、8080 主应用）不会自动启动**，需要时手动 `mvnw.cmd spring-boot:run -pl <模块>`（见第 26.4 条）。
25. **`.wslconfig` 要开 `networkingMode=mirrored`**：镜像网络下 Windows `localhost` 直达 WSL 内服务（容器 `-p` 端口），这是项目 yaml 里 `localhost:11434` / `localhost:19530` / `localhost:3306` / `localhost:8081` 都能直接用的前提；改动后必须 `wsl --shutdown`（见第 4 条）。
26. **`.env` 必含 4 个 `MYSQL_*`**：`ChatClientConfig` 用 `@Value("${MYSQL_HOST}")` 等拼 MySQL MCP 提示词、datasource 取 `${MYSQL_PASSWORD}`，缺任一项应用起不来（见第 26.2 条）。

---

## 附录：关键文件与脚本清单

| 路径 | 用途 |
|------|------|
| `D:\WSL\Ubuntu` | **WSL 发行版根目录**（导入位置，在 D 盘，避免占满 C 盘） |
| `C:\Users\YU\.wslconfig` | WSL2 内存/交换/CPU + **镜像网络** + 空闲保活 |
| `/etc/wsl.conf`（WSL 内） | systemd 开关、默认用户 |
| `/etc/apt/sources.list.d/ubuntu.sources` | 清华 apt 源 |
| `/etc/apt/keyrings/docker.gpg` | Docker 官方 GPG key（二进制，dearmor 后） |
| `/etc/docker/daemon.json` | 镜像加速 + 日志策略 |
| `/usr/sbin/policy-rc.d` | 屏蔽 dpkg 自动启动服务（exit 101） |
| `/run/user/0` | LXC rootfs 缺失需手工创建（见第 13 条） |
| `/etc/systemd/system/systemd-firstboot.service -> /dev/null` | **firstboot 已 mask，勿取消**（见第 17 条） |
| `/var/lib/docker/volumes/ollama/_data` | **Ollama 模型数据（5.2GB，勿删）** |
| `/var/lib/docker/volumes/open-webui/_data` | **Open WebUI 数据**（账号/会话/上传文件/RAG 库/嵌入模型缓存） |
| `/var/lib/docker/volumes/milvus/_data` | **Milvus 向量数据（命名卷，勿删）** |
| `/var/lib/docker/volumes/portainer_data/_data` | Portainer 自身配置/账号（重建不丢） |
| `/root/milvus/embedEtcd.yaml`、`user.yaml` | Milvus embed 模式 etcd 配置（**运行中容器挂载，勿删**） |
| `/root/docker/mysql/data`（绑定挂载） | **MySQL 数据（勿删，备份直接拷此目录）** |
| `/root/docker/mysql/conf/mysql.cnf` | MySQL 配置（utf8mb4 / +08:00 / 慢查询） |
| `/root/backups/` | 容器卷备份存放处（已有 `portainer_data_pre-migrate-2026-09-19.tar.gz`） |
| `/var/log/dockerd.log`、`/var/log/ollama-pull.log` | 运行日志 |
| `D:\JavaProjects\SpringAiPractice\.env` | 项目对接配置（git 忽略，7 个 key） |
| `C:\Users\YU\AppData\Roaming\...\Startup\Docker-AutoStart.vbs` | **开机自启入口**：延时 60s 后调用 `/root/.docker-autostart.sh` |
| `/root/.docker-autostart.sh`（WSL 内） | **自启脚本**：等 docker 就绪后 `docker start` 全部容器 |
| `D:\JavaProjects\SpringAiPractice\spring-ai-application\src\main\resources\static\knowledgeBase\` | **本文档所在目录** |
| `...\spring-ai-application\src\main\resources\sql\sys_user.sql`、`user_address.sql` | MySQL 建表脚本（导入方式见第 26.3 条） |
| `...\spring-ai-application\src\main\resources\mcp\mcp-servers.json` | stdio MCP 配置（bing / mysql，走宿主机 npx） |
| `...\spring-ai-mcp-server-webflux\src\main\resources\application.yaml` | MCP Server 配置（端口 8081、STREAMABLE、`/mcp`） |
| `D:\JavaProjects\SpringAiPractice\logs\mcp-server-webflux.log` | MCP Server 运行日志 |

> 当前运行容器：`ollama`（11434，模型服务）、`open-webui`（3000，网页界面）、`portainer`（9443，HTTPS 管理界面）、`mysql8`（3306，项目数据库）、`milvus`（19530/9091，向量库）、`attu`（8000，Milvus 管理界面）。六个均 `--restart unless-stopped`；其中 ollama / open-webui / mysql8 / milvus 带健康检查显示 `(healthy)`，**portainer 与 attu 官方镜像无 HEALTHCHECK，`Up` 即正常**，WSL 启动后由 systemd 托管 Docker 自动拉起（Spring 应用不在此链路内）。

> 部署/排查过程的临时脚本与自建 portainer 镜像均已清理，现统一使用官方镜像 `portainer/portainer-ce`（重建命令见第 32.1 条）。
