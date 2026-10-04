$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
docker compose up -d --build --scale orders=2 --wait
if($LASTEXITCODE -ne 0){throw 'Compose failed'}
docker compose ps
1..8 | ForEach-Object {Invoke-RestMethod http://127.0.0.1:8155/api/orders | ConvertTo-Json -Depth 6 -Compress}
Invoke-RestMethod http://127.0.0.1:8155/api/deliveries -Method Post -ContentType 'application/json' -Body '{"orderId":101}' | ConvertTo-Json -Depth 6
$replicas=@(docker compose ps -q orders)
if($replicas.Count -lt 2){throw 'Need two replicas'}
try {
  docker stop $replicas[0]
  Start-Sleep -Seconds 6
  1..3 | ForEach-Object {Invoke-RestMethod http://127.0.0.1:8155/api/orders | ConvertTo-Json -Depth 6 -Compress}
} finally {docker start $replicas[0]}
