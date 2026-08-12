<#
============================================================
          NebulaMind · 星云智脑
  服务组成: 后端(Spring Boot) · AI服务 · 前端(React)
  环境要求: Docker, Java 21, Maven, Node.js, Python
============================================================
#>

$ErrorActionPreference = "Continue"
$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $rootDir

# 读取 .env，把 MAAS_API_KEY 等配置加载到当前环境变量
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

# 查找监听指定端口的进程 PID
function Get-ListenerPids($Port) {
    $lines = @(& netstat -ano 2>$null | Where-Object { $_ -match 'LISTENING' -and $_ -match ":$Port\s" })
    return @($lines | ForEach-Object { ($_ -split '\s+')[-1] } | Where-Object { $_ -match '^\d+$' } | Sort-Object -Unique)
}

function Test-PortListening($Port) {
    return (Get-ListenerPids $Port).Count -gt 0
}

function Wait-PortReady($Port, $ServiceName, $TimeoutSeconds = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening $Port) {
            Write-Host "  $ServiceName 已就绪 (端口 $Port)" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "  $ServiceName 等待超时（$TimeoutSeconds 秒），请检查上方日志" -ForegroundColor Red
    return $false
}

function Stop-Listener($Port, $ServiceName) {
    $pids = @(Get-ListenerPids $Port)
    if ($pids.Count -gt 0) {
        Write-Host "  正在停止旧服务 $ServiceName (PID $($pids -join ', '))..." -ForegroundColor Yellow
        foreach ($processId in $pids) {
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
        for ($i = 0; $i -lt 10; $i++) {
            Start-Sleep -Milliseconds 500
            $still = @(Get-ListenerPids $Port)
            if ($still.Count -eq 0) { break }
        }
    }
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "      NebulaMind · 星云智脑启动..."          -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# =====================================================
# 第 1 步：检查 Docker 依赖 PostgreSQL、Redis、RabbitMQ、MinIO
# =====================================================
Write-Host "[1/4] 检查 Docker 服务..." -ForegroundColor Yellow
$dockerRunning = docker ps 2>$null | Select-String -Pattern "CONTAINER ID"
if (-not $dockerRunning) {
    Write-Host "  Docker 未运行，请先启动 Docker Desktop" -ForegroundColor Red
    Write-Host "  可手动执行: docker-compose up -d" -ForegroundColor Gray
} else {
    $existing = docker ps --format "{{.Names}}" 2>$null
    if ($existing -match "nebulamind-postgres|nebulamind-redis") {
        Write-Host "  依赖容器已在运行" -ForegroundColor Green
    } else {
        docker-compose up -d
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Docker Compose 启动成功，等待依赖就绪..." -ForegroundColor Green
            Start-Sleep -Seconds 5
        } else {
            Write-Host "  Docker Compose 启动失败，请检查 Docker" -ForegroundColor Red
        }
    }
}
Write-Host ""

# =====================================================
# 第 2 步：启动 AI 服务 Python FastAPI（端口 8081）
# =====================================================
Write-Host "[2/4] 启动 AI 服务 (端口 8081)..." -ForegroundColor Yellow
$aiServicePath = Join-Path $rootDir "ai-services"
if (Test-Path $aiServicePath) {
    # 先停止可能占用端口的旧进程
    Stop-Listener 8081 "AI 服务"
    $aiRunning = Test-PortListening 8081
    if ($aiRunning) {
        Write-Host "  AI 服务已在运行 (端口 8081)" -ForegroundColor Green
    } else {
        # 优先使用项目 venv，避免系统 Python 抢端口或缺少 OCR 依赖
        $venvPath = Join-Path $aiServicePath "venv"
        $pythonCmd = Join-Path $venvPath "Scripts\python.exe"
        if (-not (Test-Path $pythonCmd)) {
            $pythonCmd = "python"
        }
        Start-Process powershell -WindowStyle Normal -ArgumentList @"
            Set-Location '$aiServicePath';
            Write-Host '=== NebulaMind AI 服务 (端口 8081) ===' -ForegroundColor Cyan;
            if (Test-Path '$pythonCmd') {
                Write-Host '检查/安装 Python 依赖...' -ForegroundColor Yellow;
                & '$pythonCmd' -m pip install -r requirements.txt -q;
                & '$pythonCmd' -m pip install -r requirements-ocr.txt -q;
            }
            Write-Host '启动 AI 服务...' -ForegroundColor Green;
            `$env:DEBUG='false';
            & '$pythonCmd' main.py;
            Read-Host '按 Enter 关闭';
"@
        Write-Host "  AI 服务启动中..." -ForegroundColor Green
        Wait-PortReady 8081 "AI 服务" 180
    }
} else {
    Write-Host "  AI 服务目录不存在" -ForegroundColor DarkYellow
}
Write-Host ""

# =====================================================
# 第 3 步：启动后端 Spring Boot（端口 8080）
# =====================================================
Write-Host "[3/4] 启动后端服务 (端口 8080)..." -ForegroundColor Yellow
$backendPath = Join-Path $rootDir "backend"
if (Test-Path $backendPath) {
    # 先停止可能占用端口的旧进程
    Stop-Listener 8080 "后端服务"
    $backendRunning = Test-PortListening 8080
    if ($backendRunning) {
        Write-Host "  端口 8080 已被占用，请检查旧后端进程" -ForegroundColor Red
    } else {
        Start-Process powershell -WindowStyle Normal -ArgumentList @"
            Set-Location '$backendPath';
            Write-Host '=== NebulaMind 后端服务 (端口 8080) ===' -ForegroundColor Cyan;
            `$env:SPRING_PROFILES_ACTIVE='dev';
            Write-Host '正在启动后端 (dev 环境)...' -ForegroundColor Yellow;
            mvn spring-boot:run;
            Read-Host '按 Enter 关闭';
"@
        Write-Host "  后端服务启动中..." -ForegroundColor Green
        Wait-PortReady 8080 "后端服务" 180
    }
} else {
    Write-Host "  后端目录不存在" -ForegroundColor DarkYellow
}
Write-Host ""

# =====================================================
# 第 4 步：启动前端 React + Vite（端口 5173）
# =====================================================
Write-Host "[4/4] 启动前端服务 (端口 5173)..." -ForegroundColor Yellow
$frontendPath = Join-Path $rootDir "frontend"
if (Test-Path $frontendPath) {
    # 先停止可能占用端口的旧进程
    Stop-Listener 5173 "前端服务"
    $frontendRunning = Test-PortListening 5173
    if ($frontendRunning) {
        Write-Host "  端口 5173 已被占用，请检查旧前端进程" -ForegroundColor Red
    } else {
        # 优先使用 npm.cmd，兼容 PowerShell 脚本执行策略
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
            Write-Host '启动前端开发服务器...' -ForegroundColor Green;
            & $npmCmd run dev;
            Read-Host '按 Enter 关闭';
"@
        Write-Host "  前端服务启动中..." -ForegroundColor Green
    }
} else {
    Write-Host "  前端目录不存在" -ForegroundColor DarkYellow
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  NebulaMind 全部服务已启动"              -ForegroundColor Cyan
Write-Host "  前端:  http://localhost:5173"             -ForegroundColor White
Write-Host "  后端:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  AI:    http://localhost:8081/docs"         -ForegroundColor White
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "提示: 三个服务会分别打开独立窗口" -ForegroundColor Gray
Write-Host "      关闭对应窗口即可停止该服务，或按 Ctrl+C 停止" -ForegroundColor Gray
