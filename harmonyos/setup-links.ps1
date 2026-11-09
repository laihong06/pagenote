# 建立 harmonyos/node_modules 到本机 DevEco 自带 hvigor 的目录联接（离线构建用）。
# 只需运行一次；node_modules 不入库。
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$deveco = 'D:\Program Files\DevEco Studio\tools\hvigor'
New-Item -ItemType Directory -Force -Path "$root\node_modules\@ohos" | Out-Null
New-Item -ItemType Junction -Force -Path "$root\node_modules\@ohos\hvigor" -Target "$deveco\hvigor" | Out-Null
New-Item -ItemType Junction -Force -Path "$root\node_modules\@ohos\hvigor-ohos-plugin" -Target "$deveco\hvigor-ohos-plugin" | Out-Null
Write-Output 'links ready'
