# DNF 服务器管理平台

一个开放的 DNF Web 后台，集游戏运营、服务运维、登录器和客户端配置于一体。在浏览器里管理玩家、物品和频道，让开服与日常维护更直观。

- [DNF 一体化服务](https://github.com/onlyGuo/dnf)：一个 Docker 容器运行游戏服务、数据库和本平台。
- [DNFLogin 登录器](https://github.com/onlyGuo/DNFLogin)：连接平台，提供游戏登录、客户端下载和自动更新。
- [PVF/NPK 解析器](https://github.com/onlyGuo/dnf_parser)：解析游戏数据和物品图标。

## 能做什么

- **玩家运营**：管理账号、角色和属性，发送游戏邮件及物品，完成任务。
- **背包与快捷栏**：查看物品属性、编辑物品，在背包和快捷栏间移动。
- **频道管理**：新增、编辑、启用、停用和重启频道，选择频道类型，查看内存占用；默认地址和各频道支持固定 IP 与 DDNS。
- **PVF 与图标**：浏览装备和道具；上传 PVF 更新服务，上传 `ImagePacks2` ZIP 更新物品图标。
- **登录器与客户端**：配置登录器背景、下载和更新版本，集中设置等级上限、服务器地址、画质与自动拾取等。
- **数据库与密钥**：浏览及编辑数据库，执行 SQL，管理游戏登录公私钥。

![频道管理](doc/screenshots/channel-management.png)

## 快速开始

已有游戏服务和数据库时，可以单独启动平台。将下面的数据库地址、账号和密码改成实际值：

```bash
mkdir -p platform-data
docker run -d --name dnf-platform --restart unless-stopped \
  -p 8003:80 \
  -e 'SPRING_DATASOURCE_URL=jdbc:mysql://数据库地址:3000/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true' \
  -e 'SPRING_DATASOURCE_USERNAME=数据库用户名' \
  -e 'SPRING_DATASOURCE_PASSWORD=数据库密码' \
  -v "$PWD/platform-data:/app/data" \
  guoshengkai/dnf-server:latest
```

打开 `http://服务器地址:8003/login`。没有超管时，已有的第一个账号会被设为超管；空账号库的第一个注册账号会成为超管。

想一次启动整套游戏服务？使用 [DNF 一体化服务的快速开始](https://github.com/onlyGuo/dnf#快速开始)。数据库连接和相应端口的防火墙策略请按自己的部署环境配置。

## 选择部署方式

| 部署方式 | 可管理的内容 |
| --- | --- |
| 平台单独运行或单独使用 Docker | 连接数据库，管理账号、角色、邮件等数据库内容。 |
| 平台和服务位于同一宿主机 | 发现服务目录和 Supervisor 后，还可以管理频道、PVF 和密钥。 |
| 使用 DNF 一体化镜像 | 一个容器包含服务、数据库和平台，支持完整管理功能。 |

host 网络只共享网络，不会自动共享文件和进程；单独的平台容器需要额外访问服务目录及 Supervisor 才能管理服务进程，无需挂载 Docker socket。

## 功能预览

<details>
<summary>玩家、背包与邮件</summary>

![角色管理](doc/screenshots/role-management.png)
![角色背包与快捷栏](doc/screenshots/inventory-management.png)
![发送邮件与选择物品](doc/screenshots/mail-items.png)

</details>

<details>
<summary>PVF、登录器、客户端和数据库</summary>

![PVF 管理](doc/screenshots/pvf-management.png)
![登录器配置](doc/screenshots/launcher-settings.png)
![版本管理](doc/screenshots/launcher-versions.png)
![客户端配置](doc/screenshots/client-settings.png)
![数据库管理](doc/screenshots/database-management.png)

</details>

## 使用说明

- **密钥**：游戏登录使用与服务公钥匹配的私钥。独立部署时，将现有私钥放入 `platform-data/private.key`，或通过 `DNF_PRIVATE_KEY_PATH` 指定容器内路径。平台支持上传匹配的公私钥对或重新生成密钥。
- **PVF 和图标**：平台读取服务目录的 `Script.pvf`。上传新 PVF 会更新服务文件并重启运行中的频道。图标来自 `data/ImagePacks2`；上传 ZIP 时，支持直接打包文件内容或包含 `ImagePacks2/` 文件夹。
- **频道**：单个频道的地址变化只重启对应频道。默认地址变化会更新共用中继和 STUN，并重新加载相关进程。
- **数据库**：独立平台容器首次启动默认补充缺失的表和字段；`DNF_DB_AUTO_INIT=false` 可关闭初始化。不要对已有数据库直接执行完整的 `init.sql`。
- **权限**：`admin=2` 为超管，`1` 为 GM，`0` 为普通玩家；普通注册的权限由后台设置。编辑在线角色背包前请先退出游戏。

平台数据目录建议持久化 `private.key`、`ImagePacks2` 和初始化标记。旧版 `data/imagepacks-uploads` 中的资源会在启动时尝试迁移到 `ImagePacks2`。

## 宿主机运行与源码构建

需要 Node.js 22、Java 17 或更高版本及 Maven：

```bash
cd webui2
npm ci
npx vite build
cd ..
mvn -Dmaven.test.skip=true package
```

平台和服务在同一宿主机运行时，可按实际位置设置以下环境变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `DNF_GAME_DIR` | `/home/neople/game` | 游戏服务目录 |
| `DNF_DATA_DIR` | `/data` | 服务持久化目录 |
| `DNF_SUPERVISORCTL` | `/usr/local/bin/supervisorctl` | 服务进程管理命令 |
| `DNF_CHANNEL_DDNS_INTERVAL_MS` | `10000` | DDNS 刷新间隔 |

配置数据库环境变量后，运行 `java -jar target/dnf-server-0.0.1-SNAPSHOT.jar`，访问 `http://服务器地址:9001/login`。直接运行 JAR 时需先准备平台数据库表和字段，可使用 `deploy/integrated/init_db.py` 生成增量 SQL。服务目录与 Supervisor 必须对平台进程可访问。

本地模拟和集成说明分别见 [本地模拟](deploy/local-sim/README.md) 和 [服务集成](deploy/integrated/README.md)。已发布镜像见 [Docker Hub](https://hub.docker.com/r/guoshengkai/dnf-server)，架构为 `linux/amd64`。
