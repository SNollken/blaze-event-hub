ALTER TABLE events ADD COLUMN IF NOT EXISTS finalization_cutoff_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS finalization_attempt_id VARCHAR(64);
ALTER TABLE chat_polling_cursors ADD COLUMN IF NOT EXISTS event_id VARCHAR(64);
ALTER TABLE chat_polling_cursors ADD COLUMN IF NOT EXISTS scan_cursor VARCHAR(1024);
ALTER TABLE chat_polling_cursors ADD COLUMN IF NOT EXISTS scan_anchor_message_id VARCHAR(255);

ALTER TABLE chat_polling_cursors ADD CONSTRAINT fk_chat_polling_cursor_event
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;

ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_status;
ALTER TABLE events ADD CONSTRAINT chk_events_status
    CHECK (status IN ('draft', 'open', 'finalizing', 'closed', 'completed', 'cancelled'));

ALTER TABLE events ADD CONSTRAINT chk_events_finalizing_cutoff
    CHECK (status <> 'finalizing' OR (finalization_cutoff_at IS NOT NULL AND finalization_attempt_id IS NOT NULL));

ALTER TABLE events ADD CONSTRAINT chk_events_attempt_lifecycle
    CHECK (status = 'finalizing' OR finalization_attempt_id IS NULL);

-- O cursor do chat e por canal; por isso so pode existir um giveaway em captura por canal.
UPDATE events
SET active_capture_key = creator_channel_id
WHERE status IN ('open', 'finalizing');

ALTER TABLE events ADD CONSTRAINT chk_events_active_capture_lifecycle
    CHECK ((status IN ('open', 'finalizing') AND active_capture_key IS NOT NULL)
        OR (status NOT IN ('open', 'finalizing') AND active_capture_key IS NULL));

ALTER TABLE events ADD CONSTRAINT chk_events_frozen_pool_lifecycle
    CHECK (status NOT IN ('closed', 'completed') OR (
        finalization_cutoff_at IS NOT NULL
        AND closed_at IS NOT NULL
        AND finalized_participant_count > 0
        AND finalized_pool_hash IS NOT NULL));

ALTER TABLE events ADD CONSTRAINT chk_events_completion_lifecycle
    CHECK ((status = 'completed' AND completed_at IS NOT NULL)
        OR (status <> 'completed' AND completed_at IS NULL));

CREATE INDEX IF NOT EXISTS idx_events_finalizing ON events(status, updated_at);
