# Publicação da Vippela — somente o que falta fazer

## Já preparado no projeto

- Dockerfile para compilar e executar o backend Java 17.
- render.yaml: configuração automática do serviço Docker, porta, memória e pool de conexões. Sugere o plano gratuito; confira a disponibilidade e as condições na sua conta antes de criar.
- /health: verifica o servidor e a conexão com o banco, sem expor credenciais.
- Rotas para bloqueios de apps e sites, ícones e estatísticas.
- Exclusão de .env e credenciais do contexto Docker.

O banco continua no Supabase. O servidor Kotlin é publicado no Render: as Edge Functions do Supabase não executam este projeto Java diretamente. Documentação: [Supabase](https://supabase.com/docs/guides/functions), [Blueprints Render](https://render.com/docs/infrastructure-as-code).

## 1. Enviar os arquivos atualizados para o GitHub

O repositório oficial é `Vippela/backend-Vippela`. Antes de publicar, confirme que a branch escolhida no Render contém o `render.yaml` e as alterações mais recentes.

Pelo cliente Git que você utiliza, revise e envie os arquivos de código, Dockerfile, .dockerignore, render.yaml, README e este guia para o repositório `Vippela/backend-Vippela`. Inclua os arquivos novos. Não envie .env, JSON de conta de serviço Firebase, pasta build ou senhas.

Se houver alterações de outros integrantes, sincronize-as e resolva os conflitos antes de publicar. Não substitua o trabalho deles por uma cópia antiga.

## 2. Conectar o repositório no Render

1. A conta Render já foi criada e o painel está aberto em **New → Blueprint**.
2. Escolha **New → Blueprint** e conecte o repositório atualizado.
3. Selecione a branch que contém render.yaml. O Render lerá a configuração pronta; não é necessário cadastrar cada opção do serviço manualmente.
4. Confira o plano gratuito indicado. Se a conta exigir um plano pago, pare e escolha conscientemente o custo antes de criar.

Falta autorizar a integração GitHub para o repositório da equipe. Essa autorização permite que o Render leia o código; selecione apenas o repositório necessário.

## 3. Preencher as três variáveis privadas

O Blueprint solicitará:

- VIPPELA_DB_URL
- VIPPELA_DB_USER
- VIPPELA_DB_PASSWORD

Copie os valores do `.env` local do backend para os campos privados do Render. Não copie `CHAVE=` junto ao valor nem adicione aspas. Mantenha a URL JDBC com `sslmode=require` e a conexão Session pooler já utilizada. Não use chave anon/service_role como senha do PostgreSQL.

Se quiser Google neste primeiro deploy, adicione em **Environment → Secret Files** um arquivo chamado `firebase-admin.json`, usando o JSON privado da conta de serviço Firebase. Depois adicione:

```text
VIPPELA_GOOGLE_CREDENTIALS=/etc/secrets/firebase-admin.json
```

Esse arquivo é diferente de google-services.json. Sem ele, o acesso por e-mail e senha continua funcionando; Google não estará disponível no backend. Não publique o arquivo secreto no GitHub.

Documentação: [segredos no Render](https://render.com/docs/configure-environment-variables).

## 4. Publicar e conferir

1. Antes do deploy desta versão, execute `db/vippela_site_blocking.sql` uma vez no SQL Editor do Supabase. O backend de produção usa `ddl-auto=none` e não cria essa tabela sozinho.
2. Confirme a criação do serviço e aguarde build e inicialização.
3. Abra `https://SEU-SERVICO.onrender.com/health`. O resultado esperado é HTTP 200 com `{"status":"ok"}`. Não use literalmente esse endereço de exemplo: copie o endereço gerado para seu serviço.
4. Se houver erro, consulte os logs do serviço. Envie apenas o erro, sem credenciais.
5. No Supabase, confirme que tabelas privadas do backend (`usuario`, `sessao_conta`, `device_links` e `link_*`) não estão expostas à Data API para anon/authenticated. Essa verificação de permissões depende do acesso ao painel da equipe. Faça backup antes de aplicar atualizações de esquema a dados importantes.

O serviço publicado fica em `https://vippela-backend.onrender.com/`; confira `/health` após cada deploy. O JAR e os testes também podem ser validados localmente pelo Gradle, sem acessar o banco de produção.

## 5. Configurar os aparelhos

1. Instale o APK 0.7.2 nos dois aparelhos, sem desinstalar.
2. O servidor oficial do Render já vem configurado automaticamente. Use **Alterar servidor** apenas para apontar os dois aparelhos a outro ambiente.
3. Faça login e gere um novo vínculo. A mudança do endereço local para HTTPS muda o conjunto de chaves utilizado pelo app; o vínculo anterior não é transferido automaticamente.
4. No familiar, ative **Proteção familiar Vippela** em Acessibilidade para bloquear apps.
5. Separadamente, abra **Relatórios → Permitir estatísticas** e permita **Acesso ao uso** para gerar gráficos.
6. Abra e use um app monitorado, volte à Vippela e confira o relatório. Não é necessário manter o computador ligado depois que o backend estiver publicado.

O histórico começa após a autorização. Falhas de envio aparecem separadamente da permissão; os dados locais ficam no familiar e são reenviados. O responsável pode levar aproximadamente um minuto para atualizar um relatório já existente.

## Limite atual do projeto

As chaves de vínculo ainda são separadas do token de login; trocar de instalação não recupera automaticamente o vínculo. Esta implantação é para validação controlada da equipe. Planos com suspensão por inatividade podem atrasar a primeira chamada ao servidor.
