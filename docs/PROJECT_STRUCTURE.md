# 项目目录说明

## 顶层目录

```text
ai-study-rag/
├─ docs/                    项目说明文档
├─ sql/                     数据库结构升级脚本
├─ src/main/java/           Java 业务代码
├─ src/main/resources/      应用配置和 RAG 资源
├─ src/test/java/           自动化测试
├─ pom.xml                  Maven 依赖与构建配置
└─ README.md                项目入口说明
```

`target/` 是 Maven 自动生成的编译产物，不需要阅读或手动修改。在 IDEA 中建议将它保持为 Excluded。

## Java 包职责

### controller

只处理 HTTP 请求、读取参数并调用业务服务：

- `ChatController`：知识库问答
- `FileController`：上传并处理文档
- `DocumentController`：文档查询和删除
- `EvalController`：RAG 效果评测
- `TestController`：开发测试接口

### service

项目的业务流程层。接口放在 `service`，实现放在 `service/impl`：

- `ChatServiceImpl`：组装上下文、调用 LLM、校验引用
- `DocumentChunkServiceImpl`：解析文件、切分、去重、生成向量并入库
- `DocumentRetrieveServiceImpl`：问题改写、召回、重排、阈值过滤
- `DocumentServiceImpl`：文档元数据管理
- `Eval*ServiceImpl`：评测、指标和诊断
- `RedisCacheServiceImpl`：缓存操作

### rag

每个子目录对应 RAG 流程中的一个阶段：

| 目录 | 职责 |
|---|---|
| `parser` | 从上传文件中提取文本 |
| `split` | 将长文本切成 chunk |
| `chunk` | chunk 去重 |
| `embedding` | 调用 Embedding 模型 |
| `vector` | 向量解析和相似度计算 |
| `vectorindex` | Qdrant 写入、查询和删除 |
| `query` | 问题改写和关键词扩展 |
| `keyword` | BM25 与关键词评分 |
| `retriever` | 向量、关键词及混合召回 |
| `rerank` | 对候选 chunk 重新排序 |
| `compression` | 压缩传给 LLM 的上下文 |
| `llm` | 调用大模型生成回答 |
| `citation` | 校验回答中的来源编号 |

### model

```text
model/
├─ dto/
│  ├─ chat/                 问答请求
│  ├─ document/             文档请求
│  ├─ eval/                 评测请求和内部结果
│  ├─ llm/                  大模型 API 请求与响应
│  └─ retrieval/            检索条件、chunk 和检索结果
├─ entity/                  与数据库表对应的实体
└─ vo/
   ├─ chat/                 问答响应和来源
   ├─ document/             文档操作响应
   └─ eval/                 评测响应
```

DTO 用于接收请求或在内部传递数据；Entity 对应数据库表；VO 是返回给前端的数据结构。

### config

- `config/properties`：集中存放所有配置映射类
- `RestTemplateConfig`：创建调用外部 HTTP 服务所需的客户端

### mapper

MyBatis-Plus Mapper，一个 Mapper 通常对应一个 Entity 和一张数据库表。

## 两条核心调用链

问答链路：

```text
ChatController
→ ChatServiceImpl
→ DocumentRetrieveServiceImpl
→ QueryRewrite
→ HybridRetriever
→ Rerank
→ ContextCompression
→ RealLlmService
→ CitationValidator
```

文档入库链路：

```text
FileController
→ DocumentChunkServiceImpl
→ TextParser
→ SemanticTextSplitterService
→ ChunkDedupService
→ EmbeddingService
→ MySQL + Qdrant
```

## IDEA 查看建议

在 Project 工具窗口中选择 `Project` 视图，并展开 `src/main/java/com/yudong/aistudy`。阅读业务时优先使用上面的两条调用链；查看某个类的调用关系时，可以使用 IDEA 的 Call Hierarchy。
