$ErrorActionPreference='Stop'
$base='http://127.0.0.1:8177/api/deliveries'
Invoke-RestMethod $base | ConvertTo-Json -Depth 5
$createdBody=@{orderId=101;product='SSD 1TB';address='Москва, ул. Академическая, 12';status='CREATED'}|ConvertTo-Json
$created=Invoke-RestMethod $base -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($createdBody))
$deliveryId=$created.id
Invoke-RestMethod "$base/$deliveryId" | ConvertTo-Json
Invoke-RestMethod "$base/$deliveryId/order" | ConvertTo-Json
$sse=Start-Process curl.exe -ArgumentList '-N',"$base/$deliveryId/stream",'--max-time','8' -RedirectStandardOutput "$PSScriptRoot/sse-latest.log" -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 1
foreach($state in @('ACCEPTED','IN_TRANSIT','DELIVERED')){
  $body=@{orderId=101;product='SSD 1TB';address='Москва, ул. Академическая, 12';status=$state}|ConvertTo-Json
  Invoke-RestMethod "$base/$deliveryId" -Method Put -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) | ConvertTo-Json
  Start-Sleep -Seconds 1
}
$sse.WaitForExit()
Get-Content "$PSScriptRoot/sse-latest.log" -Encoding UTF8
