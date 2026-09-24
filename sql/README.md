# 数据库脚本说明

本目录同时支持全新安装和旧数据库升级，请不要混用两种方式。

## Flyway 自动迁移

应用启动时会自动扫描 `src/main/resources/db/migration` 并执行尚未运行的版本脚本。
全新数据库会从 `V1` 开始创建结构；已有的非空数据库首次接入 Flyway 时会建立版本基线，
然后执行后续迁移。迁移记录保存在 `flyway_schema_history` 表中。

新增结构变更时，应创建新的 `V数字__说明.sql`，不要修改已经在环境中执行过的迁移脚本。

## 全新数据库

执行 `docker compose up -d` 启动 MySQL，然后启动应用。Flyway 会自动执行
`V1__init_schema.sql`，创建项目所需的全部 9 张表。

停止并重新启动容器不会重复执行已经成功的迁移，也不会清空数据。

## 旧版手工升级脚本

以下脚本仅保留给未接入 Flyway 的历史安装。接入 Flyway 后不要再手工执行它们。
历史环境需要手工升级时，先备份数据库，再根据已有结构按顺序执行：

1. `add_document_user_id.sql`
2. `add_knowledge_base.sql`
3. `normalize_document_storage_fields.sql`
4. `add_document_chunk_content_hash.sql`
5. `add_eval_tables.sql`
6. `add_eval_judge_fields.sql`
7. `add_document_file_hash_unique.sql`
8. `expand_document_chunk_vector.sql`：将存储完整 Embedding 的 `vector_id` 扩容为 `MEDIUMTEXT`，避免向量写入时超出原有 128 字符限制。

这些增量脚本代表不同版本之间的结构变化，不保证可以重复执行。字段或索引已经存在时，
应跳过对应脚本，而不是再次执行。

## 注意

- Flyway 迁移在 Spring Boot 应用启动时运行，单独启动 MySQL 容器不会建表。
- 不要为了触发初始化而删除存有业务数据的数据卷。
- `normalize_document_storage_fields.sql` 会更新历史文档的存储信息，执行前必须备份。
- 当前数据库版本要求 MySQL 8，字符集为 `utf8mb4`。
