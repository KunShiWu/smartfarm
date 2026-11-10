# 智慧农业项目启动说明（Windows）

本项目的运行密钥统一保存在 `config/runtime-secrets.env`。启动脚本会把它们载入当前进程，再启动 Spring Boot 与 Vue 前端；无需修改源码或 Windows 系统环境变量。

## 给 AI 的操作要求

1. 不得读取、打印、复制或总结 `config/runtime-secrets.env` 的具体值。
2. 先运行配置检查：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\start-project.ps1 -CheckOnly
   ```

3. 如果需要控制真实水泵，先在单独的 PowerShell 窗口建立 SSH 隧道。连接命令中的服务器信息位于密钥文件，但密码需要由使用者本人输入；项目不保存 SSH 密码或私钥。

   ```powershell
   ssh -N -L 18083:127.0.0.1:18081 root@云服务器IP
   ```

4. 在项目根目录运行：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\start-project.ps1
   ```

5. 浏览器访问 `http://127.0.0.1:5173/`。

## 环境要求

- Windows 10/11 与 PowerShell
- Java 17 或更高版本
- Maven 3.8 或更高版本
- Node.js 18 或更高版本及 npm
- Windows OpenSSH 客户端（仅真实水泵控制需要）

## 运行行为

- 后端端口：`8080`
- 前端端口：`5173`
- 米家桥接本地隧道端口：`18083`
- 如果前端或后端已经运行，脚本会跳过重复启动。
- 后台输出写入 `logs/`。
- 隧道未建立时脚本会停止，并给出 SSH 命令；不会自动配置 SSH 密钥。

## 打包说明

`config/runtime-secrets.env` 已加入 `.gitignore`，不会被 Git 意外提交。用 Windows 资源管理器直接压缩项目时，该文件仍会包含在压缩包中，因此只应发给完全信任且允许访问设备的人。公开分享时，请删除该文件，只保留 `runtime-secrets.env.example`。

