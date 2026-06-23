# TDD - Validação da Camada de Persistência sem Docker (Testcontainers + fallback H2)

| Campo              | Valor                                                    |
| ------------------ | -------------------------------------------------------- |
| Tech Lead          | @Filipe (filipe.azvdo@gmail.com)                         |
| Time               | Filipe                                                   |
| Epic/Ticket        | KAN-4 (tarefas: KAN-12, KAN-13, KAN-14, KAN-15, KAN-16)  |
| Status             | Draft                                                    |
| Criado em          | 2026-06-22                                               |
| Última atualização | 2026-06-22                                               |
| Tipo               | Ajuste de infraestrutura de testes                       |
| Porte              | Pequeno (< 1 semana)                                     |

---

## Contexto

O **personalRouter** valida sua camada de persistência (entidades JPA + migration Flyway)
contra um **PostgreSQL real** provisionado via **Testcontainers**. Duas classes exercitam essa
camada:

- `PlannedRouteRepositoryTest` — valida que a migration Flyway aplica em um Postgres real e
  bate com as entidades (`ddl-auto=validate`), além do round-trip de persistência com paradas
  ordenadas.
- `RouteApiIntegrationTest` — teste end-to-end (controller → service → repository → Postgres
  real), com o OpenRouteService mockado por WireMock.

Ambas estão anotadas com `@Testcontainers(disabledWithoutDocker = true)`. Em máquinas **sem
Docker** — como o ambiente de desenvolvimento atual — esses testes **dão skip silencioso**: o
build fica verde, mas a camada de banco **nunca é exercitada**.

**Domínio:** infraestrutura de testes / qualidade.

**Stakeholders:** desenvolvedor (instância única). Não há time de QA ou requisitos de compliance.

---

## Definição do Problema & Motivação

### Problemas que estamos resolvendo

- **Build verde ≠ validação de banco.** Com `disabledWithoutDocker = true`, em máquina sem
  Docker os testes de persistência são pulados. Uma quebra na migration Flyway, no mapeamento
  JPA ou no contrato `ddl-auto=validate` passa despercebida localmente até alguém rodar em um
  ambiente com Docker.
  - Impacto: regressões silenciosas na camada de dados; falsa sensação de cobertura; o gate
    JaCoCo de 80% também não cobre os caminhos de persistência localmente.
- **Dependência forte de Docker para um feedback que deveria ser rápido.** A validação básica
  do schema/mapeamento não deveria exigir um daemon Docker rodando na máquina do dev.
  - Impacto: ciclo de feedback lento ou inexistente quando Docker não está disponível.

### Por que agora?

- As fases 1–6 estão concluídas e a camada de persistência está estável; é o momento de
  garantir que ela continue validada a cada build, inclusive sem Docker.
- A memória do projeto já registra explicitamente que "Testcontainers fazem skip (não falham)
  nesta máquina; build verde ≠ validação de DB" — o risco já foi observado.

### Impacto de NÃO resolver

- **Técnico:** acúmulo de risco na camada de dados; migrations ou mapeamentos quebrados só
  descobertos tardiamente.
- **Produto:** maior chance de bug em produção relacionado a schema/persistência.

---

## Escopo

### ✅ Dentro do escopo (V1)

- Introduzir um **fallback em memória (H2 em modo de compatibilidade PostgreSQL)** para a
  validação da camada de persistência quando o Docker **não** estiver disponível.
- Garantir que `PlannedRouteRepositoryTest` rode **sempre** (Postgres via Testcontainers quando
  houver Docker; H2 + Flyway quando não houver), **sem skip**.
- Aplicar a **migration Flyway** também no caminho H2, mantendo `ddl-auto=validate` para
  continuar validando o contrato schema ↔ entidades.
- Estender o fallback ao teste **end-to-end** `RouteApiIntegrationTest` (`@SpringBootTest` +
  MockMvc + WireMock), para que ele também rode **sem Docker** (KAN-16), reaproveitando a mesma
  seleção condicional de datasource.
- Documentar a estratégia de seleção do banco (Docker presente vs. ausente).

### ❌ Fora do escopo (V1)

- Configurar pipeline de CI (GitHub Actions com Docker). *(Pode ser tratado depois; o CI, quando
  existir, será a autoridade sobre Postgres real.)*
- Reuso/singleton de container para performance.
- Separação formal de unit vs. integração via perfis Maven/tags JUnit.

### 🔮 Considerações futuras (V2+)

- CI com Docker como autoridade sobre o Postgres real (recomendado assim que houver pipeline).
- Avaliar **embedded-postgres (Zonky)** como alternativa de maior fidelidade ao Postgres sem
  exigir daemon Docker, caso surjam migrations com sintaxe Postgres-only.

---

## Solução Técnica

### Visão geral

A estratégia é **dual-mode** para a validação da camada de persistência:

- **Docker disponível** → Testcontainers com `postgres:16-alpine` (alta fidelidade,
  comportamento autoritativo). Mantém o comportamento atual.
- **Docker ausente** → **H2 em modo PostgreSQL** (`MODE=PostgreSQL`) como datasource, com
  **Flyway habilitado** aplicando a mesma migration `V1__create_planned_route.sql` e
  `ddl-auto=validate` validando o mapeamento.

O ponto-chave é que o teste de repositório **nunca pula**: ele apenas troca o motor de banco
conforme a disponibilidade de Docker. A validação essencial (migration aplica + entidades batem
com o schema + round-trip de persistência com ordem das paradas) continua acontecendo nos dois
modos.

### Componentes

- **Detecção de Docker:** condição de ambiente que decide o modo. Em vez de
  `disabledWithoutDocker = true` (que pula), a ausência de Docker passa a **selecionar o
  datasource H2**.
- **Configuração de teste H2:** datasource H2 em memória com `MODE=PostgreSQL` e
  `DATABASE_TO_LOWER=TRUE` (compatibilidade de identificadores), Flyway habilitado apontando
  para `classpath:db/migration`, e `ddl-auto=validate`.
- **Configuração de teste Postgres (existente):** `PostgreSQLContainer` + `@ServiceConnection`,
  inalterada, usada quando há Docker.

### Fluxo de seleção do banco

1. Ao iniciar a classe de teste de persistência, verifica-se a disponibilidade de Docker.
2. **Com Docker:** sobe o container Postgres e conecta via `@ServiceConnection`.
3. **Sem Docker:** configura o datasource H2 (modo PostgreSQL) via propriedades dinâmicas.
4. Em ambos: Flyway aplica `V1__create_planned_route.sql`; Hibernate valida o schema
   (`ddl-auto=validate`); os testes de round-trip executam normalmente.

### Compatibilidade da migration com H2

A migration atual usa apenas construções suportadas por H2 em modo PostgreSQL, o que torna o
fallback viável **sem alterar o SQL**:

| Construção na migration             | Suporte H2 (MODE=PostgreSQL) |
| ----------------------------------- | ---------------------------- |
| `UUID PRIMARY KEY`                  | ✅                            |
| `DOUBLE PRECISION`                  | ✅                            |
| `BIGINT`                            | ✅                            |
| `TEXT`                              | ✅                            |
| `TIMESTAMP WITH TIME ZONE`          | ✅                            |
| `BIGINT GENERATED BY DEFAULT AS IDENTITY` | ✅                      |
| `... REFERENCES ... ON DELETE CASCADE`    | ✅                      |
| `CREATE INDEX`                      | ✅                            |

> **Importante:** o H2 é um **net de conveniência local**, não um substituto do Postgres. A
> fidelidade não é total — funcionalidades Postgres-only (ex.: `JSONB`, arrays, tipos
> geográficos) **não** são cobertas pelo H2. O caminho Testcontainers/Postgres continua sendo a
> referência de verdade.

### Mudanças no código de teste

- Substituir a semântica de `disabledWithoutDocker = true` por uma seleção de datasource
  baseada na presença de Docker em `PlannedRouteRepositoryTest`.
- Adicionar configuração/propriedades H2 (perfil de teste ou `@DynamicPropertySource`
  condicional).
- Adicionar dependência de teste do **H2** (`com.h2database:h2`, escopo `test`).
- Aplicar a mesma seleção condicional de datasource ao `RouteApiIntegrationTest` (KAN-16),
  reaproveitando a configuração de teste, para que o E2E também rode sem Docker. O WireMock do
  ORS permanece inalterado.

---

## Riscos

| Risco                                                                 | Impacto | Probabilidade | Mitigação                                                                                                  |
| --------------------------------------------------------------------- | ------- | ------------- | ---------------------------------------------------------------------------------------------------------- |
| **Divergência de comportamento H2 ↔ Postgres** dá falso positivo/negativo | Médio   | Média         | Usar H2 em `MODE=PostgreSQL`; manter Postgres/Testcontainers como autoridade quando há Docker; deixar claro que H2 é convenience net. |
| **Migration futura usa sintaxe Postgres-only** e quebra só no H2      | Médio   | Média         | Documentar a limitação; ao adotar recurso Postgres-only, reavaliar (ex.: migrar para embedded-postgres/Zonky ou marcar o caminho H2 como não suportado). |
| **Complexidade da lógica condicional** (Docker vs. H2) torna o teste frágil | Baixo   | Média         | Centralizar a seleção em uma única configuração de teste reutilizável; manter simples.                     |
| **Falsa sensação de cobertura total** (achar que H2 cobre tudo)       | Médio   | Média         | Comunicar no próprio teste/README que H2 valida schema/mapeamento, não o comportamento Postgres completo.  |

---

## Plano de Implementação

| Fase                | Tarefa                          | Descrição                                                                                  | Estimativa |
| ------------------- | ------------------------------- | ------------------------------------------------------------------------------------------ | ---------- |
| **Fase 1 – Setup**  | Dependência H2                  | Adicionar `com.h2database:h2` em escopo `test` no `pom.xml`.                                | 0,5d       |
| **Fase 2 – Config** | Configuração H2 (modo Postgres) | Datasource H2 `MODE=PostgreSQL`, Flyway habilitado, `ddl-auto=validate`.                    | 0,5d       |
|                     | Seleção condicional do banco    | Substituir `disabledWithoutDocker` por seleção Postgres/H2 conforme disponibilidade de Docker. | 1d      |
| **Fase 3 – Testes** | Ajustar `PlannedRouteRepositoryTest` | Garantir que roda nos dois modos sem skip; validar round-trip e ordem das paradas.    | 1d         |
|                     | Estender ao E2E (`RouteApiIntegrationTest`) | Reaproveitar a seleção condicional no teste end-to-end; rodar sem Docker (KAN-16). | 1d         |
|                     | Verificação cruzada             | Rodar suíte com e sem Docker; confirmar que a camada de DB é exercitada sem Docker.        | 0,5d       |
| **Fase 4 – Docs**   | Atualizar README                | Documentar a estratégia dual-mode e a ressalva de fidelidade do H2.                        | 0,5d       |

**Estimativa total:** ~5 dias (< 1 semana)

**Dependências:** Fase 1 → 2 → 3. Documentação (Fase 4) após validação.

---

## Estratégia de Testes

A própria mudança é sobre testes; o critério de aceite é comportamental:

| Cenário de verificação                          | Resultado esperado                                                        |
| ----------------------------------------------- | ------------------------------------------------------------------------- |
| `mvn verify` **sem** Docker                     | `PlannedRouteRepositoryTest` **e** `RouteApiIntegrationTest` **executam** (não pulam) usando H2; testes passam. |
| `mvn verify` **com** Docker                     | Ambos executam usando Postgres/Testcontainers; testes passam. |
| Flyway aplica no H2                             | Migration `V1` aplica sem erro; `ddl-auto=validate` não reclama do schema. |
| Round-trip de persistência (H2)                | Salva e recarrega rota com paradas na ordem correta; cascade ao excluir.  |
| Regressão proposital na migration ou entidade  | Build **falha** mesmo sem Docker (rede de segurança ativa).               |

**Validação manual chave:** introduzir temporariamente uma divergência (ex.: renomear uma coluna
na entidade sem migration) e confirmar que o build **falha localmente sem Docker** — provando que
o net de validação está ativo.

---

## Notas

- Este TDD assume **uso pessoal / instância única**: sem requisitos de segurança, monitoramento
  ou rollback formais.
- O **CI com Docker** (autoridade sobre Postgres real) fica como recomendação para V2+ e não
  bloqueia esta entrega.
