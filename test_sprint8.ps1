# Sprint 8 Manual Verification Script

# Test 1: Create Provider with isDefault=true
$providerJson = @{
    name = "TestProvider"
    channel = "EMAIL"
    providerType = "SMTP"
    config = '{"host":"smtp.example.com","port":587}'
    enabled = $true
    isDefault = $true
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/providers" -Method POST -ContentType "application/json" -Body $providerJson -UseBasicParsing
    Write-Host "Test 1 PASSED: Provider created successfully"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Test 1 FAILED: $($_.Exception.Message)"
}

# Test 2: Send Notification
$notificationJson = @{
    templateKey = "WELCOME_EMAIL"
    recipient = "test@example.com"
    variables = @{
        name = "Test User"
    }
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/notifications/send" -Method POST -ContentType "application/json" -Body $notificationJson -UseBasicParsing
    Write-Host "Test 2 PASSED: Notification sent successfully"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "Test 2 FAILED: $($_.Exception.Message)"
}

Write-Host "Manual verification requires interactive execution. Please test via Postman or browser."