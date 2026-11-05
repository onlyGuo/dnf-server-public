# 服务集成平台

[DNF 服务器管理平台](https://github.com/onlyGuo/dnf-server-public)可以独立运行，也可以与游戏服务一起运行。集成镜像的启动命令见 [DNF 服务 README](https://github.com/onlyGuo/dnf#快速开始)。

频道管理接口仅供超管使用。

## 网络与数据

集成镜像支持桥接网络和 Linux 宿主机网络。宿主机网络使用 `--network host -e DNF_HOST_NETWORK=true`，平台默认端口为 8003；桥接网络下平台在容器内监听 9001。

宿主机网络下数据库兼容端口为 3000，请根据需要自行配置防火墙。数据库、`/data`、日志和平台数据目录需要持久化。

如果旧版本曾安装宿主机守护服务，升级时需停用旧服务并清理它留下的 nftables 规则；新版本无需安装该服务。

以旧容器名 `dnf` 为例，升级时在宿主机执行一次（容器名不同时请替换）：

```bash
sudo systemctl disable --now dnf-host-guard.service
sudo nft delete table inet "dnf_host_guard_$(printf '%s' dnf | sha256sum | cut -c1-12)"
sudo rm -f /etc/systemd/system/dnf-host-guard.service /usr/local/sbin/dnf-host-guard
sudo systemctl daemon-reload
```

## 服务管理

Web 自动检查当前进程可见的游戏目录、启动脚本和 Supervisor：安装在游戏宿主机上或与游戏同容器时可以管理频道；没有发现游戏服务时，用户、邮件等数据库功能仍可使用。`application.yml` 中的 `dnf.channels.data-dir`、`game-dir` 和 `supervisorctl` 可按本机目录调整，默认分别为 `/data`、`/home/neople/game` 和 `/usr/local/bin/supervisorctl`。单独运行的 Web 容器不会自动访问宿主机的目录或进程，也无需 Docker socket。数据库连接由环境变量提供，不在项目配置中保存实际密码。`DNF_CHANNEL_DDNS_INTERVAL_MS` 可调整轮询间隔，默认 10000 毫秒。

受支持的一体化服务启动时，各启用频道会自动加载在线邮件通知组件，无需指定频道。向在线角色发送邮件后，游戏可立即收到新邮件通知；单独部署的平台连接同一数据库时，也可由一体化服务处理通知。

使用原 DNF 的全局 DDNS 时，按原镜像配置 `DDNS_ENABLE=true`、`DDNS_DOMAIN`，并不要同时指定 `PUBLIC_IP`。每个频道还可单独选择默认地址、固定 IPv4 或解析为 IPv4 的 DDNS 域名；公共 relay 和 STUN 服务继续使用默认地址。独立 DDNS 解析失败时保留上次成功地址。地址改变时仅重启该频道的游戏进程；默认地址改变时，原 DNF 的监控脚本仍可能重启公共 bridge 和中央频道服务，在线玩家可能受到影响。

Web 发现游戏服务后会自动接管默认通告地址，并保留原固定 IP 或 DDNS 配置及最后一次有效地址；无法读取原配置时会提示手动选择，避免覆盖未知设置。原地址监视脚本会保留备份，DDNS 解析失败时保留上次地址。默认地址变化会按原 DNF 流程重启 bridge、中央频道和所有已启用频道，以更新共用的 relay/STUN 地址；各频道独立的通告 IP 保持不变，不重启整个容器。宿主机路径与默认路径不同时，可用 `DNF_MONITOR_SCRIPT` 指定地址监视脚本。

频道类型默认由游戏决定。选择特定类型时只更新 `taiwan_<大区数据库>.game_channel.gc_type`，不写入 `d_channel.ch_server_data`；从指定类型恢复默认时会只读取后者的默认类型，再更新运行状态表。页面显示宿主机或容器限制内的内存使用，并按每个新增频道约 1 GB 做预估，实际占用以进程常驻内存为准。

## 持久化与迁移

首次启动时读取原 `OPEN_CHANNEL` 生成的频道列表，将其保存在 `/data/channel-manager`，并为每个游戏频道生成独立 Supervisor 进程组。新导入的频道默认使用游戏类型，不改写原有类型；已有管理配置会保留。后续以页面保存的状态为准；`OPEN_CHANNEL` 不再覆盖已迁移的频道。停用只停止该频道；配置在重启容器后仍然保留。指定频道类型后，后台会定期将 `gc_type` 恢复为该值，避免游戏进程启动时改写。留空则由游戏控制。

初始化过程只新增缺失的数据表和账号字段，不执行 `init.sql` 里的 `DROP TABLE` 或演示数据插入。游戏数据库和管理数据必须持久化。启动脚本会备份并适配 `/data/run/start_game.sh`；如果脚本或 Supervisor 配置结构不是支持的格式，初始化会中止，避免覆盖定制内容。

Web 优先使用自己的 `data/private.key`，密钥管理页会检查它与游戏持久化目录的私钥及游戏公钥是否匹配；启动时不会更换已有密钥。超管可上传匹配的 RSA 公私钥文件或重新生成 2048 位公私钥：系统同步平台私钥、游戏持久化目录的公私钥和游戏目录公钥，然后重启运行中的游戏频道。在线玩家会断开，页面会列出重启失败的进程。

Web 从游戏目录的 `Script.pvf` 解析物品；默认是 `/home/neople/game/Script.pvf`，可通过 `DNF_GAME_DIR` 指定。未配置时，物品列表不可用；发送游戏邮件仍可直接填写物品 ID。独立 Web 可以只挂载游戏目录以查看 PVF，上传和自动重启则还需要发现同一环境中的游戏服务和 Supervisor。

在 PVF 管理页上传新 PVF 后，Web 会先解析校验，再更新游戏目录的 PVF，并在存在时同步游戏的持久化版本 `/data/Script.pvf`。随后逐个重启正在运行的游戏频道，停用频道保持原状态；在线玩家会断开。页面会报告重启失败的频道。Web 和数据库不随频道重启。

上传 `ImagePacks2` ZIP 可以更新物品图标，压缩包可包含 `ImagePacks2/` 文件夹，也可直接包含文件夹内的内容。

部署前应备份现有 DNF 容器配置、`/data`、数据库和管理站密钥；切换镜像时需要一次游戏停机。应用的频道编辑不需要重启 DNF 容器，但频道重启会断开该频道的玩家。不得把自动生成的频道配置与原有 `dnf_channel` 中的同一游戏进程同时启用。
