. "$PSScriptRoot\common.ps1"
. "$PSScriptRoot\native-path.ps1"
function Invoke-MySql([string]$Sql){
    $result=Invoke-PrivateCommand (Runtime-Path 'mysql\bin\mysql.exe') @('--defaults-extra-file=secrets/mysql-client.ini','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4') (Get-MySqlNativeRoot) $Sql
    if($result.exit -ne 0){throw ('MySQL operation failed: '+(Redact $result.stderr))}
    $result.stdout.Trim()
}
function Start-MySql([switch]$AllowInitialize) {
    $data=Runtime-Path 'data\mysql'
    $config=Runtime-Path 'config\mysql.ini'
    $rootPassword=$script:Secrets.mysqlRoot
    $native=Get-MySqlNativeRoot
    Write-Utf8 (Runtime-Path 'secrets\mysql-client.ini') ("[client]`r`nuser=root`r`npassword=$rootPassword`r`nhost=127.0.0.1`r`nport=3306`r`nprotocol=tcp`r`n")
    $nativeBase=($native+'\mysql').Replace('\','/')
    $nativeData=($native+'\data\mysql').Replace('\','/')
    $nativeLog=($native+'\logs\mysql-server.log').Replace('\','/')
    Write-Utf8 $config ("[mysqld]`r`nbasedir=`"$nativeBase`"`r`ndatadir=`"$nativeData`"`r`nport=3306`r`nbind-address=127.0.0.1`r`nmysqlx=0`r`ncharacter-set-server=utf8mb4`r`ncollation-server=utf8mb4_0900_ai_ci`r`nlog-error=`"$nativeLog`"`r`nskip-log-bin`r`n")
    $initState=Runtime-Path 'state\mysql-initialized.json'
    if(-not (Test-Path -LiteralPath $data)){
        if(-not $AllowInitialize){throw 'MySQL datadir is missing. start-all never initializes or resets a database.'}
        Write-Stage 'MySQL: initializing a new private datadir (no Windows Service)'
        $result=Invoke-PrivateCommand (Runtime-Path 'mysql\bin\mysqld.exe') @('--defaults-file=config/mysql.ini','--initialize-insecure') $native
        if($result.exit -ne 0){
            Write-Utf8 (Runtime-Path 'logs\mysql-initialize.error.log') (Redact ($result.stdout+"`n"+$result.stderr))
            throw ('MySQL initialization failed (exit '+$result.exit+'); see '+(Runtime-Path 'logs\mysql-initialize.error.log'))
        }
        if(-not (Test-Path -LiteralPath (Join-Path $data 'mysql'))){throw 'MySQL did not initialize the intended private datadir.'}
        Save-Json $initState @{initializedAt=(Get-Date -Format o);datadir=$data;credentialsReady=$false}
    }
    if(-not (Test-Path -LiteralPath $initState)){throw 'Existing MySQL datadir has no installation state. Refusing to reinitialize or reset it.'}
    $state=Read-Json $initState
    # The wrapper manages lifecycle; suppress MySQL's Windows monitor fork so
    # the recorded PID is the real server and Unicode argv is not re-encoded.
    $serverArguments=@('--defaults-file=config/mysql.ini','--console','--no-monitor')
    $bootstrap=Runtime-Path 'secrets\mysql-bootstrap.sql'
    if(-not $state.credentialsReady){
        Write-Utf8 $bootstrap ("ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`r`n")
        $serverArguments+=('--init-file='+($native+'\secrets\mysql-bootstrap.sql').Replace('\','/'))
    }
    Start-Owned 'mysql' (Runtime-Path 'mysql\bin\mysqld.exe') $serverArguments @{} $native
    Wait-Ready 'mysql' {if(-not (Test-Tcp 3306)){return $false};(Invoke-MySql 'SELECT 1;') -eq '1'}
    if(-not $state.credentialsReady){$state.credentialsReady=$true;Save-Json $initState $state}
    if(Test-Path -LiteralPath $bootstrap){Remove-Item -LiteralPath $bootstrap -Force}
}
function Configure-Databases {
    $names=@('user_db','station_db','reservation_db','order_db')
    $before=Invoke-MySql "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('user_db','station_db','reservation_db','order_db') ORDER BY schema_name;"
    foreach($name in $names){$null=Invoke-MySql ('CREATE DATABASE IF NOT EXISTS '+$name+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')}
    $password=$script:Secrets.mysqlApp
    $null=Invoke-MySql ("CREATE USER IF NOT EXISTS 'smartcharge_app'@'127.0.0.1' IDENTIFIED BY '$password';")
    foreach($name in $names){$null=Invoke-MySql ('GRANT ALL PRIVILEGES ON '+$name+".* TO 'smartcharge_app'@'127.0.0.1';")}
    $tables=Invoke-MySql "SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ('user_db','station_db','reservation_db','order_db') ORDER BY table_schema,table_name;"
    if(-not (Test-Path -LiteralPath (Runtime-Path 'state\fresh-databases-before-services.json'))){Save-State 'fresh-databases-before-services' @{existingSchemasBeforeSetup=$before;schemas=$names;tablesBeforeServices=$tables;time=(Get-Date -Format o)}}
    Write-Stage 'MySQL: four schemas ready; business tables are owned by service Flyway migrations'
}
