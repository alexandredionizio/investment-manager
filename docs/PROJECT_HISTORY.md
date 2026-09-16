
# Histórico do Projeto — Investment Manager API

Este documento registra a evolução técnica do projeto por Sprint e funciona como checkpoint de continuidade, rastreabilidade e base para a documentação final.

## Sprint 1 — Fundação técnica + Asset

**Status:** Concluída

### Objetivo
Criar a fundação da aplicação e entregar a primeira feature vertical completa: `Asset`.

### Principais entregas
- Projeto Spring Boot
- Java 21 como versão-alvo
- PostgreSQL via Docker
- Flyway
- Migration V1 para `assets`
- Entidade `Asset`
- Enum `AssetType`
- `AssetRepository`
- DTOs
- Bean Validation
- `AssetService`
- `AssetController`
- `GlobalExceptionHandler`
- Tratamento de 404
- Tratamento de validação
- Git/GitHub
- Testes unitários do `AssetService`

### Endpoints
```text
POST /api/v1/assets
GET  /api/v1/assets
GET  /api/v1/assets/{id}
```

### Conceitos estudados
- Spring Boot
- JPA/Hibernate
- Repository
- Service
- Controller
- DTO
- `record`
- Bean Validation
- Dependency Injection por construtor
- `Optional`
- Exceptions
- Git
- JUnit
- Mockito

### Ambiente
- IntelliJ atualizado
- JDK Temurin 21 configurado
- `JAVA_HOME`, `PATH` e Maven alinhados ao Java 21

---

## Sprint 2 — Portfolio + MapStruct + Testcontainers

**Status:** Concluída

### Objetivo
Adicionar carteiras ao domínio e amadurecer a arquitetura e os testes.

### Principais entregas
- Modelagem de `Portfolio`
- Migration V2
- Entidade `Portfolio`
- `PortfolioRepository`
- DTOs
- `PortfolioService`
- `PortfolioController`
- `PortfolioNotFoundException`
- Validação
- Organização por feature
- MapStruct
- `PortfolioMapper`
- `AssetMapper`
- Refatoração dos Services
- Testes unitários adaptados
- Testcontainers
- `PortfolioRepositoryIntegrationTest`
- `AssetRepositoryIntegrationTest`
- `InvestmentManagerApiApplicationTests` com Testcontainers

### Endpoints
```text
POST /api/v1/portfolios
GET  /api/v1/portfolios
GET  /api/v1/portfolios/{id}
```

### Conceitos estudados
- Entity x DTO
- Mapper
- MapStruct
- `@AfterMapping`
- Method reference
- Teste unitário x integração
- `@DataJpaTest`
- `@Testcontainers`
- `@Container`
- `@ServiceConnection`

### Quiz da Sprint
Resultado: 5/5.

---

## Sprint 3 — Transactions

**Status:** Concluída

### Objetivo
Adicionar ao domínio o registro de compras e vendas de ativos dentro de uma carteira.

### Principais entregas
- `TransactionType` com `BUY` e `SELL`
- Entidade `Transaction`
- Relacionamentos `Transaction -> Portfolio` e `Transaction -> Asset`
- `@ManyToOne`
- `@JoinColumn`
- `BigDecimal` para quantidade e preço unitário
- Migration V3
- Foreign keys para `portfolios` e `assets`
- `TransactionRepository`
- `findByPortfolioIdOrderByTransactionDateAscIdAsc(Long portfolioId)`
- `TransactionRequest`
- `TransactionResponse`
- `@NotNull`
- `@Positive`
- `TransactionMapper`
- `TransactionService`
- `TransactionController`
- `TransactionNotFoundException`
- Tratamento global de 404
- Padronização de mensagens para PT-BR
- Exceptions específicas organizadas por feature
- Testes manuais no Postman
- Testes unitários do `TransactionService`
- Testes de integração do `TransactionRepository`
- Validação real das foreign keys
- Validação real da consulta de transações por carteira

### Endpoints
```text
POST /api/v1/transactions
GET  /api/v1/transactions
GET  /api/v1/transactions/{id}
GET  /api/v1/transactions/portfolio/{portfolioId}
```

### Conceitos estudados
- `@ManyToOne`
- Foreign Keys
- `BigDecimal`
- Dependency Injection
- `final`
- Derived Query Methods
- `Stream`
- `map`
- Method reference
- `ResponseEntity`
- `@PostMapping`
- `@GetMapping`
- `@PathVariable`
- `@RequestBody`
- `@Valid`
- Mockito: `when`, `thenReturn`, `verify`, `verifyNoInteractions`, `any`
- Arrange / Act / Assert
- Testcontainers com relacionamentos reais

### Resultado final da suíte
```text
Tests run: 20
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

### Quiz da Sprint
Resultado: 8/10.

Pontos para revisão:
- `@ManyToOne`: muitas `Transaction` podem apontar para um mesmo `Asset`
- Uma exception lançada interrompe o fluxo normal do método, salvo se for capturada/tratada

---

## Sprint 4 — Posição e Preço Médio

**Status:** Concluída

### Objetivo
Calcular a posição consolidada dos ativos de uma carteira a partir do histórico de transações, incluindo quantidade, preço médio e custo total, além de impedir vendas superiores à posição disponível.

### Principais entregas
- Feature `position`
- `PositionResponse`
- `PositionService`
- `PositionController`
- Endpoint de posições por carteira
- Consolidação das transações por ativo
- Cálculo de quantidade atual
- Cálculo de preço médio ponderado
- Cálculo de custo total
- Tratamento de compras (`BUY`)
- Tratamento de vendas (`SELL`)
- Venda parcial mantendo o preço médio
- Venda total zerando quantidade e custo
- `InsufficientPositionException`
- Bloqueio de venda superior à posição disponível
- Validação da posição antes da persistência de uma venda
- Tratamento global com HTTP 400 para posição insuficiente
- Ordenação cronológica das transações
- `findByPortfolioIdOrderByTransactionDateAscIdAsc`
- Testes manuais no Postman
- Testes unitários adicionais do `TransactionService`
- `PositionServiceTest`
- Testes de preço médio, venda parcial, venda total e posição insuficiente
- Validação de carteira inexistente
- Configuração da ferramenta Database do IntelliJ para acesso ao PostgreSQL

### Endpoint
```text
GET /api/v1/portfolios/{portfolioId}/positions
```

### Regras de negócio implementadas

```text
BUY
→ aumenta quantidade
→ aumenta custo total
→ recalcula preço médio ponderado

SELL
→ valida quantidade disponível
→ reduz quantidade
→ reduz custo pelo preço médio
→ mantém preço médio da posição restante

SELL total
→ quantidade = 0
→ custo total = 0
→ preço médio = 0

SELL > posição disponível
→ InsufficientPositionException
→ HTTP 400
→ transação não é persistida
```

### Exemplo validado

```text
BUY 100 ITUB4 @ 35,50
BUY  50 ITUB4 @ 41,50
────────────────────────
Quantidade: 150
Preço médio: 37,50
Custo total: 5.625,00

SELL 50 ITUB4
────────────────────────
Quantidade: 100
Preço médio: 37,50
Custo total: 3.750,00
```

### Conceitos estudados
- Consolidação de transações
- Preço médio ponderado
- Regras de domínio
- `BigDecimal`
- `compareTo`
- `RoundingMode.HALF_UP`
- Ordenação cronológica
- Derived Query Methods com `OrderBy`
- Validação antes da persistência
- Separação de responsabilidades entre Services
- Testes de regras de negócio
- Mockito `never()`
- Proteção contra persistência de dados inválidos
- PostgreSQL pelo Database Tool do IntelliJ

### Resultado final da suíte
```text
Tests run: 27
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

### Quiz da Sprint
Pendente.

Próximo marco:

```text
Sprint 4 — Posição, preço médio e patrimônio
```

## Ritual de fechamento de Sprint

```text
implementação incremental
→ testes
→ suíte completa
→ commit/push
→ retrospectiva
→ quiz
→ atualização deste histórico
```

## Convenções do projeto

### Organização por feature
```text
feature
├── controller
├── dto
├── exception
├── mapper
├── repository
├── service
└── entidades/enums
```

### Exceptions
Exceptions específicas ficam na própria feature. Itens globais permanecem em `shared/exception`.

### Persistência
- Mudanças de schema via Flyway
- Não alterar migrations já aplicadas
- Criar nova migration para cada evolução

### Testes
- Mockito para testes unitários
- Testcontainers para integração com PostgreSQL real

### Versionamento
```text
git status
git add .
git diff --cached
git commit
git push
```
## Sprint 5 — Proventos e Corretoras

**Status:** Concluída

### Objetivo
Adicionar corretoras ao domínio, associá-las às transações e implementar o gerenciamento de proventos recebidos pelas carteiras.

### Principais entregas

#### Corretoras
- Entidade `Broker`
- Migration para `brokers`
- `BrokerRepository`
- `BrokerRequest` e `BrokerResponse`
- `BrokerMapper`
- `BrokerService`
- `BrokerController`
- `BrokerNotFoundException`
- Validação de entrada
- Associação `Transaction -> Broker`
- `@ManyToOne(fetch = FetchType.LAZY)`
- Campo `brokerId` em `TransactionRequest`
- `brokerId` e `brokerName` em `TransactionResponse`
- Atualização do `TransactionMapper`
- Validação da existência da corretora no `TransactionService`
- Testes unitários atualizados
- Teste para `BrokerNotFoundException`
- Testes manuais no Postman

#### Proventos
- `IncomeType`
    - `DIVIDEND`
    - `JCP`
    - `FII_INCOME`
- Entidade `Income`
- Migration V6 para `incomes`
- Relacionamentos com `Portfolio` e `Asset`
- `IncomeRequest`
- `IncomeResponse`
- `IncomeRepository`
- Consulta por carteira com ordenação cronológica
- `IncomeMapper`
- Cálculo de `totalAmount`
- `IncomeService`
- `IncomeController`
- `IncomeNotFoundException`
- Validações com Bean Validation
- Consultas com `@Transactional(readOnly = true)`
- Testes unitários do `IncomeService`
- Testes de integração do `IncomeRepository`
- Testes manuais no Postman

### Regra de cálculo de proventos

O valor total do provento não é armazenado no banco.

```text
totalAmount = amountPerUnit × quantity
```

O cálculo é realizado ao montar o `IncomeResponse`, evitando redundância e inconsistência entre os valores persistidos.

### Endpoints de corretoras

```text
POST /api/v1/brokers
GET  /api/v1/brokers
GET  /api/v1/brokers/{id}
```

### Endpoints de proventos

```text
POST /api/v1/incomes
GET  /api/v1/incomes
GET  /api/v1/incomes/{id}
GET  /api/v1/incomes/portfolio/{portfolioId}
```

### Conceitos estudados
- Relacionamentos JPA com `FetchType.LAZY`
- Lazy loading
- `LazyInitializationException`
- Limite da sessão Hibernate
- `@Transactional(readOnly = true)`
- Diferença entre `jakarta.transaction.Transactional` e a annotation do Spring
- MapStruct com propriedades aninhadas
- MapStruct com `expression`
- Cálculos financeiros com `BigDecimal`
- Derived Query Methods com ordenação
- Validação de regras de domínio
- Mockito com `verify`, `never` e `verifyNoInteractions`
- Testes de integração com relacionamentos reais
- Ordenação determinística por data e ID

### Resultado final da suíte

```text
Tests run: 37
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
Sprint 5 ✅
```

Próximo marco:

```text
Sprint 6 — Cotações externas e cache
```

---

## Sprint 6 — Cotações Externas e Cache

**Status:** Concluída

### Objetivo

Integrar a aplicação a uma fonte externa de cotações de mercado, utilizar cache para reduzir chamadas à API e enriquecer as posições da carteira com valor atual, lucro/prejuízo e rentabilidade.

### Principais entregas

#### Integração com cotações

- Criação da feature `quote`
- Integração com a API brapi
- Utilização do endpoint de cotações da brapi
- Autenticação via Bearer Token
- Token armazenado através da variável de ambiente `BRAPI_TOKEN`
- `BrapiClient`
- DTOs específicos para a resposta da API externa
- `QuoteService`
- `QuoteController`
- `QuoteResponse`
- `QuoteNotFoundException`
- Tratamento de ticker inexistente com HTTP 404
- Proteção contra respostas vazias da API externa

#### Cache com Redis

- Redis 7 executado via Docker Compose
- Spring Data Redis
- `StringRedisTemplate`
- Cache de cotações por ticker
- Padrão de chave:

```text
quote:{ticker}
```

- TTL de 5 minutos
- Implementação de cache HIT
- Implementação de cache MISS
- Persistência automática da cotação no cache após consulta à brapi
- Validação real do cache através do `redis-cli`
- Validação real do TTL

Fluxo implementado:

```text
QuoteService
    ↓
consulta Redis
    ↓
cache HIT ─────────────→ retorna cotação
    ↓
cache MISS
    ↓
brapi
    ↓
salva no Redis com TTL
    ↓
retorna cotação
```

#### Valorização das posições

- Criação de `PositionMarketResponse`
- Novo cálculo de posição valorizada a mercado
- Integração entre `PositionService` e `QuoteService`
- Manutenção do cálculo contábil separado da consulta de mercado
- Cálculo do preço atual
- Cálculo do valor atual da posição
- Cálculo de lucro/prejuízo não realizado
- Cálculo percentual de rentabilidade
- Proteção contra divisão por zero
- Exclusão de posições zeradas da consulta de mercado
- Correção da precisão do cálculo percentual com `BigDecimal`
- Carregamento explícito de `Asset` através de `@EntityGraph`
- Correção de `LazyInitializationException` sem alterar a associação para `EAGER`

### Endpoints

```text
GET /api/v1/quotes/{symbol}

GET /api/v1/portfolios/{portfolioId}/positions
GET /api/v1/portfolios/{portfolioId}/positions/market
```

### Exemplo validado

```text
Ativo:             ITUB4
Quantidade:        40
Preço médio:       42,50
Custo total:       1.700,00

Cotação atual:     42,52
Valor atual:       1.700,80
Lucro/prejuízo:    0,80
Rentabilidade:     0,0471%
```

Cálculos:

```text
currentValue =
quantity × currentPrice

profitLoss =
currentValue - totalCost

profitabilityPercent =
(profitLoss × 100) / totalCost
```

### Decisão arquitetural

O cálculo contábil da posição permaneceu separado da valorização a mercado.

```text
calculatePositions()
        ↓
quantidade
preço médio
custo total
        ↓
não depende de serviço externo


calculateMarketPositions()
        ↓
calculatePositions()
        +
QuoteService
        ↓
cotação atual
valor atual
lucro/prejuízo
rentabilidade
```

Essa separação evita que regras internas, como a validação de uma venda, dependam de Redis, internet ou da API externa de cotações.

### Lazy loading

Durante o teste real do endpoint de posições de mercado foi identificada uma `LazyInitializationException` ao acessar o `Asset` associado a uma `Transaction`.

A solução adotada foi carregar explicitamente o ativo na consulta necessária:

```java
@EntityGraph(attributePaths = "asset")
List<Transaction> findByPortfolioIdOrderByTransactionDateAscIdAsc(
        Long portfolioId
);
```

A associação permaneceu `LAZY`, evitando transformar o carregamento antecipado em comportamento global.

### Testes

Foram adicionados testes para:

- retorno de cotação atual
- cotação inexistente
- resposta vazia da API externa
- cache HIT
- cache MISS
- persistência da cotação no Redis com TTL
- cálculo de posição valorizada a mercado
- valor atual da posição
- lucro/prejuízo
- rentabilidade
- exclusão de posição zerada
- garantia de que posição zerada não consulta o `QuoteService`

O teste de contexto utiliza um token fictício:

```java
@SpringBootTest(properties = "brapi.token=test-token")
```

Isso permite carregar o contexto Spring durante os testes sem depender de uma credencial real da brapi.

### Conceitos estudados

- Integração com API REST externa
- `RestClient`
- Bearer Token
- Variáveis de ambiente
- Proteção de credenciais
- Redis
- Cache
- TTL
- Cache HIT e cache MISS
- `StringRedisTemplate`
- Docker Compose com múltiplos serviços
- Mock de dependências externas
- `verifyNoInteractions`
- `BigDecimal` em cálculos financeiros
- Precisão e arredondamento
- Lazy loading
- Hibernate Proxy
- `LazyInitializationException`
- `@EntityGraph`
- Separação entre cálculo de domínio e integração externa
- Testes unitários sem dependência de internet

### Resultado final da suíte

```text
Tests run: 43
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

---

## Sprint 7 — Usuários, Autenticação e Segurança

**Status:** Concluída

### Objetivo

Adicionar usuários à aplicação, implementar autenticação com JWT e garantir que os dados pertencentes a uma carteira sejam acessíveis somente pelo usuário autenticado proprietário.

### Principais entregas

#### Usuários

- Criação da feature `user`
- Entidade `User`
- Migration V7 para criação da tabela `users`
- `UserRepository`
- `UserCreateRequest`
- `UserResponse`
- `UserService`
- `UserController`
- Senhas armazenadas com BCrypt
- Validação de e-mail único
- `UserAlreadyExistsException`
- Retorno HTTP 409 para tentativa de cadastro de e-mail já existente
- Senha não exposta nos DTOs de resposta

#### Autenticação e JWT

- Criação da feature `auth`
- `LoginRequest`
- `LoginResponse`
- `AuthService`
- `AuthController`
- `InvalidCredentialsException`
- `JwtService`
- Geração e validação de JWT
- ID do usuário armazenado no `subject` do token
- Chave JWT fornecida pela variável de ambiente `JWT_SECRET`
- Expiração configurável do token
- `JwtAuthenticationFilter`
- `JwtAuthenticationEntryPoint`
- Integração com Spring Security
- Endpoints de criação de usuário e login públicos
- Demais endpoints protegidos por autenticação

Fluxo de autenticação:

```text
POST /api/v1/auth/login
        ↓
AuthService
        ↓
validação de e-mail e senha
        ↓
JwtService
        ↓
JWT com userId no subject
        ↓
Bearer Token nas requisições protegidas
```

#### Ownership e isolamento de dados

Foi adotado o relacionamento:

```text
User 1:N Portfolio

User
 └── Portfolio
      ├── Transaction
      ├── Income
      └── Position
```

A propriedade do usuário é armazenada na carteira. `Transaction`, `Income` e posições derivam o ownership através de `Portfolio`, evitando duplicação desnecessária de `user_id`.

- Migration V8 adicionando `user_id` em `portfolios`
- Relacionamento `Portfolio -> User` com `@ManyToOne(fetch = FetchType.LAZY)`
- Foreign key entre `portfolios.user_id` e `users.id`
- Carteiras filtradas pelo usuário autenticado
- Transações filtradas pelo proprietário da carteira
- Proventos filtrados pelo proprietário da carteira
- Posições contábeis protegidas por ownership
- Posições valorizadas a mercado protegidas por ownership
- Usuário não proprietário recebe HTTP 404 ao tentar acessar carteira de outro usuário
- Assets, Brokers e Quotes permanecem globais por decisão de domínio

Fluxo de autorização:

```text
JWT
 ↓
JwtAuthenticationFilter
 ↓
Authentication
 ↓
Controller extrai userId
 ↓
Service valida ownership
 ↓
Repository filtra pelo usuário
```

O `Service` não consulta diretamente o `SecurityContextHolder`. O `Controller` recebe o usuário autenticado e repassa o `userId`, mantendo explícita a dependência de autenticação na camada de entrada.

### Migrations

```text
V7 - criação da tabela users
V8 - associação de user a portfolios
```

### Endpoints de usuários e autenticação

```text
POST /api/v1/users
POST /api/v1/auth/login
```

Os demais endpoints da aplicação exigem:

```text
Authorization: Bearer <JWT>
```

### Tratamento de erros

```text
401 Unauthorized  - ausência de autenticação, token inválido/expirado ou credenciais inválidas
404 Not Found     - recurso inexistente ou recurso pertencente a outro usuário
409 Conflict      - tentativa de cadastrar e-mail já existente
```

### Decisões arquiteturais

- `User` possui relação 1:N com `Portfolio`.
- O `user_id` não foi replicado em `Transaction`, `Income` ou outras entidades dependentes.
- O JWT utiliza o ID do usuário como `subject`.
- Controllers extraem o ID do usuário autenticado e o enviam aos Services.
- Services validam ownership explicitamente.
- Exceptions específicas permanecem dentro de cada feature.
- `GlobalExceptionHandler` permanece compartilhado.
- O relacionamento com `User` permanece `LAZY`.
- `PortfolioMapper` ignora explicitamente `user` no mapeamento do DTO de criação, pois o usuário é definido pelo contexto autenticado.

### Testes

Foram adicionados e atualizados testes para:

- criação de usuário
- tentativa de criação com e-mail já existente
- geração de JWT
- validação de JWT
- login válido
- credenciais inválidas
- ownership de carteiras
- isolamento de transações
- isolamento de proventos
- isolamento de posições
- acesso autorizado e não autorizado às posições de mercado
- repositories com consultas filtradas por usuário
- carregamento do contexto Spring com configuração JWT de teste

### Resultado final da suíte

```text
Tests run: 57
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

### Conceitos estudados

- Spring Security
- `SecurityFilterChain`
- `PasswordEncoder`
- BCrypt
- JWT
- Bearer Token
- `OncePerRequestFilter`
- `Authentication`
- `SecurityContextHolder`
- autenticação x autorização
- ownership de dados
- isolamento entre usuários
- relacionamento `User 1:N Portfolio`
- derived query methods atravessando relacionamentos
- HTTP 401, 404 e 409
- variáveis de ambiente para segredos
- testes unitários de autenticação e autorização

### Versionamento

Commit principal da Sprint:

```text
8a32c7b feat: add JWT authentication and user data isolation
```

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
Sprint 5 ✅
Sprint 6 ✅
Sprint 7 ✅
```

Próximo marco:

```text
Sprint 8 — Consolidação, documentação e preparação para produção
```

### Quiz da Sprint

Pendente de realização no fechamento da Sprint 7.

---

## Sprint 8 — Consolidação, documentação e preparação para produção

**Status:** Concluída

### Objetivo

Consolidar a API construída nas Sprints anteriores, documentar os endpoints com OpenAPI/Swagger, revisar contratos HTTP, DTOs, validações e configurações sensíveis, além de preparar a aplicação para execução em diferentes ambientes sem expor segredos.

### Principais entregas

#### OpenAPI e Swagger

- Adição do SpringDoc OpenAPI 3.1.1
- Swagger UI disponível em `/swagger-ui/index.html`
- Especificação OpenAPI disponível em `/v3/api-docs`
- Configuração de autenticação HTTP Bearer com JWT no Swagger
- Liberação das rotas do Swagger no Spring Security
- Documentação dos 9 controllers da aplicação
- Revisão das principais respostas HTTP documentadas
- Validação real do fluxo de login e autorização pelo Swagger UI

#### Padronização de erros

Foi criado o contrato compartilhado `ErrorResponse`:

```text
status
error
message
```

As respostas simples de erro deixaram de utilizar estruturas genéricas baseadas em `Map<String, Object>`, tornando o contrato da API mais previsível e melhor documentado pelo OpenAPI.

Exemplo validado:

```json
{
  "status": 404,
  "error": "Não encontrado",
  "message": "Ativo não encontrado com o id: 999999"
}
```

O `ValidationErrorResponse` permanece específico para erros de Bean Validation, preservando os campos inválidos e suas respectivas mensagens.

#### DTOs e validações

- Revisão dos DTOs de entrada e saída
- Padronização das mensagens de Bean Validation em PT-BR
- Revisão de `@NotBlank`, `@NotNull`, `@Positive`, `@Size` e `@Email`
- Remoção de imports não utilizados em DTOs revisados
- Manutenção das entidades isoladas dos contratos HTTP através de DTOs

#### Revisão dos códigos HTTP

Foram revisados os controllers de:

```text
Asset
Auth
Broker
Income
Portfolio
Position
Quote
Transaction
User
```

Principais códigos utilizados:

```text
200 OK
201 Created
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
```

#### Configuração por ambiente

O `application.yml` foi revisado para permitir configuração externa através de variáveis de ambiente:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
BRAPI_TOKEN
JWT_SECRET
JWT_EXPIRATION
```

Banco de dados e Redis possuem valores padrão adequados ao ambiente local. Segredos como `BRAPI_TOKEN` e `JWT_SECRET` continuam sem valores reais versionados no repositório.

#### Limpeza de código

- Identificada injeção desnecessária de `BrokerMapper` no `BrokerController`
- Confirmado que `BrokerMapper` continua necessário no `BrokerService`
- Removida apenas a dependência não utilizada do controller, sem alterar a arquitetura da feature

### Testes e validação final

Após as alterações da Sprint, a aplicação foi compilada e a suíte completa foi executada com sucesso:

```text
Tests run: 57
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução final confirmou ausência de regressões nas funcionalidades implementadas nas Sprints anteriores.

### Conceitos estudados

- OpenAPI
- Swagger UI
- SpringDoc
- documentação de contratos REST
- HTTP Bearer Authentication
- documentação de respostas HTTP
- padronização de respostas de erro
- Bean Validation
- configuração externa por variáveis de ambiente
- valores padrão em propriedades Spring (`${VARIAVEL:valorPadrao}`)
- separação entre configuração local e segredos
- revisão e remoção de dependências não utilizadas
- regressão através de suíte automatizada de testes

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
Sprint 5 ✅
Sprint 6 ✅
Sprint 7 ✅
Sprint 8 ✅
```

Próximo marco:

```text
Sprint 9 — Front-end React (opcional)
```

### Quiz da Sprint

Pendente de realização após o fechamento técnico da Sprint 8.

````

---

## Sprint 9 — Front-end React

**Status:** Concluída

### Objetivo

Construir uma interface web moderna e responsiva para consumir a API REST do Investment Manager, permitindo autenticação, navegação e operação das principais funcionalidades já implementadas no backend.

### Principais entregas

#### Fundação do frontend

- Projeto separado `investment-manager-web`
- React com TypeScript
- Vite
- React Router
- Axios
- Configuração da URL da API através de `VITE_API_URL`
- Estrutura organizada em `components`, `pages`, `services` e `types`
- CSS responsivo sem dependência de biblioteca visual externa

#### Autenticação e segurança

- Tela de login integrada a `POST /api/v1/auth/login`
- JWT armazenado no `localStorage`
- `ProtectedRoute` para impedir acesso sem autenticação
- Interceptor Axios para envio automático de `Authorization: Bearer <JWT>`
- Interceptor de resposta para tratamento de HTTP 401
- Remoção do token inválido e redirecionamento para `/login`
- Logout com remoção do token
- CORS centralizado no backend para `http://localhost:5173`

#### Navegação

- Header compartilhado
- Rotas para Dashboard, Carteiras, Transações, Proventos e Ativos
- Destaque visual da rota ativa
- Menu mobile do tipo hambúrguer
- Fechamento automático do menu após a navegação

#### Dashboard

- Seleção de carteira
- Carregamento paralelo de posições valorizadas e proventos
- Patrimônio atual
- Resultado não realizado
- Rentabilidade percentual
- Custo investido
- Proventos registrados
- Quantidade de ativos
- Cards das posições com preço médio, preço atual, custo, valor atual e resultado
- Indicadores positivos e negativos com diferenciação visual

#### Carteiras e posições

- Listagem das carteiras do usuário autenticado
- Resumo financeiro por carteira
- Acesso ao detalhe da carteira
- Consulta de posições valorizadas a mercado
- Exibição de quantidade, preço médio, preço atual, custo, valor atual, resultado e rentabilidade

#### Transações

- Seleção de carteira
- Cadastro de compras e vendas
- Seleção de ativo e corretora
- Histórico de transações
- Integração das transações com o recálculo das posições
- Exibição de erros de regra de negócio retornados pelo backend
- Validação de venda superior à posição disponível

#### Proventos

- Cadastro de dividendos, JCP e rendimentos de FIIs
- Seleção de carteira e ativo
- Valor por unidade, quantidade e data de pagamento
- Histórico de proventos
- Exibição do valor total calculado pelo backend
- Consolidação dos proventos no Dashboard

#### Ativos

- Listagem dos ativos disponíveis
- Exibição de ticker, nome, tipo, setor e bolsa
- Tradução dos tipos de ativo para apresentação ao usuário

#### Responsividade

- Cards reorganizados para telas menores
- Formulários adaptados para layout mobile
- Posições e resumos financeiros responsivos
- Header mobile com menu hambúrguer
- Validação visual realizada em viewport de aproximadamente 399 x 757 pixels

### Revisão funcional da Sprint

Antes do encerramento da Sprint 9 foi executada uma revisão funcional manual cobrindo os fluxos principais.

```text
Teste 1 — Login, logout e proteção das rotas       OK
Teste 2 — Navegação entre as páginas                OK
Teste 3 — Transação -> posição -> Dashboard          OK
Teste 4 — Provento -> histórico -> Dashboard         OK
Teste 5 — Validações e regras de negócio             OK
Teste 6 — JWT/token inválido                         OK
Teste 7 — Responsividade/mobile                      OK
```

Durante o Teste 7 foi identificado que o menu horizontal não disponibilizava todos os itens em telas pequenas. O problema foi corrigido com a implementação do menu mobile hambúrguer e o teste foi repetido com sucesso.

### Conceitos estudados

- React
- TypeScript
- JSX/TSX
- Componentes
- Props
- `useState`
- `useEffect`
- estado e renderização
- formulários controlados
- React Router
- SPA
- `NavLink`
- rotas protegidas
- Axios
- `async/await`
- interfaces TypeScript
- variáveis de ambiente no Vite
- `localStorage`
- JWT no frontend
- request e response interceptors
- CORS e preflight
- `Promise.all`
- renderização condicional
- `map` e `key`
- CSS Grid e Flexbox
- media queries
- design responsivo

### Decisões de implementação

- O frontend permanece como aplicação independente do backend.
- O React consome exclusivamente a API REST; não foi utilizado Thymeleaf.
- O token JWT é anexado às requisições pelo cliente Axios centralizado.
- Regras de negócio permanecem no backend; o frontend apresenta ao usuário os erros retornados pela API.
- Proventos são apresentados separadamente do patrimônio atual, pois ainda não existe um módulo de saldo/caixa.
- A interface utiliza componentes e CSS próprios, evitando adicionar uma biblioteca visual apenas para a Sprint 9.

### Evolução futura registrada

O módulo de proventos poderá evoluir para registrar data-base/data-com e calcular automaticamente a quantidade elegível a partir do histórico de transações existente naquela data, reduzindo a necessidade de informar manualmente a quantidade.

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
Sprint 5 ✅
Sprint 6 ✅
Sprint 7 ✅
Sprint 8 ✅
Sprint 9 ✅
```

A aplicação possui agora backend Java/Spring Boot e frontend React/TypeScript integrados, com os principais fluxos funcionais disponíveis através da interface web.

