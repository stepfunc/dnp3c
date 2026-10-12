@echo off
rem Runs the dnp3c CLI with the Java in JAVA_HOME, or on the PATH, which must be version @java.release@ or newer.
setlocal

set REQUIRED=@java.release@

if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")

rem Java prints its settings, including java.specification.version (e.g. "17", or "1.8" for Java 8), to stderr.
rem They go through a file because quoting a pipe inside "for /f" breaks on paths with spaces.
set "SETTINGS=%TEMP%\dnp3c-java-%RANDOM%.txt"
"%JAVA%" -XshowSettings:properties -version 2> "%SETTINGS%"
if errorlevel 1 (
  del "%SETTINGS%" > nul 2>&1
  echo dnp3c needs Java %REQUIRED% or newer, but no Java was found. 1>&2
  echo Install one, e.g. from https://adoptium.net, and put it on the PATH or set JAVA_HOME. 1>&2
  exit /b 1
)

set VERSION=
for /f "tokens=2 delims==" %%v in ('findstr /c:"java.specification.version" "%SETTINGS%"') do set VERSION=%%v
del "%SETTINGS%" > nul 2>&1

set MAJOR=
if defined VERSION for /f "tokens=1,2 delims=. " %%a in ("%VERSION%") do (
  if "%%a"=="1" (set MAJOR=%%b) else (set MAJOR=%%a)
)

if not defined MAJOR goto old
if %MAJOR% LSS %REQUIRED% goto old

"%JAVA%" -jar "%~dp0..\lib\dnp3c.jar" %*
exit /b %ERRORLEVEL%

:old
echo dnp3c needs Java %REQUIRED% or newer, but "%JAVA%" is an older or unrecognized Java. 1>&2
echo Install a newer one, e.g. from https://adoptium.net, and put it on the PATH or set JAVA_HOME. 1>&2
exit /b 1
