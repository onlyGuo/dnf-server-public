# DNF 服务器管理平台

DNF 服务器管理平台（简称平台）用于管理 DNF 游戏服务（简称服务）。平台和服务是两个独立项目：平台可以单独连接游戏数据库运行，也可以与服务部署在同一宿主机或同一容器中。

- [服务项目](https://github.com/onlyGuo/dnf)
- [登录器项目](https://github.com/onlyGuo/DNFLogin)
- [PVF/NPK 解析项目](https://github.com/onlyGuo/dnf_parser)

## 镜像

| 镜像 | 用途 | 已发布架构 |
| --- | --- | --- |
| `guoshengkai/dnf-server:latest` | 独立平台 | `linux/amd64` |
| `guoshengkai/dnf-server:dnf-integrated` | 集成平台的服务 | `linux/amd64` |

固定版本标签及发布内容可在 [Docker Hub](https://hub.docker.com/r/guoshengkai/dnf-server) 查看。集成镜像的构建和部署说明见 [服务集成与频道管理](deploy/integrated/README.md)。

## 平台单独部署

平台需要连接已有的游戏数据库。在服务器上准备环境变量文件，例如：

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://数据库地址:端口/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true
SPRING_DATASOURCE_USERNAME=数据库用户名
SPRING_DATASOURCE_PASSWORD=数据库密码
```

使用该文件运行平台镜像：

```bash
mkdir -p data
docker run -d --name dnf-platform --restart unless-stopped \
  -p 8003:80 \
  --env-file /实际路径/platform.env \
  -v "$PWD/data:/app/data" \
  guoshengkai/dnf-server:latest
```

访问 `http://服务器地址:8003/login`。容器内 Nginx 默认监听 80，Java 服务默认监听 9001；可分别通过 `NGINX_PORT` 和 `SERVER_PORT` 调整。环境变量文件包含数据库凭据，应保存在项目目录外并限制访问权限。

独立平台可以管理用户、角色、邮件和其他通过数据库实现的功能。游戏登录授权还需要与服务公钥匹配的私钥；可将现有私钥挂载进容器并通过 `DNF_PRIVATE_KEY_PATH` 指向它。平台不会为独立部署自动生成一把与服务无关的密钥。未发现本地服务时，频道、PVF 上传和密钥更换等需要访问服务文件或进程的功能不可用。

容器首次启动时默认执行增量数据库初始化，只创建缺失的表和字段，不执行 `init.sql` 中的 `DROP` 或示例数据插入。设置 `DNF_DB_AUTO_INIT=false` 可关闭该步骤。不要直接对已有数据库执行整个 `init.sql`。

## 与服务一起运行

平台与服务在同一宿主机或同一容器内时，会根据以下配置发现服务目录和 Supervisor：

| 环境变量 | 用途 | 默认值 |
| --- | --- | --- |
| `DNF_GAME_DIR` | 服务运行目录，包含 `Script.pvf` 和游戏公钥 | `/home/neople/game` |
| `DNF_DATA_DIR` | 服务的持久化数据目录 | `/data` |
| `DNF_SUPERVISORCTL` | 管理服务进程的 `supervisorctl` 路径 | `/usr/local/bin/supervisorctl` |
| `DNF_GATE_PRIVATE_KEY_PATH` | 非默认布局下原网关的私钥路径 | 自动按服务目录推断 |
| `DNF_CHANNEL_DDNS_INTERVAL_MS` | 频道 DDNS 刷新间隔 | `10000` |

管理进程需要具备访问相应目录和 Supervisor 的权限。独立容器不会自动看到宿主机上的文件或进程；不需要为平台挂载 Docker socket。

集成镜像支持桥接网络和 Linux 宿主机网络。宿主机网络模式下，平台默认端口为 **8003**；启动前按 [集成部署说明](deploy/integrated/README.md) 配置网络守护和防火墙。桥接模式下平台在容器内默认使用 9001，可按需要映射到宿主机端口。

## 管理功能

- **账号和角色**：管理玩家账号、角色及游戏邮件。`admin=2` 是超管，`1` 是 GM，`0` 是普通玩家；普通注册由后台设置权限，管理员也可以作为玩家登录游戏。
- **背包和快捷栏**：查看物品属性，编辑背包及快捷栏位置。编辑前需要退出游戏，以免游戏内存与数据库互相覆盖。
- **频道**：独立启停频道，设置默认或独立的固定 IPv4、DDNS 地址及频道类型；页面显示实际内存和每频道约 1 GB 的预估。局部变更优先重启相关频道，不需要重启整个容器。
- **PVF**：从服务目录读取 `Script.pvf`。上传的新 PVF 校验通过后会更新服务文件和已有持久化副本，随后重启运行中的频道，在线玩家会断开。
- **图标资源**：在 PVF 管理页上传 `ImagePacks2` ZIP。支持压缩包包含 `ImagePacks2/` 文件夹或直接包含其内容；校验通过后，新 NPK 替换 `data/ImagePacks2` 中的旧 NPK 并立即生效。
- **密钥**：在“登录器管理 → 密钥管理”上传匹配的 RSA 公私钥对，或重新生成 2048 位公私钥。平台会同步服务及原网关的密钥，并重启运行中的相关进程；在线玩家会断开。启动时不会自动更换已有密钥。

平台数据目录通常保留 `private.key` 和 `ImagePacks2`。已有部署迁移前应先确认私钥、图标及其他实际使用的数据已备份；旧版 `data/imagepacks-uploads` 中的当前资源会在启动时尝试迁移，旧目录不会自动删除。

## 从源码构建

需要 Node.js 22、Java 17 或更高版本及 Maven。先构建前端，再打包平台：

```bash
cd webui2
npm ci
npx vite build
cd ..
mvn -Dmaven.test.skip=true package
```

生成的 `target/dnf-server-0.0.1-SNAPSHOT.jar` 包含前端页面。运行 JAR 时在环境变量中配置数据库；默认监听 9001。源码中的私钥文件不会打入新 JAR，运行时使用数据目录或环境变量指定的密钥。

本地模拟环境的目录、启动方式和数据库配置见 [本地模拟说明](deploy/local-sim/README.md)。在线邮件通知的调查见 [在线邮件调查](doc/online-mail.md)。

## 页面预览

| 登录 | 账号管理 | PVF 管理 |
| --- | --- | --- |
| ![登录页](doc/admin-login.png) | ![账号管理](doc/player-account.png) | ![PVF 管理](doc/pvf.png) |
