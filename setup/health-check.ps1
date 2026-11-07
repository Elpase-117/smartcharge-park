[CmdletBinding()]param()
. "$PSScriptRoot\lib\configure-mysql.ps1"
. "$PSScriptRoot\lib\configure-nacos.ps1"
. "$PSScriptRoot\lib\health.ps1"
try{
    $script:Secrets=Read-Json (Runtime-Path 'secrets\credentials.json')
    $null=Check-Installation
    Write-Host 'SmartCharge Park health: PASS; details: .runtime/state/health-check.json'
    exit 0
}catch{Write-Host ('SmartCharge Park health: FAIL - '+(Redact $_.Exception.Message));exit 1}
