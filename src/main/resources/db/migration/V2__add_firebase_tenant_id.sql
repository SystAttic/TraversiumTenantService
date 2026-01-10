ALTER TABLE tenants ADD COLUMN IF NOT EXISTS firebase_tenant_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_tenants_firebase_tenant_id ON tenants(firebase_tenant_id);

