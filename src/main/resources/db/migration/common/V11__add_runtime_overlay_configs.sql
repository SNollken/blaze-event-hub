-- Migration V11: Runtime Overlay Configs
-- Tabela usada pelo RuntimeOverlayConfigService para persistir configs de overlays
-- (overlay-runtime.html + overlay-runtime.js — sistema primário usado pelos streamers).
-- A runtime overlay config store (InMemoryRuntimeOverlayConfigStore) faz UPDATE-then-INSERT;
-- a tabela precisa existir onde migrations rodam (Supabase/PostgreSQL em prod).
-- NOTE: schema.sql já cria esta tabela para o perfil dev/local (sql.init.mode=always).

CREATE TABLE IF NOT EXISTS runtime_overlay_configs (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(80) NOT NULL,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    refresh_interval_ms BIGINT NOT NULL,
    custom_css TEXT,
    position_x INT NOT NULL,
    position_y INT NOT NULL,
    position_width INT NOT NULL,
    position_height INT NOT NULL,
    opacity DOUBLE PRECISION NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_runtime_overlay_configs_type ON runtime_overlay_configs(type);
