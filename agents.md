# Agents.md — Regras e Padrões de Desenvolvimento

Este documento define as regras e padrões de desenvolvimento para o projeto **personalRouter**, uma API REST backend em Java com Spring Boot. Todo código gerado ou revisado por agentes (humanos ou IA) deve seguir estas diretrizes.

## 1. Stack Tecnológica

- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.x
- **Build tool:** Maven
- **Persistência:** Spring Data JPA + PostgreSQL
- **Redução de boilerplate:** Lombok
- **Mapeamento entre camadas:** MapStruct
- **Validação:** Bean Validation (`jakarta.validation`)
- **Integrações externas:** Feign Client
- **Documentação de API:** springdoc-openapi (Swagger UI)
- **Segurança:** Spring Security + JWT (stateless)
- **Testes:** JUnit 5, Mockito, Testcontainers
- **Cobertura de testes:** JaCoCo
- **Estilo de código:** Google Java Style Guide + Checkstyle
- **Logging:** SLF4J + Lombok `@Slf4j`

## 2. Estrutura de Pacotes

Organização em camadas técnicas (layered architecture):

```
com.personalrouter.<dominio>
├── controller    // Endpoints REST (@RestController)
├── service       // Regras de negócio (@Service)
├── repository    // Acesso a dados (Spring Data JPA)
├── client        // Integrações com APIs de terceiros (Feign Clients)
├── dto           // Objetos de transferência (request/response)
├── model         // Entidades JPA (@Entity)
├── mapper        // Interfaces MapStruct (entity <-> DTO)
├── exception      // Exceções customizadas
└── config        // Configurações (Security, OpenAPI, Feign, etc.)
```

Regras:
- Controllers não devem conter lógica de negócio — apenas orquestração e validação de entrada/saída.
- Services não devem depender diretamente de objetos HTTP (`HttpServletRequest`, etc.).
- Toda classe de serviço (`@Service`) deve implementar uma interface funcional que define seu contrato (ex: `UserService` define a interface, `UserServiceImpl` a implementa).
- Repositories expõem apenas operações de persistência; regras de negócio não pertencem a esta camada.
- Clients Feign ficam isolados em `client/`, com configuração própria de timeout e retry.

## 3. Persistência (Spring Data JPA + PostgreSQL)

- Entidades JPA ficam em `model/`, anotadas com `@Entity`.
- Usar `Lombok` (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) para reduzir boilerplate em entidades e DTOs.
- Não expor entidades JPA diretamente nas respostas da API — sempre converter para DTOs via MapStruct.
- Migrations de banco de dados devem ser versionadas (ex: Flyway ou Liquibase).

## 4. DTOs, Validação e Mapeamento

- Toda entrada de dados via API (`@RequestBody`) deve ser validada com Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Email`, etc.) e anotada com `@Valid` no controller.
- Conversões entre `model` (entidades) e `dto` devem ser feitas via interfaces MapStruct (`@Mapper(componentModel = "spring")`), nunca manualmente em services.

## 5. Integrações com Terceiros (Feign)

- Toda integração com APIs externas deve ser implementada via interface `@FeignClient`, localizada em `client/`.
- Configurar timeouts e política de retry explicitamente para cada client (não usar defaults silenciosos).
- Erros de integração externa devem ser tratados e traduzidos para exceções de domínio antes de subir para a camada de serviço.

## 6. API e Versionamento

- Todos os endpoints devem ser versionados via URL: `/api/v1/...`.
- Controllers retornam `ResponseEntity<T>` diretamente, usando códigos HTTP semânticos (200, 201, 204, 400, 404, 409, etc.) — sem wrapper genérico de resposta.
- Documentação automática via springdoc-openapi; todo endpoint público deve ter anotações OpenAPI (`@Operation`, `@ApiResponse`) descrevendo seu comportamento.

## 7. Tratamento de Erros

- Exceções de negócio devem ser customizadas e específicas por domínio (ex: `ResourceNotFoundException`, `BusinessRuleViolationException`).
- Tratamento centralizado via `@RestControllerAdvice`, retornando respostas no formato **ProblemDetail (RFC 7807)** — `application/problem+json`.
- Nunca expor stack traces, detalhes internos ou mensagens de exceções de baixo nível (ex: SQL) nas respostas da API.

## 8. Segurança

- Autenticação via Spring Security + JWT (stateless, sem sessão de servidor).
- Endpoints são protegidos por padrão; rotas públicas devem ser explicitamente declaradas na configuração de segurança.
- Segredos, chaves e credenciais nunca devem estar hardcoded no código — sempre via `application.yml` + variáveis de ambiente.

## 9. Logging

- Usar SLF4J via `@Slf4j` (Lombok) — nunca `System.out.println`.
- Logs estruturados em formato JSON, adequados para ingestão por ferramentas de observabilidade (ELK, Datadog, etc.).
- Nunca registrar dados sensíveis em logs (senhas, tokens, dados pessoais).
- Níveis de log devem ser usados de forma consistente: `ERROR` para falhas que exigem atenção, `WARN` para situações anômalas recuperáveis, `INFO` para eventos de negócio relevantes, `DEBUG` para detalhes de diagnóstico.

## 10. Testes

- Testes unitários com JUnit 5 + Mockito, cobrindo a lógica de `service` e `mapper`.
- Testes de integração com Testcontainers, usando uma instância real de PostgreSQL.
- Cobertura mínima de **80%**, medida via JaCoCo e validada no build.
- Toda nova funcionalidade ou correção de bug deve incluir testes correspondentes.

## 11. Estilo de Código

- Seguir o **Google Java Style Guide**.
- Conformidade verificada via Checkstyle no build (falhas de estilo quebram o build).
- Sempre utilizar declaração de tipos explícitos para variáveis; o uso de `var` é proibido.
- Evitar comentários óbvios; comentar apenas decisões não evidentes (ex: workarounds, invariantes).

## 12. Controle de Versão (Git)

- Mensagens de commit devem seguir o padrão **Conventional Commits**, concatenado com o título da tarefa em desenvolvimento:

  ```
  <tipo>: <descrição da tarefa>
  ```

  Exemplo:
  ```
  feat: Desenvolvimento de integração de pagamento com Stripe
  ```

  Tipos válidos: `feat`, `fix`, `chore`, `refactor`, `test`, `docs`, `perf`, `build`, `ci`.
