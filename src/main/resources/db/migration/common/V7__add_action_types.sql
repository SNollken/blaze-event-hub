-- Migration V7: Action types para múltiplos gatilhos de entrada
-- Adiciona tabela event_action_rules (regras por tipo de ação)
-- e coluna action_type em event_participants (origem da entrada)

-- Tipos de ação suportados: chat, vote, sub, gifted_sub, follow, donation
-- A tabela event_action_rules permite configurar peso/regras por tipo

CREATE TABLE IF NOT EXISTS event_action_rules (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    weight INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_event_action_rules_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT uq_event_action_rule UNIQUE (event_id, action_type),
    CONSTRAINT chk_action_type CHECK (action_type IN ('chat', 'vote', 'sub', 'gifted_sub', 'follow', 'donation')),
    CONSTRAINT chk_action_weight CHECK (weight >= 1 AND weight <= 100)
);

-- Coluna action_type na tabela de participantes
ALTER TABLE event_participants ADD COLUMN IF NOT EXISTS action_type VARCHAR(40) DEFAULT 'chat';
ALTER TABLE event_participants ADD COLUMN IF NOT EXISTS entry_weight INT DEFAULT 1;

-- Atualiza participantes existentes como 'chat'
UPDATE event_participants SET action_type = 'chat' WHERE action_type IS NULL;

-- Índices
CREATE INDEX IF NOT EXISTS idx_event_action_rules_event ON event_action_rules(event_id);
CREATE INDEX IF NOT EXISTS idx_event_participants_action ON event_participants(event_id, action_type);
