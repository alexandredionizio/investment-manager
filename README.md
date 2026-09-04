# Investment Manager API

API REST para gerenciamento de investimentos, desenvolvida em Java com Spring Boot como projeto de estudo e evolução prática em desenvolvimento backend, arquitetura, persistência, testes e boas práticas.

## Objetivo

Construir uma aplicação capaz de gerenciar carteiras, ativos e transações de investimentos, calcular posições e preços médios, evoluindo gradualmente para recursos como patrimônio, proventos, integrações externas, segurança e autenticação.

## Tecnologias atuais

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- Jakarta Validation
- PostgreSQL 17
- Flyway
- MapStruct
- JUnit
- Mockito
- Testcontainers
- Docker / Docker Compose
- Maven
- Git / GitHub

## Arquitetura atual

O projeto é organizado por feature:

```text
com.investmanager.api
├── asset
│   ├── controller
│   ├── dto
│   ├── exception
│   ├── mapper
│   ├── repository
│   ├── service
│   ├── Asset
│   └── AssetType
├── portfolio
│   ├── controller
│   ├── dto
│   ├── exception
│   ├── mapper
│   ├── repository
│   ├── service
│   └── Portfolio
├── position
│   ├── controller
│   ├── dto
│   ├── exception
│   └── service
├── transaction
│   ├── controller
│   ├── dto
│   ├── exception
│   ├── mapper
│   ├── repository
│   ├── service
│   ├── Transaction
│   └── TransactionType
└── shared
    └── exception
        ├── GlobalExceptionHandler
        └── ValidationErrorResponse
```

## Funcionalidades implementadas

### Ativos

- Cadastro de ativos
- Busca por ID
- Listagem de ativos
- Validação de entrada
- Tratamento de ativo inexistente

```text
POST /api/v1/assets
GET  /api/v1/assets
GET  /api/v1/assets/{id}
```

### Carteiras

- Cadastro de carteiras
- Busca por ID
- Listagem de carteiras
- Validação de entrada
- Tratamento de carteira inexistente

```text
POST /api/v1/portfolios
GET  /api/v1/portfolios
GET  /api/v1/portfolios/{id}
```

### Transações

- Cadastro de compras e vendas
- Associação com carteira e ativo
- Busca por ID
- Listagem geral
- Listagem por carteira
- Ordenação cronológica das transações
- Validação de quantidade e preço
- Validação de posição disponível antes de uma venda
- Bloqueio de vendas superiores à posição disponível
- Tratamento de transação inexistente


```text
POST /api/v1/transactions
GET  /api/v1/transactions
GET  /api/v1/transactions/{id}
GET  /api/v1/transactions/portfolio/{portfolioId}
```

### Corretoras

- Cadastro de corretoras
- Busca por ID
- Listagem de corretoras
- Associação da corretora às transações
- Validação e tratamento de corretora inexistente

```text
POST /api/v1/brokers
GET  /api/v1/brokers
GET  /api/v1/brokers/{id}
```

### Proventos

- Cadastro de dividendos, JCP e rendimentos de FIIs
- Associação com carteira e ativo
- Busca por ID
- Listagem geral
- Listagem por carteira
- Cálculo automático do valor total do provento
- Validação de quantidade e valor por unidade
- Tratamento de provento inexistente

```text
POST /api/v1/incomes
GET  /api/v1/incomes
GET  /api/v1/incomes/{id}
GET  /api/v1/incomes/portfolio/{portfolioId}
```

### Posições

- Consolidação das transações por ativo
- Cálculo da quantidade atual
- Cálculo do preço médio ponderado
- Cálculo do custo total da posição
- Processamento cronológico das transações
- Venda parcial mantendo o preço médio
- Venda total zerando a posição
- Proteção contra venda superior à quantidade disponível
- Tratamento de carteira inexistente

```text
GET /api/v1/portfolios/{portfolioId}/positions
```

Exemplo de cálculo:

```text
BUY 100 ITUB4 @ 35,50
BUY  50 ITUB4 @ 41,50

Quantidade: 150
Preço médio: 37,50
Custo total: 5.625,00

SELL 50 ITUB4

Quantidade: 100
Preço médio: 37,50
Custo total: 3.750,00
```

## Regras de negócio

### Compra

Uma compra aumenta a quantidade e o custo total da posição. O preço médio é recalculado considerando todas as compras processadas.

### Venda

Uma venda:

- exige posição disponível suficiente
- reduz a quantidade do ativo
- reduz o custo da posição com base no preço médio
- não altera o preço médio da posição restante
- não pode gerar posição negativa

Quando toda a posição é vendida:

```text
quantidade = 0
preço médio = 0
custo total = 0
```

Uma tentativa de venda superior à posição disponível retorna:

```text
HTTP 400 - Bad Request
```

e a transação não é persistida.

## Banco de dados e migrations

```text
V1 - criação de assets
V2 - criação de portfolios
V3 - criação de transactions
V4 - criação de brokers
v5 - adição de broker a transações
V6 - criação de incomes
```

## Testes

O projeto possui testes unitários e de integração.

- JUnit
- Mockito
- Testcontainers
- PostgreSQL real e descartável
- Integração com Spring Boot via `@ServiceConnection`
- Testes das regras de cálculo de posição
- Testes de preço médio
- Testes de venda parcial e total
- Testes de posição insuficiente
- Testes de persistência e relacionamentos

Ao final da Sprint 4:

```text
Tests run: 37
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## Como executar

Pré-requisitos:

- Java 21
- Docker Desktop
- Git

Subir os containers:

```powershell
docker compose up -d
```

Parar os containers:

```powershell
docker compose down
```

Executar a suíte de testes:

```powershell
.\mvnw.cmd test
```

## Roadmap

- [x] Sprint 1 — Fundação técnica + Asset
- [x] Sprint 2 — Portfolio + MapStruct + Testcontainers
- [x] Sprint 3 — Transactions
- [x] Sprint 4 — Posição, preço médio e patrimônio
- [x] Sprint 5 — Proventos e corretoras
- [ ] Sprint 6 — Cotações externas e cache
- [ ] Sprint 7 — Usuários, autenticação e segurança
- [ ] Sprint 8 — Consolidação, documentação e preparação para produção
- [ ] Sprint 9 opcional — Front-end React

## Histórico detalhado

Consulte:

```text
docs/PROJECT_HISTORY.md
```