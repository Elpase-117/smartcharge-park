. "$PSScriptRoot\common.ps1"
function Configure-Seata {
    $seataHome=Runtime-Path 'seata'
    $main=@(Get-ChildItem -LiteralPath $seataHome -Recurse -File -Filter 'seata-server.jar'|Where-Object {$_.FullName -notmatch 'namingserver'})
    if($main.Count -ne 1){throw 'Cannot identify the Seata 2.5.0 server entry point.'}
    $lib=Runtime-Path 'seata\compat\lib'
    [void][IO.Directory]::CreateDirectory($lib)
    $done=Runtime-Path 'state\seata-compat.json'
    if(-not (Test-Path -LiteralPath $done)){
        $jars=@(Get-ChildItem -LiteralPath $seataHome -Recurse -File -Filter '*.jar'|Where-Object {
            $_.FullName -ne $main[0].FullName -and $_.FullName -notlike ($lib+'\*') -and $_.FullName -notmatch 'namingserver' -and $_.Name -notmatch '^(nacos-|httpclient5-|httpcore5-|micrometer-)'
        })
        foreach($jar in $jars){Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $lib $jar.Name) -Force}
        # Some releases package dependencies in BOOT-INF/lib; support both layouts.
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip=[IO.Compression.ZipFile]::OpenRead($main[0].FullName)
        try{foreach($entry in $zip.Entries|Where-Object {$_.FullName -like 'BOOT-INF/lib/*.jar'}){
            $name=[IO.Path]::GetFileName($entry.FullName)
            if($name -notmatch '^(nacos-|httpclient5-|httpcore5-|micrometer-)'){
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $name),$true)
            }
        }}finally{$zip.Dispose()}
        if(-not (Test-Path -LiteralPath (Join-Path $lib 'nacos-client-3.0.3.jar'))){throw 'Seata Nacos 3.0.3 compatibility libraries are missing.'}
        if(@(Get-ChildItem -LiteralPath $lib -Filter 'nacos-*-1.*.jar').Count){throw 'An old Nacos client remains in the Seata compatibility classpath.'}
        Save-Json $done @{server='2.5.0';nacosClient='3.0.3';main=$main[0].FullName;libraryCount=@(Get-ChildItem -LiteralPath $lib -Filter '*.jar').Count}
    }
    $config=@'
server:
  port: 8091
  address: 127.0.0.1
spring:
  application:
    name: seata-server
  main:
    web-application-type: none
logging:
  file:
    path: ${SEATA_LOG_DIR}
console:
  user:
    username: smartcharge_admin
    password: ${SEATA_CONSOLE_PASSWORD}
seata:
  security:
    secretKey: ${SEATA_TOKEN_SECRET}
  config:
    type: file
  registry:
    type: nacos
    nacos:
      application: seata-server
      server-addr: 127.0.0.1:8848
      group: SEATA_GROUP
      namespace: ""
      cluster: default
      username: smartcharge_app
      password: ${NACOS_PASSWORD}
  server:
    service-port: 8091
  store:
    mode: file
    file:
      dir: ${SEATA_DATA_DIR}
'@
    Write-Utf8 (Runtime-Path 'config\seata.yml') $config
    $script:SeataMain=$main[0].FullName
}
function Start-Seata {
    Configure-Seata
    $envs=@{NACOS_PASSWORD=$script:Secrets.nacosApp;SEATA_CONSOLE_PASSWORD=$script:Secrets.seataConsole;SEATA_TOKEN_SECRET=$script:Secrets.seataToken;SEATA_DATA_DIR=(Runtime-Path 'data\seata').Replace('\','/');SEATA_LOG_DIR=(Runtime-Path 'logs\seata').Replace('\','/');JAVA_TOOL_OPTIONS='';_JAVA_OPTIONS='';JDK_JAVA_OPTIONS=''}
    # Seata's own argument parser only accepts its documented -h/-p/-m flags.
    # Spring configuration is injected as a JVM property before -jar.
    $arguments=@('-Xms128m','-Xmx512m','-Dfile.encoding=UTF-8',('-Dloader.path='+(Runtime-Path 'seata\compat\lib')),('-Dapp.home='+(Runtime-Path 'seata')),('-Dbasedir='+(Runtime-Path 'seata')),('-Dlog.home='+(Runtime-Path 'logs\seata')),('-Dspring.config.location=file:'+(Runtime-Path 'config\seata.yml').Replace('\','/')),'-Dseata.ip=127.0.0.1','-jar',$script:SeataMain,'-h','127.0.0.1','-p','8091','-m','file')
    Start-Owned 'seata' (Runtime-Path 'jdk\bin\java.exe') $arguments $envs (Runtime-Path 'seata')
    Wait-Ready 'seata' {
        if(-not (Test-Tcp 8091)){return $false}
        $hosts=@(Get-NacosInstances 'seata-server' 'SEATA_GROUP')
        @($hosts|Where-Object {$_.healthy -and $_.port -eq 8091}).Count -eq 1
    }
}
