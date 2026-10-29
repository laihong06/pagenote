@echo off
setlocal
if defined JAVA_HOME (
  set "PAGENOTE_JAVA=%JAVA_HOME%\bin\java.exe"
) else (
  set "PAGENOTE_JAVA=java.exe"
)
"%PAGENOTE_JAVA%" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
