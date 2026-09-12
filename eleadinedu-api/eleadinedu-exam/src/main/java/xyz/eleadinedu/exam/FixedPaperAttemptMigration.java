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
package xyz.eleadinedu.exam;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persists immutable learner submissions against a published fixed-paper version. */
@Component
@Order(35)
public class FixedPaperAttemptMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public FixedPaperAttemptMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String name = "20260911_fixed_paper_attempt_v1";
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, name)
                > 0) return;
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS exam_fixed_paper_attempts (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    paper_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    answers_json LONGTEXT NOT NULL,
                    grading_json LONGTEXT NOT NULL,
                    score DECIMAL(10,2) NOT NULL,
                    max_score DECIMAL(10,2) NOT NULL,
                    pass_score DECIMAL(10,2) NOT NULL,
                    passed BOOLEAN NOT NULL,
                    question_count INT NOT NULL,
                    correct_count INT NOT NULL,
                    request_key VARCHAR(64) NOT NULL,
                    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_exam_fixed_paper_request UNIQUE (user_id, request_key),
                    INDEX idx_exam_fixed_paper_user (user_id, submitted_at),
                    INDEX idx_exam_fixed_paper_paper (paper_id, version_no, submitted_at),
                    CONSTRAINT fk_exam_fixed_paper_paper FOREIGN KEY (paper_id)
                        REFERENCES exam_papers(id)
                )
                """);
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", name);
    }
}
