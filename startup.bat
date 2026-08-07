@echo off
chcp 65001 >nul
title NebulaMind · 星云智脑 — 一键启动
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0startup.ps1"
pause