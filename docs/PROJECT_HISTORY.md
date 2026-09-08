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

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
Sprint 5 ✅
Sprint 6 ✅
```

Próximo marco:

```text
Sprint 7 — Usuários, autenticação e segurança
```