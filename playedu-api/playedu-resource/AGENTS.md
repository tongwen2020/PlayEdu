# playedu-resource 模块协作指南

## 模块职责

本模块维护资源主数据、资源分类关联、视频扩展信息以及兼容 S3/MinIO 的上传能力。HTTP 协议位于 `playedu-api`，课程引用关系位于 `playedu-course`。

## 一致性边界

- 数据库记录与对象存储对象无法共享本地事务；创建、合并和删除流程必须定义失败补偿。
- 删除资源前检查课程课时、附件等引用，不能只删除资源表记录。
- 分片上传需要处理重复请求、未完成分片、重复合并、唯一命名和超时清理。
- 预签名 URL、桶、Endpoint 和对象 Key 的生成沿用公共 S3 配置与工具。

## 数据访问

- `Resource` 保存资源主数据，`ResourceExtra` 保存视频时长、封面等扩展信息。
- `ResourceCategory` 表示资源与分类关系；重建关系时保证批量操作和事务边界完整。
- Mapper 接口与 XML SQL 必须同步，资源类型或字段变化还需检查课程播放和前端资源选择。

## 安全

- 不记录或返回 Access Key、Secret Key 等凭据。
- 上传接口必须验证文件类型、大小、对象 Key 和操作者权限，不能只相信文件扩展名。
- 用户可控文件名不得直接拼接为本地路径或 SQL。

## 验证

```powershell
.\mvnw.cmd -pl playedu-resource -am test
.\mvnw.cmd -pl playedu-api -am test
```

重点验证普通上传、分片合并、分类重建、资源详情、删除引用检查以及对象存储失败后的数据状态。

