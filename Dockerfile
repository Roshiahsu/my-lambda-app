# 階段 1：構建階段（Build Stage）
FROM maven:3.8.6-jdk-8 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 階段 2：運行階段（Runtime Stage）
FROM public.ecr.aws/lambda/java:8
COPY --from=builder /app/target/aws-lambda-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["org.example.HelloWorld::handleRequest"]