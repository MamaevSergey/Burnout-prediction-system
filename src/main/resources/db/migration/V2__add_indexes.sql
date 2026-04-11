CREATE INDEX idx_burnout_scores_employee_date
    ON burnout_scores(employee_id, calculated_at DESC);

CREATE INDEX idx_daily_metrics_employee_date
    ON daily_metrics(employee_id, date DESC);

CREATE UNIQUE INDEX idx_ml_models_active
    ON ml_models(is_active) WHERE is_active = TRUE;