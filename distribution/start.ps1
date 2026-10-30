$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
try {
    $java = 'java'
    if ($env:JAVA_HOME) { $java = Join-Path $env:JAVA_HOME 'bin/java.exe' }
    & $java -version
    if ($LASTEXITCODE -ne 0) { throw 'Java 21 is required: https://adoptium.net/temurin/releases/?version=21' }
    if (-not (Test-Path 'paper.jar')) {
        Invoke-WebRequest 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar' -OutFile 'paper.jar.download'
        if ((Get-FileHash 'paper.jar.download' -Algorithm SHA256).Hash -ne '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba') { throw 'Paper checksum mismatch' }
        Move-Item 'paper.jar.download' 'paper.jar'
    }
    if (-not (Test-Path 'eula.txt') -or -not (Select-String -Path 'eula.txt' -Pattern '^eula=true$' -Quiet)) {
        Write-Host 'Minecraft EULA: https://www.minecraft.net/eula'
        if ((Read-Host 'Type AGREE only if you accept the EULA') -cne 'AGREE') { throw 'EULA was not accepted' }
        'eula=true' | Set-Content 'eula.txt' -Encoding ASCII
    }
    if (-not (Test-Path 'server.properties')) {
        @('server-ip=127.0.0.1','server-port=25565','online-mode=true','gamemode=survival','difficulty=easy','view-distance=8','simulation-distance=6','motd=Mai Tycoon Local') | Set-Content 'server.properties' -Encoding ASCII
    }
    & $java -Xms512M -Xmx2G -jar paper.jar --nogui
} catch { Write-Host $_ -ForegroundColor Red }
Read-Host 'Press Enter to close'
