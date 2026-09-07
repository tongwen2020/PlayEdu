-- Question bank v1. Additive schema; existing course tables are not modified.
CREATE TABLE IF NOT EXISTS exam_bank_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id INT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS exam_question_banks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    practice_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    owner_id INT NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS exam_question_categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bank_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_exam_category UNIQUE (bank_id, parent_id, name),
    CONSTRAINT fk_exam_category_bank FOREIGN KEY (bank_id) REFERENCES exam_question_banks(id)
);
CREATE TABLE IF NOT EXISTS exam_questions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bank_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    code VARCHAR(64) NOT NULL,
    type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    stem VARCHAR(10000) NOT NULL,
    tags_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_question_code UNIQUE (bank_id, code),
    CONSTRAINT fk_exam_question_bank FOREIGN KEY (bank_id) REFERENCES exam_question_banks(id),
    CONSTRAINT fk_exam_question_category FOREIGN KEY (category_id) REFERENCES exam_question_categories(id)
);
CREATE TABLE IF NOT EXISTS exam_question_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content_json TEXT NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_question_version UNIQUE (question_id, version_no),
    CONSTRAINT fk_exam_version_question FOREIGN KEY (question_id) REFERENCES exam_questions(id)
);
CREATE TABLE IF NOT EXISTS exam_practice_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    question_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    answer_json TEXT NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    result VARCHAR(20) NOT NULL,
    request_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_practice_request UNIQUE (user_id, request_key),
    CONSTRAINT fk_exam_practice_version FOREIGN KEY (question_id, version_no) REFERENCES exam_question_versions(question_id, version_no)
);
