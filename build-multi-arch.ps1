# ================= 配置区域 =================
# 默认镜像仓库地址 (如果脚本运行时检测到是这个默认值，会提示你输入)
$DEFAULT_REPO = "your-docker-username/dnf-server"
$IMAGE_REPO = $DEFAULT_REPO
$TAG = "latest"

# 目标平台
$PLATFORMS = "linux/amd64,linux/arm64"
# ===========================================

# 检查是否修改了镜像名，如果没有，则提示输入
if ($IMAGE_REPO -eq $DEFAULT_REPO) {
    Write-Host "========================================================"
    Write-Host "错误: 你还没有配置镜像仓库地址！"
    Write-Host "构建多架构镜像必须推送到 Docker Hub 或私有仓库。"
    Write-Host "--------------------------------------------------------"
    $USER_INPUT_REPO = Read-Host "请输入你的镜像仓库地址 (例如: guoshengkai/dnf-server)"

    if ([string]::IsNullOrEmpty($USER_INPUT_REPO)) {
        Write-Host "错误: 输入不能为空。"
        exit 1
    }

    # 检测用户输入是否已包含tag（含有冒号）
    if ($USER_INPUT_REPO -match ":") {
        # 用户输入已包含tag，直接使用
        $IMAGE_REPO = $USER_INPUT_REPO
        $TAG = ""  # 清空TAG，避免重复追加
    } else {
        # 用户只输入了仓库名，使用默认tag
        $IMAGE_REPO = $USER_INPUT_REPO
    }
}

Write-Host "准备构建多架构镜像: $PLATFORMS"
if ([string]::IsNullOrEmpty($TAG)) {
    Write-Host "目标仓库: $IMAGE_REPO"
} else {
    Write-Host "目标仓库: ${IMAGE_REPO}:${TAG}"
}
Write-Host "----------------------------------------------------"
Write-Host "要实现【对方拉取时自动识别平台】，必须将镜像 Push 到 Docker Registry (如 Docker Hub 或 阿里云)。"
Write-Host "本地 Docker Daemon 无法同时根据一个 Tag 保存多种架构的镜像。"
Write-Host "----------------------------------------------------"

# 确认是否继续
$REPLY = Read-Host "是否继续构建并推送到仓库? (y/n)"
if ($REPLY -ne "y" -and $REPLY -ne "Y") {
    Write-Host "已取消。"
    exit 1
}

# 1. 准备 Buildx 环境
# 默认的 builder 可能不支持多架构 docker-container 驱动
$BUILDER_NAME = "my-multi-arch-builder"

try {
    docker buildx inspect $BUILDER_NAME 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "使用现有的 Buildx Builder: $BUILDER_NAME"
        docker buildx use $BUILDER_NAME
    } else {
        throw "Builder not found"
    }
} catch {
    Write-Host "创建新的 Buildx Builder: $BUILDER_NAME ..."
    docker buildx create --name $BUILDER_NAME --use --bootstrap
}

# 2. 构建并推送
Write-Host "开始构建并推送..."
# 构建完整的镜像tag
if ([string]::IsNullOrEmpty($TAG)) {
    $FULL_IMAGE_TAG = $IMAGE_REPO
} else {
    $FULL_IMAGE_TAG = "${IMAGE_REPO}:${TAG}"
}

# --push 会自动把构建好的 manifest list 推送到远程仓库
# --no-cache 强制重新构建，避免使用缓存的旧层
docker buildx build --platform $PLATFORMS `
  -t "$FULL_IMAGE_TAG" `
  --no-cache `
  --push `
  .

if ($LASTEXITCODE -eq 0) {
    Write-Host "=========================================="
    Write-Host "构建并推送成功!"
    Write-Host "你的朋友现在可以使用以下命令拉取并将自动适配其架构:"
    Write-Host "docker pull $FULL_IMAGE_TAG"
    Write-Host "=========================================="
} else {
    Write-Host "构建失败!"
    exit 1
}
