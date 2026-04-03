-- Schema separado para no interferir con guardianos-audit
CREATE SCHEMA IF NOT EXISTS blockchain;

-- Tabla de targets (direcciones/contratos a auditar)
CREATE TABLE blockchain.blockchain_targets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    address     TEXT NOT NULL,
    chain_id    TEXT NOT NULL DEFAULT 'ethereum',
    label       TEXT,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now(),
    UNIQUE(tenant_id, address, chain_id)
);

-- Tabla de reportes blockchain
CREATE TABLE blockchain.blockchain_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    target_id       UUID REFERENCES blockchain.blockchain_targets(id) ON DELETE CASCADE,
    status          TEXT NOT NULL DEFAULT 'pending', -- pending, running, completed, failed
    report_json     JSONB,
    findings_count  INT DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- Índices para rendimiento
CREATE INDEX idx_blockchain_targets_tenant ON blockchain.blockchain_targets(tenant_id);
CREATE INDEX idx_blockchain_reports_tenant ON blockchain.blockchain_reports(tenant_id);
CREATE INDEX idx_blockchain_reports_status ON blockchain.blockchain_reports(status);

-- Trigger para updated_at
CREATE OR REPLACE FUNCTION blockchain.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_targets_updated_at 
    BEFORE UPDATE ON blockchain.blockchain_targets 
    FOR EACH ROW EXECUTE FUNCTION blockchain.update_updated_at_column();

CREATE TRIGGER update_reports_updated_at 
    BEFORE UPDATE ON blockchain.blockchain_reports 
    FOR EACH ROW EXECUTE FUNCTION blockchain.update_updated_at_column();
