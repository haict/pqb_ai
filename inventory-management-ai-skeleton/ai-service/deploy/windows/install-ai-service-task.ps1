#Requires -RunAsAdministrator
<#
  Dang ky AI Service (D-64) chay nen bang Task Scheduler: tu chay khi khoi dong may, tu chay lai khi loi,
  do uu tien CPU thap hon Tomcat / MySQL. Xem docs/10-deployment.md muc 5.

  Vi du:
    .\install-ai-service-task.ps1
    .\install-ai-service-task.ps1 -AiHome D:\ERP\ai-service -EnvFile D:\ERP\config\ai-service.env -LogDir D:\ERP\logs
    .\install-ai-service-task.ps1 -Uninstall
#>
param(
    [string]$AiHome = 'C:\ERP\ai-service',
    [string]$EnvFile = 'C:\ERP\config\ai-service.env',
    [string]$LogDir = 'C:\ERP\logs',
    [string]$TaskName = 'ERP AI Service',
    # Tai khoan quyen thap mac dinh cua Windows; doi sang 'SYSTEM' chi khi LOCALSERVICE khong chay duoc.
    [string]$RunAs = 'NT AUTHORITY\LOCALSERVICE',
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'

if ($Uninstall) {
    Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    Write-Host "Da go tac vu '$TaskName'."
    return
}

$python = Join-Path $AiHome '.venv\Scripts\python.exe'
$runner = Join-Path $AiHome 'deploy\windows\run-ai-service.cmd'
foreach ($path in @($python, $runner, $EnvFile)) {
    if (-not (Test-Path $path)) { throw "Khong tim thay: $path" }
}
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# Tai khoan chay dich vu: doc thu muc cai dat va file env, ghi thu muc log.
& icacls $AiHome /grant "${RunAs}:(OI)(CI)RX" /T /Q | Out-Null
& icacls $EnvFile /grant "${RunAs}:R" /Q | Out-Null
& icacls $LogDir /grant "${RunAs}:(OI)(CI)M" /Q | Out-Null
# Thu muc model (OCR_MODEL_DIR trong file env) thuong nam ngoai thu muc cai dat.
$modelLine = Get-Content $EnvFile | Where-Object { $_ -match '^\s*OCR_MODEL_DIR\s*=' } | Select-Object -First 1
$modelDir = if ($modelLine) { ($modelLine -replace '^\s*OCR_MODEL_DIR\s*=\s*', '').Trim() } else { '' }
if (-not $modelDir -or -not (Test-Path $modelDir)) { throw "OCR_MODEL_DIR trong $EnvFile khong ton tai: '$modelDir'" }
& icacls $modelDir /grant "${RunAs}:(OI)(CI)RX" /T /Q | Out-Null

$argument = '/c ""' + $runner + '" "' + $EnvFile + '" "' + $LogDir + '""'
$action = New-ScheduledTaskAction -Execute 'cmd.exe' -Argument $argument -WorkingDirectory $AiHome
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId $RunAs -LogonType ServiceAccount -RunLevel Limited
# Priority 7: duoi muc binh thuong, nhuong CPU cho Tomcat / MySQL (docs/14 muc 9.2).
$settings = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -Priority 7 -MultipleInstances IgnoreNew -StartWhenAvailable `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal `
    -Settings $settings -Force | Out-Null
Start-ScheduledTask -TaskName $TaskName
Write-Host "Da dang ky va khoi dong '$TaskName' (tai khoan $RunAs). Log: $LogDir\ai-service.log"
