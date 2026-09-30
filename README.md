# Vippela — cadastro, vínculo e regras de aplicativos

O responsável gera um código válido por cinco minutos; o familiar confirma no próprio aparelho. Cada vínculo ativo recebe uma lista de aplicativos e regras independentes. As regras são persistidas no PostgreSQL. Códigos antigos do protótipo devem ser substituídos por um novo vínculo.

## Cadastro e login

Contas existem no servidor: nome, e-mail, senha com hash BCrypt e tipo de conta (responsável ou familiar). O login devolve um token de sessão de 256 bits, guardado apenas como SHA-256 em `sessao_conta`; o logout o revoga e o token expira sozinho. O app manda esse token em `Authorization: Bearer`.

- `POST /auth/register` — corpo `{nome, email, senha, tipoConta}` → 201 com `{id, nome, email, tipoConta, token, expiraEm}`.
- `POST /auth/login` — corpo `{email, senha}` → o mesmo formato, 200.
- `POST /auth/google` — corpo `{idToken, tipoConta, nome}` valida o ID token do Firebase no servidor e cria a conta se o e-mail ainda não existir. Sem `VIPPELA_GOOGLE_CREDENTIALS` responde 503.
- `GET /auth/me` — exige `Authorization: Bearer`; 401 quando o token está revogado ou expirado.
- `POST /auth/logout` — revoga o token informado; 204.
- `GET /auth/existe?email=` — só para o app escolher entre login e cadastro.

E-mail repetido responde 409, credencial errada 401 sem distinguir e-mail inexistente de senha errada, campo inválido 400 e mais de `VIPPELA_AUTH_MAX_TENTATIVAS` tentativas na janela responde 429. Nenhuma resposta devolve hash de senha ou o token de outra pessoa.

Antes de subir, rode `db/vippela_auth.sql` no Supabase: ele cria `sessao_conta` e deixa `usuario.data_nascimento` opcional (o cadastro não coleta data de nascimento).

## Executar

O banco é o PostgreSQL do Supabase, sempre por variáveis de ambiente. Copie `.env.example` e preencha:

| Variável | Padrão | Para que serve |
| --- | --- | --- |
| `SUPABASE_DB_URL` | — | `jdbc:postgresql://db.<ref>.supabase.co:5432/postgres?sslmode=require` na conexão direta, ou o host do Supavisor (`aws-0-<regiao>.pooler.supabase.com:5432`, usuário `postgres.<ref>`) se a rede for só IPv4. |
| `SUPABASE_DB_USER` | — | `postgres`. |
| `SUPABASE_DB_PASSWORD` | — | Senha do projeto, em Settings → Database do painel. |
| `DB_POOL_SIZE` | `5` | Conexões do Hikari. Planes gratuitos do Supabase aceitam poucas. |
| `PORT` | `8080` | Porta HTTP. |
| `VIPPELA_AUTH_TTL_HOURS` | `720` | Validade da sessão. |
| `VIPPELA_AUTH_MAX_TENTATIVAS` | `10` | Tentativas de login/cadastro por IP na janela. |
| `VIPPELA_AUTH_JANELA_MINUTOS` | `15` | Tamanho da janela do limite. |
| `VIPPELA_GOOGLE_CREDENTIALS` | vazio | Caminho do JSON de service account do Firebase. |

Sem `SUPABASE_DB_URL`, o backend aceita `VIPPELA_DB_URL`/`VIPPELA_DB_USER`/`VIPPELA_DB_PASSWORD` para um PostgreSQL local. A senha não está embutida no projeto nem no JAR, e `.env` está no `.gitignore`.

Use `bash gradlew bootRun` ou `java -jar build/libs/backend-0.0.1-SNAPSHOT.jar`. O Gradle utiliza o toolchain Java 17 para compilar a aplicação.

Instale a nova versão Android nos dois celulares. Em Perfil → Vínculo familiar → Configurar servidor, informe a mesma URL nos dois. Em teste local, use `http://IP_DO_COMPUTADOR:8080/` com os celulares na mesma rede e acesso à porta 8080. `10.0.2.2` só funciona no emulador. A versão Android release exige HTTPS.

No responsável, selecione o familiar e gere o código. No familiar, confirme o código, leia a explicação da proteção e ative **Proteção familiar Vippela** em Acessibilidade. Em Aplicativos, o responsável liga/desliga a permissão de cada app. O familiar sincroniza a cada cinco segundos enquanto o serviço de proteção está conectado; com a Vippela aberta também há sincronização.

## Protocolo

- `POST /links/generate`, cabeçalho `X-Owner-Key`, corpo `{memberKey, memberName, ownerName}` → `{id, token, expiresAt, memberKey}`.
- `POST /links/confirm`, cabeçalho `X-Device-Key`, corpo `{token, deviceId}` → vínculo.
- `GET /links`, cabeçalho `X-Owner-Key` → vínculos ativos desse responsável.
- `GET /links/{id}`, cabeçalho `X-Owner-Key` → estado do vínculo.
- `PUT /links/{id}/rule`, cabeçalho `X-Owner-Key`, corpo `{packageName, blocked}` → regra salva e revisão atualizada.
- `POST /links/{id}/sync`, cabeçalho `X-Device-Key`, corpo `{apps: [{packageName, label}], appliedRevision, protectionEnabled}` → regras atuais.

A resposta de vínculo contém `id`, `memberKey`, `memberName`, `ownerName`, `linkedDeviceId`, `status`, `revision`, `appliedRevision`, `protectionEnabled`, `lastSeenAt`, `apps` e `blockedPackages`. Nenhuma resposta de consulta contém credenciais ou código de pareamento.

O Android cria chaves aleatórias de 256 bits, separadas por conta local/perfil/servidor. O servidor armazena apenas seus hashes. A chave do familiar não permite alterar regras; a do responsável não permite ler vínculos de outras chaves. Códigos são consumidos com bloqueio transacional; geração e confirmação têm limite de tentativas por IP nesta instância. As restrições únicas impedem dois vínculos ativos para o mesmo aparelho/conta ou mesmo familiar do responsável.

Essa autorização usa credenciais de vínculo. Não é um servidor de cadastro/login: e-mail e perfis continuam locais, e tokens Google ainda não são validados por este backend. A mesma conta em outra instalação não recupera automaticamente suas chaves ou vínculos. Recuperação, transferência de aparelho e desvinculação ficam para a próxima etapa. Em implantação pública, use HTTPS e integre a identidade do backend à autenticação das contas.

## Confirmação e limites

`revision` indica a regra salva. O celular salva a resposta localmente antes de informar a revisão recebida na próxima sincronização. `protectionEnabled` só é verdadeiro com o serviço Android conectado. A interface considera sem confirmação recente um aparelho sem contato há mais de 20 segundos.

A proteção retorna à tela inicial ao detectar a abertura de um app bloqueado. Não suspende o pacote no sistema, não lê conteúdo de telas e não é uma solução Device Owner. Apps essenciais, launcher, discador padrão, SMS padrão e a própria Vippela são excluídos; alguns apps de entretenimento pré-instalados, como YouTube, são elegíveis. As últimas regras continuam no aparelho sem internet. Novas regras e liberações precisam de conexão. Desativar o serviço, sair da conta, limpar os dados ou desinstalar interrompe essa proteção.

Pedidos de liberação, tempo de tela, trilhas e relatórios anteriores continuam demonstrativos; as permissões reais são alteradas na tela Aplicativos. O bloqueio se aplica ao aparelho/instalação vinculado, não a qualquer dispositivo onde o mesmo e-mail seja digitado.

## Testes

`bash gradlew test` usa H2 isolado, sem acessar o PostgreSQL do usuário. Cobre autorização entre famílias, consumo concorrente de códigos, expiração, regras, confirmações e cabeçalhos HTTP. A validação em dois aparelhos físicos continua necessária para comportamento em segundo plano e diferenças entre fabricantes.
