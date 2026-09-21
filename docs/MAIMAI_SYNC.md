# 舞萌成绩同步

KokaBot 使用官方 `maimai.py` REST 客户端读取国服机台二维码中的玩家信息。Windows 本地运行时，适配器只监听 `127.0.0.1:8082`；Docker 部署时只在 Compose 内网开放。不要将该端口暴露到公网。

云服务器完整部署方法见 `docs/DOCKER_DEPLOYMENT.md`。

当前固定使用 `v1.5.2rc2` 或更高版本，因为 `v1.5.1` 不支持国服 1.55 Mai-Encoding，会出现二维码可以绑定但无法读取成绩的问题。

## Windows 本地运行

在 PowerShell 中进入项目目录后运行：

```powershell
.\scripts\install-maimai-adapter.ps1
.\scripts\start-maimai-adapter.ps1
```

保持适配器窗口运行，再启动 KokaBot。若修改了端口，同时设置环境变量：

```powershell
$env:MAIMAI_ADAPTER_BASE_URL = "http://127.0.0.1:8082"
```

## 首次建表

依次执行：

- `database/V001__create_bot_user.sql`
- `database/V002__create_lxns_oauth_binding.sql`
- `database/V003__create_maimai_account_binding.sql`
- `database/V004__create_diving_fish_binding.sql`
- `database/V005__create_arcade_occupancy_report.sql`

## 用户绑定

1. 私聊机器人发送 `/bind lxns`，按提示完成落雪 OAuth 授权。
2. 若需同步水鱼，登录水鱼查分器，在个人资料页生成 Import-Token，再私聊机器人发送 `/bind fish Import-Token`。
3. 在舞萌游戏内打开玩家二维码，扫码复制以 `SGWCMAID` 开头的完整内容。
4. 私聊机器人发送 `/bind mai SGWCMAID...`。
5. 绑定完成后，在群内 `@机器人 /update` 或私聊发送 `/update`。

发送 `/help` 可以查看机器人当前支持的常用指令与功能说明。

快捷更新会先读取用户通过 `/source fish` 或 `/source lxns` 选择的平台，只向当前平台上传达成率、DX 分数或徽章有提升的谱面；完全相同或更差的数据不会重复写入。当前平台未绑定时会提示对应绑定命令，舞萌玩家标识未绑定时会提示 `/bind mai`。机台快捷数据目前不包含 FC、FC+、AP、AP+ 与同步标志，目标平台已有的徽章不会被空值覆盖。

机器人不会保存原始二维码内容，只会保存由适配器换取的长期玩家标识。水鱼 Import-Token、舞萌玩家标识和落雪 OAuth Token 均使用 `TOKEN_ENCRYPTION_KEY` 进行 AES-256-GCM 加密后写入数据库。

## 安全要求

- `TOKEN_ENCRYPTION_KEY`、落雪 OAuth 应用密钥不得提交到 Git。
- `.runtime/` 中的适配器二进制不会提交到 Git。
- 生产环境应单独运行适配器，并通过防火墙限制为本机访问。
