@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
set "SCP_EXIT=%errorlevel%"
if not defined SMARTCHARGE_NONINTERACTIVE pause
exit /b %SCP_EXIT%
