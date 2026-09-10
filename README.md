# Investment Manager API

API REST para gerenciamento de investimentos, desenvolvida em Java com Spring Boot como projeto de estudo e evolução prática em desenvolvimento backend, arquitetura, persistência, testes, integrações, segurança e boas práticas.

## Objetivo

Construir uma aplicação capaz de gerenciar usuários, carteiras, ativos, transações, corretoras e proventos, calcular posições e preços médios, consultar cotações de mercado e evoluir gradualmente para uma solução completa de acompanhamento de investimentos.

## Tecnologias atuais

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- Spring Security
- Jakarta Validation
- PostgreSQL 17
- Redis 7
- Flyway
- MapStruct
- JJWT 0.13.0
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
│   └── service
├── auth
│   ├── controller
│   ├── dto
│   ├── exception
│   ├── security
│   └── service
├── broker
├── income
├── portfolio
│   ├── controller
│   ├── dto
│   ├── exception
│   ├── mapper
│   ├── repository
│   └── service
├── position
├── quote
├── transaction
├── user
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── exception
│   ├── repository
│   └── service
└── shared
    ├── config
    └── exception
```

Exceptions específicas permanecem dentro da própria feature. Configurações e tratamento global compartilhado ficam em `shared`.

## Funcionalidades implementadas

### Usuários e autenticação

- Cadastro de usuários
- Senhas protegidas com BCrypt
- Validação de e-mail único
- Login com e-mail e senha
- Geração e validação de JWT
- ID do usuário armazenado no `subject` do token
- Proteção dos endpoints com Spring Security
- Tratamento de credenciais inválidas
- HTTP 409 para e-mail já cadastrado

```text
POST /api/v1/users
POST /api/v1/auth/login
```

Os endpoints de criação de usuário e login são públicos. Os demais endpoints exigem:

```text
Authorization: Bearer <JWT>
```

### Isolamento de dados por usuário

Cada `Portfolio` pertence a um `User`.

```text
User
 └── Portfolio
      ├── Transaction
      ├── Income
      └── Position
```

O `user_id` é armazenado apenas em `Portfolio`. Transações, proventos e posições derivam a propriedade através da carteira.

O fluxo utilizado nos endpoints protegidos é:

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

Um usuário não pode consultar carteiras, transações, proventos ou posições pertencentes a outro usuário.

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

- Cadastro de carteiras vinculadas ao usuário autenticado
- Busca por ID respeitando ownership
- Listagem apenas das carteiras do usuário autenticado
- Validação de entrada
- Tratamento de carteira inexistente

```text
POST /api/v1/portfolios
GET  /api/v1/portfolios
GET  /api/v1/portfolios/{id}
```

### Transações

- Cadastro de compras e vendas
- Associação com carteira, ativo e corretora
- Busca por ID
- Listagem geral limitada ao usuário autenticado
- Listagem por carteira com validação de ownership
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
- Busca por ID respeitando ownership
- Listagem limitada ao usuário autenticado
- Listagem por carteira com validação de ownership
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
- Validação de ownership da carteira

```text
GET /api/v1/portfolios/{portfolioId}/positions
```

### Posições valorizadas a mercado

- Consulta de cotação atual dos ativos
- Integração com API externa brapi
- Cache de cotações utilizando Redis
- TTL de 5 minutos para as cotações
- Cálculo do valor atual da posição
- Cálculo de lucro ou prejuízo não realizado
- Cálculo percentual de rentabilidade
- Exclusão de posições zeradas da consulta de mercado
- Carregamento explícito do ativo com `@EntityGraph`
- Tratamento de cotação inexistente
- Validação de ownership antes da consulta

```text
GET /api/v1/portfolios/{portfolioId}/positions/market
GET /api/v1/quotes/{symbol}
```

Exemplo de cálculo contábil:

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

### Segurança e ownership

- Senhas não são armazenadas em texto puro.
- O JWT identifica o usuário autenticado pelo ID presente no `subject`.
- O cliente não informa manualmente o `userId` para definir ownership.
- O usuário de uma requisição protegida é obtido a partir da autenticação.
- A carteira é a raiz do ownership para transações, proventos e posições.
- Tentativas de acessar recursos de outra carteira são tratadas como recurso não encontrado.

## Banco de dados e migrations

```text
V1 - criação de assets
V2 - criação de portfolios
V3 - criação de transactions
V4 - criação de brokers
V5 - adição de broker a transactions
V6 - criação de incomes
V7 - criação de users
V8 - associação de user a portfolios
```

## Configuração

A aplicação utiliza variáveis de ambiente para credenciais e segredos.

### API de cotações

```text
BRAPI_TOKEN
```

### JWT

```text
JWT_SECRET
```

`JWT_SECRET` deve conter uma chave Base64 adequada para assinatura do token. O segredo real não deve ser versionado no repositório.

A expiração configurada atualmente é de 1 hora.

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

Executar a suíte completa de testes:

```powershell
.\mvnw.cmd clean test
```

## Testes

O projeto utiliza testes unitários e de integração com:

- JUnit
- Mockito
- Testcontainers
- PostgreSQL real e descartável
- `@ServiceConnection`
- testes das regras de cálculo de posição
- testes de persistência e relacionamentos
- testes de integração com cotações e cache
- testes de autenticação e JWT
- testes de ownership e isolamento entre usuários

Ao final da Sprint 7:

```text
Tests run: 57
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

## Códigos HTTP relevantes

```text
200 OK            - consulta realizada com sucesso
201 Created       - recurso criado
400 Bad Request   - validação ou regra de negócio inválida
401 Unauthorized  - autenticação ausente/inválida ou credenciais inválidas
404 Not Found     - recurso inexistente ou não pertencente ao usuário
409 Conflict      - e-mail já cadastrado
```

## Roadmap

- [x] Sprint 1 — Fundação técnica + Asset
- [x] Sprint 2 — Portfolio + MapStruct + Testcontainers
- [x] Sprint 3 — Transactions
- [x] Sprint 4 — Posição, preço médio e patrimônio
- [x] Sprint 5 — Proventos e corretoras
- [x] Sprint 6 — Cotações externas e cache
- [x] Sprint 7 — Usuários, autenticação e segurança
- [ ] Sprint 8 — Consolidação, documentação e preparação para produção
- [ ] Sprint 9 opcional — Front-end React

## Histórico detalhado

Consulte:

```text
docs/PROJECT_HISTORY.md
```
