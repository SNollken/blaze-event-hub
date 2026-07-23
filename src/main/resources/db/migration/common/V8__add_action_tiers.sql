-- Migration V8: Bonus Tier System para Action Types
-- Adiciona tabela event_action_tiers para sistema de tiers/bônus por tipo de ação
-- Adiciona coluna mode em event_action_rules (REPLACE/ACCUMULATE)
-- Adiciona raw_action_count em event_participants para rastrear contagem bruta

-- Tabela de tiers de ação por evento
-- Cada tier define um threshold (ex: 10 votos), entries ganhos (ex: 1 entry) e tier_order para ordenação
CREATE TABLE IF NOT EXISTS event_action_tiers (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    threshold INT NOT NULL,
    entries INT NOT NULL,
    tier_order INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_event_action_tiers_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT uq_event_action_tier_order UNIQUE (event_id, action_type, tier_order),
    CONSTRAINT chk_tier_threshold CHECK (threshold >= 1),
    CONSTRAINT chk_tier_entries CHECK (entries >= 1),
    CONSTRAINT chk_tier_order CHECK (tier_order >= 1),
    CONSTRAINT chk_tier_action_type CHECK (action_type IN ('chat', 'vote', 'sub', 'gifted_sub', 'follow', 'donation', 'manual'))
);

-- Adiciona coluna mode na tabela event_action_rules
-- REPLACE: tier mais alto substitui os anteriores (ex: 10 votos=1e, 30 votos=5e -> 30 votos = 5 entries total)
-- ACCUMULATE: tiers somam (ex: 10 votos=1e, 30 votos=5e -> 30 votos = 6 entries total)
ALTER TABLE event_action_rules ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'REPLACE';
ALTER TABLE event_action_rules ADD CONSTRAINT chk_action_mode CHECK (mode IN ('REPLACE', 'ACCUMULATE'));

-- Adiciona coluna raw_action_count em event_participants
-- Rastreia quantas ações brutas do tipo o usuário realizou (ex: quantos votos deu, quantos chats enviou)
-- Usado para calcular qual tier o usuário atingiu
ALTER TABLE event_participants ADD COLUMN IF NOT EXISTS raw_action_count INT DEFAULT 0;
ALTER TABLE event_participants ADD CONSTRAINT chk_raw_action_count CHECK (raw_action_count >= 0);

-- Índices para performance
CREATE INDEX IF NOT EXISTS idx_event_action_tiers_event_type ON event_action_tiers(event_id, action_type, tier_order);
CREATE INDEX IF NOT EXISTS idx_event_participants_raw_count ON event_participants(event_id, action_type, raw_action_count);