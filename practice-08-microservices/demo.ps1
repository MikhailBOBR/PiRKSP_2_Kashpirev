$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
docker compose up -d --build --wait
if($LASTEXITCODE -ne 0){throw 'Compose failed'}
$base='http://127.0.0.1:8188'
$trace='kashpirev-1123-'+[DateTimeOffset]::Now.ToUnixTimeSeconds()
$headers=@{'X-Request-ID'=$trace}
Invoke-RestMethod "$base/api/suppliers" | ConvertTo-Json -Depth 6
$order=Invoke-RestMethod "$base/api/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"productId":1,"quantity":2,"address":"Moscow Akademicheskaya 12"}'
$order | ConvertTo-Json
$id=$order.ID
$sse=Start-Process curl.exe -ArgumentList '-N',"$base/api/orders/$id/events",'--max-time','12' -RedirectStandardOutput "$PSScriptRoot/sse.log" -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 3
Invoke-RestMethod "$base/api/deliveries" | ConvertTo-Json -Depth 6
foreach($state in @('IN_TRANSIT','DELIVERED')){
  Invoke-RestMethod "$base/api/deliveries/$id/status" -Method Put -Headers $headers -ContentType 'application/json' -Body (@{status=$state}|ConvertTo-Json) | ConvertTo-Json
  Start-Sleep -Seconds 2
}
$sse.WaitForExit()
Get-Content "$PSScriptRoot/sse.log"
docker compose logs --no-color | Select-String $trace
docker compose exec -T broker rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
try {
  docker compose stop supplier
  1..3 | ForEach-Object {curl.exe -s -o - -w '\nHTTP=%{http_code} elapsed=%{time_total}\n' -H 'Content-Type: application/json' -H "X-Request-ID: $trace-failure" -d '{"productId":1,"quantity":1,"address":"Moscow"}' "$base/api/orders"}
} finally {docker compose start supplier}
Start-Sleep -Seconds 8
Invoke-RestMethod "$base/api/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body '{"productId":1,"quantity":1,"address":"Moscow"}' | ConvertTo-Json
docker compose ps
