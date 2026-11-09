# AGENTS.md — Investment Manager (IM)

## Objetivo e escopo
Este arquivo define as regras permanentes para trabalho nos repositórios do **Investment Manager (IM)**. Siga-as em tarefas de implementação, revisão e testes. O **código atual** e o **backlog fornecido pelo usuário** são as fontes de verdade; este documento não comprova que um PBI esteja concluído.

## Stack e estrutura
- Backend: Java 21, Spring Boot 4.x (validar versão no repositório), PostgreSQL 17, Spring Data JPA, Flyway, Spring Security/JWT, JUnit, Mockito e Testcontainers.
- Frontend: React, TypeScript, Vite e Axios.
- Infraestrutura: Docker Compose; PostgreSQL e Redis; cache de cotações e históricos.
- Integrações conhecidas: Brapi (ações, FIIs, ETFs e IBOV), BCB/SGS série 12 (CDI). Outras fontes só quando justificadas pelo requisito.
- O backend pode estar em `investment-manager-api/investment-manager-api` e o frontend em `investment-manager-web`; confirme a organização real antes de operar.

## Princípios inegociáveis
1. **Integridade financeira primeiro.** Nunca invente preços, taxas, proventos, posições ou retornos. Distinga zero legítimo, ausência de dados, histórico insuficiente e falha técnica. Não trate indisponibilidade como valor zero.
2. **Contabilidade separada da avaliação de mercado.** Falhas de cotação não podem apagar ou distorcer quantidade, custo, transações e proventos. Não apresente patrimônio parcial como total sem sinalizar a limitação.
3. **Preservar regras existentes.** Não altere compras, vendas retroativas, preço/custo médio, proventos, validações temporais, locks, concorrência e autenticação sem evidência de defeito e necessidade para o escopo.
4. **Precisão e rastreabilidade.** Use `BigDecimal` e regras de escala/arredondamento coerentes com o domínio; preserve datas, moeda, origem e referência das avaliações. Evite `double` em cálculos monetários.
5. **Integrações resilientes.** Reutilize clientes HTTP, limites de tempo, retries seletivos, backoff, `Retry-After`, orçamento agregado e cache existentes. Chamadas externas e esperas de retry devem ocorrer fora de transações de persistência.
6. **Idempotência.** Sincronizações e importações repetidas não podem duplicar posições, operações, proventos ou séries. Preserve constraints, integridade transacional e tratamento de concorrência.
7. **Segurança.** Nunca exponha senhas, JWTs, tokens de fornecedores, credenciais, dados pessoais ou payloads sensíveis em logs, commits e relatórios.
8. **Manutenibilidade sem overengineering.** Reutilize componentes e contratos. Prefira alterações pequenas em escopo e uma implementação coesa; evite abstrações, novas dependências e infraestrutura sem necessidade comprovada.

## Estado e decisões de produto
- Os PBIs **IM-001 a IM-027** foram relatados como concluídos. Último baseline confirmado: **468 testes backend + 39 frontend = 507 aprovados** (não presuma que esse número permaneça constante).
- **IM-028 a IM-030** (cripto, renda fixa e Tesouro Direto) estavam em desenvolvimento na última atualização; verifique o estado atual antes de agir.
- Para **IM-028**, suportar inicialmente apenas **BTC e ETH**. **SOL e outras criptomoedas permanecem sem avaliação**, sem excluir seus registros. Projete a identificação/mapeamento de moedas e a consulta de cotações para facilitar ampliação futura, sem hardcode espalhado nem abstração excessiva. Um enum de moedas suportadas e um provedor desacoplado podem ser adequados se compatíveis com a arquitetura existente.
- A tela **Ativos** é de consulta. Nas **compras**, o produto deve poder localizar um instrumento elegível e cadastrá-lo internamente sob demanda; nas **vendas**, a disponibilidade deve respeitar carteira e data, inclusive operações retroativas. O mesmo serviço de identificação deve ser reutilizável na futura importação B3.
- Para futura importação B3: pré-visualizar, validar e exigir confirmação antes de alterar a carteira; conciliar registros, preservar eventos corporativos e evitar duplicações.
- O dashboard deve preservar os dados contábeis quando o mercado ou benchmark estiver indisponível. Não reinterpretar nem alterar a queda de rentabilidade de outubro/2026 sem solicitação explícita e evidências.
- Backlog: usar **a versão mais recente fornecida pelo usuário**. IDs e escopos não devem ser renumerados automaticamente; status "a verificar" não significa defeito confirmado.

## Forma de execução — economia de créditos
- O usuário prioriza **GPT-6** e **mínimo de iterações**. Ao receber um épico, faça diagnóstico objetivo internamente e execute as alterações necessárias **em uma única rodada contínua**, sem interromper para apresentar planos ou pedir confirmações rotineiras.
- **Não delegue ao Codex, a outros agentes ou a serviços externos** por iniciativa própria. Qualquer encaminhamento/delegação que consuma créditos adicionais exige autorização explícita do usuário. Esta regra não impede executar a tarefa diretamente no ambiente autorizado em que você já está trabalhando.
- Inspecione primeiro o código e testes relevantes; evite análises repetidas, mudanças cosméticas, documentação supérflua e trabalho fora do escopo.
- Concentre a **suíte completa** de testes backend/frontend **ao final da implementação**; durante o trabalho, execute somente verificações pontuais indispensáveis. Se falhar, corrija e repita apenas o necessário. Não enfraqueça nem remova testes para forçar aprovação.
- Se um item já estiver implementado corretamente, registre a evidência e **não reimplemente**. Não invente medições, testes ou resultados.
- Só peça autorização quando indispensável: mudanças destrutivas, uso de credenciais/serviços pagos, ações irreversíveis ou decisões fundamentais não cobertas pelos requisitos. Se estiver bloqueado, documente objetivamente o motivo e conclua o restante seguro do escopo.

## Proteção do ambiente e dos dados
- **Não** execute `docker compose down -v`, `docker system prune --volumes`, reset de fábrica, `DROP`, `TRUNCATE`, limpeza massiva ou alterações destrutivas de dados sem autorização explícita.
- Não altere senhas, credenciais, dados reais do usuário, posições nem transações reais para testar.
- Prefira bancos/Redis temporários e dados sintéticos para testes; fornecedores HTTP simulados para regressão. Testes reais de provedores só quando necessários, controlados, de leitura e sem carga excessiva.
- Use migrações Flyway aditivas e seguras quando necessário; nunca atribua dados financeiros fictícios a registros legados.
- Não atualize README ou crie documentação adicional, salvo pedido expresso.

## Entrega e critérios de qualidade
- Atenda aos critérios de aceite do PBI/épico e preserve compatibilidade REST sempre que possível; atualize consumidores afetados quando mudanças forem indispensáveis.
- Valide backend, frontend, build e lint conforme impacto. Declare claramente o que foi executado e o que não pôde ser validado.
- Relatório final **curto**, com: PBIs e status (concluído/parcial/pendente); alterações essenciais; migrações; testes executados e contagens; limitações/riscos. Não produza relatórios parciais por padrão.
- Não altere automaticamente a planilha de backlog, a menos que isso seja parte do pedido; em caso de atualização, preserve histórico, IDs e evidências.

## Product Backlog — Fonte Oficial de Requisitos

O Product Backlog oficial do Investment Manager está localizado em:

`docs/product-backlog.xlsx`

Regras obrigatórias:

1. Antes de implementar um épico ou PBI, consulte a planilha e identifique seus requisitos, atividades, dependências e critérios de aceite.
2. Implemente exclusivamente os PBIs solicitados, preservando as funcionalidades já concluídas.
3. Não presuma que uma funcionalidade esteja ausente sem verificar o código existente.
4. Não altere o status dos PBIs sem evidências de implementação e testes aprovados.
5. Se a planilha não estiver acessível, informe o impedimento. Não invente requisitos.
6. Siga as demais instruções deste AGENTS.md, priorizando uma execução concentrada e a economia de créditos.

O backlog é a referência dos requisitos funcionais. O código atual é a referência do comportamento efetivamente implementado.