-- Blaze Event Hub — Schema v1.0.0
-- Tabelas do MVP: members, events, event_rules, event_interests,
-- detected_actions, event_entries, event_winners, audit_log

-- Perfil do usuário logado com Blaze
CREATE TABLE IF NOT EXISTS members (
    id VARCHAR(64) PRIMARY KEY,
    blaze_user_id VARCHAR(255) NOT NULL UNIQUE,
    blaze_username VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512),
    wallet_address VARCHAR(255),
    access_token_encrypted CLOB,
    refresh_token_encrypted CLOB,
    status VARCHAR(40) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Evento/giveaway criado por um membro
CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(64) PRIMARY KEY,
    creator_member_id VARCHAR(64) NOT NULL,
    creator_blaze_user_id VARCHAR(255) NOT NULL,
    creator_channel_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description CLOB,
    prize_type VARCHAR(80),
    prize_description CLOB,
    status VARCHAR(40) NOT NULL DEFAULT 'draft',
    rules_mode VARCHAR(20) NOT NULL DEFAULT 'tier',
    max_entries_per_participant INT DEFAULT 0,
    requires_interest_before_action BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (creator_member_id) REFERENCES members(id)
);

-- Regras de entries configuradas por evento
CREATE TABLE IF NOT EXISTS event_rules (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    threshold_amount INT NOT NULL,
    entries INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id),
    CHECK (threshold_amount > 0),
    CHECK (entries > 0)
);

-- Manifestação de interesse de um membro em um evento
CREATE TABLE IF NOT EXISTS event_interests (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'interested',
    interested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_calculated_entries INT DEFAULT 0,
    notes CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (member_id) REFERENCES members(id),
    UNIQUE (event_id, member_id)
);

-- Ação detectada via WebSocket/API Blaze
CREATE TABLE IF NOT EXISTS detected_actions (
    id VARCHAR(64) PRIMARY KEY,
    idempotency_hash VARCHAR(128) NOT NULL UNIQUE,
    event_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    target_channel_id VARCHAR(255) NOT NULL,
    actor_blaze_user_id VARCHAR(255) NOT NULL,
    actor_username VARCHAR(255),
    actor_wallet_address VARCHAR(255),
    amount INT NOT NULL DEFAULT 0,
    raw_payload CLOB,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (member_id) REFERENCES members(id)
);

-- Entry concedida a um participante com base em ação detectada
CREATE TABLE IF NOT EXISTS event_entries (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    detected_action_id VARCHAR(64),
    action_type VARCHAR(40) NOT NULL,
    amount INT NOT NULL DEFAULT 0,
    entries_granted INT NOT NULL DEFAULT 0,
    calculation_reason CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (member_id) REFERENCES members(id),
    FOREIGN KEY (detected_action_id) REFERENCES detected_actions(id)
);

-- Vencedor do sorteio de um evento
CREATE TABLE IF NOT EXISTS event_winners (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    entries_at_draw_time INT NOT NULL DEFAULT 0,
    draw_seed VARCHAR(255),
    draw_method VARCHAR(80),
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    selected_by VARCHAR(255),
    notes CLOB,
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (member_id) REFERENCES members(id)
);

-- Log de auditoria para todas operações críticas
CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(64) PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    before_state CLOB,
    after_state CLOB,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Índices para performance
CREATE TABLE IF NOT EXISTS blaze_events_log (
    id VARCHAR(64) PRIMARY KEY,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    source VARCHAR(120) NOT NULL,
    message CLOB,
    raw_payload CLOB
);

CREATE TABLE IF NOT EXISTS event_subscriptions (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(120) NOT NULL,
    version VARCHAR(40) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_creator ON events(creator_member_id);
CREATE INDEX IF NOT EXISTS idx_event_interests_event ON event_interests(event_id);
CREATE INDEX IF NOT EXISTS idx_event_interests_member ON event_interests(member_id);
CREATE INDEX IF NOT EXISTS idx_detected_actions_event ON detected_actions(event_id);
CREATE INDEX IF NOT EXISTS idx_detected_actions_member ON detected_actions(member_id);
CREATE INDEX IF NOT EXISTS idx_detected_actions_idempotency ON detected_actions(idempotency_hash);
CREATE INDEX IF NOT EXISTS idx_event_entries_event ON event_entries(event_id);
CREATE INDEX IF NOT EXISTS idx_event_entries_member ON event_entries(member_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_blaze_events_log_received ON blaze_events_log(received_at);
CREATE INDEX IF NOT EXISTS idx_blaze_events_log_type ON blaze_events_log(event_type);
CREATE INDEX IF NOT EXISTS idx_event_subscriptions_type ON event_subscriptions(type);
