<#
╔══════════════════════════════════════════════════════════════╗
║               NebulaMind — 一键启动脚本                      ║
║  启动顺序: 基础设施(Docker) → AI服务 → 后端 → 前端           ║
║  使用前确保已安装: Docker, Java 21, Maven, Node.js, Python   ║
╚══════════════════════════════════════════════════════════════╝
#>

$ErrorActionPreference = "Continue"
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $rootDir

# 加载根目录 .env（含 MAAS_API_KEY 等密钥，不提交到仓库）
$envFile = Join-Path $rootDir ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            Set-Item -Path "Env:$key" -Value $value
        }
    }
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "      NebulaMind — 启动中..."                -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# =====================================================
# 步骤 1: 启动 Docker 基础设施（PostgreSQL, Redis, RabbitMQ, MinIO 等）
# =====================================================
Write-Host "[1/4] 启动 Docker 基础设施..." -ForegroundColor Yellow
$dockerRunning = docker ps 2>$null | Select-String -Pattern "CONTAINER ID"
if (-not $dockerRunning) {
    Write-Host "  → Docker 未运行，请先启动 Docker Desktop" -ForegroundColor Red
    Write-Host "  → 或手动执行: docker-compose up -d" -ForegroundColor Gray
} else {
    $existing = docker ps --format "{{.Names}}" 2>$null
    if ($existing -match "nebulamind-postgres|nebulamind-redis") {
        Write-Host "  → 基础设施容器已运行，跳过" -ForegroundColor Green
    } else {
        docker-compose up -d
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✓ 基础设施启动成功（等待服务就绪...）" -ForegroundColor Green
            Start-Sleep -Seconds 5
        } else {
            Write-Host "  ✗ Docker Compose 启动失败，请检查 Docker 配置" -ForegroundColor Red
        }
    }
}
Write-Host ""

# =====================================================
# 步骤 2: 启动 AI 服务（Python FastAPI，端口 8081）
# =====================================================
Write-Host "[2/4] 启动 AI 服务 (端口 8081)..." -ForegroundColor Yellow
$aiServicePath = Join-Path $rootDir "ai-services"
if (Test-Path $aiServicePath) {
    # 检查是否已有实例在运行
    $aiRunning = Get-NetTCPConnection -LocalPort 8081 -ErrorAction SilentlyContinue | Where-Object State -eq Listen
    if ($aiRunning) {
        Write-Host "  → AI 服务已在运行 (端口 8081)" -ForegroundColor Green
    } else {
        # 检查 venv 是否存在
        $venvPath = Join-Path $aiServicePath "venv"
        $pythonCmd = "python"
        if (Test-Path $venvPath) {
            $pythonCmd = "$venvPath\Scripts\python"
        }
        Start-Process powershell -WindowStyle Normal -ArgumentList @"
            Set-Location '$aiServicePath';
            Write-Host '=== NebulaMind AI 服务 (端口 8081) ===' -ForegroundColor Cyan;
            if (-not (Test-Path '$venvPath')) {
                Write-Host '正在安装依赖...' -ForegroundColor Yellow;
                & $pythonCmd -m pip install -r requirements.txt;
            }
            Write-Host '启动 AI 服务...' -ForegroundColor Green;
            & $pythonCmd main.py;
            Read-Host '`n按 Enter 关闭';
"@
        Write-Host "  ✓ AI 服务新窗口已打开" -ForegroundColor Green
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "  → AI 服务目录不存在，跳过" -ForegroundColor DarkYellow
}
Write-Host ""

# =====================================================
# 步骤 3: 启动后端（Spring Boot，端口 8080）
# =====================================================
Write-Host "[3/4] 启动后端服务 (端口 8080)..." -ForegroundColor Yellow
$backendPath = Join-Path $rootDir "backend"
if (Test-Path $backendPath) {
    $backendRunning = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object State -eq Listen
    if ($backendRunning) {
        Write-Host "  → 后端已在运行 (端口 8080)" -ForegroundColor Green
    } else {
        Start-Process powershell -WindowStyle Normal -ArgumentList @"
            Set-Location '$backendPath';
            Write-Host '=== NebulaMind 后端服务 (端口 8080) ===' -ForegroundColor Cyan;
            `$env:SPRING_PROFILES_ACTIVE='dev';
            Write-Host '编译并启动后端 (dev 环境)...' -ForegroundColor Yellow;
            mvn spring-boot:run;
            Read-Host '`n按 Enter 关闭';
"@
        Write-Host "  ✓ 后端服务新窗口已打开" -ForegroundColor Green
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "  → 后端目录不存在，跳过" -ForegroundColor DarkYellow
}
Write-Host ""

# =====================================================
# 步骤 4: 启动前端（React + Vite，端口 5173）
# =====================================================
Write-Host "[4/4] 启动前端服务 (端口 5173)..." -ForegroundColor Yellow
$frontendPath = Join-Path $rootDir "frontend"
if (Test-Path $frontendPath) {
    $frontendRunning = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue | Where-Object State -eq Listen
    if ($frontendRunning) {
        Write-Host "  → 前端已在运行 (端口 5173)" -ForegroundColor Green
    } else {
        # 与 README 保持一致，前端统一使用 npm
        $npmCmd = "npm.cmd"
        if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
            $npmCmd = "npm"
        }
        Start-Process powershell -WindowStyle Normal -ArgumentList @"
            Set-Location '$frontendPath';
            Write-Host '=== NebulaMind 前端服务 (端口 5173) ===' -ForegroundColor Cyan;
            if (-not (Test-Path 'node_modules')) {
                Write-Host '正在安装前端依赖...' -ForegroundColor Yellow;
                & $npmCmd install;
            }
            Write-Host '启动开发服务器...' -ForegroundColor Green;
            & $npmCmd run dev;
            Read-Host '`n按 Enter 关闭';
"@
        Write-Host "  ✓ 前端服务新窗口已打开" -ForegroundColor Green
    }
} else {
    Write-Host "  → 前端目录不存在，跳过" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  NebulaMind 启动中，请稍候..."               -ForegroundColor Cyan
Write-Host "  前端:  http://localhost:5173"              -ForegroundColor White
Write-Host "  后端:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  AI:    http://localhost:8081/docs"         -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示: 各个服务在独立窗口中运行，关闭窗口即停止服务" -ForegroundColor Gray
Write-Host "      也可按 Ctrl+C 逐个停止"                 -ForegroundColor Gray
