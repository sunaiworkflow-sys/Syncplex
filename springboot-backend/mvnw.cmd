@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_HOME=%USERPROFILE%\.m2\wrapper\apache-maven-%MAVEN_VERSION%
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip

@REM Check if Maven is installed globally
where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    mvn %*
    goto end
)

@REM Download Maven if not present
if not exist "%MAVEN_HOME%" (
    echo Downloading Maven %MAVEN_VERSION%...
    mkdir "%USERPROFILE%\.m2\wrapper" 2>nul
    powershell -Command "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%TEMP%\maven.zip'"
    powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper' -Force"
    del "%TEMP%\maven.zip"
    echo Maven %MAVEN_VERSION% installed successfully
)

@REM Run Maven
set PATH=%MAVEN_HOME%\bin;%PATH%
mvn %*

:end
endlocal
