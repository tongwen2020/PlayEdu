-- Exam paper library v1. Fixed papers only; published versions are immutable snapshots.
CREATE TABLE IF NOT EXISTS exam_paper_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    admin_id INT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_paper_categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_paper_category UNIQUE (owner_id, parent_id, name)
);

CREATE TABLE IF NOT EXISTS exam_papers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    category_id BIGINT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    tags_json TEXT NOT NULL,
    mode VARCHAR(20) NOT NULL DEFAULT 'fixed',
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    revision INT NOT NULL DEFAULT 1,
    current_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_paper_code UNIQUE (owner_id, code),
    INDEX idx_exam_papers_owner_status (owner_id, status),
    INDEX idx_exam_papers_category (category_id),
    CONSTRAINT fk_exam_paper_category FOREIGN KEY (category_id) REFERENCES exam_paper_categories(id)
);

CREATE TABLE IF NOT EXISTS exam_paper_draft_sections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    position INT NOT NULL,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_exam_paper_section_position UNIQUE (paper_id, position),
    CONSTRAINT fk_exam_paper_section_paper FOREIGN KEY (paper_id) REFERENCES exam_papers(id)
);

CREATE TABLE IF NOT EXISTS exam_paper_draft_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_version INT NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    position INT NOT NULL,
    CONSTRAINT uq_exam_paper_question UNIQUE (paper_id, question_id),
    CONSTRAINT uq_exam_paper_item_position UNIQUE (section_id, position),
    CONSTRAINT fk_exam_paper_item_paper FOREIGN KEY (paper_id) REFERENCES exam_papers(id),
    CONSTRAINT fk_exam_paper_item_section FOREIGN KEY (section_id) REFERENCES exam_paper_draft_sections(id),
    CONSTRAINT fk_exam_paper_item_question_version FOREIGN KEY (question_id, question_version)
        REFERENCES exam_question_versions(question_id, version_no)
);

CREATE TABLE IF NOT EXISTS exam_paper_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    paper_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content_json LONGTEXT NOT NULL,
    question_count INT NOT NULL,
    total_score DECIMAL(10,2) NOT NULL,
    objective_score DECIMAL(10,2) NOT NULL,
    subjective_score DECIMAL(10,2) NOT NULL,
    requires_manual_grading BOOLEAN NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exam_paper_version UNIQUE (paper_id, version_no),
    CONSTRAINT fk_exam_paper_version_paper FOREIGN KEY (paper_id) REFERENCES exam_papers(id)
);
