. "$PSScriptRoot\common.ps1"
function Remove-RuntimeTemporary([string]$Path){
    $full=[IO.Path]::GetFullPath($Path)
    $allowed=Runtime-Path 'tmp'
    if(-not $full.StartsWith($allowed+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Refusing to remove a non-staging directory.'}
    if(Test-Path -LiteralPath $full){Remove-Item -LiteralPath $full -Recurse -Force}
}
function Install-Runtimes {
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    if(-not ('SmartChargeArchive' -as [type])){Add-Type -TypeDefinition ([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'archive.cs'))) -ReferencedAssemblies @('System','System.Core','System.IO.Compression','System.IO.Compression.FileSystem')}
    $manifest=Read-Json (Join-Path $script:Setup 'install-manifest.json')
    $verification=@()
    foreach($a in $manifest.artifacts){
        if($a.archive -ne [IO.Path]::GetFileName($a.archive) -or $a.target -notin @('jdk','maven','node','mysql','nacos','seata','seata/compat/lib')){throw 'Invalid Runtime archive name or target.'}
        $uri=[uri]$a.url
        if($uri.Scheme -ne 'https' -or $uri.Host -notin @('github.com','nodejs.org','archive.apache.org','cdn.mysql.com','repo.maven.apache.org')){throw ('Unapproved artifact source: '+$a.name)}
        $length=@{'SHA256'=64;'SHA512'=128;'MD5'=32}[$a.algorithm]
        if(-not $length -or $a.checksum -notmatch ('^[a-fA-F0-9]{'+$length+'}$')){throw ('Invalid upstream checksum lock: '+$a.name)}
        $cache=Runtime-Path ('cache\'+$a.archive)
        if(Test-Path -LiteralPath $cache){
            if((Get-FileHash -LiteralPath $cache -Algorithm $a.algorithm).Hash -ne $a.checksum){Remove-Item -LiteralPath $cache -Force;Write-Stage ($a.name+': corrupt cache removed')}
        }
        $action='cache-reused'
        if(-not (Test-Path -LiteralPath $cache)){
            Write-Stage ('Downloading '+$a.name+' '+$a.version)
            $partial=$cache+'.partial'
            try{
                $curl=Join-Path $env:SystemRoot 'System32\curl.exe'
                if($a.PSObject.Properties['downloadMode'] -and $a.downloadMode -eq 'range'){
                    [SmartChargeArchive]::DownloadRanges($a.url,$partial,[long]$a.size)
                }elseif(Test-Path -LiteralPath $curl){
                    $r=Invoke-PrivateCommand $curl @('--fail','--location','--proto','=https','--proto-redir','=https','--connect-timeout','30','--max-time','900','--retry','2','--retry-delay','3','--silent','--show-error','--output',$partial,$a.url) $script:Project
                    if($r.exit -ne 0){throw 'Windows HTTPS download failed.'}
                }else{
                    $request=[Net.HttpWebRequest]::Create($a.url)
                    $request.Timeout=60000;$request.ReadWriteTimeout=60000;$request.UserAgent='SmartCharge-Park-Setup/1'
                    $response=$request.GetResponse()
                    try{
                        if($response.ResponseUri.Scheme -ne 'https'){throw 'Non-HTTPS redirect rejected.'}
                        $input=$response.GetResponseStream();$output=[IO.File]::Create($partial)
                        try{$input.CopyTo($output)}finally{$output.Dispose();$input.Dispose()}
                    }finally{$response.Dispose()}
                }
            }catch{
                if(Test-Path -LiteralPath $partial){Remove-Item -LiteralPath $partial -Force}
                throw ('Download failed: '+$a.name+'. Retry install-and-run; existing database data is preserved.')
            }
            if((Get-FileHash -LiteralPath $partial -Algorithm $a.algorithm).Hash -ne $a.checksum){Remove-Item -LiteralPath $partial -Force;throw ('Checksum mismatch: '+$a.name+'. Corrupt download removed; extraction was not attempted.')}
            Move-Item -LiteralPath $partial -Destination $cache
            $action='downloaded'
        }
        $actual=(Get-FileHash -LiteralPath $cache -Algorithm $a.algorithm).Hash
        if($actual -ne $a.checksum){throw ('Checksum mismatch: '+$a.name)}
        Write-Stage ($a.name+': '+$a.algorithm+' PASS ('+$action+')')
        $target=Runtime-Path $a.target
        if($a.type -eq 'jar'){
            [void][IO.Directory]::CreateDirectory($target)
            $dest=Join-Path $target $a.archive
            if(-not (Test-Path -LiteralPath $dest) -or (Get-FileHash -LiteralPath $dest -Algorithm $a.algorithm).Hash -ne $a.checksum){Copy-Item -LiteralPath $cache -Destination $dest -Force}
        } else {
            $stamp=Runtime-Path ('state\artifact-'+$a.name+'.json')
            if(Test-Path -LiteralPath $stamp){
                $locked=Read-Json $stamp
                if($locked.checksum -ne $a.checksum -or -not (Test-Path -LiteralPath $target)){throw ('Installed Runtime differs from its lock: '+$a.name+'. No data was reset.')}
            } else {
                if(Test-Path -LiteralPath $target){throw ('Unrecorded Runtime directory exists: '+$target+'. Review it before continuing.')}
                $stage=Runtime-Path ('tmp\extract-'+$a.name)
                Remove-RuntimeTemporary $stage
                [SmartChargeArchive]::Extract($cache,$stage,($a.type -eq 'tar.gz'))
                $children=@(Get-ChildItem -LiteralPath $stage -Force)
                if($children.Count -eq 1 -and $children[0].PSIsContainer){Move-Item -LiteralPath $children[0].FullName -Destination $target;Remove-RuntimeTemporary $stage}
                else{Move-Item -LiteralPath $stage -Destination $target}
                Save-Json $stamp @{version=$a.version;checksum=$a.checksum;source=$a.url}
            }
        }
        $verification+=@{name=$a.name;version=$a.version;algorithm=$a.algorithm;checksum=$actual;source=$a.url;result='PASS';action=$action}
        Save-State 'download-verification' $verification
    }
    Set-PrivateBuildEnvironment
    $jdk=Invoke-PrivateCommand (Runtime-Path 'jdk\bin\java.exe') @('--version') $script:Project
    if($jdk.exit -ne 0 -or $jdk.stdout -notmatch '17\.0\.20\.1'){throw 'Unexpected private JDK version.'}
    $node=& (Runtime-Path 'node\node.exe') --version
    if($node.Trim() -ne 'v22.23.2'){throw 'Unexpected private Node version.'}
    $mysql=Invoke-PrivateCommand (Runtime-Path 'mysql\bin\mysqld.exe') @('--version') $script:Project
    if($mysql.exit -ne 0){throw 'Private mysqld could not execute. If VCRUNTIME/MSVC is missing, install the Microsoft official Visual C++ Redistributable (may require UAC): https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist'}
    Save-State 'installed-versions' @{jdk='Temurin 17.0.20.1+1';maven='3.9.16';node='22.23.2';npm=$manifest.npmVersion;mysql='8.0.46';nacos='3.3.0';seata='2.5.0';seataNacosClient='3.0.3';sentinel='embedded; rules restored locally; Dashboard not bundled';installedAt=(Get-Date -Format o)}
}
