# TDD - Praças de Pedágio: Ingestão por CSV + Pedágios no Trajeto

| Campo              | Valor                                                        |
| ------------------ | ------------------------------------------------------------ |
| Tech Lead          | @Filipe (filipe.azvdo@gmail.com)                             |
| Time               | Filipe                                                       |
| Epic/Ticket        | [KAN-18](https://filipeazvdo.atlassian.net/browse/KAN-18) — Stories KAN-19 / KAN-20 |
| Status             | Draft                                                        |
| Criado em          | 2026-06-23                                                   |
| Última atualização | 2026-06-23                                                   |
| Tipo               | Nova funcionalidade (nova tabela + extensão de roteamento)   |
| Porte              | Médio (2-3 semanas)                                          |

---

## Contexto

O **personalRouter** calcula e armazena rotas via **OpenRouteService (ORS)**, devolvendo distância,
duração, geometria (polyline codificada) e detalhamento por segmento. Hoje a aplicação **não tem
noção de pedágios**: uma rota São Paulo → Rio é devolvida sem qualquer informação sobre as praças
de pedágio que o trajeto cruza.

Este TDD cobre **duas features encadeadas** em torno de praças de pedágio:

- **Parte A — Ingestão (CSV):** um novo serviço/controller para carregar a base de praças de
  pedágio do Brasil a partir de um arquivo CSV, mantendo uma tabela própria sincronizada com o
  conteúdo do arquivo (insere/atualiza o que está no CSV, **remove logicamente** o que não está).
- **Parte B — Pedágios no trajeto:** ao **simular** (`planRoute`), **criar** (`createRoute`) ou
  **consultar por id** (`getRoute`) uma rota, devolver em um **novo campo do JSON** a lista de
  praças de pedágio que caem **sobre o traçado** (geometry/polyline) daquela rota.

A Parte B depende dos dados carregados pela Parte A.

**Insumo de dados:** `assets/csv/example.csv` — base real com 262 praças, delimitador `;`, 13
colunas:
`concessionaria;praca_de_pedagio;ano_do_pnv_snv;rodovia;uf;km_m;municipal;tipo_de_pista;sentido;situacao;data_da_inativacao;latitude;longitude`.
O **contrato de upload é UTF-8** (validado); o `example.csv` versionado já está em UTF-8 e serve de
referência canônica / fixture dos testes.

**Domínio:** planejamento/roteamento pessoal. Uso pessoal / instância única.

**Stakeholders:** desenvolvedor (instância única). Sem time de produto ou requisitos de compliance.

---

## Definição do Problema & Motivação

### Problemas que estamos resolvendo

- **A rota não informa custos/pontos de pedágio.** Quem planeja um trajeto não sabe quais praças
  vai cruzar — informação central para estimar custo e escolher caminho.
  - Impacto: a rota é geometricamente correta, mas "cega" para pedágios; o usuário precisa
    cruzar manualmente o trajeto com uma planilha de praças.
- **Não existe uma base de praças de pedágio na aplicação.** Os dados existem como um CSV solto
  (`assets/csv`); não há tabela, nem forma de atualizá-la quando a fonte muda (novas praças,
  desativações).
  - Impacto: sem ingestão, a Parte B não tem o que consultar; e sem um processo de sincronização,
    a base envelhece silenciosamente.

### Por que agora?

- A base de pedágios (`example.csv`) **já está no repositório** e a rota já carrega a **geometria**
  necessária para o casamento espacial — os dois insumos da feature já existem.
- É a evolução natural do produto: sai de "rota geométrica" para "rota com informação de pedágio".

### Impacto de NÃO resolver

- **Produto:** a ferramenta continua sem o dado mais pedido para planejamento rodoviário no Brasil
  (pedágio); fica limitada a distância/tempo.
- **Técnico:** a base de praças permanece um arquivo morto; qualquer cálculo de pedágio teria de
  ser feito fora da aplicação.

---

## Escopo

### ✅ Dentro do escopo (V1)

**Parte A — Ingestão CSV**

- Endpoint de upload (`multipart/form-data`) que recebe **um arquivo CSV** no padrão do
  `example.csv` e **sincroniza** a tabela `toll_plaza`:
  - **Insere/atualiza** (upsert) cada praça presente no arquivo;
  - **Remove logicamente** (soft delete) cada praça **ausente** do arquivo (reconciliação:
    a base ativa passa a refletir exatamente o conteúdo do CSV).
- **Processamento assíncrono** (o arquivo pode crescer muito): o endpoint faz a **validação
  estrutural rápida** (encoding, vazio, cabeçalho, idempotência) de forma síncrona e, se ok,
  **aceita o job** (`202 Accepted` + `importId`/status) e processa o parse pesado + reconciliação
  **em background**. O resultado é consultado por um **endpoint de status**.
- **Idempotência por conteúdo**: uma **chave de idempotência = hash do arquivo** (ex.: SHA-256 dos
  bytes) evita reprocessar o mesmo arquivo. Se um import com o mesmo hash já está em andamento ou
  concluído, a requisição **retorna o import existente** em vez de reprocessar.
- **Validação do arquivo** antes de aceitar o job (nenhuma escrita ocorre se falhar):
  - **Encoding**: o arquivo deve ser **UTF-8 válido** → arquivo em outro encoding (ou bytes UTF-8
    inválidos) é negado;
  - Arquivo **vazio** → requisição negada;
  - Cabeçalho/formato fora do padrão do `example.csv` (as **13 colunas** esperadas, na ordem, com
    delimitador `;`) → requisição negada. As colunas `situacao` e `data_da_inativacao` **precisam
    existir** no cabeçalho (validação estrutural), mas **não são persistidas**.
- **Resumo + relatório de erros** disponível no status do import: contagens (inseridas,
  reativadas, atualizadas, desativadas, total) e **erros por linha** (linhas individualmente
  inválidas são reportadas, não derrubam o lote — ver Decisão #7).

**Parte B — Pedágios no trajeto**

- Novo campo na resposta (ex.: `tollPlazas`) em **`RouteResultDto`** (usado por `planRoute`) e
  **`PlannedRouteDto`** (usado por `createRoute` e `getRoute`), listando as praças **ativas** que
  caem sobre o traçado da rota (dentro de uma faixa/buffer da polyline).
- Decodificação da **polyline codificada** do ORS para a sequência de coordenadas do traçado.
- Pré-filtro por **bounding box** + cálculo de proximidade ponto↔traçado em aplicação (sem
  dependência de extensão geográfica no banco — compatível com o dual-mode H2/Postgres).

### ❌ Fora do escopo (V1)

- **Cálculo de tarifa/custo** de pedágio (valor em R$). A Parte B identifica *quais* praças, não
  *quanto* custam.
- **Sensibilidade a sentido/direção** da praça (`sentido`): em V1 o casamento é puramente
  geométrico (proximidade do traçado), sem checar se o sentido da praça bate com o da viagem.
- **PostGIS / tipos geográficos** no banco (mantém o dual-mode H2 viável; ver Alternativas).
- **UI** de upload — o projeto é backend/REST; o arquivo vai por chamada à API.
- Histórico/auditoria de versões da base de pedágios (apenas o estado atual + soft delete).

### 🔮 Considerações futuras (V2+)

- Tarifas por praça/categoria de veículo e **custo total** da rota.
- Casamento sensível ao **sentido** e à **rodovia** (reduz falsos positivos em vias paralelas).
- Migrar o casamento espacial para **PostGIS** (`ST_DWithin`) quando a base/volume crescer.
- Snapshot dos pedágios no momento da criação da rota (V1 decidiu pelo cálculo "ao vivo" — ver
  Decisões #3).

---

## Solução Técnica

### Visão geral

```
                ┌───────────────────────────── Parte A (ingestão, assíncrona) ─────────────────────────┐
POST .../import │  TollPlazaController → validação rápida (encoding/cabeçalho/vazio) + hash(conteúdo)   │
(multipart CSV) │        │  hash já visto? → devolve import existente (idempotência)                    │
   202 + id     │        └─ cria TollPlazaImport (PENDING) → @Async worker:                             │
                │              CsvParser → reconciliação (TollPlazaRepository: upsert + soft delete)     │
                │              → atualiza job (SUCCESS/FAILED + resumo + erros por linha)                │
GET .../import/{id} ─────────► status + resumo do job                                                   │
                └────────────────────────────────────────────────────────────────────────────────────────┘

                ┌───────────────────────────── Parte B (trajeto) ──────────────────────────────┐
plan/create/get │  RouteService → TollMatchingService( geometry, toll_plaza ativos )            │
   de rota       │                    ├─ PolylineDecoder (polyline → [lat,lon])                  │
                │                    ├─ bounding box → TollPlazaRepository.findActiveWithinBbox  │
                │                    └─ distância ponto↔traçado ≤ buffer → tollPlazas[]          │
                └───────────────────────────────────────────────────────────────────────────────┘
```

### Parte A — Ingestão CSV

**Endpoints**

| Endpoint                              | Método | Corpo / Param                  | Resposta                                          |
| ------------------------------------- | ------ | ------------------------------ | ------------------------------------------------- |
| `/api/v1/toll-plazas/import`          | POST   | `multipart/form-data` (`file`) | `202 Accepted` + `{ importId, status, contentHash }` (ou import existente, se hash repetido) |
| `/api/v1/toll-plazas/imports/{id}`    | GET    | —                              | `200 OK` + status do job (estado, resumo, erros)  |

**Componentes**

- **`TollPlazaController`** — recebe o `MultipartFile`; dispara a validação rápida + criação do job
  e devolve `202`; expõe a consulta de status.
- **`TollPlazaImportService`** — validação síncrona (encoding/cabeçalho/vazio), cálculo do **hash**,
  checagem de **idempotência**, criação do `TollPlazaImport` e disparo do worker **assíncrono**.
- **Worker assíncrono** — parse + reconciliação (transacional) e atualização do job
  (estado/resumo/erros). Roda em um **executor dedicado single-thread**
  (`@Async("tollImportExecutor")`) que **serializa** os imports (1 por vez) para evitar
  reconciliações concorrentes; **não** usa o executor virtual-thread global (ilimitado). Ver
  Decisão #10 e Riscos.
- **Parser CSV** — biblioteca de CSV (ex.: Apache Commons CSV / OpenCSV) configurada com
  delimitador `;` e charset **UTF-8** (com **validação de encoding**: decodificação UTF-8 estrita,
  rejeitando bytes inválidos); valida o cabeçalho contra as 13 colunas esperadas.
- **`TollPlaza` (entidade)** + **`TollPlazaRepository`** (Spring Data JPA).
- **`TollPlazaImport` (entidade)** + **`TollPlazaImportRepository`** — rastreia o job (hash único,
  estado, contagens, erros).
- **Migrations Flyway** `V2__create_toll_plaza.sql` e `V3__create_toll_plaza_import.sql`.

**Fluxo (síncrono → assíncrono)**

1. **Síncrono (na requisição):** validar encoding UTF-8, não-vazio e cabeçalho (13 colunas);
   calcular `contentHash` (SHA-256 dos bytes). Falhou? → **4xx**, nenhuma escrita.
2. **Idempotência:** se já existe `TollPlazaImport` com o mesmo `contentHash` em estado
   `PENDING`/`PROCESSING`/`SUCCESS` → **não reprocessa**; devolve o import existente.
3. Caso contrário, cria `TollPlazaImport(status=PENDING, contentHash)` e devolve **`202`** com o
   `importId`.
4. **Assíncrono (worker):** parse linha a linha; linhas inválidas vão para o **relatório de erros**
   (não abortam o lote). Em transação:
   - **upsert** por **chave natural `(rodovia, km_m, sentido)`**, marcando como **ativa** (reativa
     se estava soft-deleted);
   - praças ativas **não vistas** nesta carga → **soft delete** (`active=false`).
5. Atualiza o job: `status=SUCCESS|FAILED`, resumo `{ inseridas, reativadas, atualizadas,
   desativadas, totalNoArquivo }` e a lista de erros por linha.

> A "remoção lógica" é dirigida pela **ausência no arquivo** (reconciliação). As colunas
> `situacao`/`data_da_inativacao` do CSV são **exigidas na validação estrutural** do cabeçalho, mas
> **não são persistidas** (Decisão #4).

**Tabela nova `toll_plaza`** (campos derivados do CSV + controle):

| Coluna           | Origem CSV            | Tipo            | Observação                          |
| ---------------- | --------------------- | --------------- | ----------------------------------- |
| `id`             | —                     | UUID/BIGINT     | PK                                  |
| `concessionaria` | `concessionaria`      | VARCHAR         |                                     |
| `nome`           | `praca_de_pedagio`    | VARCHAR         |                                     |
| `ano_pnv_snv`    | `ano_do_pnv_snv`      | INT             |                                     |
| `rodovia`        | `rodovia`             | VARCHAR         | **chave natural**                   |
| `uf`             | `uf`                  | VARCHAR(2)      |                                     |
| `km_m`           | `km_m`                | NUMERIC         | **chave natural**                   |
| `municipio`      | `municipal`           | VARCHAR         |                                     |
| `tipo_pista`     | `tipo_de_pista`       | VARCHAR         |                                     |
| `sentido`        | `sentido`             | VARCHAR         | **chave natural**                   |
| `latitude`       | `latitude`            | DOUBLE          | indexado (bbox)                     |
| `longitude`      | `longitude`           | DOUBLE          | indexado (bbox)                     |
| `active`         | —                     | BOOLEAN         | soft delete (reconciliação)         |
| `created_at`     | —                     | TIMESTAMPTZ     |                                     |
| `updated_at`     | —                     | TIMESTAMPTZ     |                                     |

> As colunas `situacao` e `data_da_inativacao` existem no CSV e são **validadas** no cabeçalho, mas
> **não têm coluna** em `toll_plaza` (decisão: não persistir).

Índices: **único em `(rodovia, km_m, sentido)`** (chave natural) e `(latitude, longitude)` para o
pré-filtro espacial da Parte B.

**Tabela nova `toll_plaza_import`** (rastreamento do job assíncrono + idempotência):

| Coluna         | Tipo            | Observação                                                        |
| -------------- | --------------- | ----------------------------------------------------------------- |
| `id`           | UUID            | PK = `importId`                                                   |
| `content_hash` | VARCHAR(64)     | SHA-256 do arquivo — **único** (chave de idempotência)            |
| `status`       | VARCHAR         | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`                    |
| `inserted`     | INT             | contagem (preenchida ao concluir)                                 |
| `reactivated`  | INT             |                                                                   |
| `updated`      | INT             |                                                                   |
| `deactivated`  | INT             |                                                                   |
| `total_rows`   | INT             | total de linhas de dados no arquivo                               |
| `errors`       | TEXT/JSON       | relatório de linhas inválidas (linha + motivo)                    |
| `created_at`   | TIMESTAMPTZ     |                                                                   |
| `finished_at`  | TIMESTAMPTZ     | nullable                                                          |

### Parte B — Pedágios no trajeto

**Componentes**

- **`PolylineDecoder`** — decodifica a polyline codificada do ORS (algoritmo Google, **precisão 5**)
  para `List<Coordinate>`. Atenção à **ordem das coordenadas** do ORS (lat/lon) — coberto por teste.
- **`TollMatchingService`** — dada a geometria de uma rota:
  1. decodifica a polyline;
  2. calcula o **bounding box** do traçado (+ margem do buffer);
  3. consulta `TollPlazaRepository.findActiveWithinBbox(minLat,maxLat,minLon,maxLon)`;
  4. para cada candidata, calcula a **distância mínima ponto↔segmentos** do traçado (Haversine);
  5. inclui as praças com distância ≤ **buffer de até 500 m**, ordenadas pela posição ao longo da
     rota.
- **`TollPlazaDto`** — projeção devolvida (nome, concessionária, rodovia, uf, km, sentido, lat, lon).
- **Integração** em `RouteService`: `planRoute`/`createRoute`/`getRoute` chamam o
  `TollMatchingService` e preenchem o novo campo `tollPlazas`.

**Mudanças de contrato (campo novo, aditivo)**

`RouteResultDto` e `PlannedRouteDto` ganham `List<TollPlazaDto> tollPlazas`.

```json
// 200 OK — POST /api/v1/routes/plan  (trecho)
{
  "profile": "driving-car",
  "distanceMeters": 451230,
  "durationSeconds": 21600,
  "geometry": "…polyline…",
  "segments": [ /* … */ ],
  "tollPlazas": [
    {
      "nome": "3 (Cambuí)",
      "concessionaria": "AUTOPISTA FERNÃO DIAS",
      "rodovia": "BR-381",
      "uf": "MG",
      "km": 900.9,
      "sentido": "Crescente/Decrescente",
      "latitude": -22.628487,
      "longitude": -46.07789
    }
  ]
}
```

> Para `getRoute`, a rota já tem a `geometry` persistida; o casamento é recalculado **ao vivo**
> contra a base de pedágios atual (Decisão #3 — reflete sempre a base mais recente).

### Banco de dados

- **Novas migrations** `V2__create_toll_plaza.sql` (praças + índices) e
  `V3__create_toll_plaza_import.sql` (jobs de import, com `content_hash` único).
- `planned_route` **não muda** — a Parte B lê a `geometry` existente; nada é persistido na rota
  (cálculo ao vivo, Decisão #3).
- O pré-filtro usa apenas `BETWEEN` em `latitude`/`longitude` (sem tipos geográficos) → **compatível
  com o dual-mode H2/Postgres** já existente.

---

## Riscos

| Risco                                                                                      | Impacto | Probabilidade | Mitigação                                                                                                                              |
| ------------------------------------------------------------------------------------------ | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Arquivo enviado não-UTF-8** (ou bytes UTF-8 inválidos) corrompe acentos/dados                | Médio   | Média         | Contrato é UTF-8 + **validação de encoding** (decodificação estrita) rejeitando o arquivo na entrada.                                  |
| **CSV vazio/malformado zera a base** via reconciliação (soft delete em massa)               | Alto    | Média         | **Validar antes de aceitar o job**: rejeitar vazio e cabeçalho fora do padrão; reconciliação só roda no worker após parse válido; tudo transacional. |
| **Chave natural ambígua** → duplicatas ou soft-delete indevido                             | Alto    | Média         | Chave `(rodovia, km_m, sentido)` com índice único; teste de re-importação idempotente.                                                |
| **Imports concorrentes** corrompem a reconciliação (soft delete cruzado)                    | Alto    | Baixa         | Executor **serializa** imports (1 por vez); reconciliação transacional; idempotência por hash evita disparos duplicados.              |
| **Job perdido em restart** (worker em memória) deixa import `PROCESSING` órfão              | Médio   | Média         | Estado persistido em `toll_plaza_import`; na subida, marcar `PROCESSING` órfão como `FAILED` (reenvio é idempotente pelo hash).        |
| **Idempotência prende re-aplicação legítima** de um arquivo já processado                   | Baixo   | Baixa         | Escopo do dedup definido (Decisão #9); reprocessamento explícito fica para V2 (`force`).                                              |
| **Ordem de coordenadas da polyline** invertida (lat/lon) → nenhum/wrong match              | Alto    | Média         | Teste do `PolylineDecoder` contra uma polyline conhecida; validar 1º ponto ≈ origem da rota.                                           |
| **Buffer mal calibrado** → falsos positivos (vias paralelas) ou praças não detectadas       | Médio   | Média         | Buffer default conservador + teste E2E com rota conhecida (ex.: SP→RJ pela Dutra) conferindo praças esperadas.                        |
| **Performance** do casamento (varredura por rota)                                          | Baixo   | Baixa         | Pré-filtro por bounding box com índice em lat/lon; base pequena (~260 praças).                                                         |
| **Regressão no contrato de rota** ao adicionar `tollPlazas`                                 | Baixo   | Baixa         | Campo **aditivo**; manter testes atuais verdes; clientes que ignoram campos novos não quebram.                                        |

---

## Plano de Implementação

| Fase                          | Tarefa                                | Descrição                                                                                          | Estimativa |
| ----------------------------- | ------------------------------------- | -------------------------------------------------------------------------------------------------- | ---------- |
| **Fase 1 – Modelo/migração**  | Entidades + migrations `V2`/`V3`      | `TollPlaza` + `TollPlazaImport`, repositórios, `V2`/`V3` migrations, validação dual-mode (H2/Postgres). | 1,5d   |
| **Fase 2 – Ingestão (A)**     | Validação + parser CSV                | Validação síncrona (encoding UTF-8, cabeçalho 13 colunas, vazio) + parser `;`; relatório de erros por linha. | 1d  |
|                               | Async + idempotência                  | Hash SHA-256, dedup por `content_hash`, worker `@Async` serializado, transições de estado do job, `202` + status. | 1,5d |
|                               | Reconciliação + endpoints             | Upsert por chave natural + soft delete dos ausentes; `POST /import` (multipart) + `GET /imports/{id}`. | 1d      |
| **Fase 3 – Trajeto (B)**      | Decoder de polyline                   | `PolylineDecoder` (precisão 5, ordem ORS) + testes.                                                 | 0,5d       |
|                               | Casamento espacial                    | Bbox + distância ponto↔traçado (Haversine) + buffer 500 m → `tollPlazas`.                           | 1d         |
|                               | Integração nos DTOs/serviço           | Campo `tollPlazas` em `RouteResultDto`/`PlannedRouteDto`; ligar em plan/create/get.                 | 0,5d       |
| **Fase 4 – Testes & Docs**    | Testes unitários + integração E2E     | Parser, idempotência, async, reconciliação (dual-mode), decoder, casamento; E2E upload→rota com WireMock. | 2d   |
|                               | README + Swagger/OpenAPI              | Documentar `POST /import` (202) + `GET /imports/{id}`, campo `tollPlazas`, ressalvas (sem custo/sentido em V1). | 0,5d  |

**Estimativa total:** ~11 dias (~2-3 semanas). **Dependências:** Fase 1 → 2 e Fase 1 → 3; Parte B
depende de dados carregados pela Parte A para os testes E2E. Todas as decisões de design estão
fechadas — sem pendências bloqueantes.

---

## Estratégia de Testes

O projeto é **test-first** com persistência **dual-mode** (Testcontainers/Postgres + fallback H2) e
integração mockada com **WireMock**. Cenários:

| Cenário                                                              | Resultado esperado                                                                  |
| ------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Upload de CSV válido (1ª carga)                                      | `202` + `importId`; job evolui `PENDING`→`SUCCESS`; todas as praças inseridas/ativas; resumo bate. |
| Re-upload do **mesmo** arquivo (mesmo `content_hash`)               | **Idempotência de requisição**: devolve o import existente; **nenhum job novo / nenhum reprocesso**. |
| Re-upload com **conteúdo alterado** (hash diferente)                | Novo job processa normalmente.                                                      |
| Linha **individualmente inválida** (ex.: lat/lon não numérica)      | Linha entra no **relatório de erros**; demais linhas processadas; job `SUCCESS`.    |
| Re-upload **sem** uma praça antes presente                          | Aquela praça vira `active=false` (soft delete); demais intactas.                    |
| Re-upload **reintroduzindo** praça soft-deleted                     | Praça **reativada** (`active=true`), sem duplicar registro.                          |
| CSV **vazio**                                                        | Requisição **negada** (4xx); **nenhuma** alteração na base.                          |
| CSV com **cabeçalho/colunas fora do padrão** (faltando qualquer das 13, incl. `situacao`) | Requisição **negada** (4xx); nenhuma escrita.                              |
| Arquivo **UTF-8** com acentos                                       | Persistido como `FERNÃO`/`Cambuí` (sem mojibake).                                   |
| Arquivo **não-UTF-8** (ex.: Latin-1) ou bytes UTF-8 inválidos       | Requisição **negada** (4xx) na validação de encoding; nenhuma escrita.              |
| `situacao`/`data_da_inativacao` presentes no arquivo                | Lidas para validação estrutural, **não** persistidas em `toll_plaza`.               |
| `PolylineDecoder` com polyline conhecida                            | Sequência de coordenadas correta; 1º ponto ≈ origem.                                 |
| Praça **sobre** o traçado (dentro do buffer)                        | Aparece em `tollPlazas`.                                                             |
| Praça **distante** do traçado                                       | **Não** aparece em `tollPlazas`.                                                     |
| `planRoute` / `createRoute` / `getRoute`                            | Todos devolvem `tollPlazas` coerente com a geometria.                                |
| Regressão de rota (sem pedágios na área)                            | `tollPlazas` vazio; demais campos inalterados; testes atuais verdes.                |

**Validação manual chave:** upload do `example.csv` e cálculo de uma rota interestadual conhecida
(ex.: SP→RJ pela BR-116/Dutra), conferindo que as praças esperadas aparecem em `tollPlazas`.

---

## Alternativas Consideradas

| Decisão                     | Escolha (V1)                                   | Alternativa                          | Por que não agora                                                                 |
| --------------------------- | ---------------------------------------------- | ------------------------------------ | --------------------------------------------------------------------------------- |
| Casamento espacial          | **Cálculo em aplicação** (bbox + Haversine)    | PostGIS `ST_DWithin`                 | PostGIS quebraria o dual-mode H2; base pequena não exige índice geográfico.        |
| Parse de CSV                | **Biblioteca** (Commons CSV / OpenCSV)         | Split manual por `;`                 | Lib trata aspas/encoding/escape com menos bugs; custo de dependência é baixo.      |
| Pedágios em `getRoute`      | **Recalcular ao vivo** a partir da `geometry`  | Persistir snapshot na criação        | Ao vivo reflete a base atual sem migration na `planned_route` (Decisão #3).         |
| Remoção                     | **Soft delete** (reconciliação por ausência)   | Hard delete                          | Requisito do usuário é remoção lógica; preserva histórico/reativação.              |
| Processamento do import     | **`@Async` + tabela de job** (estado no banco) | Fila/broker (Redis, RabbitMQ, SQS)   | Single-instance/uso pessoal; tabela de job dá durabilidade sem nova infra. V2.     |
| Idempotência                | **Hash do conteúdo** (SHA-256) como chave      | Token de idempotência no header      | A dedup deve ser pelo *conteúdo* (mesmo arquivo = mesma carga), não por cliente.   |

---

## Decisões & Questões em Aberto

| #   | Questão                                                                                         | Decisão / Status                                                                     |
| --- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| 1   | **Chave natural** de uma praça para upsert/reconciliação                                         | ✅ `(rodovia, km_m, sentido)` — índice único (2026-06-23).                            |
| 2   | **Buffer** (m) para considerar a praça "sobre o traçado"                                         | ✅ Até **500 m** (2026-06-23).                                                        |
| 3   | Pedágios em `getRoute`: ao vivo ou snapshot na criação                                           | ✅ **Ao vivo** (sem migration na `planned_route`) (2026-06-23).                       |
| 4   | Mapear `situacao`/`data_da_inativacao` do CSV?                                                   | ✅ **Validar no cabeçalho, não persistir** (2026-06-23).                              |
| 5   | Encoding do arquivo enviado                                                                      | ✅ **UTF-8** + validação de encoding; `example.csv` já em UTF-8 (2026-06-23).         |
| 6   | Casar por **sentido**/rodovia para reduzir falsos positivos                                      | 🟡 V2 — V1 é casamento puramente geométrico.                                          |
| 7   | Import: falhar 4xx vs. relatório parcial em linhas individualmente inválidas                     | ✅ **Erro estrutural → 4xx**; linha inválida vai no **relatório de erros** do job, sem abortar o lote (2026-06-23). |
| 8   | **Processamento do import**                                                                       | ✅ **Assíncrono** (`202` + job; worker `@Async` serializado), estado em `toll_plaza_import` (2026-06-23). |
| 9   | **Escopo da idempotência** por hash de conteúdo                                                   | ✅ Dedup contra import `PENDING`/`PROCESSING`/`SUCCESS`; reprocesso explícito (`force`) fica p/ V2 (2026-06-23). |
| 10  | **Virtual threads** (Java 21 + Spring Boot 3.4.6)                                                 | ✅ Habilitar `spring.threads.virtual.enabled=true` (web + Feign/ORS); worker de import em **executor dedicado single-thread**, fora do executor VT global, para preservar a serialização (2026-06-23). |

---

## Notas

- Este TDD assume **uso pessoal / instância única**: sem requisitos formais de segurança,
  monitoramento ou rollback. A Parte B é **aditiva** (campo novo) e compatível com o contrato atual.
- A reconciliação é **destrutiva por design** (soft delete em massa quando uma praça some do
  arquivo); por isso a **validação prévia** (vazio/cabeçalho) é a salvaguarda crítica — está como
  primeiro risco e primeiro passo do fluxo.
- A escolha de **não usar PostGIS** preserva a estratégia dual-mode de validação de banco
  (Testcontainers + H2) já documentada no README.
- **Encoding:** o contrato de upload é **UTF-8** com validação estrita; o `assets/csv/example.csv`
  versionado já está em UTF-8 e serve de fixture/referência dos testes E2E.
- **Async + idempotência:** a validação estrutural (encoding/cabeçalho/vazio) e o cálculo do hash
  são **síncronos** (resposta rápida e garantia de "nenhuma escrita em arquivo inválido"); o parse
  pesado e a reconciliação rodam **em background**, com estado durável em `toll_plaza_import`. A
  serialização dos imports (1 por vez) é a salvaguarda contra reconciliações concorrentes.
- **Virtual threads (Java 21 + Spring Boot 3.4.6):** habilitar com
  `spring.threads.virtual.enabled=true` — beneficia o **web layer** (Tomcat) e as chamadas I/O ao
  **ORS (Feign)**. ⚠️ O worker de import **não** deve herdar o executor virtual-thread global
  (ilimitado): usa um **executor single-thread dedicado** para preservar a serialização da
  reconciliação (Decisão #10).
- Todas as decisões de design estão **fechadas** (tabela de Decisões) — o TDD está pronto para
  revisão/implementação.
