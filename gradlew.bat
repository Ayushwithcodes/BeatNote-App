@echo off
set DIRNAME=%~dp5
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

:: Find java
if not "%JAVA_HOME%" == "" goto JAVAHOME
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto EXEC
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto FAIL

:JAVAHOME
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:EXEC
"%JAVA_EXE%" -version >NUL 2>&1
if not "%ERRORLEVEL%" == "0" (
    echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    goto FAIL
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:FAIL
exit /b 1
