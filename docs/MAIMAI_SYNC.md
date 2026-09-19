# 舞萌成绩同步

KokaBot 使用官方 `maimai.py` REST 客户端读取国服机台二维码中的玩家信息。适配器只监听本机 `127.0.0.1:8082`，不要将该端口暴露到公网。

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

- `database/V002__create_lxns_oauth_binding.sql`
- `database/V003__create_maimai_account_binding.sql`

## 用户绑定

1. 私聊机器人发送 `/bind lxns`，按提示完成落雪 OAuth 授权。
2. 在舞萌游戏内打开玩家二维码，扫码复制以 `SGWCMAID` 开头的完整内容。
3. 私聊机器人发送 `/bind mai SGWCMAID...`。
4. 绑定完成后，在群内 `@机器人 /update` 或私聊发送 `/update`。

发送 `/help` 可以查看机器人当前支持的常用指令与功能说明。

快捷更新会先读取落雪已有成绩，只上传达成率、DX 分数或徽章有提升的谱面；完全相同或更差的数据不会重复写入。机台快捷数据目前不包含 FC、FC+、AP、AP+ 与同步标志，已有的落雪徽章不会被空值覆盖。

机器人不会保存原始二维码内容，只会保存由适配器换取的长期玩家标识；该标识使用 `TOKEN_ENCRYPTION_KEY` 进行 AES-256-GCM 加密后写入数据库。

## 安全要求

- `TOKEN_ENCRYPTION_KEY`、落雪 OAuth 应用密钥不得提交到 Git。
- `.runtime/` 中的适配器二进制不会提交到 Git。
- 生产环境应单独运行适配器，并通过防火墙限制为本机访问。
