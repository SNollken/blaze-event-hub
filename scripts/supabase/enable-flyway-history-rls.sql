-- Execute somente depois que o Flyway terminar. Durante migrations, o Flyway
-- mantem um lock em sua tabela de historico e este ALTER aguardaria esse lock.
-- Sem FORCE, o proprietario postgres continua apto a executar migrations.
ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY;
