# AWS Lambda 自動部署管道

本倉庫提供一個 GitHub Actions 工作流程，自動將 Java 應用打包為 Docker 映像，部署至 AWS Lambda，並清理 Amazon ECR 中的舊映像。通過 AWS OIDC 實現安全身份驗證，高效且節省資源。

## 功能

- **自動部署**：根據配置，將 Java 應用打包為 Docker 映像（推送至 ECR）或 JAR 檔案（上傳至 S3），並選擇性地部署至 Lambda。
- **安全認證**：支援直接部署到 Lambda 或搭配 AWS CodeDeploy 進行進階部署管理。
- **安全認證**：使用 AWS OIDC 進行無密鑰身份驗證，確保安全且簡化配置。
- **資源管理**：自動刪除 ECR 舊映像，配合生命週期策略僅保留最新映像，降低儲存成本。

## 前置條件

- **AWS 帳戶**：需具備 ECR 和 Lambda 權限。
- **ECR 倉庫**：`ap-northeast-3` 區域的 `my-lambda-image` 倉庫。
- **IAM 角色**：`github-pipeline` 角色，支持 OIDC，包含 ECR 和 Lambda 權限：
  - `ecr:BatchCheckLayerAvailability`,`ecr:PutImage`, `ecr:InitiateLayerUpload`,`ecr:UploadLayerPart`,`ecr:CompleteLayerUpload`,`ecr:DescribeRepositories`
  - `lambda:UpdateFunctionCode`
 ```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "lambda:UpdateFunctionCode",
            "Resource": [
                "arn:aws:lambda:ap-northeast-3:{your-account-id}:function:github_pipeline_docker_Img",
                "arn:aws:lambda:ap-northeast-3:{your-account-id}:function:github_pipeline_hello_world"
            ]
        },
        {
            "Effect": "Allow",
            "Action": [
                "ecr:BatchCheckLayerAvailability",
                "ecr:PutImage",
                "ecr:InitiateLayerUpload",
                "ecr:UploadLayerPart",
                "ecr:CompleteLayerUpload",
                "ecr:DescribeRepositories",
                "ecr:BatchGetImage",
                "ecr:DescribeImages"
            ],
            "Resource": "arn:aws:ecr:ap-northeast-3:{your-account-id}:repository/my-lambda-image"
        },
        {
            "Effect": "Allow",
            "Action": [
                "ecr:GetAuthorizationToken"
            ],
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::lambda-deploy-jar-roshia/*"
            ]
        }
    ]
}
```
- **Java 應用**：Java 8 應用，含 Lambda 處理程序（`org.example.HelloWorld::handleRequest`）。
- **Dockerfile**：基於 `public.ecr.aws/lambda/java:8`。
- **Maven**：使用 `maven-shade-plugin` 構建 fat JAR。
```
 <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.8.1</version>
                <configuration>
                    <source>8</source>
                    <target>8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
```
## 工作流程

推送至 `main` 分支時，執行以下步驟：
1. 檢查環境變數（BUILD_TYPE, AWS_REGION, AWS_S3_BUCKET_JAR, DEPLOY_TO_LAMBDA）。
2. 檢出代碼（`actions/checkout@v3`）。
3.  配置 Java 8 環境並運行 Maven 構建（透過自定義的 .github/actions/maven-build），包括：
     - 使用 JaCoCo 進行測試覆蓋率掃描，運行 mvn test 並生成覆蓋率報告（儲存於 target/site/jacoco）。
     - 運行 mvn clean package 構建 fat JAR。
4. 若 BUILD_TYPE 為 docker：
   - 配置 AWS OIDC 憑證（aws-actions/configure-aws-credentials@v2），假設角色 github-yml-test-tamp。
   - 檢查並創建 ECR 倉庫 my-lambda-image（若不存在）
   - 登錄 Amazon ECR（aws-actions/amazon-ecr-login@v2）。
   - 構建並推送 Docker 映像至 <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-3.amazonaws.com/my-lambda-image:latest。
   - 構建並推送映像至 <AWS_ACCOUNT_ID>.dkr.ecr.ap-northeast-3.amazonaws.com/my-lambda-image:latest。
   - 若 DEPLOY_TO_LAMBDA 為 true，更新 Lambda 函數 github_pipeline_docker_Img。
   - 配合 ECR 生命週期策略清理舊映像。
5. 若 BUILD_TYPE 為 s3：
   - 配置 AWS OIDC 憑證（aws-actions/configure-aws-credentials@v2），假設角色 github-yml-test-tamp。
   - 上傳 JAR 檔案至指定的 S3 儲存桶（AWS_S3_BUCKET_JAR）。
   - 若 DEPLOY_TO_LAMBDA 為 true，更新 Lambda 函數 github_pipeline_hello_world。

### 示例 Dockerfile

```dockerfile
FROM public.ecr.aws/lambda/java:8
COPY target/aws-lambda-1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["org.example.HelloWorld::handleRequest"]
```

### ECR 生命週期策略

僅保留最新映像，舊映像約 24 小時內刪除：

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep only the latest 1 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 1
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

## 快速設置

1. 克隆倉庫：
   ```bash
   git clone <倉庫 URL>
   cd <倉庫名稱>
   ```

2. 配置 AWS：
   - 創建 `my-lambda-image` ECR 倉庫。
   - 配置 `github-pipeline` IAM 角色，附加權限。
   - 設置 ECR 生命週期策略（見上）。

3. 推送至 `main` 分支觸發工作流程。

4. 驗證：
   - 確認 Lambda 函數 `github_pipeline_docker_Img` 已更新。
   - 查看 CloudWatch 日誌：
     ```bash
     aws logs tail /aws/lambda/github_pipeline_docker_Img

## 檢查環境設定

在 GitHub 倉庫中配置必要的環境變數和機密，以確保 CI/CD 工作流程正確運行。請按照以下步驟操作：

1. **創建環境**：
   - 前往 GitHub 倉庫的 `Settings > Environments`。
   - 點擊 `New environment`，名稱設為 `AWS`。

2. **添加環境機密 (Secrets)**：
   - 在 `AWS` 環境中，添加以下機密：
     - `AWS_ACCOUNT_ID`：你的 AWS 帳戶 ID（12 位數字，例如 `123456789012`）。

3. **添加環境變數 (Variables)**：
   - 在 `AWS` 環境中，添加以下變數：
     - `AWS_REGION`：AWS 服務所在的區域（例如 `ap-northeast-3`）。
     - `AWS_S3_BUCKET_JAR`：用於儲存 JAR 檔案的 S3 儲存桶名稱（例如 `my-app-bucket`）。
     - `BUILD_TYPE`：選擇部署 Lambda 的方式，可選值：
       - `docker`：構建並推送 Docker 映像至 ECR。
       - `s3`：構建並上傳 JAR 檔案至 S3。
     - `DEPLOY_TO_LAMBDA`：是否在工作流程中直接部署至 Lambda，可選值：
       - `true`：在工作流程中直接更新 Lambda 函數程式碼，適合快速部署場景。
       - `false`：僅構建並上傳檔案至 ECR 或 S3，後續可搭配 AWS CodeDeploy 進行進階部署管理（見下方設計概念）。

4. **確認設置**：
   - 確保所有機密和變數正確輸入，避免空格或格式錯誤。
   - 檢查 `AWS` 環境是否啟用，且無需額外的審核規則（除非你需要限制部署）。

### 設計概念：使用 AWS CodeDeploy 的好處

當 `DEPLOY_TO_LAMBDA` 設為 `false` 時，工作流程僅負責構建並上傳檔案（Docker 映像至 ECR 或 JAR 檔案至 S3），而不直接更新 Lambda 函數。這種設計允許你將部署邏輯交由 AWS CodeDeploy 管理，帶來以下好處：

- **藍綠部署與滾動更新**：AWS CodeDeploy 支持藍綠部署和逐步滾動更新，可降低部署風險，確保新版本上線時服務不中斷。
- **自動回滾**：CodeDeploy 提供自動回滾機制，若部署失敗（例如 Lambda 函數執行錯誤），可快速恢復到先前穩定版本。
- **進階部署策略**：支援流量分流（Canary 部署），允許小比例流量測試新版本，確保穩定後再完全切換。
- **集中化管理**：CodeDeploy 提供統一的部署監控與日誌，方便追蹤部署歷史與效能指標。
- **靈活的部署控制**：可與 AWS Lambda 別名（Alias）結合，實現版本控制與流量管理，適合需要高可用性的應用。

選擇 `false` 時，你可以將構建好的檔案（ECR 映像或 S3 JAR）作為 CodeDeploy 的部署來源，透過 CodeDeploy 的控制台或 CLI 配置部署管道，享受更穩健的部署流程。

### 注意事項
- 確保 `AWS_ACCOUNT_ID` 與你的 AWS 帳戶一致，並與 ECR 和 S3 的權限設置對應。
- 如果選擇 `docker` 作為 `BUILD_TYPE`，需確保 `my-lambda-image` ECR 倉庫已創建（見「配置 AWS」部分）。
- 如果選擇 `s3` 作為 `BUILD_TYPE`，確保 `AWS_S3_BUCKET_JAR` 指定的 S3 儲存桶存在且可寫入。
- 若 `DEPLOY_TO_LAMBDA` 設為 `true`，確保 Lambda 函數 `github_pipeline_docker_Img` 已存在。
- 若使用 AWS CodeDeploy，需額外配置 CodeDeploy 應用程式和部署群組，並授予 IAM 角色相應權限（如 `codedeploy:*`）。
