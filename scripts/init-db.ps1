$ErrorActionPreference = 'Stop'

$baseDir = Split-Path -Parent $PSScriptRoot
$schema = Join-Path $baseDir 'src\main\resources\db\schema.sql'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

if (-not (Test-Path $schema)) {
    throw "schema.sql not found: $schema"
}

& $mysql --protocol=tcp --host=127.0.0.1 --port=3307 -uroot -e "SOURCE $($schema -replace '\\','/');"
& $mysql --protocol=tcp --host=127.0.0.1 --port=3307 -uroot -e "SHOW DATABASES LIKE 'payment_db'; USE payment_db; SHOW TABLES;"

