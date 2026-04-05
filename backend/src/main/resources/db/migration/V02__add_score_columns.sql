-- Sprint 2: columnas de scoring, metadata token, error y dirección desnormalizada
ALTER TABLE blockchain.blockchain_reports
    ADD COLUMN IF NOT EXISTS overall_score    INT,
    ADD COLUMN IF NOT EXISTS risk_level       TEXT,
    ADD COLUMN IF NOT EXISTS token_name       TEXT,
    ADD COLUMN IF NOT EXISTS token_symbol     TEXT,
    ADD COLUMN IF NOT EXISTS total_supply     TEXT,
    ADD COLUMN IF NOT EXISTS holder_count     INT,
    ADD COLUMN IF NOT EXISTS error_message    TEXT,
    ADD COLUMN IF NOT EXISTS contract_address TEXT,
    ADD COLUMN IF NOT EXISTS chain            TEXT;

CREATE INDEX IF NOT EXISTS idx_blockchain_reports_risk  ON blockchain.blockchain_reports(risk_level);
CREATE INDEX IF NOT EXISTS idx_blockchain_reports_chain ON blockchain.blockchain_reports(chain);
