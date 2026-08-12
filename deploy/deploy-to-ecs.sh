#!/bin/bash

ECS_HOST="<ECS_SERVER_IP>"
ECS_USER="root"
PROJECT_NAME="nebulamind"
DEPLOY_DIR="/opt/$PROJECT_NAME"

echo "=========================================="
echo "  NebulaMind 部署脚本 - 方案A (ECS + MinIO)"
echo "=========================================="
echo ""

echo "[1/7] 检查远程服务器环境..."
ssh $ECS_USER@$ECS_HOST "echo '=== 服务器信息 ===' && hostname && uname -a && echo '' && echo '=== Docker 版本 ===' && docker --version && echo '' && echo '=== Docker Compose ===' && docker compose version && echo '' && echo '=== 端口占用检查 ===' && ss -tlnp | grep -E '80|5432|6379|5672|8080|8081|9000' || echo '无冲突端口'"
echo ""

echo "[2/7] 创建部署目录..."
ssh $ECS_USER@$ECS_HOST "mkdir -p $DEPLOY_DIR/{images,logs}"
echo ""

echo "[3/7] 上传Docker镜像..."
scp nebulamind-images.tar $ECS_USER@$ECS_HOST:$DEPLOY_DIR/images/
echo ""

echo "[4/7] 上传配置文件..."
scp docker-compose.prod.yml $ECS_USER@$ECS_HOST:$DEPLOY_DIR/docker-compose.yml
scp .env.example $ECS_USER@$ECS_HOST:$DEPLOY_DIR/.env
echo ""

echo "[5/7] 加载Docker镜像..."
ssh $ECS_USER@$ECS_HOST "docker load -i $DEPLOY_DIR/images/nebulamind-images.tar"
echo ""

echo "[6/7] 配置环境变量..."
ssh $ECS_USER@$ECS_HOST "cat > $DEPLOY_DIR/.env << 'EOF'
MAAS_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
JWT_SECRET=your-production-jwt-secret-key-must-be-at-least-32-bytes-long
INTERNAL_API_KEY=your-internal-api-key
EOF"
echo ""

echo "[7/7] 启动服务..."
ssh $ECS_USER@$ECS_HOST "cd $DEPLOY_DIR && docker compose up -d"
echo ""

echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
echo "访问地址："
echo "  前端应用: http://$ECS_HOST"
echo "  MinIO控制台: http://$ECS_HOST:9001"
echo "  后端API: http://$ECS_HOST/api/v1"
echo ""
echo "默认登录账号：admin / 123456"
echo ""
echo "查看日志："
echo "  docker logs nebulamind-backend -f"
echo "  docker logs nebulamind-frontend -f"
