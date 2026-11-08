[CmdletBinding()]param([switch]$NoBrowser)
. "$PSScriptRoot\lib\configure-mysql.ps1"
. "$PSScriptRoot\lib\configure-nacos.ps1"
. "$PSScriptRoot\lib\configure-seata.ps1"
. "$PSScriptRoot\lib\application.ps1"
. "$PSScriptRoot\lib\health.ps1"
try{
    Assert-Platform;Enter-OperationLock
    foreach($file in @('state\installation-complete.json','secrets\credentials.json','state\mysql-initialized.json')){if(-not (Test-Path -LiteralPath (Runtime-Path $file))){throw 'Installation is incomplete. Run setup/install-and-run.cmd first.'}}
    Assert-Ports
    $script:Secrets=Read-Json (Runtime-Path 'secrets\credentials.json')
    Start-MySql
    Start-Nacos;Configure-Nacos
    Start-Seata
    Start-SkyWalking
    Start-Applications
    $null=Check-Installation
    Write-Host 'SmartCharge Park start: PASS (no download, build or database initialization)'
    if(-not $NoBrowser){Start-Process 'http://127.0.0.1:5173'}
    exit 0
}catch{Write-Host ('SmartCharge Park start: FAIL - '+(Redact $_.Exception.Message));exit 1}
