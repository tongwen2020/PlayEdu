-- playedu.admin_logs 定义

CREATE TABLE `admin_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_id` int NOT NULL DEFAULT '0' COMMENT '管理员ID',
  `admin_name` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '管理员姓名',
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '模块',
  `title` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求方法标题',
  `opt` int NOT NULL DEFAULT '0' COMMENT '操作指令（0其它 1新增 2修改 3删除 4登录 5退出登录）',
  `method` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求方法',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求方式POST,GET,PUT,DELETE',
  `url` varchar(266) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求URL',
  `param` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '请求参数',
  `result` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '返回参数',
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'IP',
  `ip_area` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '地址',
  `error_msg` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误消息',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `a_m_o` (`admin_id`,`module`,`opt`)
) ENGINE = InnoDB AUTO_INCREMENT = 122 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员操作日志记录表';
-- playedu.admin_permissions 定义

CREATE TABLE `admin_permissions` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '类型[行为:action,数据:data]',
`group_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分组',
`sort` int NOT NULL DEFAULT '0' COMMENT '升序',
`name` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '权限名',
`slug` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'slug',
`created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 26 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'SQL变更记录表';
-- playedu.admin_role_permission 定义

CREATE TABLE `admin_role_permission` (
  `role_id` int unsigned NOT NULL DEFAULT '0' COMMENT '角色ID',
`perm_id` int unsigned NOT NULL DEFAULT '0' COMMENT '权限ID',
KEY `role_id` (`role_id`),
KEY `perm_id` (`perm_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员角色权限关联表';
-- playedu.admin_roles 定义

CREATE TABLE `admin_roles` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '角色名',
  `slug` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'slug',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `slug` (`slug`)
) ENGINE = InnoDB AUTO_INCREMENT = 2 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员角色表';
-- playedu.admin_user_role 定义

CREATE TABLE `admin_user_role` (
  `admin_id` int unsigned NOT NULL DEFAULT '0' COMMENT '管理员ID',
  `role_id` int unsigned NOT NULL DEFAULT '0' COMMENT '角色ID',
  KEY `admin_id` (`admin_id`),
  KEY `role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员角色关联表';
-- playedu.admin_users 定义

CREATE TABLE `admin_users` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `email` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `password` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `salt` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'salt',
  `login_ip` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '登录IP',
  `login_at` timestamp NULL DEFAULT NULL COMMENT '登录时间',
  `is_ban_login` tinyint NOT NULL DEFAULT '0' COMMENT '1禁止登录,0否',
  `login_times` int NOT NULL DEFAULT '0' COMMENT '登录次数',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL ON
  UPDATE
      CURRENT_TIMESTAMP COMMENT '修改时间',
      PRIMARY KEY (`id`),
      UNIQUE KEY `administrators_email_unique` (`email`)
) ENGINE = InnoDB AUTO_INCREMENT = 2 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员表';
-- playedu.app_config 定义

CREATE TABLE `app_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_name` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分组',
  `name` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '升序',
  `field_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '字段类型',
  `key_name` varchar(188) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '键',
  `key_value` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '值',
  `option_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '可选值',
  `is_private` tinyint NOT NULL DEFAULT '0' COMMENT '是否私密信息',
  `help` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '帮助信息',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `is_hidden` tinyint NOT NULL DEFAULT '0' COMMENT '1显示,0否',
  PRIMARY KEY (`id`),
  UNIQUE KEY `app_config_key_unique` (`key_name`)
) ENGINE = InnoDB AUTO_INCREMENT = 23 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置表';
-- playedu.course_attachment 定义

CREATE TABLE `course_attachment` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `sort` int NOT NULL DEFAULT '0' COMMENT '升序',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '附件名',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '附件类型',
  `rid` int NOT NULL DEFAULT '0' COMMENT '资源ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `course_id` (`course_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程附件表';
-- playedu.course_attachment_download_log 定义

CREATE TABLE `course_attachment_download_log` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '课程标题',
  `courser_attachment_id` int NOT NULL DEFAULT '0' COMMENT '课程附件ID',
  `rid` int NOT NULL DEFAULT '0' COMMENT '资源ID',
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '下载IP',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程附件下载日志记录表';
-- playedu.course_chapters 定义

CREATE TABLE `course_chapters` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '章节名',
  `sort` int NOT NULL DEFAULT '0' COMMENT '升序',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '管理员权限表';
-- playedu.course_department_user 定义

CREATE TABLE `course_department_user` (
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `range_id` int NOT NULL DEFAULT '0' COMMENT '指派范围ID',
  `type` int NOT NULL DEFAULT '0' COMMENT '指派范围类型[0:部门,1:学员]',
  KEY `course_id` (`course_id`),
  KEY `range_id` (`range_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程指派范围表';
-- playedu.course_hour 定义

CREATE TABLE `course_hour` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `chapter_id` int NOT NULL DEFAULT '0' COMMENT '章节ID',
  `sort` int NOT NULL DEFAULT '0' COMMENT '升序',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '课时名',
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '课时类型',
  `rid` int NOT NULL DEFAULT '0' COMMENT '资源ID',
  `duration` int NOT NULL DEFAULT '0' COMMENT '时长[s]',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint unsigned DEFAULT '0' COMMENT '删除标志[0:存在,1:删除]',
  PRIMARY KEY (`id`),
  KEY `course_id` (`course_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程课时表';
-- playedu.courses 定义

CREATE TABLE `courses` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '课程标题',
`thumb` int NOT NULL DEFAULT '0' COMMENT '封面',
`charge` int NOT NULL DEFAULT '0' COMMENT '课程价格(分)',
`short_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '简介',
`class_hour` int NOT NULL DEFAULT '0' COMMENT '课时数',
`is_show` tinyint NOT NULL DEFAULT '0' COMMENT '显示[1:是,0:否]',
`is_required` tinyint NOT NULL DEFAULT '0' COMMENT '1:必修,0:选修',
`sort_at` timestamp NULL DEFAULT NULL COMMENT '排序时间',
`extra` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '其它规则[设置]',
`admin_id` int NOT NULL DEFAULT '0' COMMENT '管理员ID',
`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
`deleted_at` timestamp NULL DEFAULT NULL COMMENT '删除时间',
PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程表';
-- playedu.departments 定义

CREATE TABLE `departments` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '部门名',
`parent_id` int NOT NULL DEFAULT '0' COMMENT '父ID',
`parent_chain` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '父链',
`sort` int NOT NULL DEFAULT '0' COMMENT '升序',
`from_scene` int NOT NULL DEFAULT '0' COMMENT '来源[0:本地]',
`created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
`updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员上传图片日志记录表';
-- playedu.ldap_department 定义

CREATE TABLE `ldap_department` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
`uuid` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '唯一特征值',
`department_id` int NOT NULL DEFAULT '0' COMMENT '部门ID',
`dn` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'dn',
`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `unique_uuid` (`uuid`)
    USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- playedu.ldap_sync_department_detail 定义

CREATE TABLE `ldap_sync_department_detail` (
  `id` int NOT NULL AUTO_INCREMENT,
`record_id` int NOT NULL COMMENT '关联的同步记录ID',
`department_id` int NOT NULL DEFAULT '0' COMMENT '关联的部门ID',
`uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LDAP部门UUID',
`dn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LDAP部门DN',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
`action` tinyint NOT NULL COMMENT '操作：1-新增，2-更新，3-删除，4-无变化',
`created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
KEY `record_id` (`record_id`),
KEY `department_id` (`department_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'LDAP部门同步详情表';
-- playedu.ldap_sync_record 定义

CREATE TABLE `ldap_sync_record` (
  `id` int NOT NULL AUTO_INCREMENT,
`admin_id` int NOT NULL DEFAULT '0' COMMENT '执行同步的管理员ID，0表示系统自动执行',
`status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-进行中，1-成功，2-失败',
`s3_file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'S3存储中的文件路径',
`total_department_count` int NOT NULL DEFAULT '0' COMMENT '总部门数量',
`created_department_count` int NOT NULL DEFAULT '0' COMMENT '新增部门数量',
`updated_department_count` int NOT NULL DEFAULT '0' COMMENT '更新部门数量',
`deleted_department_count` int NOT NULL DEFAULT '0' COMMENT '删除部门数量',
`total_user_count` int NOT NULL DEFAULT '0' COMMENT '总用户数量',
`created_user_count` int NOT NULL DEFAULT '0' COMMENT '新增用户数量',
`updated_user_count` int NOT NULL DEFAULT '0' COMMENT '更新用户数量',
`deleted_user_count` int NOT NULL DEFAULT '0' COMMENT '删除用户数量',
`banned_user_count` int NOT NULL DEFAULT '0' COMMENT '被禁止的用户数量',
`error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
`created_at` datetime NOT NULL,
`updated_at` datetime NOT NULL,
PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'LDAP同步记录表';
-- playedu.ldap_sync_user_detail 定义

CREATE TABLE `ldap_sync_user_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
`record_id` int NOT NULL COMMENT '关联的同步记录ID',
`user_id` bigint NOT NULL DEFAULT '0' COMMENT '关联的用户ID',
`uuid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LDAP用户UUID',
`dn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LDAP用户DN',
`cn` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名称',
`uid` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID/登录名',
`email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户邮箱',
`ou` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户部门路径',
`action` tinyint NOT NULL COMMENT '操作：1-新增，2-更新，3-删除，4-无变化，5-禁止',
`created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
KEY `record_id` (`record_id`),
KEY `user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'LDAP用户同步详情表';
-- playedu.ldap_user 定义

CREATE TABLE `ldap_user` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
`uuid` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '唯一特征值',
`user_id` int NOT NULL DEFAULT '0' COMMENT '用户ID',
`cn` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'cn',
`dn` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'dn',
`ou` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'ou',
`uid` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'uid',
`email` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '邮箱',
`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
`updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `unique_uuid` (`uuid`)
    USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- playedu.migrations 定义

CREATE TABLE `migrations` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`migration` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更记录',
PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 39 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程章节表';
-- playedu.resource 定义

CREATE TABLE `resource` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`admin_id` int NOT NULL DEFAULT '0' COMMENT '管理员ID',
`type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '类型',
`name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '资源名',
`extension` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '文件类型',
`size` bigint DEFAULT '0' COMMENT '大小[字节]',
`disk` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '存储磁盘',
`path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '相对地址',
`parent_id` int NOT NULL DEFAULT '0' COMMENT '所属素材',
`is_hidden` tinyint NOT NULL DEFAULT '0' COMMENT '隐藏[0:否,1:是]',
`created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
PRIMARY KEY (`id`),
KEY `type` (`type`)
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源表';
-- playedu.resource_categories 定义

CREATE TABLE `resource_categories` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
`parent_id` int NOT NULL DEFAULT '0' COMMENT '父ID',
`parent_chain` varchar(2550) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '父链',
`name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '分类名',
`sort` int NOT NULL DEFAULT '0' COMMENT '升序',
`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
PRIMARY KEY (`id`)
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门表';
-- playedu.resource_category 定义

CREATE TABLE `resource_category` (
  `cid` int NOT NULL DEFAULT '0' COMMENT '分类ID',
`rid` int NOT NULL DEFAULT '0' COMMENT '资源ID',
KEY `cid` (`cid`),
KEY `rid` (`rid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源分类关联表';
-- playedu.resource_course_category 定义

CREATE TABLE `resource_course_category` (
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `category_id` int NOT NULL DEFAULT '0' COMMENT '父级ID',
  KEY `course_id` (`course_id`),
  KEY `category_id` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程分类关联表';
-- playedu.resource_extra 定义

CREATE TABLE `resource_extra` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `rid` int unsigned NOT NULL DEFAULT '0' COMMENT '资源ID',
  `poster` int unsigned NOT NULL DEFAULT '0' COMMENT '封面资源ID',
  `duration` int unsigned NOT NULL DEFAULT '0' COMMENT '视频、音频总时长,文档总页数',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `rid` (`rid`)
) ENGINE = InnoDB AUTO_INCREMENT = 2 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '资源详细信息表';
-- playedu.user_course_hour_records 定义

CREATE TABLE `user_course_hour_records` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `hour_id` int NOT NULL DEFAULT '0' COMMENT '课时ID',
  `total_duration` int NOT NULL DEFAULT '0' COMMENT '总时长',
  `finished_duration` int NOT NULL DEFAULT '0' COMMENT '已完成时长',
  `real_duration` int NOT NULL DEFAULT '0' COMMENT '实际观看时长',
  `is_finished` tinyint NOT NULL DEFAULT '0' COMMENT '是否看完[1:是,0:否]',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '看完时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `u_h_c_id` (`user_id`,
  `hour_id`,
  `course_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '线上课课时学员学习记录表';
-- playedu.user_course_records 定义

CREATE TABLE `user_course_records` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `course_id` int NOT NULL DEFAULT '0' COMMENT '课程ID',
  `hour_count` int NOT NULL DEFAULT '0' COMMENT '课时数量',
  `finished_count` int NOT NULL DEFAULT '0' COMMENT '已完成课时数',
  `progress` int NOT NULL DEFAULT '0' COMMENT '进度',
  `is_finished` tinyint NOT NULL DEFAULT '0' COMMENT '看完[1:是,0:否]',
  `finished_at` timestamp NULL DEFAULT NULL COMMENT '看完时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分类表';
-- playedu.user_department 定义

CREATE TABLE `user_department` (
  `user_id` int unsigned NOT NULL DEFAULT '0' COMMENT '学员ID',
  `dep_id` int unsigned NOT NULL DEFAULT '0' COMMENT '部门ID',
  KEY `user_id` (`user_id`),
  KEY `dep_id` (`dep_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员部门关联表';
-- playedu.user_learn_duration_records 定义

CREATE TABLE `user_learn_duration_records` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `created_date` date NOT NULL COMMENT '创建时间',
  `duration` int unsigned NOT NULL DEFAULT '0' COMMENT '已学习时长[微秒]',
  `start_at` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `end_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `from_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '来源ID',
  `from_scene` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '记录来源[线上课:COURSE,学习任务:STUDY]',
  PRIMARY KEY (`id`),
  KEY `u_d` (`user_id`,
  `created_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员学习时长表';
-- playedu.user_learn_duration_stats 定义

CREATE TABLE `user_learn_duration_stats` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `duration` bigint NOT NULL DEFAULT '0' COMMENT '学习时长',
  `created_date` date NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `u_d` (`user_id`,
  `created_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员学习时长记录表';
-- playedu.user_login_records 定义

CREATE TABLE `user_login_records` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员ID',
  `jti` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'JTI',
  `ip` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '登录IP',
  `ip_area` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'IP解析区域',
  `browser` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '浏览器',
  `browser_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '浏览器版本',
  `os` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '操作系统',
  `expired` bigint NOT NULL DEFAULT '0' COMMENT '过期时间',
  `is_logout` tinyint NOT NULL DEFAULT '0' COMMENT '是否注销',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `jti` (`jti`),
  KEY `user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员登录记录表';
-- playedu.user_upload_image_logs 定义

CREATE TABLE `user_upload_image_logs` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` int NOT NULL DEFAULT '0' COMMENT '学员时间',
  `typed` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '图片类型',
  `scene` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '上传场景',
  `driver` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '驱动',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '相对路径',
  `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '访问地址',
  `size` bigint NOT NULL DEFAULT '0' COMMENT '大小,单位:字节',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '文件名',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '线上课学员学习记录表';
-- playedu.users 定义

CREATE TABLE `users` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '邮件',
  `name` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '真实姓名',
  `avatar` int NOT NULL DEFAULT '0' COMMENT '头像',
  `password` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '密码',
  `salt` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'salt',
  `id_card` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '身份证号',
  `credit1` int NOT NULL DEFAULT '0' COMMENT '学分',
  `create_ip` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '注册IP',
  `create_city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '注册城市',
  `is_active` tinyint NOT NULL DEFAULT '0' COMMENT '激活[1:是,0:否]',
  `is_lock` tinyint NOT NULL DEFAULT '0' COMMENT '锁定[1:是,0:否]',
  `is_verify` tinyint NOT NULL DEFAULT '0' COMMENT '实名认证[1:是,0:否]',
  `verify_at` timestamp NULL DEFAULT NULL COMMENT '实名认证时间',
  `is_set_password` tinyint NOT NULL DEFAULT '0' COMMENT '设置密码[1:是,0:否]',
  `login_at` timestamp NULL DEFAULT NULL COMMENT '登录时间',
  `created_at` timestamp NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  `from_scene` int NOT NULL DEFAULT '0' COMMENT '来源[0:本地,1:企业微信,2:飞书]',
  `deleted` tinyint unsigned DEFAULT '0' COMMENT '删除标志[0:存在,1:删除]',
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学员表';
