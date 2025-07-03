@echo off
setlocal

REM =======================================
REM 這2個設定檔需要修改
REM 設定 Lambda function 名稱
REM 設定 handler
REM =======================================
set FUNCTION_NAME=github_pipeline_hello_world
set HANDLER_NAME=org.example.HelloWorld::handleRequest
REM =======================================
REM 切換到 target 資料夾
REM =======================================
cd /d "%~dp0target"

REM =======================================
REM 自動抓取最新的 .jar 檔案名稱（排序取第一個）
REM 這會把符合 *.jar 的檔案名稱存進變數 JAR_FILE
REM =======================================
for /f "delims=" %%i in ('dir /b /o-d *.jar') do (
  set JAR_FILE=%%i
  goto :found
)
:found

REM 顯示找到的 jar
echo Using JAR file: %JAR_FILE%

REM =======================================
REM 上傳到 AWS Lambda
REM =======================================
aws lambda update-function-code ^
  --function-name %FUNCTION_NAME% ^
  --zip-file fileb://%JAR_FILE%
  
REM =======================================
REM 修改handler
REM =======================================
aws lambda update-function-configuration ^
  --function-name %FUNCTION_NAME% ^
  --handler %HANDLER_NAME%

REM =======================================
REM 呼叫 Lambda 並顯示回應
REM =======================================
aws lambda invoke ^
  --function-name %FUNCTION_NAME% ^
  --payload "{}" ^
  --cli-binary-format raw-in-base64-out ^
  response.json

type response.json
pause
