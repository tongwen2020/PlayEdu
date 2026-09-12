/*
 * Copyright (C) 南京意领信息科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.eleadinedu.system.checks;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adds an independent login account to existing student records. */
@Component
@Order(11)
public class UserAccountMigration implements CommandLineRunner {
    private static final String MIGRATION = "20260912_user_login_account_v1";

    private final JdbcTemplate jdbc;

    public UserAccountMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        Long migrated =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, MIGRATION);
        if (migrated != null && migrated > 0) {
            return;
        }

        if (!columnExists()) {
            jdbc.execute(
                    "ALTER TABLE users ADD COLUMN username VARCHAR(64) CHARACTER SET utf8mb4"
                            + " COLLATE utf8mb4_unicode_ci NULL COMMENT '登录账号' AFTER email");
        }
        jdbc.update("UPDATE users SET username=email WHERE username IS NULL OR username='' ");
        jdbc.execute("ALTER TABLE users MODIFY COLUMN username VARCHAR(64) NOT NULL COMMENT '登录账号'");
        if (!uniqueIndexExists()) {
            jdbc.execute("ALTER TABLE users ADD UNIQUE INDEX username (username)");
        }
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", MIGRATION);
    }

    private boolean columnExists() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                                + " AND TABLE_NAME='users' AND COLUMN_NAME='username'",
                        Long.class);
        return count != null && count > 0;
    }

    private boolean uniqueIndexExists() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()"
                                + " AND TABLE_NAME='users' AND COLUMN_NAME='username'"
                                + " AND NON_UNIQUE=0",
                        Long.class);
        return count != null && count > 0;
    }
}
