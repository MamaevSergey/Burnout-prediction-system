-- Справочники
CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    jira_key VARCHAR(50) UNIQUE NOT NULL
);

-- Сотрудники (Анонимизация)
CREATE TABLE employees (
    id UUID PRIMARY KEY,
    team_id BIGINT REFERENCES teams(id),
    role VARCHAR(100),
    github_username VARCHAR(100) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE
);

-- Сырые данные (Источники)
CREATE TABLE jira_tasks (
    internal_id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(50) UNIQUE,
    employee_id UUID REFERENCES employees(id),
    project_id BIGINT REFERENCES projects(id),
    status VARCHAR(100),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE git_commits (
    internal_id BIGSERIAL PRIMARY KEY,
    external_hash VARCHAR(64) UNIQUE,
    employee_id UUID REFERENCES employees(id),
    committed_at TIMESTAMP,
    message_length INTEGER
);

CREATE TABLE git_pull_requests (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(50) UNIQUE,
    employee_id UUID REFERENCES employees(id),
    created_at TIMESTAMP,
    merged_at TIMESTAMP,
    lead_time_minutes INTEGER
);

-- Аналитика и Математика
CREATE TABLE ml_models (
    id BIGSERIAL PRIMARY KEY,
    trained_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    w0_bias FLOAT NOT NULL,
    w1_ee FLOAT NOT NULL,
    w2_dp FLOAT NOT NULL,
    w3_rpa FLOAT NOT NULL,
    is_active BOOLEAN DEFAULT FALSE
);

CREATE TABLE daily_metrics (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID REFERENCES employees(id),
    date DATE NOT NULL,
    night_work_seconds INTEGER DEFAULT 0,
    weekend_work_seconds INTEGER DEFAULT 0,
    total_work_seconds INTEGER DEFAULT 0,
    avg_commit_msg_len INTEGER DEFAULT 0,
    jira_comments_count INTEGER DEFAULT 0,
    pr_lead_time_avg INTEGER DEFAULT 0,
    reopen_rate INTEGER DEFAULT 0,
    task_stagnation_seconds INTEGER DEFAULT 0,
    CONSTRAINT uk_employee_date UNIQUE (employee_id, date)
);

-- Результаты
CREATE TABLE burnout_scores (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID REFERENCES employees(id),
    model_id BIGINT REFERENCES ml_models(id),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ee_index FLOAT,
    dp_index FLOAT,
    rpa_index FLOAT,
    risk_probability FLOAT CHECK (risk_probability >= 0 AND risk_probability <= 1),
    status_color VARCHAR(10)
);