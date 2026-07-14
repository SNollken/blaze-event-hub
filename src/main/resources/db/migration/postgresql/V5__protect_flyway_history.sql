-- O backend usa JDBC como proprietario do schema. Sem FORCE, o Flyway continua
-- acessando o proprio historico, enquanto a Data API recebe default-deny.
ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;

-- A FK e usada ao remover um evento; mantenha a verificacao indexada mesmo
-- quando a tabela de cursores crescer.
CREATE INDEX IF NOT EXISTS idx_chat_polling_cursors_event_id
    ON public.chat_polling_cursors(event_id);
