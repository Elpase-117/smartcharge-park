$ErrorActionPreference = 'Stop'
$baseUrl = if ($env:API_BASE) { $env:API_BASE } else { 'http://127.0.0.1:8080/api' }
$date = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')

$loginBody = @{ username = 'driver'; password = '123456' } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
$headers = @{ Authorization = "Bearer $($login.token)" }

$stations = Invoke-RestMethod -Uri "$baseUrl/stations" -Headers $headers
if (-not $stations -or $stations.Count -lt 1) { throw '站点列表为空' }

$station = Invoke-RestMethod -Uri "$baseUrl/stations/$($stations[0].id)" -Headers $headers
$availability = Invoke-RestMethod -Uri "$baseUrl/stations/$($station.id)/availability?date=$date" -Headers $headers
$slot = $availability | Where-Object { $_.remaining -gt 0 } | Select-Object -First 1
if (-not $slot) { throw '没有可预约时段' }

$reservationBody = @{
    stationId = $station.id
    reservationDate = $date
    timeSlot = $slot.timeSlot
} | ConvertTo-Json
$result = Invoke-RestMethod -Uri "$baseUrl/reservations" -Method Post -Headers $headers -ContentType 'application/json' -Body $reservationBody

if ($result.reservationStatus -ne 'PENDING_PAYMENT') { throw '预约状态不是 PENDING_PAYMENT' }
if ($result.orderStatus -ne 'PENDING_PAYMENT') { throw '订单状态不是 PENDING_PAYMENT' }

[pscustomobject]@{
    Login = 'PASS'
    StationList = "PASS ($($stations.Count) stations)"
    StationDetail = "PASS ($($station.name))"
    Availability = "PASS ($($slot.timeSlot), remaining before create: $($slot.remaining))"
    ReservationId = $result.reservationId
    ReservationStatus = $result.reservationStatus
    OrderId = $result.orderId
    OrderNo = $result.orderNo
    OrderStatus = $result.orderStatus
    EstimatedAmount = $result.estimatedAmount
} | Format-List
