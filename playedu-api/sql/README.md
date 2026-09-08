# PlayEdu 数据库初始化

初始化一个空的 MySQL 8 数据库时，按顺序执行：

```bash
mysql -h 127.0.0.1 -P 23307 -u root -p playedu < create_tables.sql
mysql -h 127.0.0.1 -P 23307 -u root -p playedu < init_data.sql
```

`create_tables.sql` 创建完整表结构，`init_data.sql` 创建默认超级管理员和角色关系。默认登录信息：

- 账号：`admin@playedu.xyz`
- 密码：`playedu`

`init_data.sql` 可以重复执行：已有角色、账号和关联不会重复创建，也不会覆盖已有账号的密码。该默认密码是公开的，首次登录后应立即修改。
