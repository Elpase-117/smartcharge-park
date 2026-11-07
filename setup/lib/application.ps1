. "$PSScriptRoot\common.ps1"
function Invoke-BuildStep([string]$Exe,[string[]]$Arguments,[string]$WorkingDirectory,[string]$LogName){
    $log=Runtime-Path ('logs\'+$LogName)
    Push-Location -LiteralPath $WorkingDirectory
    $previous=$ErrorActionPreference
    try{$ErrorActionPreference='Continue';& $Exe @Arguments *> $log;$code=$LASTEXITCODE}
    finally{$ErrorActionPreference=$previous;Pop-Location}
    if($code -ne 0){throw ('Build failed (exit '+$code+'). See '+$log)}
    Write-Stage ($LogName+': PASS')
}
function Build-Application {
    Set-PrivateBuildEnvironment
    Write-Utf8 (Runtime-Path 'config\maven-settings.xml') '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"><mirrors><mirror><id>smartcharge-central</id><mirrorOf>*</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>'
    Write-Utf8 (Runtime-Path 'config\user.npmrc') ''
    Write-Utf8 (Runtime-Path 'config\global.npmrc') ''
    $settings=Runtime-Path 'config\maven-settings.xml'
    Invoke-BuildStep (Runtime-Path 'maven\bin\mvn.cmd') @('-B','-s',$settings,'-gs',$settings,('-Dmaven.repo.local='+(Runtime-Path 'cache\maven-repository')),'clean','package','-DskipTests') $script:Project 'maven-build.log'
    $front=Join-Path $script:Project 'frontend'
    if(-not (Test-Path -LiteralPath (Join-Path $front 'package-lock.json'))){throw 'frontend/package-lock.json is required for npm ci.'}
    $lock=(Get-FileHash -LiteralPath (Join-Path $front 'package-lock.json')).Hash
    $env:npm_config_userconfig=Runtime-Path 'config\user.npmrc'
    $env:npm_config_globalconfig=Runtime-Path 'config\global.npmrc'
    $env:VITE_API_BASE_URL='http://127.0.0.1:8084/api'
    Invoke-BuildStep (Runtime-Path 'node\npm.cmd') @('ci','--no-audit','--no-fund') $front 'npm-ci.log'
    Invoke-BuildStep (Runtime-Path 'node\npm.cmd') @('run','build') $front 'frontend-build.log'
    if((Get-FileHash -LiteralPath (Join-Path $front 'package-lock.json')).Hash -ne $lock){throw 'npm ci changed package-lock.json; installation stopped.'}
    [void][IO.Directory]::CreateDirectory((Runtime-Path 'app'))
    foreach($name in $script:Services){
        $jars=@(Get-ChildItem -LiteralPath (Join-Path $script:Project ($name+'\target')) -Filter '*.jar' -File)
        if($jars.Count -ne 1){throw ('Cannot identify build output for '+$name)}
        Copy-Item -LiteralPath $jars[0].FullName -Destination (Runtime-Path ('app\'+$name+'.jar')) -Force
    }
    Save-State 'build' @{apiBase=$env:VITE_API_BASE_URL;packageLock=$lock;frontendDist=(Join-Path $front 'dist');builtAt=(Get-Date -Format o)}
}
function Start-Applications {
    foreach($name in $script:Services){
        $envs=@{NACOS_USERNAME='smartcharge_app';NACOS_PASSWORD=$script:Secrets.nacosApp;NACOS_SERVER_ADDR='127.0.0.1:8848';NACOS_DISCOVERY_IP='127.0.0.1';DB_USERNAME='smartcharge_app';DB_PASSWORD=$script:Secrets.mysqlApp;SERVER_PORT=[string]$script:Ports[$name][0];SERVER_ADDRESS='127.0.0.1';SEATA_ROLLBACK_EXPERIMENT='false';SEATA_ROLLBACK_PAUSE_MS='0';MANAGEMENT_ENDPOINT_SHUTDOWN_ENABLED='true';MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE='health,shutdown';JAVA_TOOL_OPTIONS='';_JAVA_OPTIONS='';JDK_JAVA_OPTIONS='';SENTINEL_DASHBOARD='';SENTINEL_LOG_DIR=(Runtime-Path 'logs\sentinel-client');SENTINEL_TRANSPORT_PORT='8719';SENTINEL_CLIENT_IP='127.0.0.1';SPRING_PROFILES_ACTIVE=''}
        foreach($key in @('USER_DB_URL','STATION_DB_URL','RESERVATION_DB_URL','ORDER_DB_URL','SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_APPLICATION_JSON')){$envs[$key]=''}
        # Explicit datasource URLs override unrelated inherited developer settings.
        if($name -ne 'gateway-service'){
            $db=@{'user-service'='user_db';'station-service'='station_db';'reservation-service'='reservation_db';'order-service'='order_db'}[$name]
            $envs.SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/'+$db+'?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
            $envs.SPRING_DATASOURCE_USERNAME='smartcharge_app';$envs.SPRING_DATASOURCE_PASSWORD=$script:Secrets.mysqlApp
        }
        $clientHome=Runtime-Path ('data\client-'+$name)
        [void][IO.Directory]::CreateDirectory($clientHome)
        $arguments=@('-Xms64m','-Xmx320m','-Dfile.encoding=UTF-8',('-Duser.home='+$clientHome),'-jar',(Runtime-Path ('app\'+$name+'.jar')))
        if(-not (Test-Path -LiteralPath (Runtime-Path ('app\'+$name+'.jar')))){throw ('Built application missing: '+$name+'. Run install-and-run.')}
        Start-Owned $name (Runtime-Path 'jdk\bin\java.exe') $arguments $envs
        $port=$script:Ports[$name][0]
        Wait-Ready $name {Test-Health $port}
        Wait-Ready $name {@(Get-NacosInstances $name|Where-Object {$_.healthy -and $_.port -eq $port}).Count -eq 1}
    }
    Restore-SentinelRules
    $front=Join-Path $script:Project 'frontend'
    if(-not (Test-Path -LiteralPath (Join-Path $front 'dist\index.html'))){throw 'Frontend dist is missing. Run install-and-run.'}
    Start-Owned 'frontend' (Runtime-Path 'node\node.exe') @((Join-Path $front 'node_modules\vite\bin\vite.js'),'preview','--host','127.0.0.1','--port','5173','--strictPort') @{} $front
    Wait-Ready 'frontend' {(Invoke-WebRequest 'http://127.0.0.1:5173' -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200}
}
function Restore-SentinelRules {
    $rules=Read-Json (Join-Path $script:Setup 'config\sentinel-rules.json')
    Wait-Ready 'reservation-service' {Test-Tcp 8719}
    foreach($type in @('flow','degrade','system')){
        $json=ConvertTo-Json -InputObject @($rules.$type) -Depth 10 -Compress
        $null=Http-Json ('http://127.0.0.1:8719/setRules?type='+$type+'&data='+[uri]::EscapeDataString($json))
    }
    Write-Stage 'Sentinel: QPS=2, THREAD=1, exception count=3/min5/5000ms/30s, CPU=0.95 restored; Dashboard not bundled'
}
