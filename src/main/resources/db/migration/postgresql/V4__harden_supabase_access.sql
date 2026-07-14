ALTER TABLE members ENABLE ROW LEVEL SECURITY;
ALTER TABLE events ENABLE ROW LEVEL SECURITY;
ALTER TABLE oauth_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE event_draw_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_polling_cursors ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON TABLE public.members, public.events, public.oauth_credentials, '
            || 'public.event_participants, public.event_draw_results, '
            || 'public.chat_polling_cursors, public.flyway_schema_history FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON TABLE public.members, public.events, public.oauth_credentials, '
            || 'public.event_participants, public.event_draw_results, '
            || 'public.chat_polling_cursors, public.flyway_schema_history FROM authenticated';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        EXECUTE 'REVOKE ALL ON TABLE public.members, public.events, public.oauth_credentials, '
            || 'public.event_participants, public.event_draw_results, '
            || 'public.chat_polling_cursors, public.flyway_schema_history FROM service_role';
    END IF;
END
$$;

-- O backend usa JDBC e nao publica tabelas pela Data API. Feche tambem os defaults
-- antigos do Supabase para que migrations futuras nao ampliem a superficie sem querer.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
