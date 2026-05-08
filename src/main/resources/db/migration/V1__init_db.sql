CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    jira_key VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE system_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE system_settings (
    id BIGINT PRIMARY KEY,
    green_threshold DOUBLE PRECISION,
    yellow_threshold DOUBLE PRECISION,
    scoring_alpha DOUBLE PRECISION,
    jira_url VARCHAR(255),
    github_owner VARCHAR(255),
    jira_username VARCHAR(1024),
    jira_token VARCHAR(1024),
    github_token VARCHAR(1024),
    timezone VARCHAR(100),
    min_commit_length INTEGER,
    boilerplate_words TEXT,
    done_statuses VARCHAR(255),
    isolated_event_duration_seconds BIGINT
);

CREATE TABLE employees (
    id UUID PRIMARY KEY,
    team_id BIGINT REFERENCES teams(id),
    role VARCHAR(100),
    github_username VARCHAR(100) UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    timezone VARCHAR(50) DEFAULT 'Asia/Yekaterinburg'
);

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
    message TEXT,
    message_length INTEGER
);

CREATE TABLE git_pull_requests (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(50) UNIQUE,
    employee_id UUID REFERENCES employees(id),
    created_at TIMESTAMP,
    merged_at TIMESTAMP,
    closed_at TIMESTAMP,
    mergeable_state VARCHAR(50),
    lead_time_minutes INTEGER
);

CREATE TABLE jira_task_comments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT REFERENCES jira_tasks(internal_id) ON DELETE CASCADE,
    employee_id UUID REFERENCES employees(id),
    created_at TIMESTAMP,
    body_length INTEGER,
    attachments_count INTEGER DEFAULT 0
);

CREATE TABLE jira_task_changelogs (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT REFERENCES jira_tasks(internal_id) ON DELETE CASCADE,
    field_name VARCHAR(100),
    from_string VARCHAR(255),
    to_string VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE ml_models (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50),
    trained_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    w0_bias DOUBLE PRECISION,
    w1_ee DOUBLE PRECISION,
    w2_dp DOUBLE PRECISION,
    w3_rpa DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT FALSE
);

CREATE TABLE daily_metrics (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID REFERENCES employees(id) NOT NULL,
    date DATE NOT NULL,

    -- EE Index
    activity_span_seconds BIGINT DEFAULT 0,
    night_events_count INTEGER DEFAULT 0,
    weekend_events_count INTEGER DEFAULT 0,

    -- DP Index
    bad_commit_ratio DOUBLE PRECISION DEFAULT 0.0,
    jira_effort_score INTEGER DEFAULT 0,

    -- RPA Index
    pr_lead_time_avg_minutes DOUBLE PRECISION DEFAULT 0.0,
    task_stagnation_seconds BIGINT DEFAULT 0,
    reopen_rate DOUBLE PRECISION DEFAULT 0.0,
    merge_conflicts_count INTEGER DEFAULT 0,

    CONSTRAINT uk_employee_date UNIQUE (employee_id, date)
);

CREATE TABLE burnout_scores (
    id BIGSERIAL PRIMARY KEY,
    employee_id UUID REFERENCES employees(id),
    model_id BIGINT REFERENCES ml_models(id),
    target_date DATE NOT NULL,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ee_index FLOAT,
    dp_index FLOAT,
    rpa_index FLOAT,
    risk_probability FLOAT CHECK (risk_probability >= 0 AND risk_probability <= 1),
    status_color VARCHAR(10),
    CONSTRAINT uk_burnout_score_emp_date UNIQUE (employee_id, target_date)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES system_users(id) ON DELETE CASCADE NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expiry_date TIMESTAMP NOT NULL
);

CREATE INDEX idx_burnout_scores_employee_date ON burnout_scores(employee_id, target_date DESC);
CREATE INDEX idx_daily_metrics_employee_date ON daily_metrics(employee_id, date DESC);
CREATE UNIQUE INDEX idx_ml_models_active ON ml_models(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
INSERT INTO system_settings (id, green_threshold, yellow_threshold, scoring_alpha, jira_url, github_owner, timezone, min_commit_length, boilerplate_words, done_statuses, isolated_event_duration_seconds)
VALUES (1, 0.4, 0.75, 0.1, 'https://company.atlassian.net', 'OrganizationName', 'Asia/Yekaterinburg', 8, 'fix,update,\.,исправил,обновил,test,vip,фича', 'Done,Готово,Closed,Resolved', 1800);