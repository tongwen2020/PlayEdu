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

/** Additive migration for fixed-paper exam result records. */
@Component
@Order(36)
public class ExamRecordMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public ExamRecordMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String name = "20260914_exam_record_v1";
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, name)
                > 0) return;
        jdbc.execute(
                """
                CREATE TABLE IF NOT EXISTS exam_records (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    paper_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    fixed_paper_attempt_id BIGINT NULL,
                    exam_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    score DECIMAL(10,2) NOT NULL,
                    max_score DECIMAL(10,2) NOT NULL,
                    pass_score DECIMAL(10,2) NOT NULL,
                    passed BOOLEAN NOT NULL,
                    question_count INT NOT NULL,
                    correct_count INT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_exam_record_fixed_attempt UNIQUE (fixed_paper_attempt_id),
                    INDEX idx_exam_record_user_time (user_id, exam_time),
                    INDEX idx_exam_record_paper_time (paper_id, version_no, exam_time),
                    INDEX idx_exam_record_passed_time (passed, exam_time),
                    CONSTRAINT fk_exam_record_paper FOREIGN KEY (paper_id)
                        REFERENCES exam_papers(id),
                    CONSTRAINT fk_exam_record_fixed_attempt FOREIGN KEY (fixed_paper_attempt_id)
                        REFERENCES exam_fixed_paper_attempts(id)
                )
                """);
        jdbc.update(
                """
                INSERT INTO exam_records(
                    user_id,paper_id,version_no,fixed_paper_attempt_id,exam_time,score,max_score,
                    pass_score,passed,question_count,correct_count,created_at)
                SELECT
                    user_id,paper_id,version_no,id,submitted_at,score,max_score,pass_score,
                    passed,question_count,correct_count,submitted_at
                FROM exam_fixed_paper_attempts
                WHERE id NOT IN (
                    SELECT fixed_paper_attempt_id FROM exam_records
                    WHERE fixed_paper_attempt_id IS NOT NULL
                )
                """);
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", name);
    }
}
