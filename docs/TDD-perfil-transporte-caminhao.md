# TDD - Novo Perfil de Transporte: Caminhão (driving-hgv)

| Campo              | Valor                                          |
| ------------------ | ---------------------------------------------- |
| Tech Lead          | @Filipe (filipe.azvdo@gmail.com)               |
| Time               | Filipe                                         |
| Epic/Ticket        | TBD (projeto KAN)                              |
| Status             | Draft                                          |
| Criado em          | 2026-06-22                                     |
| Última atualização | 2026-06-22                                     |
| Tipo               | Nova funcionalidade (extensão de integração)   |
| Porte              | Pequeno (< 1 semana)                           |

---

## Contexto

O **personalRouter** calcula rotas consumindo o **OpenRouteService (ORS)**, que expõe o perfil
de transporte como parâmetro de path em `POST /v2/directions/{profile}`. Hoje a aplicação
suporta **um único perfil**: `driving-car` (carro de passeio).

O perfil atravessa todas as camadas como uma `String` livre:

- **Entrada:** `RoutePlanRequest.profile`, validado por `@Pattern(regexp = "driving-car")`
  (`dto/RoutePlanRequest.java`).
- **Serviço:** `RouteServiceImpl.resolveProfile(...)` aplica o default `driving-car` quando o
  campo vem nulo/vazio (`DEFAULT_PROFILE`).
- **Gateway/Client:** `OpenRouteServiceGateway.getDirections(profile, ...)` repassa o perfil
  como path variable para o ORS (`/v2/directions/{profile}`).
- **Persistência:** `PlannedRoute.profile` é uma coluna `TEXT` (string livre) — sem enum nem
  constraint de banco.
- **Saída:** `RouteResultDto.profile` e `PlannedRouteDto.profile` devolvem o perfil usado.

**Domínio:** planejamento/roteamento pessoal. Uso pessoal / instância única.

**Stakeholders:** desenvolvedor (instância única). Sem time de produto ou requisitos de
compliance.

---

## Definição do Problema & Motivação

### Problemas que estamos resolvendo

- **A API só sabe rotear carro de passeio.** Rotas para caminhão diferem de forma relevante:
  o ORS, no perfil `driving-hgv` (Heavy Goods Vehicle), respeita restrições viárias de veículos
  pesados (vias proibidas para caminhão, pontes/túneis com limites) que o `driving-car` ignora.
  - Impacto: rotas inadequadas/ilegais para quem precisa planejar trajetos de caminhão; a
    distância e o tempo estimados não refletem a realidade do veículo pesado.
- **A validação de perfil está "hard-coded" para um único valor.** O `@Pattern(regexp =
  "driving-car")` rejeita qualquer outro perfil com 400, então hoje não há como evoluir o
  conjunto de perfis sem mexer na regra de validação.
  - Impacto: cada novo perfil exige alterar a anotação e os testes; não há um ponto único de
    verdade sobre "quais perfis são suportados".

### Por que agora?

- O perfil de transporte adicional é o próximo item natural do roadmap V2+ (README) e a base
  end-to-end (`{profile}` no ORS, coluna `profile` no banco) **já existe** — falta apenas
  liberar o valor e validá-lo.
- O custo é baixo: a mudança é majoritariamente de validação e configuração, sem migration de
  banco (a coluna `profile` já é string livre).

### Impacto de NÃO resolver

- **Produto:** quem planeja rotas de caminhão continua sem suporte; a ferramenta fica limitada
  a um único caso de uso.
- **Técnico:** a regra de validação engessada continua a acoplar "perfis suportados" a uma
  anotação espalhada, dificultando futuras extensões (bicicleta, a pé etc.).

---

## Escopo

### ✅ Dentro do escopo (V1)

- Passar a aceitar o perfil **`driving-hgv`** (caminhão) **além** do `driving-car` existente, no
  endpoint de cálculo/salvamento de rotas.
- **Centralizar** o conjunto de perfis suportados em um único ponto de verdade (em vez do regex
  literal espalhado na anotação), de modo que a mensagem de erro liste os perfis válidos.
- Manter o **default** `driving-car` quando o campo `profile` vier nulo/vazio (compatibilidade
  retroativa — nenhum cliente atual quebra).
- Persistir e devolver corretamente `driving-hgv` em `PlannedRoute.profile` /
  `RouteResultDto.profile` / `PlannedRouteDto.profile` (sem migration; a coluna já é `TEXT`).
- Atualizar testes (unitários, controller, integração E2E com WireMock) e a documentação
  (README + Swagger/OpenAPI) para refletir os dois perfis.

### ❌ Fora do escopo (V1)

- **Restrições de veículo pesado** (peso, altura, largura, comprimento, carga por eixo, cargas
  perigosas). Hoje `OrsDirectionsRequest` envia apenas `coordinates` + `instructions`; o ORS
  recebe `driving-hgv` com os **defaults** de caminhão. Os parâmetros
  `options.profile_params.restrictions` ficam para V2.
- Outros perfis do ORS (`cycling-*`, `foot-*`, `wheelchair`).
- Persistir dimensões/atributos do veículo no modelo `PlannedRoute`.
- Qualquer UI; o projeto é backend/REST.

### 🔮 Considerações futuras (V2+)

- Enviar **restrições de caminhão** ao ORS (estender `OrsDirectionsRequest` com `options` e expor
  os campos no `RoutePlanRequest`), permitindo rotas sensíveis a peso/altura/cargas perigosas.
- Modelar os perfis suportados como **enum** quando o conjunto crescer, com mapeamento explícito
  para os identificadores do ORS.
- Demais perfis do roadmap (bicicleta, a pé).

---

## Solução Técnica

### Visão geral

A funcionalidade é uma **extensão do conjunto de perfis aceitos**, não uma mudança estrutural. O
perfil já trafega como `String` por todas as camadas e é repassado ao ORS como path variable; o
banco já guarda string livre. Portanto a entrega se concentra em **dois pontos**:

1. **Validação de entrada** — substituir o `@Pattern(regexp = "driving-car")` por uma validação
   contra um **conjunto de perfis suportados** `{driving-car, driving-hgv}`, com mensagem de erro
   que lista os valores válidos.
2. **Ponto único de verdade** — centralizar esse conjunto (e o default) para que serviço,
   validação e documentação concordem.

O mapeamento `driving-hgv` → ORS é direto: o identificador da aplicação **é** o identificador do
ORS, então `OpenRouteServiceGateway`/`OpenRouteServiceClient` não mudam.

### Componentes afetados

- **`RoutePlanRequest` (`dto/`)** — trocar a anotação de perfil único por validação contra o
  conjunto suportado (ex.: `@Pattern(regexp = "driving-car|driving-hgv")` ou, preferível, uma
  validação que referencie a lista central). Mensagem: "perfil não suportado; use driving-car ou
  driving-hgv".
- **`RouteServiceImpl`** — manter `DEFAULT_PROFILE = "driving-car"`; `resolveProfile(...)`
  permanece (default em branco). Opcionalmente, validar pertencimento ao conjunto suportado como
  defesa em profundidade (o ORS já rejeitaria um perfil inválido).
- **Ponto único de perfis suportados** — uma constante/lista compartilhada (`SUPPORTED_PROFILES`)
  consumida pela validação e pela mensagem de erro.
- **`OpenRouteServiceGateway` / `OpenRouteServiceClient`** — **inalterados** (já genéricos no
  `{profile}`).
- **`PlannedRoute` + migration** — **inalterados** (coluna `profile TEXT`, sem enum/constraint).
- **Documentação OpenAPI/Swagger** — atualizar o exemplo/descrição do campo `profile` para citar
  os dois valores.

### Fluxo (inalterado, agora com dois perfis)

1. Cliente envia `RoutePlanRequest` com `profile = "driving-hgv"` (ou omite para usar o default).
2. Bean Validation confere o perfil contra o conjunto suportado → 400 se inválido.
3. `RouteServiceImpl` resolve o perfil e monta as coordenadas ordenadas (origem → paradas →
   destino).
4. `gateway.getDirections("driving-hgv", coords)` → ORS `POST /v2/directions/driving-hgv`.
5. Resposta mapeada para `RouteResultDto` (com `profile = "driving-hgv"`); se for
   `createRoute`, persiste em `PlannedRoute.profile`.

### Contrato de API (exemplo)

```json
// POST /api/v1/routes/plan  (ou endpoint atual de cálculo)
{
  "profile": "driving-hgv",
  "origin":      { "lat": -23.55, "lon": -46.63 },
  "destination": { "lat": -22.91, "lon": -43.17 },
  "stops": [ { "lat": -23.18, "lon": -45.88 } ],
  "name": "Entrega SP → RJ"
}

// 200 OK
{
  "profile": "driving-hgv",
  "distanceMeters": 451230,
  "durationSeconds": 21600,
  "geometry": "…polyline…",
  "segments": [ /* … */ ]
}
```

```json
// 400 Bad Request — perfil inválido
{
  "status": 400,
  "detail": "perfil não suportado; use driving-car ou driving-hgv"
}
```

### Banco de dados

Sem mudanças. `PlannedRoute.profile` já é `TEXT` e aceita qualquer string; `driving-hgv` é
persistido como qualquer outro valor. **Nenhuma migration Flyway** é necessária.

---

## Riscos

| Risco                                                                          | Impacto | Probabilidade | Mitigação                                                                                                                            |
| ------------------------------------------------------------------------------ | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **ORS rejeita/limita `driving-hgv`** (perfil não habilitado na instância/plano) | Médio   | Baixa         | Validar contra a instância pública do ORS num teste manual; o ErrorDecoder já traduz erros do ORS em exceções de domínio (4xx/5xx).   |
| **Validação dessincronizada** entre anotação, serviço e mensagem de erro       | Baixo   | Média         | Centralizar `SUPPORTED_PROFILES` em um único ponto de verdade consumido por validação, default e documentação.                       |
| **Expectativa de rota "real de caminhão" sem restrições de veículo**           | Médio   | Média         | Deixar explícito (README/Swagger) que V1 usa os **defaults** do `driving-hgv`; peso/altura/cargas perigosas ficam para V2.            |
| **Regressão no caminho `driving-car`** ao mexer na validação                   | Médio   | Baixa         | Manter testes existentes do `driving-car` verdes; adicionar casos paralelos para `driving-hgv` e um caso de perfil inválido (400).    |

---

## Plano de Implementação

| Fase                | Tarefa                              | Descrição                                                                                          | Estimativa |
| ------------------- | ----------------------------------- | -------------------------------------------------------------------------------------------------- | ---------- |
| **Fase 1 – Núcleo** | Conjunto de perfis suportados       | Centralizar `{driving-car, driving-hgv}` em um ponto único (constante/lista compartilhada).        | 0,5d       |
|                     | Validação de entrada                | Atualizar `RoutePlanRequest` para validar contra o conjunto; mensagem lista os perfis válidos.     | 0,5d       |
|                     | Defesa no serviço (opcional)        | `RouteServiceImpl` mantém default e, se desejado, valida pertencimento ao conjunto.                | 0,5d       |
| **Fase 2 – Testes** | Unitários (serviço/mapper)          | Casos para `driving-hgv` espelhando os de `driving-car`; round-trip e default preservados.         | 0,5d       |
|                     | Controller + integração E2E         | `RouteControllerTest` e `RouteApiIntegrationTest`: stub WireMock em `/v2/directions/driving-hgv`; caso de perfil inválido → 400. | 1d |
| **Fase 3 – Docs**   | README + Swagger/OpenAPI            | Documentar os dois perfis e a ressalva "sem restrições de veículo em V1".                           | 0,5d       |
|                     | Verificação manual                  | `mvn verify` + chamada real ao ORS com `driving-hgv` para confirmar resposta válida.               | 0,5d       |

**Estimativa total:** ~4 dias (< 1 semana)

**Dependências:** Fase 1 → 2 → 3. Sem dependências externas além da chave ORS já configurada.

---

## Estratégia de Testes

| Cenário de verificação                                   | Resultado esperado                                                                  |
| -------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `profile = "driving-hgv"` no cálculo de rota             | Gateway chamado com `driving-hgv`; resposta mapeada com `profile = "driving-hgv"`.   |
| `profile = "driving-hgv"` no salvamento (`createRoute`)  | `PlannedRoute.profile` persistido como `driving-hgv`; round-trip devolve o mesmo.    |
| `profile` omitido/nulo/vazio                             | Default `driving-car` aplicado (compatibilidade retroativa).                         |
| `profile` inválido (ex.: `foot-walking`)                 | **400** com mensagem listando `driving-car` e `driving-hgv`.                         |
| Integração E2E (WireMock)                                | Stub em `/v2/directions/driving-hgv` retorna rota; controller responde 200/201.     |
| Regressão `driving-car`                                  | Todos os testes existentes do `driving-car` permanecem verdes.                       |

**Validação manual chave:** uma chamada real ao ORS com `driving-hgv` confirmando rota válida —
provando que o perfil está habilitado na instância usada e que o caminho ponta-a-ponta funciona.

---

## Notas

- Este TDD assume **uso pessoal / instância única**: sem requisitos formais de segurança,
  monitoramento ou rollback. A mudança é aditiva e compatível com o comportamento atual.
- A persistência **não muda** (coluna `profile` já é string livre); por isso a estratégia
  dual-mode de validação de banco (Testcontainers + H2) não é impactada.
- Quando o conjunto de perfis crescer (bicicleta, a pé) e/ou surgir a necessidade de restrições
  de veículo, reavaliar a modelagem (enum + `options` no request ORS) — ver Considerações Futuras.
