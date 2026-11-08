[CmdletBinding()]param([switch]$NoBrowser)
. "$PSScriptRoot\lib\download-runtime.ps1"
. "$PSScriptRoot\lib\configure-mysql.ps1"
. "$PSScriptRoot\lib\configure-nacos.ps1"
. "$PSScriptRoot\lib\configure-seata.ps1"
. "$PSScriptRoot\lib\application.ps1"
. "$PSScriptRoot\lib\health.ps1"
try{
    Write-Host '[1/10] Preflight'
    Assert-Platform;Enter-OperationLock;Assert-Ports
    $running=@($script:Ports.Keys|Where-Object {$null -ne (Get-OwnedProcess $_)})
    if($running.Count){
        & "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'stop-all.ps1') -InstallerOwnsLock
        if($LASTEXITCODE -ne 0){throw 'Stop the existing verified Runtime before reinstalling.'}
    }
    Initialize-Folders
    Write-Stage '[2/10] Download and verify private Runtime'
    Install-Runtimes
    Write-Stage '[3/10] Create or reuse private credentials'
    Initialize-Secrets
    Write-Stage '[4/10] Initialize private MySQL and four schemas'
    Start-MySql -AllowInitialize;Configure-Databases
    Write-Stage '[5/10] Start Nacos and preserve/create configuration'
    Start-Nacos;Configure-Nacos
    Write-Stage '[6/10] Start Seata 2.5.0 with isolated Nacos 3.0.3 libraries'
    Start-Seata
    Write-Stage '[7/10] Build Java and Frontend with private tools'
    Build-Application
    Write-Stage '[8/10] Start services and Frontend; restore Sentinel rules'
    Start-SkyWalking
    Start-Applications
    Write-Stage '[9/10] Health Check'
    $null=Check-Installation
    Save-State 'installation-complete' @{complete=$true;completedAt=(Get-Date -Format o)}
    Write-Stage '[10/10] Ready'
    Write-Host 'SmartCharge Park installation: PASS'
    Write-Host 'Frontend: http://127.0.0.1:5173'
    Write-Host 'Gateway: http://127.0.0.1:8084/api'
    Write-Host 'Nacos Console: http://127.0.0.1:8081 (credentials in .runtime/secrets; never printed)'
    Write-Host 'SkyWalking UI: http://127.0.0.1:8080'
    Write-Host 'Sentinel: embedded client; approved rules restored; Dashboard not bundled'
    Write-Host ('Logs: '+(Runtime-Path 'logs'))
    if(-not $NoBrowser){Start-Process 'http://127.0.0.1:5173'}
    exit 0
}catch{Write-Host ('SmartCharge Park installation: FAIL - '+(Redact $_.Exception.Message));Write-Host ('Logs: '+(Runtime-Path 'logs'));exit 1}
