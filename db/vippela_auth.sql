-- =====================================================================
-- VIPPELA — Delta de cadastro e login (PostgreSQL / Supabase)
--
-- Este script complementa o modelo de dados completo
-- (vippela_schema.sql, na pasta acima deste projeto). Rode uma única
-- vez no SQL Editor do Supabase, depois de criar o schema base.
--
-- O que muda aqui:
--   1. usuario.data_nascimento deixa de ser obrigatório. O cadastro do
--      app não coleta data de nascimento e o backend não inventa
--      valores; a coluna continua preenchível para quando houver essa
--      etapa no onboarding.
--   2. Nova tabela sessao_conta. O token de sessão é aleatório e o
--      banco guarda apenas o SHA-256 dele (64 caracteres hex), igual
--      ao tratamento já usado em convite_vinculo.codigo_hash.
-- =====================================================================

-- 1. Data de nascimento opcional.
ALTER TABLE usuario ALTER COLUMN data_nascimento DROP NOT NULL;

-- 2. Sessões de login.
CREATE TABLE sessao_conta (
    id_sessao      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_usuario     UUID NOT NULL REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em      TIMESTAMPTZ NOT NULL,
    ultimo_uso_em  TIMESTAMPTZ,
    revogado_em    TIMESTAMPTZ,
    CONSTRAINT chk_sessao_expiracao CHECK (expira_em > criado_em)
);

-- Logout, "sair de todos os aparelhos" e limpeza periódica usam este índice.
CREATE INDEX idx_sessao_usuario ON sessao_conta (id_usuario);
