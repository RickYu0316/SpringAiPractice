# WSL2 + Docker + Ollama + DeepSeek 本地部署详细流程

> 适用环境：Windows 11 + AMD 显卡（RX 7600M XT，RDNA3）+ 国内网络。
> 目标：在 WSL2 的 Docker 里运行 Ollama，加载 `deepseek-r1:7b`，供本机 Spring Ai 项目本地推理。
> 本文基于**实际部署过程**整理，所有坑都按「现象 → 原因 → 解决」记录，已实测跑通。

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

- WSL2 版本 2.7.11，Ubuntu 24.04 LTS（noble）
- Docker Engine 29.7.1（由 systemd 托管，开机自启）
- Ollama 0.32.5 容器 + `deepseek-r1:7b` 模型（4.7GB）
- **Open WebUI** 图形界面已部署（`http://localhost:3000`，浏览器网页对话）
- 由于 WSL 内缺 `/dev/kfd`，**ROCm GPU 加速不可用，走 CPU 推理**，实测约 11.6 token/s
- 开机后 Docker、Ollama、Open WebUI 容器**全部自动恢复**，无需手动操作

### 国内网络要点速览

| 资源 | 可达性 | 解决方案 |
|------|--------|----------|
| 微软商店 / `wsl --update` | 卡 0% | ghfast.top 代理手动下载 MSI |
| `wsl --install -d Ubuntu` | 卡死 | 清华 TUNA LXC rootfs 导入 |
| Docker Hub | 极慢/不通 | daemon.json 配置 4 个国内镜像加速 |
| Ollama 模型源 | 慢但可下载 | 断点续传，中断重跑即可 |

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
# 下载 WSL 2.7.11 安装包（约 247MB），经 ghfast.top 代理转发 GitHub 资源
Invoke-WebRequest -Uri "https://ghfast.top/https://github.com/microsoft/WSL/releases/download/2.7.11/wsl.2.7.11.0.x64.msi" -OutFile "$env:TEMP\wsl.2.7.11.0.x64.msi"
# 双击安装，或：
Start-Process msiexec -ArgumentList '/i', "$env:TEMP\wsl.2.7.11.0.x64.msi", '/quiet'
```

**验证**：

```powershell
wsl --version
# WSL 版本：2.7.11.0
# 内核版本：6.18.33.2-microsoft-standard-WSL2
```

> 若该版本链接失效，去 GitHub Releases 页找更新版本，套用 `https://ghfast.top/<原始github下载链接>` 即可。

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

### 4. 给 WSL2 分配内存

编辑 `C:\Users\YU\.wslconfig`（不存在则新建）：

```
[wsl2]
memory=24GB
swap=4GB
processors=8
```

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

## 五、安装 Ollama + deepseek-r1:7b

### 19. 拉取镜像

```bash
docker pull ollama/ollama    # 约 8GB，走镜像加速；中断重跑可续传
```

### 20. 运行容器

```bash
docker run -d --name ollama --restart unless-stopped \
  --device /dev/dxg \
  -e HSA_OVERRIDE_GFX_VERSION=11.0.2 \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  ollama/ollama
```

要点解释：

- `--device /dev/dxg`：WSL2 的 GPU paravirtualization 设备，**不是** Docker Desktop 的 `--gpus all`（后者在此场景无效）。
- `-e HSA_OVERRIDE_GFX_VERSION=11.0.2`：给 AMD RDNA3（RX 7600M XT）强制指定 ROCm 架构。
- `-v ollama:/root/.ollama`：**模型数据持久化卷，必须保留**，删容器不丢模型。
- `--restart unless-stopped`：Docker 重启时容器自动拉起，配合 systemd 实现开机自愈。
- `-p 11434:11434`：暴露 Ollama API 端口供 Windows 侧访问。

### 21. 下载模型（后台执行 + 断点续传）

国内访问 Ollama 模型源较慢（实测约 400KB/s，4.7GB 全程约 1.5 小时），建议后台执行并写日志：

```bash
nohup docker exec ollama ollama pull deepseek-r1:7b > /var/log/ollama-pull.log 2>&1 &
# 查看进度
tail -f /var/log/ollama-pull.log
docker exec ollama ollama list    # 出现 deepseek-r1:7b  4.7GB 即完成
```

- 中途断了**直接重跑 pull 即可**，Ollama 自动断点续传。
- ⚠️ WSL 后台任务会随「最后一个前台进程退出」而被终止：长时间任务要么保持窗口打开，要么用 `tmux`/`screen` 挂起。

### 22. GPU 确认（实测：走 CPU）

```bash
ls -l /dev/dxg /dev/kfd    # 实测：dxg 存在，kfd 不存在
docker exec ollama ollama ps
# NAME            PROCESSOR
# deepseek-r1:7b  100% CPU
```

- `/dev/dxg`（WSL GPU 半虚拟化）存在 → 基础可探测 GPU；
- `/dev/kfd`（ROCm 需要）缺失 → **ROCm GPU 加速不可用**，Ollama 自动退回 CPU；
- 7840H 8 核 CPU 实测约 **11.6 token/s**（80 tokens / 6.9s），对 7b 模型可用。

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
> - 7600M XT 仅 8GB 显存，7b 模型默认量化（Q4_K_M ≈ 4.7GB）刚好装下；更大的模型需更低量化或换模型。
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
curl.exe http://localhost:11434/api/chat -d '{"model":"deepseek-r1:7b",...}'
# 返回 {"error":"invalid character 'm' looking for beginning of object key string"}
```

**原因**：PowerShell 5.1 把参数传给原生程序时会**吃掉内部双引号**，JSON 变成 `{model:...}`（key 无引号），Ollama 的 Go 解析器在 `{` 后第一个字符 `m` 处报错。单引号只在 PowerShell 解析层有效，救不了传给 curl.exe 的引号。
**解决**：见下一条推荐命令。

### 25. 正确的测试命令（首选）

```powershell
Invoke-RestMethod -Uri http://localhost:11434/api/chat -Method Post -ContentType 'application/json' -Body '{"model":"deepseek-r1:7b","messages":[{"role":"user","content":"1+1等于几？"}],"stream":false}' | Select-Object -ExpandProperty message
```

**备选**：

```powershell
# 方案 B：curl.exe + --% 停止 PowerShell 解析，之后原样传给 curl
curl.exe http://localhost:11434/api/chat -d --% "{\"model\":\"deepseek-r1:7b\",\"messages\":[{\"role\":\"user\",\"content\":\"1+1等于几？\"}],\"stream\":false}"

# 方案 C：JSON 写入文件再让 curl 读文件
'{"model":"deepseek-r1:7b","messages":[{"role":"user","content":"1+1等于几？"}],"stream":false}' | Out-File -Encoding ascii $env:TEMP\body.json
curl.exe http://localhost:11434/api/chat -d "@$env:TEMP\body.json"

# 方案 D：进 WSL 跑 Linux 原生 curl（无引号问题）
wsl -d Ubuntu -- bash -lc 'curl -s http://localhost:11434/api/chat -d "{\"model\":\"deepseek-r1:7b\",\"messages\":[{\"role\":\"user\",\"content\":\"1+1等于几？\"}],\"stream\":false}"'
```

返回 JSON 的 `message.content` 字段就是模型回答。

---

## 七、对接 Spring Ai Practice 项目

### 26. 配置 `.env`（项目根目录，git 忽略）

> 现状：`.env` 目前只含 `DEEPSEEK_API_KEY`、`OPENROUTER_API_KEY` 两个 key。Ollama 的地址与模型在 `application-dev/sit/prod.yaml` 里有默认值，但**默认模型是 `deepseek-r1:14b`，与本地已安装的 `deepseek-r1:7b` 不一致**，必须覆盖。

向 `.env` 追加（`OLLAMA_BASE_URL` 可省，默认值就是 `http://localhost:11434`；`OLLAMA_CHAT_MODEL` **必须加**）：

```
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=deepseek-r1:7b
```

> `localhost` 在 Windows 侧即可直通 WSL2 的 11434 端口（WSL2 自动 NAT 转发）。
> ⚠️ 不加 `OLLAMA_CHAT_MODEL` 时，项目会采用 yaml 默认值 `deepseek-r1:14b`，本地没有该模型，接口会报「模型不存在」。

### 27. 启动项目

```powershell
# 必须先切 JDK 25，否则构建失败
$env:JAVA_HOME="D:\softwarework\JDK\ms-25.0.3"
.\mvnw spring-boot:run
```

浏览器打开 `http://localhost:8080/swagger-ui.html`，用 `/ollama/chat` 接口提问。参数：`message`（必填，问题内容）、`voice`（可选，口吻，默认「聊天AI助手」）。例如直接访问：

```
http://localhost:8080/ollama/chat?message=你好
```

走 `OllamaController` → 本地 CPU 推理。若报模型不存在，多半是 `.env` 没设 `OLLAMA_CHAT_MODEL=deepseek-r1:7b`（见第 26 条）。

---

## 八、Open WebUI 图形界面（使用与维护）

> Open WebUI 是浏览器网页版对话界面，直接连本地 Ollama，免去敲命令。访问地址 **`http://localhost:3000`**。

### 28. 部署命令（已部署，实测 healthy）

```bash
docker run -d --name open-webui --restart unless-stopped \
  -p 3000:8080 \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v open-webui:/app/backend/data \
  --add-host host.docker.internal:host-gateway \
  ghcr.io/open-webui/open-webui:main
```

参数说明：

- `-p 3000:8080`：浏览器访问 `localhost:3000`，容器内服务跑在 8080。
- `OLLAMA_BASE_URL=http://host.docker.internal:11434`：让 Open WebUI 通过 `host.docker.internal` 指向宿主（WSL）的 Ollama 端口 11434。
- `--add-host host.docker.internal:host-gateway`：WSL 下必须加，否则容器内无法解析 `host.docker.internal`。
- `-v open-webui:/app/backend/data`：**持久化卷**（账号、会话、上传文件、RAG 知识库都存这里），升级/重建不丢。
- `--restart unless-stopped`：配合 systemd 开机自愈。
- 首次启动会自动下载**嵌入向量模型**（`sentence-transformers/all-MiniLM-L6-v2`，约 80MB）用于 RAG 知识库，属正常现象。

验证：

```powershell
curl http://localhost:3000        # 返回 HTML 即正常
wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'    # 应显示 open-webui Up (healthy)
```

### 29. 访问与使用

1. 浏览器打开 `http://localhost:3000`；
2. 首次访问按提示**注册账号**（数据存在本机 volume，仅本机可见）；
3. 登录后在顶部/侧栏**模型下拉框选择 `deepseek-r1:7b`**，即可网页对话；
4. 常用功能：
   - 多会话管理（左栏历史会话，可重命名/删除/搜索）；
   - Markdown / 代码高亮 / LaTeX 渲染；
   - **RAG 知识库**：左侧「知识库」→ 新建并上传文档（PDF/Word/TXT 等）→ 在聊天页顶部的 `+` 里勾选知识库，模型即可基于文档回答；
   - 图片/音频上传（图片走视觉模型，7b 纯文本模型不支持看图，音频需 Whisper 配置，默认基础可用）；
   - 多模型切换（后续 `ollama pull` 装了别的模型，刷新页面即可在列表中选择）。

> deepseek-r1:7b 是**纯文本推理模型**，上传图片无法"看图"，但 RAG 文档问答（上传文字类文件）完全可用。

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
| 对话报「无法连接 Ollama」 | 确认 ollama 容器在跑；确认 env：`docker inspect open-webui | grep OLLAMA_BASE_URL` 应为 `host.docker.internal:11434`；容器内测 `docker exec open-webui curl http://host.docker.internal:11434` |
| 第一次提问特别慢 | 首次要加载 embedding 模型 + 7b CPU 推理，属正常，稍等即可 |
| 升级后数据没了 | 检查升级命令是否漏了 `-v open-webui:/app/backend/data`；旧卷还在就补挂载 |
| 磁盘越来越大 | 上传附件/会话数据在 volume 里，先 `du` 确认，再删无用的知识库/会话 |

### 32. Open WebUI 注意事项

- 3000 端口界面**带账号体系**，但默认只应在本机使用，勿直接映射到公网；
- 浏览器侧要**手动注册第一个账号**（admin），后续账号也由该界面管理；
- 升级频率可参考官方 GitHub Releases，`main` 镜像滚动更新，重拉即可；
- Open WebUI 与 Spring 项目的 `OllamaController` 互不影响，两套入口都能用同一个 Ollama。

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
  -e HSA_OVERRIDE_GFX_VERSION=11.0.2 \
  -v ollama:/root/.ollama \
  -p 11434:11434 \
  ollama/ollama
docker exec ollama ollama list      # 确认 deepseek-r1:7b 还在（4.7GB，未重下）
```

**经验**：`-v ollama:/root/.ollama` 卷保住了 4.7GB 模型，重建容器零损失。Docker 正常停启**必须**用 `systemctl stop/restart docker`，禁用 `kill -9 dockerd`。

---

## 十、日常使用

### 35. 开机自愈链路（无需手动操作）

```
Windows 开机 → WSL2 随用随启动 → systemd 启动
  → docker.service（enable 开机自启）
    → ollama 容器（--restart unless-stopped 自动拉起）→ 11434 端口就绪
    → open-webui 容器（--restart unless-stopped 自动拉起）→ 3000 端口就绪
```

### 36. 快速验证

```powershell
curl http://localhost:11434        # 返回 "Ollama is running" 即正常
curl http://localhost:3000         # 返回 HTML 即 Open WebUI 正常
wsl -d Ubuntu -- docker ps --format '{{.Names}} {{.Status}}'   # 看两个容器状态
```

### 37. 日常对话

```powershell
# 推荐：浏览器网页对话（Open WebUI）
#   打开 http://localhost:3000，选模型 deepseek-r1:7b 即可

# 命令行非交互（一条命令）
Invoke-RestMethod -Uri http://localhost:11434/api/chat -Method Post -ContentType 'application/json' -Body '{"model":"deepseek-r1:7b","messages":[{"role":"user","content":"你好"}],"stream":false}' | Select-Object -ExpandProperty message

# 交互模式（进入对话界面，输入 /bye 退出）
wsl -d Ubuntu -- docker exec -it ollama ollama run deepseek-r1:7b
```

### 38. 常用管理命令

| 用途 | 命令 |
|------|------|
| 看容器状态 | `wsl -d Ubuntu -- docker ps -a` |
| 手动启容器 | `wsl -d Ubuntu -- docker start ollama` |
| 启 Open WebUI | `wsl -d Ubuntu -- docker start open-webui` |
| 看容器日志 | `wsl -d Ubuntu -- docker logs ollama` |
| 看 Open WebUI 日志 | `wsl -d Ubuntu -- docker logs open-webui` |
| 看模型列表 | `wsl -d Ubuntu -- docker exec ollama ollama list` |
| 下载/换模型 | `wsl -d Ubuntu -- docker exec ollama ollama pull <模型名>` |
| 删模型 | `wsl -d Ubuntu -- docker exec ollama ollama rm deepseek-r1:7b` |
| 看推理设备 | `wsl -d Ubuntu -- docker exec ollama ollama ps` |
| 看内存占用 | `wsl -d Ubuntu -- docker stats` |
| 重启 WSL | `wsl --shutdown`（之后重开任意 WSL 命令即恢复） |

### 39. 常见故障排查表

| 症状 | 检查与修复 |
|------|-----------|
| `localhost:11434` 连不上 | `wsl -d Ubuntu -- docker ps`；容器不在就 `docker start ollama`；报错先看 `docker logs ollama` |
| `curl` 报 "找不到位置形式参数" | 把 `curl` 换成 `curl.exe` 或 `Invoke-RestMethod` |
| 对话返回 JSON 解析错误 | 双引号被 PowerShell 吃掉，改用 `Invoke-RestMethod` 或 `curl.exe --%` |
| 模型缺失 | `wsl -d Ubuntu -- docker exec ollama ollama list`；缺则重 `ollama pull deepseek-r1:7b` |
| 端口 11434 被占 | `netstat -ano | findstr 11434` 查占用，检查是否有第二个 ollama 容器 |
| 容器重建后模型不见了 | 检查是否用了 `-v ollama:/root/.ollama`；若用了旧卷，`ollama pull` 秒回因为已有缓存则正常 |
| 项目 `/ollama/chat` 报模型不存在 | 检查 `.env` 是否设了 `OLLAMA_CHAT_MODEL=deepseek-r1:7b`（yaml 默认是 `deepseek-r1:14b`，本地未装该模型） |
| WSL 内存不足 | 改 `.wslconfig` 后 `wsl --shutdown` 生效 |

---

## 十一、注意事项

1. **PowerShell 命令纪律**：调 HTTP 一律 `curl.exe`（或 `Invoke-RestMethod`），**永远不要**用 `curl`（别名 `Invoke-WebRequest`，参数不同）。
2. **禁用 `kill -9 dockerd`**：会损坏容器 RWLayer 与镜像记录。停/启 Docker 统一用 `systemctl stop|restart docker`。
3. **重建容器必须保留 `-v ollama:/root/.ollama`**，否则 4.7GB 模型要重新下载。
4. **项目默认模型是 14b**：`application-*.yaml` 里 `OLLAMA_CHAT_MODEL` 默认 `deepseek-r1:14b`，本地只装了 `deepseek-r1:7b`，`.env` 必须显式设 `OLLAMA_CHAT_MODEL=deepseek-r1:7b`，否则 `/ollama/chat` 报模型不存在。
5. **WSL 后台任务会被杀**：`wsl -d Ubuntu -- bash -lc "nohup ... &"` 的进程随会话退出而终止。长时间任务（模型下载等）要**前台保持窗口**，或用 `tmux`。
6. **GPU 现状与加速**：`/dev/dxg` 有、`/dev/kfd` 无（官方 WSL 内核 `CONFIG_HSA_AMD` 关闭）→ 当前 CPU 推理（约 11.6 token/s）。要 GPU 需：更新 AMD 驱动（≥ Adrenalin 24.1.1）→ 让 WSL 出现 `/dev/kfd`（新内核或自编译带 `HSA_AMD` 的内核）→ 换 `ollama/ollama:rocm` 并挂载两个设备，详见第 22 条。
7. **国内网络**：Docker 镜像加速列表（daemon.json）可能失效需更换；Ollama 模型走自身源，慢但可断点续传，重跑 `ollama pull` 即可。
8. **内存与磁盘**：7b 推理时 `.wslconfig` 分配 24GB 足够（`docker stats` 可观察实际占用）；镜像 8GB + 模型 4.7GB + 系统盘占用，清理用 `docker system prune`。
9. **端口安全**：11434（Ollama）只作本机使用，勿暴露公网（无鉴权）；3000（Open WebUI）有账号体系，但也只在本机使用，勿映射公网。
10. **systemd-firstboot 已 mask**：不要取消，否则重启再次卡死。
11. **配置生效**：`.wslconfig`、`/etc/wsl.conf` 改动后必须 `wsl --shutdown` 再重进；daemon.json 改动后 `systemctl restart docker`。
12. **中文乱码**：PowerShell 看中文输出先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`。
13. **日志位置**：dockerd 手动启动日志 `/var/log/dockerd.log`（systemd 托管后走 `journalctl -u docker`）；容器日志 `docker logs ollama` / `docker logs open-webui`；模型下载日志 `/var/log/ollama-pull.log`。
14. **不要用 PowerShell `Set-Content`/`Out-File` 写带中文注释的 Java 文件**：UTF-8 中文会被破坏，Java 源码一律用编辑器/IDE 写入。

---

## 附录：关键文件与脚本清单

| 路径 | 用途 |
|------|------|
| `C:\Users\YU\.wslconfig` | WSL2 内存/交换/CPU 配置 |
| `/etc/wsl.conf`（WSL 内） | systemd 开关、默认用户 |
| `/etc/apt/sources.list.d/ubuntu.sources` | 清华 apt 源 |
| `/etc/docker/daemon.json` | 镜像加速 + 日志策略 |
| `/usr/sbin/policy-rc.d` | 屏蔽 dpkg 自动启动服务（exit 101） |
| `/var/lib/docker/volumes/ollama/_data` | **Ollama 模型数据（勿删）** |
| `/var/lib/docker/volumes/open-webui/_data` | **Open WebUI 数据**（账号/会话/上传文件/RAG 库） |
| `/var/log/dockerd.log`、`/var/log/ollama-pull.log` | 运行日志 |
| `D:\JavaProjects\SpringAiPractice\.env` | 项目对接配置（git 忽略） |
| `D:\JavaProjects\SpringAiPractice\src\main\resources\static\knowledgeBase\` | 本文档所在目录 |

> 当前运行容器：`ollama`（11434，模型服务）、`open-webui`（3000，网页界面）。两者均 `--restart unless-stopped`，由 systemd 托管 Docker 实现开机自愈。

> 临时部署脚本位于 `C:\Users\YU\AppData\Local\Temp\opencode\`，仅作排查参考，非必需文件。
