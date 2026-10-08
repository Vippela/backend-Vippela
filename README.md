# Vippela — vínculo e regras de aplicativos

O responsável gera um código válido por cinco minutos; o familiar confirma no próprio aparelho. Cada vínculo ativo recebe uma lista de aplicativos e regras independentes. As regras são persistidas no PostgreSQL. Códigos antigos do protótipo devem ser substituídos por um novo vínculo.

## Executar

Copie `.env.example` para `.env` na raiz do backend e preencha a conexão PostgreSQL. Informe `VIPPELA_DB_PASSWORD`. `VIPPELA_DB_USER` (padrão `postgres`), `VIPPELA_DB_URL` (padrão `jdbc:postgresql://localhost:5432/vippela_db`) e `PORT` (padrão `8080`) são opcionais. A senha não está embutida no projeto nem no JAR.

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

O backend oferece autenticação de contas nas rotas `/auth` descritas abaixo. As rotas `/links` ainda usam credenciais de vínculo separadas, não o token Bearer da sessão. A mesma conta em outra instalação não recupera automaticamente suas chaves ou vínculos. Recuperação, transferência de aparelho, desvinculação e associação dos vínculos à identidade autenticada continuam pendentes. Em implantação pública, use HTTPS.

## Confirmação e limites

`revision` indica a regra salva. O celular salva a resposta localmente antes de informar a revisão recebida na próxima sincronização. `protectionEnabled` só é verdadeiro com o serviço Android conectado. A interface considera sem confirmação recente um aparelho sem contato há mais de 20 segundos.

A proteção retorna à tela inicial ao detectar a abertura de um app bloqueado. Não suspende o pacote no sistema, não lê conteúdo de telas e não é uma solução Device Owner. Apps essenciais, launcher, discador padrão, SMS padrão e a própria Vippela são excluídos; alguns apps de entretenimento pré-instalados, como YouTube, são elegíveis. As últimas regras continuam no aparelho sem internet. Novas regras e liberações precisam de conexão. Desativar o serviço, sair da conta, limpar os dados ou desinstalar interrompe essa proteção.

Pedidos de liberação, tempo de tela, trilhas e relatórios anteriores continuam demonstrativos; as permissões reais são alteradas na tela Aplicativos. O bloqueio se aplica ao aparelho/instalação vinculado, não a qualquer dispositivo onde o mesmo e-mail seja digitado.

## Testes

`bash gradlew test` usa H2 isolado, sem acessar o PostgreSQL do usuário. Cobre autorização entre famílias, consumo concorrente de códigos, expiração, regras, confirmações e cabeçalhos HTTP. A validação em dois aparelhos físicos continua necessária para comportamento em segundo plano e diferenças entre fabricantes.

## Autenticação de contas

- `POST /auth/register`: `{nome, email, senha, tipoConta}`; senha de 8 a 256 caracteres, tipo `RESPONSAVEL` ou `FAMILIAR`.
- `POST /auth/login`: `{email, senha}`; o tipo vem da conta existente.
- `POST /auth/google`: `{idToken, tipoConta, nome?}`; recebe o **ID token Firebase** gerado no Android, valida assinatura, expiração, revogação, e-mail verificado e provedor Google. O tipo informado só é usado ao criar a conta.
- `GET /auth/me`: sessão atual com `Authorization: Bearer <token>`.
- `POST /auth/logout`: revoga esse token e retorna 204.
- `GET /auth/existe?email=...`: `{existe}`; limitado por IP junto das tentativas de autenticação.

Cadastro e login retornam `{id, nome, email, tipoConta, token, expiraEm}`. `/auth/me` não retorna o token; o Android mantém o que já possui. Sessões duram sete dias. São persistidos apenas o hash do token e o hash PBKDF2 da senha, em `auth_sessao` e `auth_usuario`, sem alterar as tabelas do vínculo. E-mails são normalizados e únicos. Uma conta Google não é mesclada automaticamente a uma conta de senha com o mesmo e-mail.

Para Google, configure `VIPPELA_GOOGLE_CREDENTIALS` com o caminho de um JSON de conta de serviço do mesmo projeto Firebase do Android. Esse arquivo é secreto, deve ficar fora do repositório e **não** é o `google-services.json` do app. Sem a configuração, apenas `/auth/google` retorna 503; cadastro/login por senha continuam disponíveis. Reinicie o servidor após configurá-lo. Consulte https://firebase.google.com/docs/auth/admin/verify-id-tokens.

Os testes de autenticação usam H2 e um verificador Google substituto para testar as decisões de conta. Não comprovam um login Google real: esse teste exige a credencial do servidor e o fluxo no celular.

## Configuração local com .env

O backend carrega automaticamente `.env` da pasta em que é iniciado. Execute `bash gradlew bootRun` ou o JAR a partir da raiz de `backend-Vippela`; no IntelliJ/Android Studio, use essa pasta como diretório de trabalho. Não é necessário instalar biblioteca dotenv nem executar `source .env`. Variáveis de ambiente e argumentos de inicialização têm prioridade sobre os valores do arquivo.

Preencha `VIPPELA_DB_URL`, `VIPPELA_DB_USER` e `VIPPELA_DB_PASSWORD` com os dados da conexão PostgreSQL disponibilizada pelo Supabase. A URL deve ser JDBC, no formato `jdbc:postgresql://HOST:PORTA/BANCO?sslmode=require`; use o host, porta, usuário e parâmetros da conexão escolhida no painel. A URL REST do projeto, a chave anon/publishable e a service_role não são usadas nesta integração JDBC.

`VIPPELA_GOOGLE_CREDENTIALS` aponta para o JSON privado de conta de serviço Firebase, fora do repositório. `PORT` controla a porta HTTP. Os campos de banco ficam vazios até receberem as credenciais reais; não inicie o servidor antes de preenchê-los.

O `.env` usa a sintaxe de Java properties: `CHAVE=valor`, sem aspas, sem `export` e sem comentário no final do valor. `#`, `$` e `=` dentro do valor são literais; barras invertidas devem ser duplicadas. Não use `source .env`. Senhas com a sequência `${...}` devem ser fornecidas pela variável de ambiente do processo para evitar interpretação como placeholder pelo Spring.

`.env` e variantes locais são ignorados pelo Git; somente `.env.example`, sem credenciais, deve ser versionado. O arquivo local foi criado com permissão 600. Nunca coloque credenciais do banco ou uma chave administrativa Firebase no app Android. O `google-services.json` continua sendo a configuração pública de cliente usada pelo plugin Google Services e permanece ignorado no repositório Android.

Os testes usam H2 com configurações próprias, sem conectar ao Supabase. O antigo `config/application.properties` local usado apenas para o caminho Firebase foi substituído pelo `.env`.

## Relatórios e publicação — Android 0.7.0

`PUT /links/{id}/report` recebe o relatório e ícones do aparelho com X-Device-Key; `GET /links/{id}/report` permite a leitura ao responsável com X-Owner-Key. O endpoint valida tamanho, datas, duração e dimensões dos PNGs. Dados e ícones são guardados nas coleções link_usage/link_icons, separadas por vínculo. Relatórios antigos não substituem novos. A sincronização de bloqueios permanece compatível com clientes anteriores.

A publicação do backend Java conectado ao banco Supabase está documentada em [docs/PUBLICAR_BACKEND.md](docs/PUBLICAR_BACKEND.md). O Dockerfile não incorpora o .env ou as credenciais administrativas. O deploy não é automático.
