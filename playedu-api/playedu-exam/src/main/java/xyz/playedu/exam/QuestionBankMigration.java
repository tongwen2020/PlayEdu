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

import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Additive, restartable migration. Fail startup if the question-bank schema cannot initialize. */
@Component
@Order(20)
public class QuestionBankMigration implements CommandLineRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public QuestionBankMigration(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        String name = "20260905_question_bank_v1";
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM migrations WHERE migration=?", Long.class, name)
                > 0) return;
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/exam-question-bank.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        jdbc.update("INSERT INTO migrations(migration) VALUES(?)", name);
    }
}
