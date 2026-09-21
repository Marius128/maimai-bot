# KokaBot Docker 云服务器部署

本文面向一台全新的 Linux x86_64 云服务器。推荐 Ubuntu 24.04，最低建议 2 核 CPU、4 GB 内存和 20 GB 可用磁盘。

整套服务包括：

- `bot`：KokaBot Java 程序；
- `mysql`：保存用户设置、加密授权和机厅人数；
- `maimai-adapter`：读取舞萌机台数据；
- `snowluma`：登录 QQ 并提供 OneBot WebSocket；
- `db-init`：启动时检查并补齐数据库表。

除 SSH 外，不需要把 MySQL、OneBot、Java 或适配器端口开放到公网。

## 1. 检查服务器架构

```bash
uname -m
```

输出应为 `x86_64`。当前固定使用的 maimai.py `v1.5.2rc2` 只提供 Linux amd64 可执行文件，ARM 服务器不能直接使用这套配置。

## 2. 安装 Docker

如果服务器还没有 Docker，请按照 Docker 官方文档安装 Docker Engine 和 Compose 插件。安装后确认：

```bash
docker version
docker compose version
```

## 3. 下载项目

请先在本地提交并推送最新代码，然后在服务器执行：

```bash
git clone https://github.com/Marius128/maimai-bot.git
cd maimai-bot
cp .env.example .env
chmod 600 .env
```

以后更新代码使用：

```bash
git pull
docker compose up -d --build
```

## 4. 填写生产环境密钥

先生成三个随机值：

```bash
openssl rand -hex 24
openssl rand -hex 24
openssl rand -base64 32
```

分别用于：

1. `MYSQL_ROOT_PASSWORD`；
2. `DB_PASSWORD`；
3. `TOKEN_ENCRYPTION_KEY`。

再生成 OneBot Token：

```bash
openssl rand -hex 32
```

把结果写入 `.env` 的 `ONEBOT_ACCESS_TOKEN`。

使用编辑器打开 `.env`：

```bash
nano .env
```

还需要填写已经申请好的：

- `DIVING_FISH_OAUTH_CLIENT_ID`；
- `DIVING_FISH_OAUTH_CLIENT_SECRET`；
- `LXNS_API_KEY`；
- `LXNS_OAUTH_CLIENT_ID`；
- `LXNS_OAUTH_CLIENT_SECRET`。

不要把 `.env` 上传 Git，也不要在聊天或截图中公开这些内容。`TOKEN_ENCRYPTION_KEY` 上线后必须长期备份；随意更换会导致数据库中的旧授权无法解密。

如果要把本地已经绑定的用户一起迁移到服务器，不要生成新的 `TOKEN_ENCRYPTION_KEY`，必须把本地正在使用的原值填入服务器 `.env`，并按后文方法导入本地数据库。否则可以使用新密钥，但所有用户都需要重新绑定。

## 5. 先启动底层服务

```bash
docker compose up -d mysql maimai-adapter snowluma
docker compose ps
```

第一次会下载镜像和 maimai.py 官方 Linux 程序，等待时间取决于服务器网络。

查看适配器是否健康：

```bash
docker compose ps maimai-adapter
docker compose logs --tail=100 maimai-adapter
```

## 6. 登录服务器上的 QQ

SnowLuma 的 noVNC 和 WebUI 只绑定在服务器 `127.0.0.1`，不能直接通过公网 IP 打开。请在自己的电脑上建立 SSH 隧道：

```bash
ssh -L 6081:127.0.0.1:6081 -L 5099:127.0.0.1:5099 用户名@服务器IP
```

保持这个 SSH 窗口运行，然后在本机浏览器打开：

- `http://127.0.0.1:6081/`：查看 QQ 窗口并扫码登录；
- `http://127.0.0.1:5099/`：打开 SnowLuma WebUI。

首次启动生成的 noVNC 和 WebUI 密码可在服务器查看：

```bash
docker compose logs snowluma | grep -E "远程桌面密码|remote desktop password|临时密码|initial credentials"
```

扫码登录 QQ 后，在 SnowLuma WebUI 中确认 OneBot WebSocket 服务：

- 启用：是；
- 监听地址：`0.0.0.0`；
- 端口：`3001`；
- 路径：`/`；
- 角色：`Universal`；
- Access Token：必须与 `.env` 中的 `ONEBOT_ACCESS_TOKEN` 完全一致；
- 消息格式：`array`。

保存配置并重启 SnowLuma：

```bash
docker compose restart snowluma
```

## 7. 启动机器人

```bash
docker compose up -d --build
docker compose ps
```

`db-init` 正常执行完后会显示 `Exited (0)`，这是一次性建表容器的正常状态，不是故障。

查看机器人日志：

```bash
docker compose logs -f --tail=200 bot
```

出现下面的日志说明连接成功：

```text
OneBot WebSocket 已连接
```

然后在 QQ 私聊测试：

```text
/help
```

群聊测试：

```text
@机器人 /b50
```

## 8. 常用维护命令

查看全部容器：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f bot
docker compose logs -f snowluma
docker compose logs -f maimai-adapter
docker compose logs -f mysql
```

重启：

```bash
docker compose restart bot
docker compose restart snowluma
```

停止但保留数据库和 QQ 登录数据：

```bash
docker compose down
```

重新启动：

```bash
docker compose up -d
```

不要执行 `docker compose down -v`，它会删除 MySQL、QQ 登录状态和已缓存资源。

## 9. 备份

备份数据库：

```bash
docker compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" maimai_bot' \
  > maimai_bot_backup.sql
```

同时单独安全备份服务器上的 `.env`。数据库备份和原来的 `TOKEN_ENCRYPTION_KEY` 必须配套保留，否则恢复后无法解密用户授权。

Docker 命名卷中还保存了 SnowLuma 和 QQ 登录数据。升级或重建容器时保留这些卷，即可尽量避免重复扫码。

### 从本地 MySQL 迁移现有用户

先在本地导出，`-p` 会提示输入本地 MySQL 密码：

```bash
mysqldump -uroot -p --default-character-set=utf8mb4 maimai_bot > maimai_bot_backup.sql
```

把备份传到服务器项目目录，先启动服务器 MySQL，再导入：

```bash
docker compose up -d mysql
docker compose exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" maimai_bot' \
  < maimai_bot_backup.sql
docker compose up -d --build
```

迁移数据库时，服务器 `.env` 中的 `TOKEN_ENCRYPTION_KEY` 必须与本地完全一致。

## 10. 防火墙建议

公网安全组只需要开放 SSH 端口。不要对公网开放：

- `3000`、`3001`：OneBot；
- `3306`：MySQL；
- `8081`：Java Bot；
- `8082`：maimai.py；
- `5099`：SnowLuma WebUI；
- `6081`：noVNC。

需要访问 `5099` 或 `6081` 时，使用前文的 SSH 隧道。

## 11. 常见问题

### 日志一直提示 OneBot WebSocket 连接失败

检查 SnowLuma 是否已经扫码登录、WebSocket 是否监听 `0.0.0.0:3001`，以及两边 Access Token 是否完全一致。

### `/update` 提示适配器没有启动

```bash
docker compose ps maimai-adapter
docker compose logs --tail=200 maimai-adapter
```

适配器应显示为 `healthy`。机器人在 Docker 内使用 `http://maimai-adapter:8082`，不要改回 `127.0.0.1:8082`。

### B50 中文或日文显示不正常

KokaBot 镜像已经安装 Noto CJK 字体。服务器不会自动拥有你 Windows 上安装的苹方字体，因此 Docker 中会使用模板配置的 Noto Sans SC 后备字体。

### 云服务器读取舞萌成绩被拒绝

部分云服务器网络可能被舞萌机台服务拒绝。此时其他功能仍可使用，但 `/update` 读取机台成绩可能失败，需要更换合适的服务器出口或为适配器配置可用代理。
