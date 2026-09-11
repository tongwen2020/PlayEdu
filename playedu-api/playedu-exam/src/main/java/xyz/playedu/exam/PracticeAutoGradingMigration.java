/*
 * Copyright (C) 2023 杭州白书科技有限公司
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
package xyz.playedu.exam;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Additive migration for bank-level submission and automatic grading. */
@Component
@Order(25)
public class PracticeAutoGradingMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public PracticeAutoGradingMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String name = "20260910_practice_auto_grading_v1";
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, name)
                > 0) return;
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS exam_practice_paper_attempts (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    bank_id BIGINT NOT NULL,
                    answers_json LONGTEXT NOT NULL,
                    grading_json LONGTEXT NOT NULL,
                    score DECIMAL(10,2) NOT NULL,
                    max_score DECIMAL(10,2) NOT NULL,
                    question_count INT NOT NULL,
                    correct_count INT NOT NULL,
                    request_key VARCHAR(64) NOT NULL,
                    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_exam_practice_paper_request UNIQUE (user_id, request_key),
                    INDEX idx_exam_practice_paper_user (user_id, submitted_at),
                    INDEX idx_exam_practice_paper_bank (bank_id, submitted_at),
                    CONSTRAINT fk_exam_practice_paper_bank FOREIGN KEY (bank_id)
                        REFERENCES exam_question_banks(id)
                )
                """);
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", name);
    }
}
