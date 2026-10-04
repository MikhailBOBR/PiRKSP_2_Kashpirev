$ErrorActionPreference = 'Stop'
        Set-Location $PSScriptRoot
        & mvn -q package dependency:copy-dependencies
        if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
        & java '-Dfile.encoding=UTF-8' -cp 'target/classes;target/dependency/*' ru.kashpirev.RSocketDemo @args
