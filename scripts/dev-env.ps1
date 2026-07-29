$ErrorActionPreference = 'Continue'

# JDK 21 for this project (session only)
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Local MySQL settings consumed by application-dev.yml
$env:MYSQL_HOST = '127.0.0.1'
$env:MYSQL_PORT = '3307'
$env:MYSQL_DATABASE = 'payment_db'
$env:MYSQL_USERNAME = 'root'
$env:MYSQL_PASSWORD = ''

Write-Host 'JAVA_HOME=' $env:JAVA_HOME
cmd /c "java -version 2>&1"
mvn -version

# Quick MySQL connectivity check
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
& $mysql --protocol=tcp --host=$env:MYSQL_HOST --port=$env:MYSQL_PORT ("-u{0}" -f $env:MYSQL_USERNAME) -e "SELECT VERSION() AS mysql_version; SHOW DATABASES LIKE '$($env:MYSQL_DATABASE)';"




