CREATE TABLE IF NOT EXISTS members (
    id VARCHAR(64) PRIMARY KEY,
    blaze_user_id VARCHAR(255) NOT NULL UNIQUE,
    blaze_username VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512),
    status VARCHAR(40) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(64) PRIMARY KEY,
    creator_member_id VARCHAR(64) NOT NULL,
    creator_blaze_user_id VARCHAR(255) NOT NULL,
    creator_channel_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    prize TEXT NOT NULL,
    entry_command VARCHAR(80) NOT NULL DEFAULT '!participar',
    active_capture_key VARCHAR(512) UNIQUE,
    status VARCHAR(40) NOT NULL DEFAULT 'draft',
    finalized_participant_count INT NOT NULL DEFAULT 0,
    finalized_pool_hash VARCHAR(64),
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_events_creator FOREIGN KEY (creator_member_id) REFERENCES members(id),
    CONSTRAINT chk_events_status CHECK (status IN ('draft', 'open', 'closed', 'completed', 'cancelled')),
    CONSTRAINT chk_events_finalized_count CHECK (finalized_participant_count >= 0)
);

CREATE TABLE IF NOT EXISTS oauth_credentials (
    member_id VARCHAR(64) PRIMARY KEY,
    token_subject_type VARCHAR(40),
    blaze_user_id VARCHAR(255) NOT NULL,
    token_type VARCHAR(40) NOT NULL,
    access_token_ciphertext TEXT NOT NULL,
    refresh_token_ciphertext TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    scopes_json TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_oauth_credentials_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE TABLE IF NOT EXISTS event_participants (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    blaze_user_id VARCHAR(255) NOT NULL,
    blaze_username VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    source_message_id VARCHAR(255) NOT NULL,
    entered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_event_participants_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT uq_event_participant_user UNIQUE (event_id, blaze_user_id),
    CONSTRAINT uq_event_participant_message UNIQUE (event_id, source_message_id)
);

CREATE TABLE IF NOT EXISTS event_draw_results (
    id VARCHAR(64) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    winner_blaze_user_id VARCHAR(255) NOT NULL,
    winner_username VARCHAR(255),
    winner_display_name VARCHAR(255) NOT NULL,
    draw_seed VARCHAR(255) NOT NULL,
    draw_method VARCHAR(80) NOT NULL,
    pool_hash VARCHAR(64) NOT NULL,
    participant_count INT NOT NULL,
    selected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    selected_by VARCHAR(64) NOT NULL,
    CONSTRAINT fk_event_draw_results_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT fk_event_draw_results_member FOREIGN KEY (selected_by) REFERENCES members(id),
    CONSTRAINT chk_event_draw_participant_count CHECK (participant_count > 0)
);

CREATE TABLE IF NOT EXISTS chat_polling_cursors (
    member_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    last_message_id VARCHAR(255),
    last_polled_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(120),
    PRIMARY KEY (member_id, channel_id),
    CONSTRAINT fk_chat_polling_cursor_member FOREIGN KEY (member_id) REFERENCES members(id)
);

CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_creator ON events(creator_member_id);
CREATE INDEX IF NOT EXISTS idx_events_open_channel ON events(creator_channel_id, status);
CREATE INDEX IF NOT EXISTS idx_event_participants_event ON event_participants(event_id);
CREATE INDEX IF NOT EXISTS idx_oauth_credentials_blaze_user ON oauth_credentials(blaze_user_id);
