# playedu-resource 模块协作指南

## 模块职责

本模块维护资源主数据、资源分类关联、视频扩展信息以及兼容 S3/MinIO 的上传能力。HTTP 协议位于 `playedu-api`，课程引用关系位于 `playedu-course`。

它依赖 `playedu-common` 的配置、S3 工具、分页与异常体系，课程模块通过资源 ID 使用本模块数据。

## 核心模型与服务

| 组成 | 职责 |
| --- | --- |
| `Resource` / `ResourceService` | 文件名、类型、大小、路径、状态等主数据和分页 |
| `ResourceCategory` / `ResourceCategoryService` | 资源分类关系、关系重建和分类查询 |
| `ResourceExtra` / `ResourceExtraService` | 视频时长、封面等扩展信息 |
| `UploadService` | 上传信息生成、对象存储交互和元数据处理 |

Mapper 位于 `mapper`，自定义 SQL 位于 `src/main/resources/mapper`。API 位于应用模块的 `ResourceController`、`ResourceCategoryController` 和 `UploadController`。

## 主要业务流程

1. 管理端获取普通上传参数或分片上传 ID。
2. 客户端向对象存储上传；大文件可使用预签名地址、分片查询与合并。
3. 上传完成后创建资源主记录，并按需要保存视频扩展信息。
4. 资源关联一个或多个分类，供后台筛选和课程选材。
5. 课程课时或附件保存资源 ID，播放/下载时读取资源和存储配置。

Spring Boot 层默认限制单文件 10 MB、单请求 15 MB；直传和分片上传还需要结合对象存储策略确认实际限制。

## 模块协作

- `playedu-common` 提供存储配置、上传类型、异常、分页和 S3 工具。
- `playedu-course` 保存资源引用和课时语义，不负责对象存储生命周期。
- `playedu-api` 负责上传协议、请求校验、权限和响应。
- 删除资源前，上层接口必须检查课程和课时引用。

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
