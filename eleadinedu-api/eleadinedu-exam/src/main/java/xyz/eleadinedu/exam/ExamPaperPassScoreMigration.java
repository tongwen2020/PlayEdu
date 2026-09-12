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

/** Additive migration for configurable paper pass scores and immutable version snapshots. */
@Component
@Order(31)
public class ExamPaperPassScoreMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public ExamPaperPassScoreMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String name = "20260911_exam_paper_pass_score_v1";
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, name)
                > 0) return;
        addColumnIfMissing(
                "exam_papers",
                "pass_score",
                "ALTER TABLE exam_papers ADD COLUMN pass_score DECIMAL(10,2) NOT NULL DEFAULT 0"
                        + " COMMENT '通过分数' AFTER description");
        addColumnIfMissing(
                "exam_paper_versions",
                "pass_score",
                "ALTER TABLE exam_paper_versions ADD COLUMN pass_score DECIMAL(10,2) NOT NULL"
                        + " DEFAULT 0 COMMENT '通过分数' AFTER total_score");
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", name);
    }

    private void addColumnIfMissing(String table, String column, String sql) {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE"
                                + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?",
                        Long.class,
                        table,
                        column);
        if (count == 0) jdbc.execute(sql);
    }
}
