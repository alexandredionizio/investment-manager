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

## Estado atual

```text
Sprint 1 ✅
Sprint 2 ✅
Sprint 3 ✅
Sprint 4 ✅
```

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
