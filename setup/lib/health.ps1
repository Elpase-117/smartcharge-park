. "$PSScriptRoot\common.ps1"
function Check-Installation {
    $checks=@()
    foreach($name in @('mysql','nacos','seata')+$script:Services+@('frontend')){
        if($null -eq (Get-OwnedProcess $name)){throw ('Health failed: '+$name+' is not a verified private Runtime process.')}
    }
    if((Invoke-MySql 'SELECT 1;') -ne '1'){throw 'MySQL health failed.'}
    $checks+=@{component='mysql';status='PASS';port=3306}
    $null=Nacos-Login 'smartcharge_app' $script:Secrets.nacosApp
    foreach($name in $script:Services+@('seata-server')){
        $group=if($name -eq 'seata-server'){'SEATA_GROUP'}else{'DEFAULT_GROUP'}
        $hosts=@(Get-NacosInstances $name $group)
        if($hosts.Count -ne 1 -or -not $hosts[0].healthy){throw ('Nacos health failed: '+$name+' must be 1/1 healthy.')}
        $checks+=@{component=('nacos/'+$name);status='PASS';instances=1;healthy=1}
    }
    if(-not (Test-Tcp 8091)){throw 'Seata transaction port 8091 is unavailable.'}
    foreach($name in $script:Services){if(-not (Test-Health $script:Ports[$name][0])){throw ('Actuator health is not UP: '+$name)};$checks+=@{component=$name;status='UP';port=$script:Ports[$name][0]}}
    $dbState=@()
    foreach($db in @('user_db','station_db','reservation_db','order_db')){
        $history=Invoke-MySql ('SELECT COUNT(*),SUM(success=0) FROM '+$db+'.flyway_schema_history;')
        if($history -notmatch '^(\d+)\s+0$' -or [int]$Matches[1] -lt 1){throw ('Flyway health failed: '+$db)}
        $dbState+=@{schema=$db;successfulHistory=$true;historySummary=$history}
    }
    $stations=Http-Json 'http://127.0.0.1:8084/api/stations'
    if(@($stations).Count -lt 1){throw 'Gateway stations lookup returned no seed data.'}
    $accounts=@()
    foreach($username in @('driver','driver2')){
        $login=Http-Json 'http://127.0.0.1:8084/api/auth/login' 'POST' (@{username=$username;password='123456'}|ConvertTo-Json)
        $me=Http-Json 'http://127.0.0.1:8084/api/auth/me' 'GET' $null @{Authorization=('Bearer '+$login.token)}
        if($me.userId -ne $login.userId){throw 'Gateway identity health failed.'}
        $accounts+=@{username=$username;userId=$login.userId;me='PASS'}
    }
    if($accounts[0].userId -eq $accounts[1].userId){throw 'Demo identities must differ.'}
    $date=(Get-Date).AddDays(1).ToString('yyyy-MM-dd')
    $stationId=@($stations)[0].id
    $availability=Http-Json ('http://127.0.0.1:8084/api/reservations/availability?stationId='+$stationId+'&date='+$date)
    if(@($availability).Count -lt 1){throw 'Gateway availability health failed.'}
    $readback=@{}
    foreach($type in @('flow','degrade','system')){$value=Http-Json ('http://127.0.0.1:8719/getRules?type='+$type);if($value -is [string]){$value=$value|ConvertFrom-Json};$readback[$type]=@($value)}
    if(@($readback.flow|Where-Object {$_.resource -eq '/api/reservations/availability' -and $_.grade -eq 1 -and $_.count -eq 2}).Count -ne 1 -or
       @($readback.flow|Where-Object {$_.resource -eq 'GET:http://station-service/api/stations/{id}' -and $_.grade -eq 0 -and $_.count -eq 1}).Count -ne 1 -or
       @($readback.degrade|Where-Object {$_.count -eq 3 -and $_.grade -eq 2 -and $_.minRequestAmount -eq 5 -and $_.statIntervalMs -eq 5000 -and $_.timeWindow -eq 30}).Count -ne 1 -or
       @($readback.system|Where-Object {$_.highestCpuUsage -eq 0.95}).Count -ne 1){throw 'Sentinel rule readback does not match the approved rules.'}
    $html=Invoke-WebRequest 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 10
    if($html.StatusCode -ne 200 -or $html.Content -notmatch '<html'){throw 'Frontend HTML health failed.'}
    $bundle=Get-ChildItem -LiteralPath (Join-Path $script:Project 'frontend\dist\assets') -File -Filter '*.js'
    $baseFound=$false
    foreach($file in $bundle){if([IO.File]::ReadAllText($file.FullName).Contains('http://127.0.0.1:8084/api')){$baseFound=$true}}
    if(-not $baseFound){throw 'Frontend build does not contain the configured Gateway API base.'}
    $counts=Invoke-MySql 'SELECT (SELECT COUNT(*) FROM user_db.app_user),(SELECT COUNT(*) FROM station_db.station),(SELECT COUNT(*) FROM reservation_db.reservation),(SELECT COUNT(*) FROM order_db.demo_order);'
    $result=[ordered]@{status='PASS';checkedAt=(Get-Date -Format o);infrastructure=$checks;databases=$dbState;demoAccounts=$accounts;countsUserStationReservationOrder=$counts;gatewayStations='PASS';gatewayMe='PASS';gatewayAvailability='PASS';frontendHtml='PASS';frontendApiBase='http://127.0.0.1:8084/api';sentinelDashboard='not bundled; not a core dependency';sentinelRules=$readback;automaticReservationPost=$false}
    Save-State 'health-check' $result
    Write-Stage 'Health Check: PASS (no reservation or order was created)'
    $result
}
