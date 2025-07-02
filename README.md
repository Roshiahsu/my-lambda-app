# AWS Lambda 自動部署管道

本倉庫提供一個 GitHub Actions 工作流程，自動將 Java 應用打包為 Docker 映像，部署至 AWS Lambda，並清理 Amazon ECR 中的舊映像。通過 AWS OIDC 實現安全身份驗證，高效且節省資源。

## 功能

- **自動部署**：將 Java 應用打包為 Docker 映像，推送至 ECR 並部署至 Lambda。
- **安全認證**：使用 AWS OIDC 進行無密鑰身份驗證。
- **資源管理**：自動刪除 ECR 舊映像，僅保留最新映像。

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
			"Resource": "arn:aws:lambda:ap-northeast-3:245178712382:function:github_pipeline"
		},
		{
			"Effect": "Allow",
			"Action": [
				"ecr:BatchCheckLayerAvailability",
				"ecr:PutImage",
				"ecr:InitiateLayerUpload",
				"ecr:UploadLayerPart",
				"ecr:CompleteLayerUpload",
				"ecr:DescribeRepositories"
			],
			"Resource": "arn:aws:ecr:ap-northeast-3:245178712382:repository/my-lambda-image"
		},
		{
			"Effect": "Allow",
			"Action": [
				"ecr:GetAuthorizationToken"
			],
			"Resource": "*"
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
1. 檢出代碼（`actions/checkout@v3`）。
2. 配置 AWS OIDC 憑證（`aws-actions/configure-aws-credentials@v2`）。
3. 登錄 ECR（`aws-actions/amazon-ecr-login@v1`）。
4. 配置 Java 8，運行 `mvn clean package`。
5. 構建並推送映像至 `<your Account ID>.dkr.ecr.ap-northeast-3.amazonaws.com/my-lambda-image:latest`。
6. 更新 Lambda 函數 `github_pipeline_docker_Img`。
7. 刪除 ECR 中 `latest` 映像，配合生命週期策略保留最新映像。

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
