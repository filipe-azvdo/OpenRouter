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
| Testes | JUnit 5 + Testcontainers + WireMock |
| Cobertura | JaCoCo (mínimo 80%) |
| Estilo | Google Java Style Guide (Checkstyle) |

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

A documentação Swagger estará disponível em: `http://localhost:8080/swagger-ui.html`

## Testes

```bash
# Todos os testes
mvn test

# Relatório de cobertura (gerado em target/site/jacoco/index.html)
mvn verify
```

Os testes de integração utilizam **Testcontainers** (requer Docker) e as chamadas ao OpenRouteService são mockadas com **WireMock**.

## Status do Projeto

| Fase | Descrição | Status |
|---|---|---|
| Fase 1 | Setup do projeto (dependências + configuração) | Concluída |
| Fase 2 | Feign Client — OpenRouteServiceClient | Concluída |
| Fase 3 | Controller, Service e persistência de rotas | Em desenvolvimento |

## Roadmap (V2+)

- Autenticação JWT para suporte multi-usuário
- Perfis de transporte adicionais (bicicleta, a pé)
- Otimização de ordem de waypoints (problema do caixeiro-viajante)
- Suporte a múltiplos provedores de rota
- Cache de rotas com Redis

## Licença

MIT.
