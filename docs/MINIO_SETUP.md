# MinIO 本地启动与项目配置

## 1. 启动 MinIO

本项目默认连接 `http://localhost:9000`，默认 Bucket 为 `ai-study-rag`。应用会在第一次上传时自动创建 Bucket。

可以使用本机 MinIO 服务，也可以使用 Docker：

```powershell
docker run -d --name ai-study-minio -p 9000:9000 -p 9001:9001 `
  -e MINIO_ROOT_USER=minioadmin `
  -e MINIO_ROOT_PASSWORD=minioadmin `
  -v ai-study-minio-data:/data `
  minio/minio server /data --console-address ":9001"
```

管理控制台地址为 `http://localhost:9001`。

## 2. 设置环境变量

开发环境可以使用以下值：

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=ai-study-rag
MINIO_PRESIGNED_URL_EXPIRY_MINUTES=10
```

生产环境必须替换访问密钥，并通过部署平台的 Secret 管理功能注入，不能提交到 Git。

## 3. 使用接口

首次运行规范化版本前，先在 MySQL 的 `ai_study_rag` 数据库执行：

```text
sql/normalize_document_storage_fields.sql
```

如果实际 Bucket 不是 `ai-study-rag`，先修改脚本开头的 `@minio_bucket`。脚本会把已有的 `documents/...` 记录迁移到 `object_key`，旧本地绝对路径则标记为 `LOCAL`。

上传接口保持不变：

```http
POST /file/upload
Content-Type: multipart/form-data
```

参数为 `file`、必填的 `userId` 和必填的 `knowledgeBaseId`。知识库必须存在且属于该用户。新文档分别保存 `object_key`、`bucket_name`、文件大小、MIME 类型和 SHA-256；`file_url` 只保留用于兼容历史数据。

上传成功后返回文档 ID、文件名、对象 Key 和处理状态：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": 25,
    "fileName": "minio-test.pdf",
    "objectKey": "documents/1/1/2026/07/26/xxx.pdf",
    "status": 0
  }
}
```

上传现在只负责保存原文件并提交后台任务，因此正常情况下立即返回 `status=0`。状态定义为：`0=PENDING`、`1=SUCCESS`、`2=FAILED`、`3=PROCESSING`。

查询处理状态：

```http
GET /document/{documentId}/status?userId={userId}
```

失败后重新提交处理：

```http
POST /document/{documentId}/retry?userId={userId}
```

只有 `status=2` 的文档可以重试；应用重新启动时会自动恢复 `status=0` 和中断的 `status=3` 任务。

获取临时下载地址：

```http
GET /file/{documentId}/download-url?userId={userId}
```

默认返回 10 分钟有效的预签名 URL，Bucket 无需设为公开读取。

删除文档同样需要提供文档所属用户：

```http
DELETE /document/delete/{documentId}?userId={userId}
```

处于 `PENDING` 或 `PROCESSING` 状态的文档不能删除，接口会返回 HTTP 409；等待成功或失败后再删除。
