# 服务集成平台

[DNF 服务器管理平台](https://github.com/onlyGuo/dnf-server-public)可以独立运行，也可以与游戏服务一起运行。集成镜像的启动命令见 [DNF 服务 README](https://github.com/onlyGuo/dnf#快速开始)。

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

平台在可访问游戏目录和 Supervisor 时自动启用频道管理；未发现服务时，账号和邮件等数据库功能仍可使用。默认路径可用以下变量修改：

- `DNF_GAME_DIR`：游戏服务目录，默认为 `/home/neople/game`。
- `DNF_DATA_DIR`：服务数据目录，默认为 `/data`。
- `DNF_SUPERVISORCTL`：Supervisor 命令，默认为 `/usr/local/bin/supervisorctl`。

默认通告地址和每个频道均可选择固定 IPv4 或 DDNS。单频道地址变化只重启该频道；默认地址变化会更新共用中继和 STUN 地址，并重新加载相关进程。首次启动时从 `OPEN_CHANNEL` 导入频道，以后使用平台保存的配置。

平台从游戏目录读取 `Script.pvf`。上传新 PVF 后会更新服务文件并重启运行中的频道；上传 `ImagePacks2` ZIP 可更新物品图标。密钥管理支持上传匹配的公私钥对或重新生成密钥，同步平台和服务后重启运行中的频道。
