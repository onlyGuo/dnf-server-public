# DNF 服务器管理平台 Docker 指南

平台和服务可以分别部署，也可以使用集成镜像：

- 平台：`guoshengkai/dnf-server:latest`，容器内默认访问端口为 80。
- 集成平台的服务：`guoshengkai/dnf-server:dnf-integrated`，桥接网络下平台默认端口为 9001，宿主机网络下默认端口为 8003。

当前发布的两个标签均为 `linux/amd64`。平台单独部署、数据库环境变量和数据目录说明见 [项目 README](README.md)；集成镜像的网络、持久化及频道管理说明见 [集成部署文档](deploy/integrated/README.md)。

平台使用现有游戏数据库。首次启动时默认只添加缺失的表和字段；设置 `DNF_DB_AUTO_INIT=false` 可关闭自动初始化。平台不会在单独部署时自动生成与服务无关的密钥：游戏登录需要配置与服务公钥匹配的现有私钥。

服务与平台位于同一环境并可由 Supervisor 管理时，超管可在平台上传或生成公私钥对；平台会同步服务密钥并重启相关游戏进程。频道和 PVF 管理同样需要平台能访问服务目录和 Supervisor。
