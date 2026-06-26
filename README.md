# PersonalRouter

REST API para planejamento e armazenamento de rotas pessoais com múltiplas paradas, integrada ao [OpenRouteService](https://openrouteservice.org/).

## Visão Geral

O **PersonalRouter** calcula rotas otimizadas entre um ponto de origem e um destino, suportando até 10 paradas intermediárias (waypoints). Para cada rota, retorna distância total, duração estimada, geometria (polyline) e detalhamento por segmento.

A integração com o OpenRouteService é feita via **Feign Client**, com tratamento de erros, retry automático e tradução de exceções para o domínio da aplicação.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.6 |
| Build | Maven |
| HTTP Client | Spring Cloud OpenFeign |
| Persistência | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Mapeamento | MapStruct 1.6.3 |
| Documentação | SpringDoc OpenAPI (Swagger UI) |
| Testes | JUnit 5 + Testcontainers + H2 + WireMock |
| Cobertura | JaCoCo (mínimo 80%) |
| Estilo | Google Java Style Guide (Checkstyle — gate de imports no build) |

## Arquitetura

```
Controller → Service → OpenRouteServiceGateway → OpenRouteServiceClient (Feign)
                  ↓
             Repository → PostgreSQL
```

Pacote raiz: `com.personalrouter`

```
client/       # Integração com OpenRouteService (Feign + Gateway + ErrorDecoder)
config/       # Propriedades externas (ors.api.*)
dto/          # Objetos de transferência (Coordinate, request/response)
exception/    # Exceções de domínio (quota, indisponibilidade)
```

## Perfis de Transporte

| Perfil | Descrição | Default |
|---|---|---|
| `driving-car` | Automóvel | Sim |
| `driving-hgv` | Caminhão (Heavy Goods Vehicle) | Não |

O campo `profile` é opcional no request — quando omitido, assume `driving-car`.

> ⚠️ **V1:** o perfil `driving-hgv` utiliza os defaults do OpenRouteService, sem restrições de veículo (peso, altura, cargas perigosas). Parâmetros de veículo pesado (`options.profile_params`) ficam para V2.

## Pré-requisitos

- Java 21 (Amazon Corretto recomendado)
- Maven 3.6+
- PostgreSQL (local ou remoto)
- Chave de API do [OpenRouteService](https://openrouteservice.org/dev/#/signup)

## Configuração

Crie um arquivo `src/main/resources/application-local.yml` com as variáveis de ambiente locais:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/personalrouter
    username: seu_usuario
    password: sua_senha

ors:
  api:
    key: sua_chave_ors
```

As variáveis de ambiente esperadas em produção:

| Variável | Descrição |
|---|---|
| `DB_HOST` | Host do banco de dados |
| `DB_PORT` | Porta do banco (padrão: 5432) |
| `DB_NAME` | Nome do banco |
| `DB_USERNAME` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco |
| `ORS_API_KEY` | Chave de API do OpenRouteService |

## Executando

```bash
# Compilar e rodar testes
mvn verify

# Subir a aplicação com perfil local
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Documentação da API

Com a aplicação no ar, acesse:

| Interface | URL |
|---|---|
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |

## Testes

```bash
# Todos os testes
mvn test

# Relatório de cobertura (gerado em target/site/jacoco/index.html)
mvn verify
```

Os testes de integração mocam as chamadas ao OpenRouteService com **WireMock** e validam a camada de persistência em **dois modos**, selecionados automaticamente conforme a disponibilidade de Docker.

### Validação da camada de persistência (dual-mode)

A validação de banco (migration Flyway + mapeamento JPA) roda em **dois modos**, selecionados
automaticamente conforme a disponibilidade de Docker — **sem skip**:

| Ambiente | Datasource | Papel |
|---|---|---|
| Com Docker | PostgreSQL 16 via Testcontainers | Autoridade de fidelidade |
| Sem Docker | H2 em `MODE=PostgreSQL` | Net de conveniência local |

Em ambos, o Flyway aplica `V1__create_planned_route.sql` e `ddl-auto=validate` confere o contrato
schema ↔ entidades.

> ⚠️ **Ressalva:** o H2 é um net de conveniência que valida schema/mapeamento e o round-trip
> básico — **não** substitui a fidelidade do Postgres. Recursos Postgres-only (`JSONB`, arrays,
> tipos geográficos) não são cobertos pelo H2; o caminho Testcontainers/Postgres continua sendo a
> referência de verdade.

## Status do Projeto

| Fase | Descrição | Status |
|---|---|---|
| Fase 1 | Setup do projeto (dependências + configuração) | Concluída |
| Fase 2 | Feign Client — OpenRouteServiceClient | Concluída |
| Fase 3 | RouteService, mappers e persistência de rotas planejadas | Concluída |
| Fase 4 | RouteController + endpoints REST | Concluída |
| Fase 5 | Entidades JPA, repositório e migration Flyway | Concluída |
| Fase 6 | Testes (unitários + integração) + gate JaCoCo 80% | Concluída |
| Fase 7 | Estratégia dual-mode de persistência (Testcontainers + H2 fallback) | Concluída |
| Fase 8 | Perfil de transporte caminhão (`driving-hgv`) | Concluída |
| Fase 9a | Praças de pedágio: ingestão por CSV (KAN-19) | Concluída |
| Fase 9b | Pedágios no trajeto (KAN-20) | Em planejamento ([TDD](docs/TDD-pracas-de-pedagio.md) · [KAN-18](https://filipeazvdo.atlassian.net/browse/KAN-18)) |

## Ingestão de Praças de Pedágio (KAN-19)

Upload assíncrono de CSV que sincroniza a tabela `toll_plaza` (upsert por chave natural + soft delete por reconciliação), com idempotência por hash SHA-256.

### Endpoints

| Método | Path | Descrição |
|---|---|---|
| `POST` | `/api/v1/toll-plazas/import` | Upload de CSV (`multipart/form-data`, campo `file`) → **202** (novo) ou **200** (idempotente) |
| `GET` | `/api/v1/toll-plazas/imports/{id}` | Status do import (contagens + erros por linha) |

### Contrato do CSV

- **Encoding:** UTF-8 estrito (rejeita bytes inválidos)
- **Delimitador:** `;`
- **13 colunas na ordem:** `concessionaria;praca_de_pedagio;ano_do_pnv_snv;rodovia;uf;km_m;municipal;tipo_de_pista;sentido;situacao;data_da_inativacao;latitude;longitude`
- `situacao` e `data_da_inativacao` são validadas no cabeçalho mas não persistidas

### Processamento

- **Validação síncrona:** encoding, cabeçalho (13 colunas na ordem) e arquivo não-vazio → 400 se inválido
- **Idempotência:** hash SHA-256 do conteúdo; reenvio do mesmo arquivo retorna o import existente (200)
- **Reconciliação:** upsert por chave natural `(rodovia, km_m, sentido)`, soft delete por ausência, reativação sem duplicar
- **Erros por linha:** linhas individualmente inválidas são reportadas sem abortar o lote
- **Executor dedicado:** single-thread (`tollImportExecutor`), isolado do pool de virtual threads global

### Exemplo de uso

```bash
# Upload do CSV
curl -i -F "file=@assets/csv/example.csv" http://localhost:8080/api/v1/toll-plazas/import

# Consulta de status
curl http://localhost:8080/api/v1/toll-plazas/imports/<importId>
```

## Documentação técnica (TDDs)

| Documento | Tema |
|---|---|
| [Planejamento de rotas](docs/TDD-planejamento-de-rotas.md) | Cálculo e persistência de rotas com paradas |
| [Validação de DB sem Docker](docs/TDD-validacao-db-sem-docker.md) | Estratégia dual-mode de persistência (Testcontainers + H2) |
| [Perfil de transporte caminhão](docs/TDD-perfil-transporte-caminhao.md) | Perfil `driving-hgv` |
| [Praças de pedágio](docs/TDD-pracas-de-pedagio.md) | Ingestão por CSV + pedágios sobre o trajeto |

## Roadmap (V2+)

- Restrições de veículo pesado para `driving-hgv` (peso, altura, cargas perigosas)
- Tarifa/custo de pedágio e casamento por sentido/rodovia (evolução das praças de pedágio)
- Autenticação JWT para suporte multi-usuário
- Perfis de transporte adicionais (bicicleta, a pé)
- Otimização de ordem de waypoints (problema do caixeiro-viajante)
- Suporte a múltiplos provedores de rota
- Cache de rotas com Redis

## Licença

MIT.
