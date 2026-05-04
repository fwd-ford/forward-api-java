@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------
@echo off

@setlocal

set ERROR_CODE=0

@REM Find Java executable
if defined JAVA_HOME goto OkJHome
for %%i in (java.exe) do set "JAVA_EXE=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVA_EXE%" goto chkMHome
echo Error: JAVA_HOME is not defined correctly and java.exe is not on PATH. >&2
goto error

:chkMHome
set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
set "WRAPPER_PROPS=%WRAPPER_DIR%\maven-wrapper.properties"

@REM Bootstrap: download maven-wrapper.jar if missing
if exist "%WRAPPER_JAR%" goto runWrapper

echo Downloading Maven Wrapper...
for /f "tokens=1,2 delims==" %%A in ('type "%WRAPPER_PROPS%"') do (
    if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
)
if "%WRAPPER_URL%"=="" (
    echo Error: wrapperUrl not found in %WRAPPER_PROPS%. >&2
    goto error
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
if errorlevel 1 (
    echo Error: failed to download Maven Wrapper JAR. >&2
    goto error
)

:runWrapper
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%~dp0" org.apache.maven.wrapper.MavenWrapperMain %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /b %ERROR_CODE%
