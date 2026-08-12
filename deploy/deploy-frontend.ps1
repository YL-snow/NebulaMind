# 部署前端镜像到 ECS 服务器（修复版：使用 docker-compose）
$ECS_HOST = "<ECS_SERVER_IP>"
$ECS_USER = "root"
$TAG = "v$(Get-Date -Format 'yyyyMMddHHmmss')"

Write-Host "=== 步骤1: 构建 Docker 镜像 ===" -ForegroundColor Cyan
docker build --no-cache -t "nebulamind-frontend:$TAG" -t "nebulamind-frontend:latest" frontend

Write-Host "`n=== 步骤2: 导出并上传镜像 ===" -ForegroundColor Cyan
$TAR = "nebulamind-frontend-$TAG.tar"
$LOCAL_TAR = "C:\projects\NebulaMind2\NebulaMind\$TAR"
docker save "nebulamind-frontend:$TAG" -o "$LOCAL_TAR"
scp "$LOCAL_TAR" "$ECS_USER@$ECS_HOST`:/tmp/$TAR"

Write-Host "`n=== 步骤3: 加载镜像并重启前端 ===" -ForegroundColor Cyan
ssh $ECS_USER@$ECS_HOST "docker load -i /tmp/nebulamind-frontend-$TAG.tar && docker tag nebulamind-frontend:$TAG nebulamind-frontend:latest && docker stop nebulamind-frontend 2>/dev/null; docker rm nebulamind-frontend 2>/dev/null; cd /opt/nebulamind && docker compose -f docker-compose.prod.yml up -d frontend"

Start-Sleep -Seconds 5

Write-Host "`n=== 步骤4: 验证 ===" -ForegroundColor Cyan
ssh $ECS_USER@$ECS_HOST "docker exec nebulamind-frontend sh -c 'cat /usr/share/nginx/html/index.html | grep src='"

Write-Host "`n=== 完成！===" -ForegroundColor Green
Write-Host "现在刷新 http://$ECS_HOST/ 应该可以正常登录" -ForegroundColor Yellow
Write-Host "登录后访问 /security 查看安全管理页面" -ForegroundColor Yellow
