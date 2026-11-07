Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
# Load the inbox utility module explicitly: Get-FileHash is a script export in
# Windows PowerShell 5.1 and must not depend on inherited module discovery paths.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
$script:Project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..')).TrimEnd('\')
$script:Runtime=Join-Path $script:Project '.runtime'
$script:Setup=Join-Path $script:Project 'setup'
$script:Services=@('user-service','station-service','order-service','reservation-service','gateway-service')
$script:Ports=@{'mysql'=@(3306);'nacos'=@(8848,8081,9848,9849);'seata'=@(8091);'user-service'=@(8086);'station-service'=@(8082);'order-service'=@(8085);'reservation-service'=@(8083,8719);'gateway-service'=@(8084);'frontend'=@(5173)}

function Write-Utf8([string]$Path,[string]$Text){[IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding $false))}
function Save-Json([string]$Path,$Object){Write-Utf8 $Path ($Object|ConvertTo-Json -Depth 18)}
function Read-Json([string]$Path){Get-Content -LiteralPath $Path -Raw -Encoding UTF8|ConvertFrom-Json}
function Runtime-Path([string]$Relative){Join-Path $script:Runtime $Relative}
function Initialize-Folders {
    foreach($p in @('cache','config','logs','pids','secrets','state','data','tmp','cache\maven-repository','cache\npm')){
        [void][IO.Directory]::CreateDirectory((Runtime-Path $p))
    }
}
function Assert-Platform {
    if(-not [Environment]::Is64BitOperatingSystem -or -not [Environment]::Is64BitProcess){throw 'Windows x64 and 64-bit PowerShell are required.'}
    if($PSVersionTable.PSVersion.Major -lt 5){throw 'Windows PowerShell 5.1 or newer is required.'}
}
function Enter-OperationLock {
    $name='Local\SmartChargePark-'+(Get-TextHash $script:Project.ToLowerInvariant()).Substring(0,24)
    $script:OperationMutex=New-Object Threading.Mutex($false,$name)
    try{$entered=$script:OperationMutex.WaitOne(0)}catch [Threading.AbandonedMutexException]{$entered=$true}
    if(-not $entered){$script:OperationMutex.Dispose();throw 'Another install/start/stop operation is active for this project. Wait for it to finish.'}
}
function Write-Stage([string]$Message){Write-Host $Message; if(Test-Path -LiteralPath (Runtime-Path 'logs')){Add-Content -LiteralPath (Runtime-Path 'logs\setup-events.log') -Value ((Get-Date -Format o)+' '+$Message) -Encoding UTF8}}
function Get-TextHash([string]$Text){$s=[Security.Cryptography.SHA256]::Create();try{([BitConverter]::ToString($s.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-','')}finally{$s.Dispose()}}
function New-RandomSecret([int]$Length=32){$bytes=New-Object byte[] $Length;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()};([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()}
function Protect-Secrets {
    $path=Runtime-Path 'secrets'
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $null=& "$env:SystemRoot\System32\icacls.exe" $path '/inheritance:r' '/grant:r' ('*'+$sid+':(OI)(CI)F') '/Q' 2>&1
    $failed=($LASTEXITCODE -ne 0)
    # File ACEs must not use directory-only inheritance flags. Repair/restrict
    # existing files explicitly, without removing access from their owner.
    foreach($file in Get-ChildItem -LiteralPath $path -File -Force){
        $null=& "$env:SystemRoot\System32\icacls.exe" $file.FullName '/inheritance:r' '/grant:r' ('*'+$sid+':F') '/Q' 2>&1
        if($LASTEXITCODE -ne 0){$failed=$true}
    }
    if($failed){Write-Stage 'WARNING: secrets ACL restriction failed; keep the project directory private.'}
}
function Initialize-Secrets {
    $path=Runtime-Path 'secrets\credentials.json'
    Protect-Secrets
    if(-not (Test-Path -LiteralPath $path)){
        Save-Json $path ([ordered]@{mysqlRoot=(New-RandomSecret);mysqlApp=(New-RandomSecret);nacosAdmin=(New-RandomSecret);nacosApp=(New-RandomSecret);nacosToken=[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes((New-RandomSecret 48)));identityKey=('scp'+(New-RandomSecret 8));identityValue=(New-RandomSecret);seataConsole=(New-RandomSecret);seataToken=[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes((New-RandomSecret 48)))})
    }
    $script:Secrets=Read-Json $path
    Protect-Secrets
}
function Redact([string]$Text){
    if(Get-Variable Secrets -Scope Script -ErrorAction SilentlyContinue){foreach($p in $script:Secrets.PSObject.Properties){if($p.Value -is [string] -and $p.Value.Length -gt 7){$Text=$Text.Replace($p.Value,'[REDACTED]')}}}
    $Text
}
function Quote-Arg([string]$Value){
    # Windows CommandLineToArgvW quoting, including trailing backslashes.
    '"'+[regex]::Replace([regex]::Replace($Value,'(\\*)"','$1$1\"'),'(\\+)$','$1$1')+'"'
}
function Join-Args([string[]]$Values){(@($Values|ForEach-Object {Quote-Arg $_}) -join ' ')}
function Get-OwnedProcess([string]$Name){
    $file=Runtime-Path ('pids\'+$Name+'.json')
    if(-not (Test-Path -LiteralPath $file)){return $null}
    $record=Read-Json $file
    $current=Get-CimInstance Win32_Process -Filter ('ProcessId='+[int]$record.pid) -ErrorAction SilentlyContinue
    if($null -eq $current){return $null}
    $p=Get-Process -Id $current.ProcessId -ErrorAction SilentlyContinue
    if($null -eq $p -or -not $current.ExecutablePath -or -not $current.CommandLine){return $null}
    if($p.StartTime.ToUniversalTime().Ticks -ne [long]$record.startTimeUtcTicks -or
       -not $current.ExecutablePath.Equals([string]$record.executable,[StringComparison]::OrdinalIgnoreCase) -or
       (Get-TextHash $current.CommandLine) -ne $record.commandHash){return $null}
    if(-not $current.ExecutablePath.StartsWith($script:Runtime+'\',[StringComparison]::OrdinalIgnoreCase)){return $null}
    $current
}
function Assert-Ports {
    foreach($name in $script:Ports.Keys){
        $owned=Get-OwnedProcess $name
        foreach($port in $script:Ports[$name]){
            $listeners=@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
            foreach($l in $listeners){
                if($null -eq $owned -or $l.OwningProcess -ne $owned.ProcessId){
                    $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$l.OwningProcess) -ErrorAction SilentlyContinue
                    $processName=if($p){$p.Name}else{'unknown'}
                    throw ('Port '+$port+' is occupied by PID '+$l.OwningProcess+' ('+$processName+'). Release this port and retry. No unknown process was stopped.')
                }
            }
        }
    }
}
function Invoke-PrivateCommand([string]$Exe,[string[]]$Arguments,[string]$WorkingDirectory,[string]$InputText=''){
    $psi=New-Object Diagnostics.ProcessStartInfo
    $psi.FileName=$Exe;$psi.Arguments=Join-Args $Arguments;$psi.WorkingDirectory=$WorkingDirectory
    $psi.UseShellExecute=$false;$psi.CreateNoWindow=$true
    $psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $p=[Diagnostics.Process]::Start($psi)
    if($InputText){$p.StandardInput.Write($InputText)};$p.StandardInput.Close()
    $stdout=$p.StandardOutput.ReadToEndAsync();$stderr=$p.StandardError.ReadToEndAsync();$p.WaitForExit()
    [pscustomobject]@{exit=$p.ExitCode;stdout=$stdout.Result;stderr=$stderr.Result}
}
function Start-Owned([string]$Name,[string]$Exe,[string[]]$Arguments,[hashtable]$Environment=@{},[string]$WorkingDirectory=$script:Project){
    $owned=Get-OwnedProcess $Name
    if($null -ne $owned){Write-Stage ($Name+': already running');return}
    $pidFile=Runtime-Path ('pids\'+$Name+'.json')
    if(Test-Path -LiteralPath $pidFile){Remove-Item -LiteralPath $pidFile -Force;Write-Stage ($Name+': stale or reused PID record removed; existing process was not stopped')}
    foreach($port in $script:Ports[$Name]){if(@(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue).Count){throw ($Name+': port '+$port+' is occupied; see .runtime/logs') }}
    $old=@{}
    try{
        foreach($key in $Environment.Keys){$old[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,[string]$Environment[$key],'Process')}
        $p=Start-Process -FilePath $Exe -ArgumentList (Join-Args $Arguments) -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Runtime-Path ('logs\'+$Name+'.log')) -RedirectStandardError (Runtime-Path ('logs\'+$Name+'.error.log'))
    }finally{foreach($key in $old.Keys){[Environment]::SetEnvironmentVariable($key,$old[$key],'Process')}}
    Start-Sleep -Milliseconds 200
    $ci=Get-CimInstance Win32_Process -Filter ('ProcessId='+$p.Id) -ErrorAction SilentlyContinue
    if($null -eq $ci){throw ($Name+' exited during startup. See .runtime/logs/'+$Name+'.error.log')}
    $p.Refresh()
    Save-Json $pidFile ([ordered]@{component=$Name;pid=$p.Id;executable=$ci.ExecutablePath;startTimeUtcTicks=$p.StartTime.ToUniversalTime().Ticks;commandHash=(Get-TextHash $ci.CommandLine);startedAt=(Get-Date -Format o)})
    Write-Stage ($Name+': started PID '+$p.Id)
}
function Wait-Ready([string]$Name,[scriptblock]$Probe,[int]$Timeout=180){
    $deadline=(Get-Date).AddSeconds($Timeout)
    do{
        if($null -eq (Get-OwnedProcess $Name)){throw ($Name+' exited. Ports: '+($script:Ports[$Name]-join ',')+'; logs: '+(Runtime-Path ('logs\'+$Name+'.log')))}
        try{if(& $Probe){Write-Stage ($Name+': Ready');return}}catch{}
        Start-Sleep -Milliseconds 800
    }while((Get-Date) -lt $deadline)
    throw ($Name+' readiness timeout. Ports: '+($script:Ports[$Name]-join ',')+'; logs: '+(Runtime-Path ('logs\'+$Name+'.log')))
}
function Test-Tcp([int]$Port){$c=New-Object Net.Sockets.TcpClient;try{$a=$c.BeginConnect('127.0.0.1',$Port,$null,$null);if(-not $a.AsyncWaitHandle.WaitOne(500)){return $false};$c.EndConnect($a);$true}catch{$false}finally{$c.Dispose()}}
function Http-Json([string]$Url,[string]$Method='GET',$Body=$null,[hashtable]$Headers=@{}){
    $options=@{Uri=$Url;Method=$Method;Headers=$Headers;TimeoutSec=10;UseBasicParsing=$true}
    if($null -ne $Body){$options.Body=$Body;if($Body -is [string]){$options.ContentType='application/json'}}
    Invoke-RestMethod @options
}
function Test-Health([int]$Port){(Http-Json ('http://127.0.0.1:'+$Port+'/actuator/health')).status -eq 'UP'}
function Set-PrivateBuildEnvironment {
    $env:JAVA_HOME=Runtime-Path 'jdk';$env:PATH=(Runtime-Path 'jdk\bin')+';'+(Runtime-Path 'node')+';'+$env:PATH
    $env:npm_config_cache=Runtime-Path 'cache\npm';$env:npm_config_registry='https://registry.npmjs.org'
    # Both Maven and its forked javac must read UTF-8 response files when the
    # project/repository path contains Chinese characters on Windows Java 17.
    $env:MAVEN_OPTS='-Dfile.encoding=UTF-8';$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8';$env:_JAVA_OPTIONS=$null;$env:JDK_JAVA_OPTIONS=$null
}
function Save-State([string]$Name,$Value){Save-Json (Runtime-Path ('state\'+$Name+'.json')) $Value}
