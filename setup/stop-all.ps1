[CmdletBinding()]param([switch]$InstallerOwnsLock)
. "$PSScriptRoot\lib\common.ps1"
. "$PSScriptRoot\lib\native-path.ps1"
try{
    if(-not (Test-Path -LiteralPath $script:Runtime)){Write-Host 'SmartCharge Park is not running (no private Runtime).';exit 0}
    if(-not $InstallerOwnsLock){Enter-OperationLock}
    if(Test-Path -LiteralPath (Runtime-Path 'secrets\credentials.json')){$script:Secrets=Read-Json (Runtime-Path 'secrets\credentials.json')}
    $result=@()
    foreach($name in @('frontend','gateway-service','reservation-service','order-service','station-service','user-service','seata','nacos','mysql')){
        $owned=Get-OwnedProcess $name
        $file=Runtime-Path ('pids\'+$name+'.json')
        if($null -eq $owned){if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file -Force};continue}
        $method='verified PID stop'
        $graceful=$false
        if($name -eq 'mysql'){
            $r=Invoke-PrivateCommand (Runtime-Path 'mysql\bin\mysqladmin.exe') @('--defaults-extra-file=secrets/mysql-client.ini','shutdown') (Get-MySqlNativeRoot)
            if($r.exit -ne 0){Write-Stage 'WARNING: MySQL graceful shutdown failed; waiting before the verified-PID fallback'}else{$method='mysqladmin graceful shutdown';$graceful=$true}
        }elseif($name -in $script:Services){
            try{$null=Http-Json ('http://127.0.0.1:'+$script:Ports[$name][0]+'/actuator/shutdown') 'POST' '{}';$method='loopback Actuator graceful shutdown';$graceful=$true}catch{}
        }else{
            $p=Get-Process -Id $owned.ProcessId -ErrorAction SilentlyContinue
            if($p){$null=$p.CloseMainWindow()}
        }
        $deadline=(Get-Date).AddSeconds($(if($graceful){20}else{2}))
        while($null -ne (Get-OwnedProcess $name) -and (Get-Date) -lt $deadline){Start-Sleep -Milliseconds 300}
        $verified=Get-OwnedProcess $name
        if($null -ne $verified){Stop-Process -Id $verified.ProcessId -Force;$method+='; identity-checked fallback';Start-Sleep -Milliseconds 300}
        if($null -ne (Get-OwnedProcess $name)){throw ('Could not stop verified '+$name+' process.')}
        if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file -Force}
        $result+=@{component=$name;pid=$owned.ProcessId;method=$method;stopped=$true}
        Write-Stage ($name+': stopped')
    }
    $left=@(Get-CimInstance Win32_Process|Where-Object {$_.Name -match '^(javaw?|node|mysqld)\.exe$' -and $_.ExecutablePath -and $_.ExecutablePath.StartsWith($script:Runtime+'\',[StringComparison]::OrdinalIgnoreCase)})
    if($left.Count){throw 'Private Runtime process residue found. No unrecorded process was killed; inspect .runtime/pids.'}
    Remove-MySqlNativeAlias
    Save-State 'last-stop' @{status='PASS';time=(Get-Date -Format o);processes=$result;unknownProcessesStopped=0}
    Write-Host 'SmartCharge Park stop: PASS (database data and secrets preserved)'
    exit 0
}catch{Write-Host ('SmartCharge Park stop: FAIL - '+(Redact $_.Exception.Message));exit 1}
