ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE oauth_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_draw_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_polling_cursors ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON TABLE members, events, oauth_credentials, '
            || 'event_participants, event_draw_results, chat_polling_cursors FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON TABLE members, events, oauth_credentials, '
            || 'event_participants, event_draw_results, chat_polling_cursors FROM authenticated';
    END IF;
END
$$;
