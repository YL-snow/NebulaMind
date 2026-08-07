$ErrorActionPreference = "Stop"
try {
    docker save -o "C:\projects\NebulaMind2\NebulaMind\nebulamind-frontend.tar" nebulamind-frontend:latest
    if (Test-Path "C:\projects\NebulaMind2\NebulaMind\nebulamind-frontend.tar") {
        $size = (Get-Item "C:\projects\NebulaMind2\NebulaMind\nebulamind-frontend.tar").Length
        "SUCCESS: $([math]::Round($size/1MB,1)) MB" | Out-File "C:\projects\NebulaMind2\NebulaMind\deploy_result.txt"
    } else {
        "FAIL: tar not created" | Out-File "C:\projects\NebulaMind2\NebulaMind\deploy_result.txt"
    }
} catch {
    "ERROR: $_" | Out-File "C:\projects\NebulaMind2\NebulaMind\deploy_result.txt"
}
