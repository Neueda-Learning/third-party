$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $PSScriptRoot
$dataDir = Join-Path $baseDir '.local\mysql-data'
$logDir = Join-Path $baseDir '.local'
$logFile = Join-Path $logDir 'mysql-3307.log'
$mysqld = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

if (-not (Test-Path $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    & $mysqld --initialize-insecure --datadir=$dataDir --console *> $logFile
}

# Check if MySQL on 3307 is already up.
& $mysql --protocol=tcp --host=127.0.0.1 --port=3307 -uroot -e "SELECT 1;" *> $null
if ($LASTEXITCODE -ne 0) {
    Start-Process -FilePath $mysqld -ArgumentList @(
        "--datadir=$dataDir",
        "--port=3307",
        "--bind-address=127.0.0.1",
        "--mysqlx=0",
        "--console"
    ) -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $logDir 'mysql-3307-error.log') -WindowStyle Hidden
    Start-Sleep -Seconds 8
}

& $mysql --protocol=tcp --host=127.0.0.1 --port=3307 -uroot -e "SELECT VERSION() AS mysql_version;"
Write-Host "MySQL local instance is ready on 127.0.0.1:3307"

