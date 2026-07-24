-- Migration V9: Adiciona 'manual' ao CHECK constraint de event_action_rules
-- O EventType.MANUAL é válido mas não estava na lista do V7

ALTER TABLE event_action_rules DROP CONSTRAINT IF EXISTS chk_action_type;
ALTER TABLE event_action_rules ADD CONSTRAINT chk_action_type
    CHECK (action_type IN ('chat', 'vote', 'sub', 'gifted_sub', 'follow', 'donation', 'manual'));
