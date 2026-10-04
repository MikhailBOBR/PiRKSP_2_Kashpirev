param([switch]$Rebuild,[Parameter(ValueFromRemainingArguments=$true)][string[]]$ApplicationArgs)
$ApplicationArgs=@($ApplicationArgs | ForEach-Object {if($_ -eq 'demo'){'--demo'}else{$_}})
$ErrorActionPreference='Stop'
$taskOldEncoding=[Console]::OutputEncoding
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
Push-Location $PSScriptRoot
try {
  $taskJava=if($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/java.exe")){"$env:JAVA_HOME/bin/java.exe"} elseif(Test-Path 'C:/JDK21/jdk-21.0.9/bin/java.exe'){'C:/JDK21/jdk-21.0.9/bin/java.exe'} else {'java'}
  if($Rebuild -or !(Test-Path 'target/classes')){
    $taskMaven=if(Get-Command mvn -ErrorAction SilentlyContinue){'mvn'} elseif(Test-Path 'C:/MAVEN/apache-maven-3.9.11/bin/mvn.cmd'){'C:/MAVEN/apache-maven-3.9.11/bin/mvn.cmd'} else {throw 'Install Maven 3.9 or set PATH'}
    & $taskMaven -q package dependency:copy-dependencies
    if($LASTEXITCODE -ne 0){throw 'Build failed'}
  }
  & $taskJava '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp 'target/classes;target/dependency/*' ru.kashpirev.SupplierExchange @ApplicationArgs
  if($LASTEXITCODE -ne 0){throw 'Application failed'}
} finally {Pop-Location;[Console]::OutputEncoding=$taskOldEncoding}
