# 服务集成平台与频道管理

服务项目的 `build/Centos7-DNF/Dockerfile.integrated` 基于 CentOS 7 服务镜像，将 DNF 服务器管理平台的 JAR 与 Java 运行环境放进服务容器。平台和服务仍是独立项目；已发布的集成镜像为 `guoshengkai/dnf-server:dnf-integrated`。桥接网络下平台监听容器的 9001 端口，宿主机网络下默认监听 8003；服务原有的网关和 Supervisor 仍各自运行。频道管理接口只允许 `d_taiwan.accounts.admin = 2` 的超管访问。

## 构建和运行

镜像同时支持桥接网络和 Linux 宿主机网络。下面的示例使用桥接网络。

```bash
# 在 DNF 项目中，使用已放入该项目的 Web JAR：
docker build --platform linux/amd64 -f build/Centos7-DNF/Dockerfile.integrated \
  -t dnf-with-admin:local .
mkdir -p dnf-data/data dnf-data/mysql dnf-data/log

docker run -d --name dnf --shm-size 8g --restart unless-stopped \
  -e SERVER_GROUP=3 -e SERVER_GROUP_DB=cain \
  -e DNF_DB_ROOT_PASSWORD='请替换数据库密码' \
  -e PUBLIC_IP='请替换客户端可访问的IPv4' \
  -e OPEN_CHANNEL='11,52,53,54,55,56' \
  -p 8003:9001 -p 3000:3306 \
  -p 7001:7001/tcp -p 7001:7001/udp \
  -p 7300:7300/tcp -p 7300:7300/udp \
  -p 2311-2313:2311-2313/udp \
  -p 30011:30011/tcp -p 31011:31011/udp \
  -p 30052-30056:30052-30056/tcp -p 31052-31056:31052-31056/udp \
  -v "$PWD/dnf-data/data:/data" \
  -v "$PWD/dnf-data/mysql:/var/lib/mysql" \
  -v "$PWD/dnf-data/log:/home/neople/game/log" \
  dnf-with-admin:local
```

随后通过 `http://服务器地址:8003/login` 登录，在后台选择“频道管理”。示例仅映射上述频道的端口；其他频道使用大区编号和频道号生成的 TCP、UDP 端口，必须在创建容器时预先映射，或由现有主机网络/端口转发提供。修改 Docker 端口映射需要重建容器，新增频道本身不需要重启容器。示例未包含公网防火墙和上游 NAT 配置。

## 宿主机网络

Linux 可用 `--network host -e DNF_HOST_NETWORK=true` 运行，并省略上面所有 `-p` 选项。管理页默认使用宿主机 `8003`；数据库兼容入口是 `3000`，Supervisor 兼容入口是 `2000`。数据库的 3306、3307、4000 和 Supervisor 的 180 仅绑定到本机回环地址。

DNF 的其他内部服务及随机 UDP 端口原本被 Docker 网络隔离，切换到宿主机网络后也会监听宿主机。请先配置随 Docker 重启而更新的宿主机防火墙，不要只过滤固定端口。项目提供 `host-guard.sh` 和 `dnf-host-guard.service`，通过 cgroup v2 和 nftables 的 socket 匹配只过滤 DNF 容器的入站流量：Web 和游戏端口允许所有来源，内部端口受限；数据库入口仅允许本机、Tailscale、Docker 网络以及配置的局域网来源。Web 上的后台操作由应用身份和权限检查控制，登录器可以从任意来源访问玩家登录接口。上游公网防火墙和转发仍需单独配置。

在支持 systemd、cgroup v2 和 nftables socket cgroupv2 的 Linux 宿主机上，先安装守护服务：

```bash
sudo install -m 755 deploy/integrated/host-guard.sh /usr/local/sbin/dnf-host-guard
sudo install -m 644 deploy/integrated/dnf-host-guard.service /etc/systemd/system/dnf-host-guard.service
# 按实际接口和来源填写 /etc/dnf-host-guard.conf；容器名默认为 dnf。
sudo systemctl daemon-reload
sudo systemctl enable --now dnf-host-guard.service
```

例如 `/etc/dnf-host-guard.conf` 可包含 `DNF_CONTAINER_NAME=dnf`、`DNF_HOST_GUARD_LAN_IF=实际网卡名`、`DNF_HOST_GUARD_LAN_IPV4=实际局域网IPv4网段` 和 `DNF_HOST_GUARD_LAN_IPV6=实际局域网IPv6网段`。未配置的 LAN 网段不会开放；配置了 LAN 网卡时还允许该接口上的 ULA 和链路本地 IPv6。启动游戏容器后，入口脚本会等待守护服务为该次启动的 cgroup 安装规则；规则未成功安装时，游戏不会启动。应验证允许与拒绝的来源、游戏 TCP/UDP 端口以及其他宿主机服务，再投入使用。

如果宿主机使用 firewalld，还需在容器启动且守护规则就绪后，允许对应端口通过活动区域。例如，`sudo DNF_SERVER_GROUP=3 DNF_ADMIN_PORT=8003 DNF_FIREWALL_ZONE=public bash deploy/integrated/configure-firewalld.sh /root/dnf-firewall-change` 会同时配置运行时和永久规则，不重载防火墙，并将新增项记录在指定目录。脚本要求宿主机守护规则已就绪；数据库端口的来源限制由守护规则执行，Web 不按来源限制。firewalld 的端口规则作用于整个宿主机，应避免其他服务复用这些端口。

宿主机网络下新增频道不需要重新配置 Docker 端口；新增大区或游戏版本所需的不同端口仍需调整防火墙与上游转发。启动前还要确认宿主机没有其他服务占用 DNF 的端口。Docker 的 host 网络会共享网络栈，必须使用可信镜像和容器。

使用原 DNF 的全局 DDNS 时，按原镜像配置 `DDNS_ENABLE=true`、`DDNS_DOMAIN`，并不要同时指定 `PUBLIC_IP`。每个频道还可单独选择默认地址、固定 IPv4 或解析为 IPv4 的 DDNS 域名；公共 relay 和 STUN 服务继续使用默认地址。独立 DDNS 解析失败时保留上次成功地址。地址改变时仅重启该频道的游戏进程；默认地址改变时，原 DNF 的监控脚本仍可能重启公共 bridge 和中央频道服务，在线玩家可能受到影响。

Web 发现游戏服务后会自动接管默认通告地址，并保留原固定 IP 或 DDNS 配置及最后一次有效地址；无法读取原配置时会提示手动选择，避免覆盖未知设置。原地址监视脚本会保留备份，DDNS 解析失败时保留上次地址。默认地址变化会按原 DNF 流程重启 bridge、中央频道和所有已启用频道，以更新共用的 relay/STUN 地址；各频道独立的通告 IP 保持不变，不重启整个容器。宿主机路径与默认路径不同时，可用 `DNF_MONITOR_SCRIPT` 指定地址监视脚本。

频道类型默认由游戏决定。选择特定类型时只更新 `taiwan_<大区数据库>.game_channel.gc_type`，不写入 `d_channel.ch_server_data`；从指定类型恢复默认时会只读取后者的默认类型，再更新运行状态表。页面显示宿主机或容器限制内的内存使用，并按每个新增频道约 1 GB 做预估，实际占用以进程常驻内存为准。

## 持久化与迁移

首次启动时读取原 `OPEN_CHANNEL` 生成的频道列表，将其保存在 `/data/channel-manager`，并为每个游戏频道生成独立 Supervisor 进程组。新导入的频道默认使用游戏类型，不改写原有类型；已有管理配置会保留。后续以页面保存的状态为准；`OPEN_CHANNEL` 不再覆盖已迁移的频道。停用只停止该频道；配置在重启容器后仍然保留。指定频道类型后，后台会定期将 `gc_type` 恢复为该值，避免游戏进程启动时改写。留空则由游戏控制。

初始化过程只新增缺失的数据表和账号字段，不执行 `init.sql` 里的 `DROP TABLE` 或演示数据插入。游戏数据库和管理数据必须持久化。启动脚本会备份并适配 `/data/run/start_game.sh`；如果脚本或 Supervisor 配置结构不是支持的格式，初始化会中止，避免覆盖定制内容。

Web 优先使用自己的 `data/private.key`，密钥管理页会检查它与游戏持久化目录及原网关的私钥、游戏公钥是否匹配；启动时不会更换已有密钥。超管可上传匹配的 RSA 公私钥文件或重新生成 2048 位公私钥：系统同步 Web 私钥、游戏持久化目录的公私钥、游戏目录公钥以及原网关的私钥副本，然后重启运行中的游戏频道和原网关。在线玩家会断开，页面会列出重启失败的进程。自定义网关私钥位置可通过 `DNF_GATE_PRIVATE_KEY_PATH` 配置。

Web 自动检查当前进程可见的游戏目录、启动脚本和 Supervisor：安装在游戏宿主机上或与游戏同容器时可以管理频道；没有发现游戏服务时，用户、邮件等数据库功能仍可使用。`application.yml` 中的 `dnf.channels.data-dir`、`game-dir` 和 `supervisorctl` 可按本机目录调整，默认分别为 `/data`、`/home/neople/game` 和 `/usr/local/bin/supervisorctl`。单独运行的 Web 容器不会自动访问宿主机的目录或进程，也无需 Docker socket。数据库连接由环境变量提供，不在项目配置中保存实际密码。`DNF_CHANNEL_DDNS_INTERVAL_MS` 可调整轮询间隔，默认 10000 毫秒。

Web 从游戏目录的 `Script.pvf` 解析物品；默认是 `/home/neople/game/Script.pvf`，可通过 `DNF_GAME_DIR` 指定。未配置时，物品列表不可用；发送游戏邮件仍可直接填写物品 ID。独立 Web 可以只挂载游戏目录以查看 PVF，上传和自动重启则还需要发现同一环境中的游戏服务和 Supervisor。

在 PVF 管理页上传新 PVF 后，Web 会先解析校验，再更新游戏目录的 PVF，并在存在时同步游戏的持久化版本 `/data/Script.pvf`。随后逐个重启正在运行的游戏频道，停用频道保持原状态；在线玩家会断开。页面会报告重启失败的频道。Web 和数据库不随频道重启。

部署前应备份现有 DNF 容器配置、`/data`、数据库和管理站密钥；切换镜像时需要一次游戏停机。应用的频道编辑不需要重启 DNF 容器，但频道重启会断开该频道的玩家。不得把自动生成的频道配置与原有 `dnf_channel` 中的同一游戏进程同时启用。
