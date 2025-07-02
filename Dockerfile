FROM public.ecr.aws/lambda/java:8

# 複製你的 Fat JAR 到 Lambda 執行目錄
COPY target/aws-lambda-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/

# 設定 Lambda handler 類別（package + class）
CMD ["org.example.HelloWorld::handleRequest"]