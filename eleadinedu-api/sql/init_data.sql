-- PlayEdu initial system data.
-- Run this script after create_tables.sql against the same database.
-- Default administrator: admin@playedu.xyz / playedu
-- The API hashes administrator passwords as MD5(password + salt).

START TRANSACTION;

INSERT INTO `admin_roles` (`name`, `slug`, `created_at`, `updated_at`)
SELECT '超级管理员', 'super-role', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM `admin_roles` WHERE `slug` = 'super-role'
);

SET @playedu_super_role_id = (
  SELECT `id` FROM `admin_roles` WHERE `slug` = 'super-role' ORDER BY `id` LIMIT 1
);

SET @playedu_default_admin_salt = LEFT(REPLACE(UUID(), '-', ''), 6);

INSERT INTO `admin_users` (
  `name`,
  `email`,
  `password`,
  `salt`,
  `is_ban_login`,
  `created_at`,
  `updated_at`
)
SELECT
  '超级管理员',
  'admin@playedu.xyz',
  MD5(CONCAT('playedu', @playedu_default_admin_salt)),
  @playedu_default_admin_salt,
  0,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM `admin_users` WHERE `email` = 'admin@playedu.xyz'
);

SET @playedu_super_admin_id = (
  SELECT `id` FROM `admin_users` WHERE `email` = 'admin@playedu.xyz' ORDER BY `id` LIMIT 1
);

INSERT INTO `admin_user_role` (`admin_id`, `role_id`)
SELECT @playedu_super_admin_id, @playedu_super_role_id
WHERE @playedu_super_admin_id IS NOT NULL
  AND @playedu_super_role_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1
    FROM `admin_user_role`
    WHERE `admin_id` = @playedu_super_admin_id
      AND `role_id` = @playedu_super_role_id
  );

COMMIT;

SET @playedu_super_role_id = NULL;
SET @playedu_super_admin_id = NULL;
SET @playedu_default_admin_salt = NULL;
