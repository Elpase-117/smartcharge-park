. "$PSScriptRoot\common.ps1"
function Get-MySqlNativeRoot {
    if(-not ('SmartChargeWindowsPath' -as [type])){
        Add-Type @'
using System.Text;
using System.Runtime.InteropServices;
public static class SmartChargeWindowsPath {
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
 public static extern uint GetShortPathName(string input,StringBuilder output,uint length);
}
'@
    }
    $buffer=New-Object Text.StringBuilder 32768
    $n=[SmartChargeWindowsPath]::GetShortPathName($script:Runtime,$buffer,32768)
    if($n -gt 0 -and $buffer.ToString() -notmatch '[^\x00-\x7f]'){
        return $buffer.ToString()
    }
    if($script:Runtime -notmatch '[^\x00-\x7f]'){return $script:Runtime}
    # On volumes without 8.3 names, keep all real files in .runtime and create
    # only a small owned directory junction in an ASCII local temporary path.
    $candidates=@([IO.Path]::GetTempPath(),[Environment]::GetFolderPath('CommonApplicationData'),$env:PUBLIC)
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $key=(Get-TextHash ($script:Runtime.ToLowerInvariant()+'|'+$sid)).Substring(0,24)
    foreach($base in $candidates){
        if(-not $base -or $base -match '[^\x00-\x7f]'){continue}
        $parent=Join-Path $base ('SmartChargePark-native-'+$key)
        $owner=Join-Path $parent 'owner.json'
        $link=Join-Path $parent 'runtime'
        try{
            if(Test-Path -LiteralPath $parent){
                if(-not (Test-Path -LiteralPath $owner)){continue}
                $owned=Read-Json $owner
                if($owned.target -ne $script:Runtime -or $owned.ownerSid -ne $sid){continue}
            }else{
                [void][IO.Directory]::CreateDirectory($parent)
                $null=& "$env:SystemRoot\System32\icacls.exe" $parent '/inheritance:r' '/grant:r' ('*'+$sid+':(OI)(CI)F') '/Q' 2>&1
                Save-Json $owner @{target=$script:Runtime;ownerSid=$sid;purpose='MySQL Unicode path alias; no copied Runtime data'}
            }
            if(-not (Test-Path -LiteralPath $link)){New-Item -ItemType Junction -Path $link -Target $script:Runtime -ErrorAction Stop|Out-Null}
            $item=Get-Item -LiteralPath $link -Force
            if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -or @($item.Target)[0].TrimEnd('\') -ne $script:Runtime){throw 'Native path alias target mismatch.'}
            Save-State 'mysql-path-alias' @{parent=$parent;link=$link;target=$script:Runtime;ownerSid=$sid}
            return $link
        }catch{}
    }
    throw 'Cannot create a private ASCII path view for native MySQL. No data was initialized or reset.'
}
function Remove-MySqlNativeAlias {
    $state=Runtime-Path 'state\mysql-path-alias.json'
    if(-not (Test-Path -LiteralPath $state)){return}
    $record=Read-Json $state
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if($record.target -ne $script:Runtime -or $record.ownerSid -ne $sid){throw 'Refusing to remove an unowned MySQL path alias.'}
    $parent=[IO.Path]::GetFullPath($record.parent).TrimEnd('\')
    $key=(Get-TextHash ($script:Runtime.ToLowerInvariant()+'|'+$sid)).Substring(0,24)
    if((Split-Path $parent -Leaf) -ne ('SmartChargePark-native-'+$key) -or $record.link -ne (Join-Path $parent 'runtime')){throw 'Unexpected native alias path.'}
    $owner=Read-Json (Join-Path $parent 'owner.json')
    if($owner.target -ne $script:Runtime -or $owner.ownerSid -ne $sid){throw 'Native alias ownership mismatch.'}
    if(Test-Path -LiteralPath $record.link){
        $item=Get-Item -LiteralPath $record.link -Force
        if(($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0 -or @($item.Target)[0].TrimEnd('\') -ne $script:Runtime){throw 'Refusing to remove an unexpected link.'}
        # Nonrecursive junction removal never traverses the Runtime target.
        [IO.Directory]::Delete($record.link,$false)
    }
    Remove-Item -LiteralPath (Join-Path $parent 'owner.json') -Force
    [IO.Directory]::Delete($parent,$false)
    Remove-Item -LiteralPath $state -Force
}
