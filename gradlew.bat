@echo off
REM Local-Gradle fallback launcher; this is not the official self-downloading wrapper.
if not "%GRADLE_HOME%"=="" if exist "%GRADLE_HOME%\bin\gradle.bat" (
  call "%GRADLE_HOME%\bin\gradle.bat" %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  call gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle is not installed. Install Gradle 8.9 or configure Android Studio with a Gradle distribution. 1>&2
exit /b 127
