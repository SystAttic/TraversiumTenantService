-- Add unique constraint on tenant name
ALTER TABLE public.tenants
ADD CONSTRAINT uk_tenants_name UNIQUE (name);

-- Add index for faster lookups by name
CREATE INDEX IF NOT EXISTS idx_tenants_name ON public.tenants(name);

