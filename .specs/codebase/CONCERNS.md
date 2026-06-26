# Codebase Concerns

**Analysis Date:** 2026-06-24

> Severities are by inspection; no runtime profiling was performed (numbers below are complexity/row-count estimates, not measured p95). Locations are exact.

## Security Considerations

**Unauthenticated write endpoints (highest priority):**
- Risk: There is **no authentication or authorization** anywhere — `spring-boot-starter-security` is not on the classpath. `POST /api/v1/toll-plazas/import`, `DELETE /api/v1/toll-plazas/{id}`, and `POST/DELETE /api/v1/routes` are open to any caller.
- Files: `pom.xml` (no security starter), `controller/TollPlazaController.java`, `controller/RouteController.java`.
- Current mitigation: **Wipe vector eliminated (KAN-26):** reconciliation no longer soft-deletes absent plazas — it only inserts, reactivates, and updates. Deactivation is now an explicit `DELETE /api/v1/toll-plazas/{id}` (one plaza at a time). An empty CSV no longer deactivates the table. Authz for all mutating endpoints remains open — tracked as KAN-25.
- Recommendations: Add Spring Security; lock down mutating endpoints (KAN-25).

**Real-looking DB credential shipped as a committed default:**
- Risk: `application.yml:12-13` hardcodes `DB_HOST` default `db.prisma.io` and a 64-hex-char `DB_USERNAME` default (`525c…932`) that matches a Prisma Postgres connection username. This directly violates agents.md §8 ("segredos/credenciais nunca hardcoded"). The password correctly has no default, so it is not directly exploitable, but host+username narrow an attack and leak infra details in a public-style repo.
- Files: `src/main/resources/application.yml:11-15`.
- Current mitigation: `application-local.yml` and `.env` are gitignored; password is env-only.
- Recommendations: Remove the host/username defaults (let them fail fast if unset, like the password), and rotate the Prisma role if this username is real.

## Performance Bottlenecks

**Toll matching re-runs per route on list/read paths:**
- Problem: `RouteServiceImpl.listRoutes()` maps every persisted route through `toDtoWithTolls`, which calls `tollMatchingService.findTollPlazasAlongRoute(geometry)` **once per route** (`src/main/java/com/personalrouter/service/RouteServiceImpl.java:53-57,94-97`). Each call decodes the polyline and runs a bbox query + O(plazas × routeSegments) point-to-segment haversine. For a list of N saved routes that is N DB queries + N decodes + N× geometric scans on every `GET /api/v1/routes`.
- Files: `service/RouteServiceImpl.java`, `service/TollMatchingService.java`.
- Cause: Toll plazas are computed on read instead of being persisted with the route.
- Improvement path: Persist matched plazas at create time (or cache by route id / geometry hash); at minimum, make the list endpoint skip toll matching or paginate.

**Reconciliation loads the whole table and writes row-by-row:**
- Problem: `reconcile` does `repository.findAll()` into a `HashMap`, then `repository.save(...)` inside the per-row loop (`TollPlazaReconciliationServiceImpl.java`). For a national toll base this is a full table load plus one INSERT/UPDATE per affected row, all in a single `@Transactional` on the single-thread `tollImportExecutor`.
- Files: `service/TollPlazaReconciliationService.java`.
- Cause: Naive in-memory diff + individual saves; no batch/bulk DML.
- Improvement path: Batch writes (JDBC batch / `saveAll` with `hibernate.jdbc.batch_size`), or push the upsert/soft-delete into SQL keyed by the natural key.

## Scaling Limits

**Whole CSV held in memory three times:**
- Current capacity: Bounded by `max-file-size: 10MB` and executor `queueCapacity: 100` (`application.yml:7-10`, `config/AsyncConfig.java:18`).
- Limit: The file bytes are read fully (`MultipartFile.getBytes()` in `TollPlazaController`), carried inside the in-memory `TollPlazaImportCreatedEvent`, and retained in the executor queue until processed — up to ~100 × 10MB ≈ 1GB of queued payloads under burst.
- Symptoms at limit: Heap pressure / OOM under many concurrent uploads.
- Scaling path: Persist the upload to a blob/temp file and pass a reference (not bytes) through the event; cap concurrent in-flight imports.

## Fragile Areas

**Engine-conditional idempotency index (Postgres-only):**
- Files: `src/main/java/db/migration/V4__toll_plaza_import_partial_unique_hash.java`, `service/TollPlazaImportServiceImpl.java:25-52`.
- Why fragile: The "skip duplicates but allow retry after FAILED" guarantee depends on a **partial** unique index (`WHERE status <> 'FAILED'`) that only exists on PostgreSQL; on H2 it degrades to a plain unique index. Behavior therefore differs between the H2 convenience net and real Postgres.
- Common failures: On H2, re-uploading a file whose previous import is `FAILED` would hit the plain unique index instead of inserting a new row — the Postgres-only path is not exercised by the H2 net.
- Safe modification: Keep idempotency assertions in the Docker/Testcontainers path; never rely on H2 to validate hash/index semantics.
- Test coverage: Covered by `integration/TollPlazaImportIntegrationTest.reuploadAposFalha_criaNovoImport_eReprocessa` — but **only when Docker is present** (Testcontainers-gated).

**Quota error mapped to 503 instead of 429:**
- Files: `client/OpenRouteServiceErrorDecoder.java:20-22`, `exception/GlobalExceptionHandler.java:43-53` (`OpenRouteServiceQuotaExceededException extends OpenRouteServiceException`).
- Why fragile: A 429 from ORS becomes `OpenRouteServiceQuotaExceededException`, which is caught by the base-class handler and returned to clients as **503** (route endpoints document 503 but not 429). Callers can't distinguish "rate-limited, back off" from "provider down".
- Safe modification: Add a dedicated handler returning 429 with a `Retry-After` hint.

## Tech Debt

**~~Style gate disabled despite policy~~ (Resolvido — KAN-32):**
- Resolvido: Checkstyle agora roda com `failOnViolation=true` na fase `validate`, com ruleset curado de higiene de imports (`config/checkstyle/checkstyle.xml`). Violações existentes foram limpas. Enforcement parcial (imports) — o restante do Google Style permanece como guideline.

**`@Service` beans without the mandated interface:**
- Issue: agents.md §2 requires every `@Service` to implement a contract interface. `TollMatchingService`, `TollPlazaReconciliationService`, and `TollPlazaImportWorker` are concrete `@Service` classes with no interface; `OpenRouteServiceGateway` is a concrete `@Component`.
- Impact: Minor — inconsistent with the stated convention; slightly harder to mock/swap.
- Fix approach: Either extract interfaces or relax the rule in agents.md to "interface where a seam adds value".

**Documentation drift:**
- Issue: README "Arquitetura" documents only the route flow (client/config/dto/exception) and omits the toll-import pipeline; agents.md §2 shows a domain-first package layout (`com.personalrouter.<dominio>.<layer>`) while the code is flat layer-first.
- Files: `README.md:27-42`, `agents.md:22-37`.
- Fix approach: Update README architecture to include the import pipeline; reconcile agents.md package guidance with the actual flat structure (or split into domains).

## Test Coverage Gaps

**Full integration path is Docker-gated:**
- What's not tested (without Docker): `integration/RouteApiIntegrationTest` and `integration/TollPlazaImportIntegrationTest` are `@Testcontainers(disabledWithoutDocker = true)` and **skip** when Docker is absent; persistence tests then fall back to H2.
- Risk: A green `mvn verify` on a Docker-less machine does **not** validate the real HTTP→PostgreSQL path nor Postgres-only behavior (partial index, types). Matches the standing project note "build-green ≠ DB validation".
- Priority: Medium.
- Difficulty to test: Low to run (needs Docker); the suite already exists.

---

_Concerns audit: 2026-06-24_
_Update as issues are fixed or new ones discovered_
