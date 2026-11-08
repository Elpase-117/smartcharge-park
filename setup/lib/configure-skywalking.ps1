. "$PSScriptRoot\common.ps1"

function Invoke-SkyWalkingQuery([string]$Query){
    $response=Http-Json 'http://127.0.0.1:12800/graphql' 'POST' (@{query=$Query}|ConvertTo-Json -Compress)
    if($response.PSObject.Properties['errors'] -and $response.errors){throw 'SkyWalking GraphQL query failed; inspect the private OAP log.'}
    if(-not $response.PSObject.Properties['data']){throw 'SkyWalking returned no query data.'}
    $response.data
}
function Initialize-SkyWalkingAgent {
    $agent=Runtime-Path 'skywalking\agent'
    if(-not (Test-Path -LiteralPath (Join-Path $agent 'skywalking-agent.jar'))){throw 'SkyWalking Agent is missing. Run install-and-run.'}
    # Official optional plugins: Gateway4/Reactor context and SpringMVC6 endpoints.
    # Do not enable the standalone WebFlux plugin together with Gateway.
    foreach($name in @('apm-spring-cloud-gateway-4.x-plugin-9.8.0.jar','apm-springmvc-annotation-6.x-plugin-9.8.0.jar')){
        $source=Join-Path $agent ('optional-plugins\'+$name)
        $destination=Join-Path $agent ('plugins\'+$name)
        if(-not (Test-Path -LiteralPath $source)){throw ('Official Agent plugin missing: '+$name)}
        if(-not (Test-Path -LiteralPath $destination) -or (Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $destination).Hash){Copy-Item -LiteralPath $source -Destination $destination -Force}
    }
}
function Get-SkyWalkingAgentArguments([string]$Service){
    if($Service -notin $script:Services){throw 'Unknown SkyWalking application service.'}
    $logDir=Runtime-Path ('logs\skywalking-agent\'+$Service)
    [void][IO.Directory]::CreateDirectory($logDir)
    @(
        ('-javaagent:'+(Runtime-Path 'skywalking\agent\skywalking-agent.jar')),
        ('-Dskywalking.agent.service_name='+$Service),
        ('-Dskywalking.agent.instance_name='+$Service+'-local'),
        '-Dskywalking.collector.backend_service=127.0.0.1:11800',
        ('-Dskywalking.logging.dir='+$logDir),
        '-Dskywalking.logging.max_file_size=10485760',
        '-Dskywalking.logging.max_history_files=3',
        '-Dskywalking.agent.sample_n_per_3_secs=-1',
        '-Dskywalking.plugin.http.include_http_headers=',
        '-Dskywalking.plugin.tomcat.collect_http_params=false',
        '-Dskywalking.plugin.springmvc.collect_http_params=false',
        '-Dskywalking.plugin.httpclient.collect_http_params=false',
        '-Dskywalking.plugin.jdbc.trace_sql_parameters=false'
    )
}
function Start-SkyWalking {
    Initialize-SkyWalkingAgent
    $oap=Runtime-Path 'skywalking\oap'
    foreach($dir in @('data\skywalking','data\skywalking\home','tmp\skywalking','logs\skywalking-oap','logs\skywalking-ui')){[void][IO.Directory]::CreateDirectory((Runtime-Path $dir))}
    $java=Runtime-Path 'jdk\bin\java.exe'
    $envs=@{
        SW_CLUSTER='standalone';SW_STORAGE='h2';SW_TELEMETRY='none';SW_HEALTH_CHECKER='default'
        SW_PROMQL='-';SW_LOGQL='-';SW_RECEIVER_AWS_FIREHOSE='-'
        SW_CORE_REST_HOST='127.0.0.1';SW_CORE_GRPC_HOST='127.0.0.1'
        SW_CORE_REST_PORT='12800';SW_CORE_GRPC_PORT='11800'
        SW_CORE_GRPC_THREAD_POOL_SIZE='4';SW_CORE_REST_MAX_THREADS='16'
        SW_STORAGE_H2_URL=('jdbc:h2:file:'+(Runtime-Path 'data\skywalking\oap').Replace('\','/')+';DB_CLOSE_ON_EXIT=TRUE;DATABASE_TO_UPPER=FALSE')
        JAVA_TOOL_OPTIONS='';_JAVA_OPTIONS='';JDK_JAVA_OPTIONS=''
    }
    $arguments=@('-Xms128m','-Xmx512m','-Dfile.encoding=UTF-8',('-Duser.home='+(Runtime-Path 'data\skywalking\home')),('-Djava.io.tmpdir='+(Runtime-Path 'tmp\skywalking')),('-Doap.logDir='+(Runtime-Path 'logs\skywalking-oap')),'-cp',((Join-Path $oap 'config')+';'+(Join-Path $oap 'oap-libs\*')),'org.apache.skywalking.oap.server.starter.OAPServerStartUp')
    Start-Owned 'skywalking-oap' $java $arguments $envs $oap
    Wait-Ready 'skywalking-oap' {$null=Invoke-SkyWalkingQuery '{ listServices { name } }';Test-Tcp 11800}
    $web=Join-Path $oap 'webapp'
    $arguments=@('-Xms64m','-Xmx192m','-Dfile.encoding=UTF-8',('-Djava.io.tmpdir='+(Runtime-Path 'tmp\skywalking')),('-Dwebapp.logDir='+(Runtime-Path 'logs\skywalking-ui')),'-cp',($web+';'+(Join-Path $web 'skywalking-webapp.jar')),'org.apache.skywalking.oap.server.webapp.ApplicationStartUp')
    Start-Owned 'skywalking-ui' $java $arguments @{SW_SERVER_PORT='8080';SW_OAP_ADDRESS='http://127.0.0.1:12800';JAVA_TOOL_OPTIONS='';_JAVA_OPTIONS='';JDK_JAVA_OPTIONS=''} $web
    Wait-Ready 'skywalking-ui' {(Invoke-WebRequest 'http://127.0.0.1:8080' -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200}
}
function Check-SkyWalking {
    foreach($name in @('skywalking-oap','skywalking-ui')){if($null -eq (Get-OwnedProcess $name)){throw ('SkyWalking private process identity failed: '+$name)}}
    $agentPath=Runtime-Path 'skywalking\agent\skywalking-agent.jar'
    $agents=@(foreach($name in $script:Services){
        $process=Get-OwnedProcess $name
        if($null -eq $process -or -not $process.CommandLine.Contains('-javaagent:'+$agentPath) -or -not $process.CommandLine.Contains('-Dskywalking.agent.service_name='+$name)){throw ('SkyWalking Agent command line not verified: '+$name)}
        @{service=$name;pid=$process.ProcessId;agent='verified'}
    })
    Wait-Ready 'skywalking-oap' {
        $names=@((Invoke-SkyWalkingQuery '{ listServices { name } }').listServices|ForEach-Object {$_.name})
        @($script:Services|Where-Object {$_ -notin $names}).Count -eq 0
    } 120
    if((Invoke-WebRequest 'http://127.0.0.1:8080' -UseBasicParsing -TimeoutSec 5).StatusCode -ne 200){throw 'SkyWalking UI HTTP check failed.'}
    $result=@{status='PASS';oap='http://127.0.0.1:12800';grpc='127.0.0.1:11800';ui='http://127.0.0.1:8080';agents=$agents;storage='embedded H2 file under .runtime/data/skywalking';allFiveServicesObserved=$true;businessTraceVerifiedByHealthCheck=$false}
    Save-State 'skywalking-health' $result
    $result
}
