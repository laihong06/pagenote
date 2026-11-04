@echo off
rem PageNote 鸿蒙版命令行构建脚本（本机 DevEco Studio 6.1，离线构建）
rem 产物：entry\build\default\outputs\default\entry-default-unsigned.hap（未签名）
setlocal
set "DEVECO=D:\Program Files\DevEco Studio"
set "DEVECO_SDK_HOME=%DEVECO%\sdk"
set "NODE_PATH=%~dp0node_modules"
set "PATH=%DEVECO%\jbr\bin;%PATH%"
if not exist "%~dp0node_modules\@ohos\hvigor" (
  echo 首次构建需要建立本机 hvigor 链接，请用管理员或开发者模式运行一次：
  echo   powershell -File "%~dp0setup-links.ps1"
  exit /b 1
)
"%DEVECO%\tools\node\node.exe" "%DEVECO%\tools\hvigor\hvigor\bin\hvigor.js" --mode module -p product=default assembleHap --no-daemon %*
endlocal
