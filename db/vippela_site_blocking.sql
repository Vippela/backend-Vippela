-- Execute uma vez no SQL Editor do Supabase antes de publicar o backend
-- que oferece regras de bloqueio de sites.
CREATE TABLE IF NOT EXISTS link_blocked_domains (
    link_id UUID NOT NULL REFERENCES device_links(id) ON DELETE CASCADE,
    domain VARCHAR(253) NOT NULL,
    PRIMARY KEY (link_id, domain)
);

-- A tabela pertence ao backend e não deve ser exposta diretamente aos apps.
REVOKE ALL ON TABLE link_blocked_domains FROM anon, authenticated;
