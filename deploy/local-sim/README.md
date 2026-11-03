# 本地模拟 DNF 与 Web

本环境运行真实的 Web、Supervisor 和模拟的频道进程。它使用 DNF 项目自带的 `Script.pvf`，不启动真实游戏进程，也不需要本地数据库。模拟进程、频道配置和重启操作只作用于本机容器。

在 `dnf_mock/local-test.env` 配置数据库连接；该文件已被 Git 和镜像构建排除：

```dotenv
SPRING_DATASOURCE_URL=jdbc:mysql://数据库地址:端口/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true
SPRING_DATASOURCE_USERNAME=数据库用户名
SPRING_DATASOURCE_PASSWORD=数据库密码
DNF_DB_AUTO_INIT=false
DNF_MEMBER_RESET_ENABLED=false
```

该连接指向的是真实数据库，Web 中的数据库写入会影响该数据库。模拟环境启动时不执行数据库初始化，示例频道也不设置数据库频道类型。模拟环境关闭了原有的角色创建限制定时清理任务，避免与服务器上正在运行的任务重复执行；其他手动数据库操作保持可用。

启动模拟环境（需要 Docker 或 macOS 的 `container` 命令）：

```bash
bash deploy/local-sim/run.sh integrated
```

打开 `http://127.0.0.1:19001/login`，使用现有后台账号登录。默认模拟一体化容器中的独立频道；`bash deploy/local-sim/run.sh legacy` 模拟游戏和 Web 在同一系统、游戏仍使用原有 Supervisor 分组的情况；`bash deploy/local-sim/run.sh web-only` 验证没有游戏服务时的独立 Web。使用 `DNF_LOCAL_WEB_PORT` 为同时启动的实例指定不同端口。

如果有以前创建、仍挂载旧 `data/local-sim` 的模拟容器，应先停止并删除该旧容器，再用启动脚本创建使用 `dnf_mock/local-sim` 的容器；持久数据已迁移，不应直接启动旧容器。

启动脚本默认从相邻 `dnf` 项目提取其 PVF，也可用 `DNF_GAME_SOURCE` 指定游戏项目目录；以前模拟环境保存的 PVF 也可作为初始版本。PVF 放在模拟游戏目录及其持久化目录中，上传后只重启模拟频道。物品图片使用模拟 Web 数据中的 `ImagePacks2`；可用 `DNF_LOCAL_NPK_DIR` 指向已有客户端目录，首次运行时会复制一份作为初始资源，后续上传只替换模拟环境里的副本。模拟器首次准备时复用现有 Web 私钥，并生成对应的游戏公钥，不覆盖已经存在的游戏密钥。旧的 `data/local-sim` 和 `data/local-test.env` 会在启动时复制到 `dnf_mock`，原文件不会删除。容器工具名称、停止和再次启动命令会在启动后显示。

## 直接从 IDE 启动 Web

也可以在 macOS 或 Linux 本机运行模拟游戏进程，Web 从 IDE 启动。需要 Python 3、pip 和 C 编译器；先在项目目录执行：

```bash
bash deploy/local-sim/ide.sh start
```

IDE 工作目录设为项目根目录，并配置：

```text
DNF_DATA_DIR=dnf_mock/data
DNF_GAME_DIR=dnf_mock/home/neople/game
DNF_SUPERVISORCTL=dnf_mock/usr/local/bin/supervisorctl
SPRING_DATASOURCE_URL=jdbc:mysql://数据库地址:端口/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true
SPRING_DATASOURCE_USERNAME=数据库用户名
SPRING_DATASOURCE_PASSWORD=数据库密码
```

使用现有的数据库配置即可。安装前端依赖后运行 `cd webui2 && npx vite build`，IDE 启动的后端会自动提供 `webui2/dist` 中的管理页面。模拟数据目录中的 `.dnf-local-simulator` 标记默认阻止重复执行角色创建限制清理；手动数据库写入不受影响。只有显式设置 `DNF_MEMBER_RESET_ENABLED=true` 才会在此环境启用该任务。

`bash deploy/local-sim/ide.sh status` 查看模拟频道，`bash deploy/local-sim/ide.sh stop` 停止模拟进程。`dnf_mock` 是本地生成数据，不会加入 Git 或 Docker 构建上下文。宿主机和集成容器统一通过 DNF 使用的 Supervisor 管理进程；Supervisor 不可用时保留数据库功能。
