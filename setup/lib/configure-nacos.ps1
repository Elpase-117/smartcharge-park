. "$PSScriptRoot\common.ps1"
function Invoke-Nacos([string]$Path,[string]$Method='GET',[hashtable]$Parameters=@{},[string]$Token=''){
    $values=@{};foreach($key in $Parameters.Keys){$values[$key]=$Parameters[$key]}
    if($Token){$values.accessToken=$Token}
    $url='http://127.0.0.1:8848/nacos'+$Path
    try{
        if($Method -eq 'GET'){
            if($values.Count){$url+='?'+(@($values.Keys|ForEach-Object {[uri]::EscapeDataString($_)+'='+[uri]::EscapeDataString([string]$values[$_])})-join '&')}
            Http-Json $url
        }else{Http-Json $url $Method $values}
    }catch{
        $failure=$_
        if($failure.ErrorDetails -and $failure.ErrorDetails.Message){
            try{$parsed=$failure.ErrorDetails.Message|ConvertFrom-Json;if($parsed.PSObject.Properties['code']){return $parsed}}catch{}
        }
        if($failure.Exception.PSObject.Properties['Response'] -and $failure.Exception.Response){
            try{$reader=New-Object IO.StreamReader($failure.Exception.Response.GetResponseStream());$body=$reader.ReadToEnd();$reader.Dispose();$parsed=$body|ConvertFrom-Json;if($parsed.PSObject.Properties['code']){return $parsed}}catch{}
        }
        throw ('Nacos API failed: '+$Method+' '+$Path+' (details withheld to protect credentials).')
    }
}
function Nacos-Login([string]$User,[string]$Password){
    $result=Invoke-Nacos '/v3/auth/user/login' 'POST' @{username=$User;password=$Password}
    if($result.PSObject.Properties['accessToken']){return $result.accessToken}
    if($result.PSObject.Properties['data'] -and $result.data.PSObject.Properties['accessToken']){return $result.data.accessToken}
    throw 'Nacos authentication did not return an access token.'
}
function Start-Nacos {
    $properties=@'
nacos.server.main.port=8848
nacos.console.port=8081
nacos.server.contextPath=/nacos
nacos.console.contextPath=
nacos.standalone=true
nacos.inetutils.ip-address=127.0.0.1
server.address=127.0.0.1
nacos.core.auth.enabled=true
nacos.core.auth.admin.enabled=true
nacos.core.auth.console.enabled=true
nacos.plugin.auth.type=nacos
nacos.core.auth.server.identity.key=${NACOS_IDENTITY_KEY}
nacos.core.auth.server.identity.value=${NACOS_IDENTITY_VALUE}
nacos.plugin.auth.nacos.token.secret.key=${NACOS_TOKEN_SECRET}
server.tomcat.accesslog.enabled=false
'@
    # Nacos 3.3 EnvUtil resolves additional-location as a DIRECTORY and reads
    # application.properties from it, even when Spring itself accepts a file.
    $configDirectory=Runtime-Path 'config\nacos'
    [void][IO.Directory]::CreateDirectory($configDirectory)
    Write-Utf8 (Join-Path $configDirectory 'application.properties') $properties
    $envs=@{NACOS_IDENTITY_KEY=$script:Secrets.identityKey;NACOS_IDENTITY_VALUE=$script:Secrets.identityValue;NACOS_TOKEN_SECRET=$script:Secrets.nacosToken;JAVA_TOOL_OPTIONS='';_JAVA_OPTIONS='';JDK_JAVA_OPTIONS=''}
    $nacosHome=(Runtime-Path 'nacos').Replace('\','/')
    $arguments=@('-Xms128m','-Xmx512m','-Dfile.encoding=UTF-8','--add-opens=java.base/java.lang=ALL-UNNAMED','--add-opens=java.base/java.lang.reflect=ALL-UNNAMED','--add-opens=java.base/java.util=ALL-UNNAMED','-Dnacos.standalone=true','-Dnacos.deployment.type=merged',('-Dnacos.home='+$nacosHome),('-Dloader.path='+$nacosHome+'/plugins,'+$nacosHome+'/plugins/health,'+$nacosHome+'/plugins/cmdb,'+$nacosHome+'/plugins/selector'),('-Duser.home='+(Runtime-Path 'data\nacos-client').Replace('\','/')),'-jar',(Runtime-Path 'nacos\target\nacos-server.jar'),('--spring.config.additional-location=file:'+($configDirectory.Replace('\','/'))+'/'))
    Start-Owned 'nacos' (Runtime-Path 'jdk\bin\java.exe') $arguments $envs (Runtime-Path 'nacos')
    $ready=Runtime-Path 'state\nacos-initialized.json'
    Wait-Ready 'nacos' {
        if(-not (Test-Tcp 8848) -or -not (Test-Tcp 8081)){return $false}
        try{$script:NacosToken=Nacos-Login 'nacos' $script:Secrets.nacosAdmin;return $true}catch{
            if(Test-Path -LiteralPath $ready){return $false}
            try{$null=Invoke-Nacos '/v3/auth/user/admin' 'POST' @{password=$script:Secrets.nacosAdmin}}catch{}
            return $false
        }
    } 240
    Save-Json $ready @{initialized=$true;auth=$true;version='3.3.0'}
}
function Configure-Nacos {
    $script:NacosToken=Nacos-Login 'nacos' $script:Secrets.nacosAdmin
    $appReady=Runtime-Path 'state\nacos-app-initialized.json'
    if(-not (Test-Path -LiteralPath $appReady)){
        try{$null=Nacos-Login 'smartcharge_app' $script:Secrets.nacosApp}catch{
            $null=Invoke-Nacos '/v3/auth/user' 'POST' @{username='smartcharge_app';password=$script:Secrets.nacosApp} $script:NacosToken
        }
        $null=Invoke-Nacos '/v3/auth/role' 'POST' @{username='smartcharge_app';role='SMARTCHARGE_APP'} $script:NacosToken
        foreach($resource in @('public:DEFAULT_GROUP:naming/*','public:SEATA_GROUP:naming/*','public:DEFAULT_GROUP:config/*')){
            $null=Invoke-Nacos '/v3/auth/permission' 'POST' @{role='SMARTCHARGE_APP';resource=$resource;action='rw'} $script:NacosToken
        }
        Save-Json $appReady @{username='smartcharge_app';role='SMARTCHARGE_APP';initialized=$true}
    }
    # Nacos 3.3 normalizes batch listening's empty group/name to namespace-level wildcards.
    $listenPermission=@{role='SMARTCHARGE_APP';resource='public:*:config/*';action='r'}
    $listenExists=Invoke-Nacos '/v3/auth/permission' 'GET' $listenPermission $script:NacosToken
    if($listenExists.code -ne 0){throw 'Could not verify the config-listener permission.'}
    if(-not $listenExists.data){
        $granted=Invoke-Nacos '/v3/auth/permission' 'POST' $listenPermission $script:NacosToken
        if($granted.code -ne 0){throw 'Could not grant the config-listener read permission.'}
    }
    # The default auth plugin may briefly retain the negative lookup performed
    # before first user creation. Wait for authentication instead of resetting it.
    Wait-Ready 'nacos' {try{$script:NacosAppToken=Nacos-Login 'smartcharge_app' $script:Secrets.nacosApp;return $true}catch{return $false}} 60
    $parameters=@{dataId='reservation-service.yaml';groupName='DEFAULT_GROUP';namespaceId='public'}
    $exists=Invoke-Nacos '/v3/admin/cs/config' 'GET' $parameters $script:NacosToken
    $created=$false
    if($exists.code -eq 0){
        $content=if($exists.data -is [string]){$exists.data}else{$exists.data.content}
        if($content -notmatch '(?m)^\s*(max-days|reservation\.max-days)\s*[:=]\s*\d+') {throw 'Existing reservation-service.yaml has no valid max-days value; it was preserved. Review Nacos configuration.'}
    }elseif($exists.code -in @(20004)){
        $parameters.content="reservation:`n  max-days: 7`n";$parameters.type='yaml'
        $published=Invoke-Nacos '/v3/admin/cs/config' 'POST' $parameters $script:NacosToken
        if($published.code -ne 0){throw 'Could not publish the initial reservation configuration.'}
        $created=$true;$content=$parameters.content
    }else{throw 'Unexpected Nacos configuration response; nothing was overwritten.'}
    Save-State 'nacos-config' @{dataId='reservation-service.yaml';group='DEFAULT_GROUP';namespace='public';created=$created;content=$content;authVerified=$true}
    Write-Stage ('Nacos: authentication verified; reservation-service.yaml '+$(if($created){'created'}else{'preserved'}))
}
function Get-NacosInstances([string]$Name,[string]$Group='DEFAULT_GROUP'){
    $token=Nacos-Login 'nacos' $script:Secrets.nacosAdmin
    $result=Invoke-Nacos '/v3/admin/ns/instance/list' 'GET' @{serviceName=$Name;groupName=$Group;namespaceId='public'} $token
    if($result.code -ne 0){return @()}
    @($result.data|Where-Object {$null -ne $_})
}
