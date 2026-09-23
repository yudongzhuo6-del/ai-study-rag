# 数据库脚本说明

本目录同时支持全新安装和旧数据库升级，请不要混用两种方式。

## 全新数据库

执行 `docker compose up -d` 即可。Compose 会在 MySQL 数据卷首次创建时自动执行
`init_schema.sql`，创建项目所需的全部 9 张表。

初始化脚本只会在数据目录为空时执行。停止并重新启动容器不会重复建表，也不会清空数据。

## 已有数据库升级

不要执行 `init_schema.sql`。备份数据库后，根据已有结构按历史顺序执行增量脚本：

1. `add_document_user_id.sql`
2. `add_knowledge_base.sql`
3. `normalize_document_storage_fields.sql`
4. `add_document_chunk_content_hash.sql`
5. `add_eval_tables.sql`
6. `add_eval_judge_fields.sql`
7. `add_document_file_hash_unique.sql`

这些增量脚本代表不同版本之间的结构变化，不保证可以重复执行。字段或索引已经存在时，
应跳过对应脚本，而不是再次执行。

## 注意

- Docker 自动初始化只对新的 `mysql_data` 数据卷生效。
- 不要为了触发初始化而删除存有业务数据的数据卷。
- `normalize_document_storage_fields.sql` 会更新历史文档的存储信息，执行前必须备份。
- 当前数据库版本要求 MySQL 8，字符集为 `utf8mb4`。
