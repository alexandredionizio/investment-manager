# Investment Manager API

API REST para gerenciamento de investimentos, desenvolvida em Java com Spring Boot como projeto de estudo e evolução prática em desenvolvimento backend, arquitetura, persistência, testes e boas práticas.

## Objetivo

Construir uma aplicação capaz de gerenciar carteiras, ativos e transações de investimentos, evoluindo gradualmente para recursos como cálculo de posição, preço médio, patrimônio, proventos, integrações externas, segurança e autenticação.

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

- Cadastro de compra e venda
- Associação com carteira e ativo
- Busca por ID
- Listagem geral
- Listagem por carteira
- Validação de quantidade e preço
- Tratamento de transação inexistente

```text
POST /api/v1/transactions
GET  /api/v1/transactions
GET  /api/v1/transactions/{id}
GET  /api/v1/transactions/portfolio/{portfolioId}
```

## Banco de dados e migrations

```text
V1 - criação de assets
V2 - criação de portfolios
V3 - criação de transactions
```

## Testes

O projeto possui testes unitários e de integração.

- JUnit
- Mockito
- Testcontainers
- PostgreSQL real e descartável
- Integração com Spring Boot via @ServiceConnection

Ao final da Sprint 3:

```text
Tests run: 20
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
- [ ] Sprint 4 — Posição, preço médio e patrimônio
- [ ] Sprint 5 — Proventos e corretoras
- [ ] Sprint 6 — Cotações externas e cache
- [ ] Sprint 7 — Usuários, autenticação e segurança
- [ ] Sprint 8 — Consolidação, documentação e preparação para produção
- [ ] Sprint 9 opcional — Front-end React

## Histórico detalhado

Consulte:

```text
docs/PROJECT_HISTORY.md
```
