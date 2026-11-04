# Phase 1 Backend: Login, Roles, Research Ingest and Stock Groups — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the backend into the login-only ADMIN/USER app. The web-research write path becomes local-only, with a token-protected push to live. Stock groups are added.

**Architecture:** Spring Security with three filter chains:
1. local ingest token, `/api/research/ingest/**`;
2. live sync token, `/api/research/sync/**`;
3. session chain for everything else, with CSRF and JSON errors.

Accounts come from configuration; there is no user table. Brute-force counters live in memory. Groups are a Flyway-seeded table referenced by `company_snapshot`.

**Tech Stack:** Java 21, Spring Boot 3.3.5 (Spring Security 6.3), Spring Data JPA, Flyway, PostgreSQL 16, JUnit 5, MockMvc + `spring-security-test`, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-11-04-v2-phase1-backend-research-groups-design.md`. It refines `docs/superpowers/specs/2026-11-03-app-scope-v2-design.md`; read both.

## Global Constraints

- Base package `com.valuescreener`. Security code goes in `security`, portfolio in `portfolio`, research and groups in `research`. Follow the existing patterns: records for DTOs, entities validate in their constructors, `@RestControllerAdvice(assignableTypes = …)` per controller group.
- Project convention: **the user does all git operations**, including deletions in git. "Commit" steps mean: stop, show the diff summary, propose the message, and let the user commit. Plain `rm` of a file the plan says to delete is fine.
- Working mode: give a short concept explanation (the **Concept** line of each task) to the user before starting the task. TDD: red → green → refactor.
- **Build tooling in this environment:**
  `export JAVA_HOME=/home/node/.cache/vs-tools/jdk-21.0.13+9; export PATH=$JAVA_HOME/bin:/home/node/.cache/vs-tools/apache-maven-3.9.11/bin:$PATH`
  then `cd backend && mvn -q -Dmaven.repo.local=/home/node/.cache/vs-tools/m2 test -Dtest=<Class>`. The first run downloads Spring Boot 3.3.5 artifacts.
- **There is no Docker here.** Testcontainers tests (`*RepositoryTest`, `ValueScreenerApplicationTests`, `StockGroupMigrationTest`) only run on the user's machine or in CI. New security and controller tests are therefore **`@WebMvcTest` slices with mocked services**, so they run here. When a task touches a Testcontainers test, ask the user to run `mvn test` locally and report the result.
- JSON error body everywhere: `{"error":"<message>"}`. Use these fixed messages:
  - `401` unauthenticated: `"Authentication required"`
  - `403`: `"Forbidden"`
  - login failure: `"Invalid username or password"`
  - `429`: `"Too many login attempts"` plus a `Retry-After` header in seconds
- Session: cookie `HttpOnly`, `SameSite=Strict`, `Secure` from `server.servlet.session.cookie.secure` (default `true`, `false` in profile `local`), idle timeout `2h`.
- Brute-force values:
  - per IP: 5 attempts per 60 s; block durations 1, 2, 4, 8, then 15 min (cap); the violation counter resets after 1 h without a violation.
  - per account: lock for 15 min after 10 consecutive failures.
  - counter maps purge expired entries once they hold more than 10,000 entries.
- Password rules (documentation only): ADMIN ≥ 20 characters, USER ≥ 16. Username `*` is reserved.
- Validation rules:
  - ISIN regex `[A-Z]{2}[A-Z0-9]{9}[0-9]`; requests accept any case and normalize with trim + uppercase.
  - `sourceUrl` must be `http`/`https`.
  - `claim` ≤ 2000 characters; at most 50 findings.
- Token hashes are SHA-256 as 64 lowercase hex characters, compared in constant time.
- Property and environment-variable names (relaxed binding):

| Property | Environment variable |
|---|---|
| `app.admin.username` / `app.admin.password-hash` | `ADMIN_USERNAME` / `ADMIN_PASSWORD_HASH` (existing placeholders) |
| `app.users[n].username` / `app.users[n].password-hash` | `APP_USERS_0_USERNAME` / `APP_USERS_0_PASSWORD_HASH` |
| `app.research-ingest.enabled` / `.token-hash` | `APP_RESEARCH_INGEST_ENABLED` / `APP_RESEARCH_INGEST_TOKEN_HASH` |
| `app.research-sync.enabled` / `.token-hash` | `APP_RESEARCH_SYNC_ENABLED` / `APP_RESEARCH_SYNC_TOKEN_HASH` |
| `app.security.trust-forwarded-for` | `APP_SECURITY_TRUST_FORWARDED_FOR` |

- Machine paths are exactly `/api/research/ingest/**` (local) and `/api/research/sync/snapshots` (live).
- Out of scope: frontend, AWS resources, usage limits, the Spring Boot upgrade.

## Review Focus

1. **Malformed or empty login body** (`{}`, invalid JSON, a password of 10,000 characters) → `400`, never `500`, and it counts as an attempt for the IP limit. The test is in Task 3.
2. **Lowercase or padded ISIN in a path** (`PUT /api/portfolio/us0378331005`, `PUT /api/research/snapshots/ us0378331005 /group`) → treated like the uppercase ISIN. Tests are in Task 4 and Task 7.
3. **Reusing the old session cookie after logout** → `401`. A session id captured before login is invalid afterwards. The test is in Task 2.
4. **Malformed `Authorization` header** (`Basic …`, `bearer` in lower case, `Bearer` with no token, a token with spaces) → `401` with a JSON body, no exception. The test is in Task 5.
5. **AI sends a lowercase or padded group key** (`" semiconductors "`) → normalized to `SEMICONDUCTORS` and accepted; a key that doesn't exist → `422`. The test is in Task 7.

---

### Task 1: Account list from configuration with startup validation

**Concept:** Accounts are a list in configuration. A single class validates them once at startup and produces the `UserDetailsService`, so a typo in the configuration stops the application instead of creating a broken account.

**Files:**
- Create: `backend/src/main/java/com/valuescreener/security/Role.java`
- Create: `backend/src/main/java/com/valuescreener/security/AccountsProperties.java`
- Create: `backend/src/main/java/com/valuescreener/security/AccountRegistry.java`
- Modify: `backend/src/main/java/com/valuescreener/security/SecurityConfig.java`: remove the `@Value` fields; the `userDetailsService()` bean delegates to `AccountRegistry`; add `@EnableConfigurationProperties(AccountsProperties.class)`.
- Modify: `backend/src/main/resources/application.yml`: add `app.users: []`.
- Modify: `.env.example` and `README.md`: show a USER entry (`APP_USERS_0_USERNAME`, `APP_USERS_0_PASSWORD_HASH`), the password-length rules, and that `AdminPasswordHashGenerator` also hashes USER passwords.
- Test: `backend/src/test/java/com/valuescreener/security/AccountRegistryTest.java` (plain JUnit, no Spring)

**Interfaces:**
- Produces:
  - `enum Role { ADMIN, USER }`
  - `@ConfigurationProperties("app") record AccountsProperties(Account admin, List<Account> users)` with nested `record Account(String username, String passwordHash)`. A `null` `users` becomes `List.of()`.
  - `AccountRegistry(AccountsProperties)`: its constructor validates and throws `IllegalStateException`. The message names the problem and the username, never the hash.
  - `static String normalize(String username)`: trim + `toLowerCase(Locale.ROOT)`.
  - `boolean exists(String username)`: normalizes first.
  - `Optional<Role> roleOf(String username)`: normalizes first.
  - `UserDetailsService userDetailsService()`: an `InMemoryUserDetailsManager` with normalized usernames and roles `ADMIN`/`USER`.

- [ ] **Step 1: Write failing tests** in `AccountRegistryTest`, using a real BCrypt hash created in the test with `new BCryptPasswordEncoder().encode("x")`:
  - `acceptsAdminAndUsers`: `roleOf("admin") == ADMIN`, `roleOf("alice") == USER`.
  - `matchesUsernamesCaseInsensitively`: `roleOf(" Alice ") == USER`, and `userDetailsService().loadUserByUsername("ALICE")` succeeds.
  - `rejectsMissingAdminHash`: the admin hash is `""` → `IllegalStateException`.
  - `rejectsDuplicateUsernamesAcrossAdminAndUsers`: `admin` plus a user `Admin` → exception whose message contains `"admin"`.
  - `rejectsReservedUsernameStar`: a user `*` → exception.
  - `rejectsBlankUsername`: a user `" "` → exception.
  - `rejectsNonBcryptHash`: `"plaintext"` → exception, and the message does not contain `"plaintext"`.
  - `allowsEmptyUserList`: `users = null` → no exception, `exists("bob") == false`.
- [ ] **Step 2: Run** `mvn … test -Dtest=AccountRegistryTest`. Expected: compilation failure or red.
- [ ] **Step 3: Implement** `Role`, `AccountsProperties` and `AccountRegistry`. A BCrypt hash must match `^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$`. Wire `SecurityConfig` and update `application.yml`.
- [ ] **Step 4: Run** `AccountRegistryTest` and the existing `PortfolioControllerTest`. Expected: green.
- [ ] **Step 5: Update** `.env.example` and README as listed under Files.
- [ ] **Step 6: Commit (user):** `feat(security): account list (ADMIN + USERs) from configuration with startup validation`

---

### Task 2: Session login, CSRF, JSON errors and default-deny authorization

**Concept:** Basic Auth sends the password with every request. A session cookie needs it only once. Because the browser then sends the cookie on every request automatically, CSRF protection becomes necessary. All write requests default to ADMIN, so a forgotten endpoint is closed rather than open.

**Files:**
- Create: `security/AuthController.java`, `security/LoginRequest.java`, `security/AuthUserView.java`, `security/CsrfTokenView.java`
- Create: `security/JsonSecurityErrorHandlers.java`: one class that implements both `AuthenticationEntryPoint` (→ `401`) and `AccessDeniedHandler` (→ `403`) and writes the JSON body.
- Create: `security/SpaCsrfTokenRequestHandler.java`: the SPA handler from the Spring Security 6.3 reference ("Single-Page Applications" section). It resolves the raw token from the header and the XOR-masked token otherwise.
- Modify: `security/SecurityConfig.java` (session chain, `@Order(3)`)
- Create: `backend/src/main/resources/application-local.yml`
- Modify: `application.yml`: `server.servlet.session.timeout: 2h`, `server.servlet.session.cookie.{http-only: true, same-site: strict, secure: true}`.
- Modify: `dev-up.sh`: `mvn -q spring-boot:run -Dspring-boot.run.profiles=local`; update the final "Login:" line, which must no longer mention Basic Auth.
- Delete: `backend/src/test/java/com/valuescreener/security/PortfolioSecurityTest.java` and `ResearchSnapshotSecurityTest.java`. Their cases move into `EndpointAuthorizationTest`.
- Test: `security/AuthControllerTest.java`, `security/EndpointAuthorizationTest.java`, and test support `security/SecuritySliceTestConfig.java`. The support class is a `@TestConfiguration` that imports `SecurityConfig`, `AccountRegistry`, `JsonSecurityErrorHandlers` (and, from Task 3 on, `LoginAttemptGuard` and `ClientIpResolver`; from Task 5 on, `MachineTokenSecurityConfig`). It also defines a fixed account set: admin `admin`/`admin-password-123456789`, user `alice`/`alice-password-1234`.

**Interfaces:**
- Consumes: `AccountRegistry` (Task 1)
- Produces:
  - `record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password)`
  - `record AuthUserView(String username, Role role)`
  - `record CsrfTokenView(String headerName, String token)`
  - Endpoints:
    - `GET /api/auth/csrf` → `CsrfTokenView` (public)
    - `POST /api/auth/login` → `AuthUserView` (public, CSRF required)
    - `POST /api/auth/logout` → `204`
    - `GET /api/auth/me` → `AuthUserView`
  - `AuthController.login(LoginRequest, HttpServletRequest, HttpServletResponse)` authenticates through the `AuthenticationManager` built from `AccountRegistry.userDetailsService()` and the BCrypt `PasswordEncoder`. On success it runs a `CompositeSessionAuthenticationStrategy(ChangeSessionIdAuthenticationStrategy, CsrfAuthenticationStrategy)` and saves the context through `HttpSessionSecurityContextRepository`. Task 3 extends this method.
- Session chain rules, in this order:
  1. `GET /api/auth/csrf` and `POST /api/auth/login` → `permitAll`
  2. `POST /api/auth/logout` → `authenticated`
  3. `GET /api/**` → `authenticated`
  4. `anyRequest()` → `hasRole("ADMIN")`

  CSRF uses `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `SpaCsrfTokenRequestHandler`. HTTP Basic, form login and the `WWW-Authenticate` header are all gone. `exceptionHandling` points to `JsonSecurityErrorHandlers`.
- `application-local.yml`: `server.servlet.session.cookie.secure: false`. The `app.research-ingest.enabled: true` line is added in Task 5.

- [ ] **Step 1: Write failing tests** in `AuthControllerTest` (`@WebMvcTest(AuthController.class)` + `@Import(SecuritySliceTestConfig.class)`):
  - `csrfEndpointIsPublicAndSetsCookie`: `200`, the body has `headerName == "X-XSRF-TOKEN"`, and an `XSRF-TOKEN` cookie is set.
  - `loginWithoutCsrfTokenIs403`.
  - `loginSucceedsForAdmin`: with `csrf()`, the body is `{"username":"admin","role":"ADMIN"}` and the session is authenticated.
  - `loginSucceedsForUserCaseInsensitive`: `" Alice "` → role `USER`, username `alice`.
  - `loginFailsWithGenericMessage`: a wrong password and an unknown user both give `401` with body `{"error":"Invalid username or password"}`.
  - `loginChangesSessionId`: create a session first, log in, assert that the session id differs.
  - `meReturnsCurrentUser` and `meWithoutSessionIs401WithJsonAndNoWwwAuthenticateHeader`.
  - `logoutInvalidatesSession`: after logout, `GET /api/auth/me` with the old session → `401` (Review Focus 3).
- [ ] **Step 2: Write failing tests** in `EndpointAuthorizationTest`. Use `@WebMvcTest({PortfolioController.class, CompanySnapshotController.class})` with `@MockBean` services; the session comes from `user("admin").roles("ADMIN")` and `user("alice").roles("USER")`. Parameterize over the current endpoints `POST /api/portfolio`, `POST /api/portfolio/sell` and `POST /api/research/snapshots` (Task 5 removes the last one; Task 4 adds `PUT`/`DELETE` here):
  - anonymous → `401`
  - USER with `csrf()` → `403` with body `{"error":"Forbidden"}`
  - ADMIN with `csrf()` → not `401`/`403`
  - ADMIN without `csrf()` → `403`

  Also: `GET /api/research/snapshots` anonymous → `401`, as USER → `200`.
- [ ] **Step 3: Run** both classes. Expected: red.
- [ ] **Step 4: Implement** as described under Interfaces; delete the two old security tests.
- [ ] **Step 5: Run** `-Dtest='AuthControllerTest,EndpointAuthorizationTest,AccountRegistryTest,PortfolioControllerTest,CompanySnapshotControllerTest'`. Expected: green. `PortfolioControllerTest` still contains `GET /api/portfolio/public` until Task 4; it runs with `addFilters = false`, so it is unaffected.
- [ ] **Step 6: Commit (user):** `feat(security): session login with CSRF, JSON 401/403, ADMIN-by-default for writes; remove HTTP Basic`

---

### Task 3: Brute-force protection and login logging

**Concept:** Long passwords make guessing slow, and a limit per IP makes it slower. An account lock stops a distributed attack on one account. The lock gives the same answer as a wrong password, so an attacker learns nothing about which accounts exist.

**Files:**
- Create: `security/LoginAttemptGuard.java`, `security/ClientIpResolver.java`, `security/LoginThrottleInterceptor.java`, `security/LoginThrottleWebConfig.java`
- Modify: `security/AuthController.java`, `security/SecurityConfig.java` (`@EnableConfigurationProperties` for the `trust-forwarded-for` property, or `@Value`)
- Modify: `application.yml`: `app.security.trust-forwarded-for: false`
- Test: `security/LoginAttemptGuardTest.java` (plain JUnit with a mutable test `Clock`), `security/ClientIpResolverTest.java`, extend `AuthControllerTest`

**Interfaces:**
- Consumes: `AccountRegistry.exists`, `AccountRegistry.normalize` (Task 1)
- Produces:
  - `LoginAttemptGuard(Clock clock)`, a `@Component`. Production uses a `Clock.systemUTC()` bean. All operations are thread-safe through `ConcurrentHashMap.compute`.
    - `Optional<Duration> ipBlockRemaining(String ip)`: empty if allowed.
    - `void recordAttempt(String ip)`: counts every attempt, including successful and malformed ones. The 6th attempt within 60 s starts a block.
    - `boolean isAccountLocked(String normalizedUsername)`
    - `void recordFailure(String normalizedUsername)`: the controller calls it only when `AccountRegistry.exists` is true.
    - `void recordSuccess(String normalizedUsername)`: resets the failure count.
  - `ClientIpResolver(boolean trustForwardedFor)`, with `String resolve(HttpServletRequest)`. When the flag is `false`, it returns `getRemoteAddr()`. When `true`, it returns the **rightmost** non-blank entry of `X-Forwarded-For`, falling back to `getRemoteAddr()`.
  - `LoginThrottleInterceptor`, a `HandlerInterceptor` registered by `LoginThrottleWebConfig implements WebMvcConfigurer` for `POST /api/auth/login` only. It runs before body binding, so malformed bodies are counted too. In `preHandle` it:
    1. resolves the IP; if `ipBlockRemaining` is present, it writes `429`, `Retry-After` (seconds, rounded up) and the JSON body, then returns `false`;
    2. otherwise calls `recordAttempt(ip)` and returns `true`.
  - Login flow in `AuthController.login`, after the interceptor:
    1. if the account is locked → `401` with the generic message;
    2. authenticate; on failure `recordFailure` if the account exists → `401`;
    3. on success `recordSuccess`.
  - Logging through SLF4J `security.login`:
    - success: `INFO login success user={} ip={}`
    - failure: `WARN login failure user={} ip={} reason={}` with reason `BAD_CREDENTIALS | ACCOUNT_LOCKED | IP_THROTTLED | MALFORMED`

    Strip CR/LF from the username and cut it to 64 characters. The password is never logged.

- [ ] **Step 1: Write failing tests** in `LoginAttemptGuardTest`:
  - `allowsFiveAttemptsPerMinuteThenBlocksOneMinute`
  - `blockGrowsOneTwoFourEightThenCapsAtFifteenMinutes`
  - `violationCounterResetsAfterOneQuietHour`
  - `locksAccountAfterTenConsecutiveFailuresForFifteenMinutes`
  - `successResetsFailureCount`
  - `lockExpiresAfterFifteenMinutes`
  - `parallelFailuresAreCountedExactly`: 20 threads × 1 failure → locked, and the count is not lost.
  - `purgesExpiredEntriesAboveTenThousand`
- [ ] **Step 2: Write failing tests** in `ClientIpResolverTest`:
  - `ignoresHeaderWhenNotTrusted`
  - `takesRightmostEntryWhenTrusted` (`"1.1.1.1, 2.2.2.2"` → `"2.2.2.2"`)
  - `fallsBackToRemoteAddrOnBlankHeader`
- [ ] **Step 3: Extend `AuthControllerTest`:**
  - `sixthAttemptWithinMinuteIs429WithRetryAfter`
  - `lockedAccountRejectsCorrectPasswordWithGenericMessage`
  - `unknownUsernamesAreNotTracked`: 50 failures for random usernames from different IPs → `admin` is not locked.
  - `malformedLoginBodyIs400AndCounts`: `{}`, `"not json"` and a 10,000-character password → `400`; afterwards the 6th attempt from the same IP → `429` (Review Focus 1).
- [ ] **Step 4: Run.** Expected: red. **Step 5: Implement.** **Step 6: Run** all security tests. Expected: green.
- [ ] **Step 7: Commit (user):** `feat(security): per-IP throttling, per-account lockout, login audit logging`

---

### Task 4: Role-dependent portfolio API with correction and delete

**Concept:** A USER must not receive holdings data at all, so hiding it in the UI is not enough. Two separate response types guarantee that structurally: the USER type has no fields for quantity, price or date.

**Files:**
- Create: `portfolio/PortfolioPositionView.java`, `portfolio/AdminPortfolioPositionView.java`, `portfolio/CorrectPositionRequest.java`
- Delete: `portfolio/PublicPortfolioPositionView.java`
- Modify: `portfolio/PortfolioController.java`, `portfolio/PortfolioService.java`, `portfolio/PortfolioPosition.java`
- Test: `PortfolioControllerTest`, `PortfolioServiceTest`, `PortfolioPositionTest` (all three exist), extend `EndpointAuthorizationTest`

**Interfaces:**
- Produces:
  - `record PortfolioPositionView(String ticker, String isin, String companyName)` (USER). Task 7 adds a `StockGroupRef group` component.
  - `record AdminPortfolioPositionView(String ticker, String isin, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate)`. Task 7 adds `group` after `companyName`.
  - `record CorrectPositionRequest(@NotBlank String ticker, @NotBlank String companyName, @NotNull @Positive BigDecimal quantity, @NotNull @Positive BigDecimal entryPrice, @NotNull LocalDate purchaseDate)`
  - `PortfolioService`:
    - `List<PortfolioPositionView> listForUser()`
    - `List<AdminPortfolioPositionView> listForAdmin()`
    - `void correct(String isin, CorrectPositionRequest)`
    - `void delete(String isin)`

    Every method that takes an `isin` normalizes it with trim + uppercase; an unknown ISIN → `PositionNotFoundException`.
  - `PortfolioPosition.correct(String ticker, String companyName, BigDecimal quantity, BigDecimal entryPrice, LocalDate purchaseDate)` reuses the constructor's validators.
  - Controller:
    - `GET /api/portfolio`: `Authentication` with `ROLE_ADMIN` → admin list, otherwise the user list.
    - `PUT /api/portfolio/{isin}` → `204`
    - `DELETE /api/portfolio/{isin}` → `204`
    - `GET /api/portfolio/public` is removed.

- [ ] **Step 1: Write failing tests:**
  - `PortfolioPositionTest`: `correctReplacesFieldsAndKeepsIsin`, `correctRejectsNonPositiveQuantity`
  - `PortfolioServiceTest`: `correctUnknownIsinThrowsNotFound`, `deleteRemovesPosition`, `correctAndDeleteNormalizeIsin` (`" us0378331005 "`, Review Focus 2)
  - `PortfolioControllerTest`, rewritten to drop `/public`:
    - `adminGetsHoldingsFields`
    - `userResponseHasNoHoldingsKeys`: `jsonPath("$[0].quantity").doesNotExist()`, same for `entryPrice` and `purchaseDate`. With `addFilters = false`, pass the role on the request: `.principal(UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))`; the controller method takes an `Authentication` parameter.
    - `putReturns204`, `putUnknownIsin404`, `deleteReturns204`
  - `EndpointAuthorizationTest`: add `PUT /api/portfolio/US0378331005` and `DELETE /api/portfolio/US0378331005` to the matrix (USER → `403`, ADMIN → not `403`).
- [ ] **Step 2: Run.** Expected: red. **Step 3: Implement.** **Step 4: Run** the portfolio and security tests. Expected: green. Ask the user to run `PortfolioPositionRepositoryTest` locally; it is unchanged but should still pass.
- [ ] **Step 5: Commit (user):** `feat(portfolio): role-dependent list, correct and delete positions; drop public endpoint`

---

### Task 5: Local research ingest with a machine token, validation hardening, sandbox and skill

**Concept:** The sandbox gets a token with exactly one right, and only locally. If the switch is off, the endpoint does not exist. The repo is mounted read-only, so a manipulated session cannot change the scripts that the owner later runs on the host.

**Files:**
- Create in `security`:
  - `MachineTokenProperties.java`
  - `MachineTokenSecurityConfig.java` (chains `@Order(1)` ingest and `@Order(2)` sync)
  - `MachineTokenFilter.java`
  - `TokenHasher.java`
  - `MachineTokenGenerator.java` (`main`)
- Create in `research`: `ResearchIngestController.java`
- Modify:
  - `research/CompanySnapshotController.java`: remove `POST`.
  - `research/CompanySnapshotExceptionHandler.java`: add the new controllers to `assignableTypes`.
  - `research/UpsertCompanySnapshotRequest.java`: ISIN `@Pattern(regexp = "\\s*[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]\\s*")`.
  - `research/FindingRequest.java`: keep `@URL` and add `@Pattern(regexp = "^https?://.+", flags = Pattern.Flag.CASE_INSENSITIVE)`.
  - `application.yml`: `app.research-ingest.enabled: false`, `app.research-sync.enabled: false`.
  - `application-local.yml`: `app.research-ingest.enabled: true`.
  - `dev-up.sh`: abort if `APP_RESEARCH_INGEST_TOKEN_HASH` is empty, pointing to `MachineTokenGenerator`.
  - `docker/claude-sandbox/run.sh`: mount `-v "$REPO_ROOT:/workspace:ro"`; remove `-e ADMIN_USERNAME -e ADMIN_PASSWORD`; add `-e RESEARCH_INGEST_TOKEN -e VALUE_SCREENER_SANDBOX=1`.
  - `.claude/skills/research-company/SKILL.md`:
    - curl uses `-H @<(printf 'Authorization: Bearer %s\n' "$RESEARCH_INGEST_TOKEN")` against `${BACKEND_URL:-http://localhost:8080}/api/research/ingest/snapshots`;
    - new mandatory step before posting: show the full JSON payload to the user and wait for an explicit OK;
    - security rule 3 now refers to `RESEARCH_INGEST_TOKEN`.
  - `.env.example` and `README.md`: generate the token, put the hash in `.env` and export the plaintext only in the shell that starts the sandbox. Note that `.env` is readable from the sandbox (read-only mount), which is why it holds hashes only, and that a strong ADMIN password matters for that reason.
- Test:
  - `security/TokenHasherTest.java`
  - `security/MachineTokenPropertiesTest.java`
  - `security/MachineTokenSecurityTest.java` (two `@WebMvcTest` classes, or `@Nested` with different `@TestPropertySource`: ingest enabled vs. disabled)
  - `research/ResearchIngestControllerTest.java` (new; validation)

**Interfaces:**
- Produces:
  - `TokenHasher`:
    - `static String sha256Hex(String token)`
    - `static boolean matches(String presentedToken, String expectedSha256Hex)` using `MessageDigest.isEqual`; `null` or blank input → `false`
  - `@ConfigurationProperties("app") record MachineTokenProperties(Endpoint researchIngest, Endpoint researchSync)` with `record Endpoint(boolean enabled, String tokenHash)`. Validation: if enabled, `tokenHash` must match `[0-9a-f]{64}`, otherwise `IllegalStateException`.
  - `MachineTokenFilter(String expectedSha256Hex, String authority)`, an `OncePerRequestFilter`. It accepts only `Authorization: Bearer <token>` (case-sensitive scheme, exactly one space, non-empty token without whitespace). On a match it sets a `PreAuthenticatedAuthenticationToken` with the given authority; otherwise it passes the request on without authentication.
  - Authorities `"RESEARCH_INGEST"` and `"RESEARCH_SYNC"`.
  - Chain `researchIngestChain`, `securityMatcher("/api/research/ingest/**")`:
    - enabled → stateless, CSRF off, `MachineTokenFilter`, `anyRequest().hasAuthority("RESEARCH_INGEST")`, JSON 401;
    - disabled → `anyRequest().permitAll()`; no controller is registered, so the result is `404`.
  - Chain `researchSyncChain`: same pattern for `/api/research/sync/**` with `RESEARCH_SYNC`; the controller arrives in Task 8.
  - `@ConditionalOnProperty(prefix = "app.research-ingest", name = "enabled", havingValue = "true") ResearchIngestController` with `@RequestMapping("/api/research/ingest")`: `POST /snapshots` → `CompanySnapshotService.upsert(request)` → `CompanySnapshotView`. Task 7 changes the service signature, Task 8 adds export.
  - `MachineTokenGenerator.main` prints two lines:
    - `token=<base64url, no padding, 32 SecureRandom bytes>`
    - `sha256=<hex>`

- [ ] **Step 1: Write failing tests:**
  - `TokenHasherTest`: a known vector (`sha256Hex("abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"`), `matchesIsFalseForNullBlankAndWrong`
  - `MachineTokenPropertiesTest`: `enabledWithoutHashFails`, `enabledWithNonHexHashFails`, `disabledWithoutHashIsFine`
  - `MachineTokenSecurityTest`, ingest **enabled** (hash of `"test-ingest-token"`):
    - `validTokenCanPostSnapshot` → `200`
    - `missingOrWrongTokenIs401Json`
    - `malformedAuthorizationHeadersAre401` (`Basic x`, `bearer test-ingest-token`, `Bearer `, `Bearer a b`; Review Focus 4)
    - `ingestTokenDoesNotOpenSessionEndpoints` (`GET /api/portfolio` with the token → `401`)
    - `ingestTokenDoesNotOpenSyncPath` (`POST /api/research/sync/snapshots` → `404` or `401`, never `2xx`)
    - `noCsrfNeededOnIngest`
  - `MachineTokenSecurityTest`, ingest **disabled** (default properties): `ingestPathIs404EvenAnonymous` for `POST /api/research/ingest/snapshots`
  - `EndpointAuthorizationTest`: remove `POST /api/research/snapshots` from the matrix and add `oldPostSnapshotsEndpointIsGone` (ADMIN with CSRF → `405`, because the GET on that path still exists)
  - New `research/ResearchIngestControllerTest` (`@WebMvcTest(ResearchIngestController.class)`, `addFilters = false`, `@TestPropertySource(properties = "app.research-ingest.enabled=true")`): `rejectsFtpAndJavascriptSourceUrl` → `400`, `rejectsMalformedIsin` → `400`, `acceptsLowercaseIsin` → `200`
- [ ] **Step 2: Run.** Expected: red. **Step 3: Implement** the Java parts. **Step 4: Run** all security and research tests. Expected: green.
- [ ] **Step 5: Update** `run.sh`, `dev-up.sh`, `SKILL.md`, `.env.example` and README as listed. Check `run.sh` with `bash -n docker/claude-sandbox/run.sh`. Ask the user to start the sandbox once and confirm that Claude Code starts with the read-only `/workspace` (spec 5.5). If it does not, report back; do not remove `:ro`.
- [ ] **Step 6: Commit (user):** `feat(research): local-only ingest endpoint with machine token; read-only sandbox mount; stricter URL/ISIN validation`

---

### Task 6: Stock group catalog (table, seed, CRUD API)

**Concept:** The groups are reference data in the database, so they can be maintained without a deploy. The key never changes, so the local and the live system can refer to the same group. Only the display name can be changed.

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__create_stock_group.sql`
- Create in `research`:
  - `StockGroup.java`, `StockGroupRepository.java`, `StockGroupService.java`, `StockGroupController.java`
  - `StockGroupView.java`, `CreateStockGroupRequest.java`, `UpdateStockGroupRequest.java`
  - `GroupSource.java`
  - `StockGroupNotFoundException.java`, `StockGroupConflictException.java`, `UnknownGroupException.java`
  - `StockGroupExceptionHandler.java`
- Modify:
  - `research/CompanySnapshot.java`: add the fields only, no behaviour yet.
  - `research/CompanySnapshotRepository.java`
- Test: `research/StockGroupTest.java`, `research/StockGroupServiceTest.java` (Mockito), `research/StockGroupControllerTest.java` (WebMvc), `research/StockGroupMigrationTest.java` (Testcontainers; the user runs it), extend `EndpointAuthorizationTest`

**Interfaces:**
- Migration `V4__create_stock_group.sql`:
  - `stock_group(key VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, sort_order INT NOT NULL, version BIGINT NOT NULL DEFAULT 0)`
  - Insert the **35 groups of spec Section 6.2 in table order**, keys and names verbatim, `sort_order` = 10, 20, … 350.
  - `ALTER TABLE company_snapshot ADD COLUMN group_key VARCHAR(40) REFERENCES stock_group(key), ADD COLUMN group_source VARCHAR(10), ALTER COLUMN sector DROP NOT NULL`
  - `CHECK (group_source IN ('AI','MANUAL'))` and `CHECK ((group_key IS NULL) = (group_source IS NULL))`
- `enum GroupSource { AI, MANUAL }`
- `StockGroup(String key, String name, int sortOrder)`:
  - the key is normalized with trim + uppercase and must match `[A-Z][A-Z0-9_]{1,39}`;
  - the name is non-blank, ≤ 100 characters, trimmed;
  - `rename(String)`, `moveTo(int)`, getters.
- `CompanySnapshot`:
  - `@ManyToOne(fetch = LAZY) @JoinColumn(name = "group_key") StockGroup stockGroup`
  - `@Enumerated(STRING) @Column(name = "group_source") GroupSource groupSource`
  - getters `getStockGroup()` and `getGroupSource()`
  - `sector` becomes `nullable = true` (the constructor keeps it until Task 7)
- `StockGroupRepository extends JpaRepository<StockGroup, String>`:
  - `boolean existsByNameIgnoreCase(String name)`
  - `@Query("select coalesce(max(g.sortOrder), 0) from StockGroup g") int maxSortOrder()`
- `CompanySnapshotRepository`: `long countByStockGroup_Key(String key)`
- `record StockGroupView(String key, String name, int sortOrder, long stockCount)`
- `record CreateStockGroupRequest(@NotBlank String key, @NotBlank @Size(max = 100) String name)`
- `record UpdateStockGroupRequest(@Size(max = 100) String name, Integer sortOrder)`: null fields stay unchanged.
- `StockGroupService`:
  - `List<StockGroupView> list()`: sorted by `sortOrder`, then `name`.
  - `StockGroupView create(CreateStockGroupRequest)`: duplicate key or name → `StockGroupConflictException`; `sortOrder = maxSortOrder() + 10`.
  - `StockGroupView update(String key, UpdateStockGroupRequest)`: unknown → `StockGroupNotFoundException`; a name clash → conflict.
  - `void delete(String key)`: unknown → not found; `countByStockGroup_Key > 0` → conflict.
  - `StockGroup require(String key)`: normalizes the key; unknown → `UnknownGroupException`. Used by Task 7.
- `StockGroupController` at `/api/research/groups`:
  - `GET` → `200`
  - `POST` → `201`
  - `PATCH /{key}` → `200`
  - `DELETE /{key}` → `204`
- `StockGroupExceptionHandler`, with `assignableTypes` set to `StockGroupController` plus the research controllers:
  - not found → `404`
  - conflict → `409 {"error": message}`
  - unknown group → `422 {"error":"Unknown group key: <KEY>"}`
  - `IllegalArgumentException` → `400`

- [ ] **Step 1: Write failing tests:**
  - `StockGroupTest`: `normalizesKey`, `rejectsKeyWithSpacesOrLeadingDigit`, `rejectsBlankName`
  - `StockGroupServiceTest`:
    - `listIsSortedBySortOrderThenName`
    - `createAppendsAfterLastSortOrder` (max 350 → 360)
    - `createRejectsDuplicateKeyOrNameCaseInsensitive`
    - `updateRenamesAndKeepsKey`
    - `deleteUsedGroupIsConflict`
    - `requireUnknownThrowsUnknownGroup`
  - `StockGroupControllerTest`: the status codes listed above
  - `StockGroupMigrationTest` (Testcontainers + Flyway):
    - `seeds35GroupsInOrder`: count = 35, the first is `BANKS`, the last is `DIVERSIFIED_OTHER`, `SEMICONDUCTORS` is named "Semiconductors"
    - `groupKeyAndSourceMustBeSetTogether`: inserting a snapshot with `group_key` set and `group_source` null fails
  - `EndpointAuthorizationTest`: `GET /api/research/groups` as USER → `200`; `POST`/`PATCH`/`DELETE` as USER → `403`
- [ ] **Step 2: Run** the non-container tests. Expected: red. **Step 3: Implement.** **Step 4: Run.** Expected: green. Ask the user to run `StockGroupMigrationTest` and the existing repository tests locally.
- [ ] **Step 5: Commit (user):** `feat(research): stock group catalog with 35 seed groups and ADMIN CRUD API`

---

### Task 7: Group assignment (AI proposal, manual override), views, filter, portfolio, skill

**Concept:** The AI proposes, the owner decides. The source of a group (`AI` or `MANUAL`) is stored, and the rule "AI never overwrites MANUAL" lives in the entity, so it applies to every way data comes in.

**Files:**
- Create in `research`: `SnapshotGroupView.java`, `StockGroupRef.java`, `StockGroupLookup.java`, `AssignGroupRequest.java`
- Modify in `research`: `CompanySnapshot.java`, `CompanySnapshotService.java`, `CompanySnapshotView.java`, `UpsertCompanySnapshotRequest.java`, `CompanySnapshotController.java`, `ResearchIngestController.java`
- Modify in `portfolio`: `PortfolioService.java`, `PortfolioPositionView.java`, `AdminPortfolioPositionView.java`
- Modify: `.claude/skills/research-company/SKILL.md`
- Test: `CompanySnapshotTest`, `CompanySnapshotServiceTest`, `CompanySnapshotControllerTest`, `ResearchIngestControllerTest`, `PortfolioServiceTest`, `PortfolioControllerTest`, `EndpointAuthorizationTest`; ask the user to run `CompanySnapshotRepositoryTest`.

**Interfaces:**
- Consumes: `StockGroupService.require`, `GroupSource`, `CompanySnapshot.stockGroup/groupSource` (Task 6)
- Produces:
  - `CompanySnapshot`:
    - the constructor becomes `(String ticker, String isin, String companyName, String country, String businessDescription)`, and `applyUpdate(companyName, country, businessDescription)`. `sector` is no longer written or read; the field stays mapped as nullable.
    - `boolean assignGroup(StockGroup group, GroupSource source)`: returns `false` and changes nothing if the current source is `MANUAL` and `source == AI`; otherwise it sets both fields and returns `true`.
  - `UpsertCompanySnapshotRequest(String ticker, String isin, String companyName, String country, String businessDescription, @NotBlank String groupKey, GroupSource groupSource, List<FindingRequest> findings)`. Existing annotations stay; `sector` is removed.
  - `CompanySnapshotService`:
    - `CompanySnapshotView upsert(UpsertCompanySnapshotRequest request, GroupSource source)`: resolves the group through `require` (→ `422` if unknown) and calls `assignGroup`. When the call returns `false`, it logs `INFO group proposal ignored isin={} proposed={} kept={}`.
    - `CompanySnapshotView assignGroupManually(String isin, String groupKey)`: source `MANUAL`.
    - `List<CompanySnapshotView> list(String groupFilter)`: `null` → all, `"none"` → no group, otherwise the normalized key (an unknown key gives an empty list). This replaces `listAll()`.
  - `record SnapshotGroupView(String key, String name, GroupSource source)`
  - `CompanySnapshotView(Long id, String ticker, String isin, String companyName, String country, String businessDescription, SnapshotGroupView group, List<FindingView> findings)`: `group` is `null` if unassigned, and there is no `sector`.
  - `record StockGroupRef(String key, String name)`
  - `@Component StockGroupLookup` with `Map<String, StockGroupRef> groupsByIsin(Collection<String> isins)`: one repository query, `findAllByIsinIn`.
  - Controller:
    - `GET /api/research/snapshots?group=`
    - `PUT /api/research/snapshots/{isin}/group` with `record AssignGroupRequest(@NotBlank String groupKey)` → `200` `CompanySnapshotView`; ADMIN through default-deny
  - Ingest:
    - `GET /api/research/ingest/groups` → `List<StockGroupView>`
    - `POST /api/research/ingest/snapshots` calls `upsert(request, GroupSource.AI)` and ignores `request.groupSource()` (spec call 11)
  - Portfolio views get a `StockGroupRef group` component (nullable): USER `(ticker, isin, companyName, group)`, ADMIN `(ticker, isin, companyName, group, quantity, entryPrice, purchaseDate)`.
  - Skill:
    - before researching: `GET /api/research/ingest/groups` (same Bearer header);
    - new required field `groupKey`, exactly one key from that list, falling back to `DIVERSIFIED_OTHER` with a one-line reason in the report;
    - `sector` is removed from the example payload;
    - the report names the effective group from the response.

- [ ] **Step 1: Write failing tests:**
  - `CompanySnapshotTest`: `aiCanSetAndReplaceAiGroup`, `aiDoesNotOverwriteManualGroup`, `manualOverwritesManualAndAi`
  - `CompanySnapshotServiceTest`:
    - `upsertUnknownGroupThrowsUnknownGroup`
    - `upsertNormalizesGroupKey` (`" semiconductors "`; Review Focus 5)
    - `assignGroupManuallySetsManual`
    - `assignGroupManuallyNormalizesIsin`
    - `listFiltersByGroupAndNone`
    - `secondAiResearchKeepsManualGroup`
  - `CompanySnapshotControllerTest`:
    - `viewHasGroupAndNoSector` (`jsonPath("$.sector").doesNotExist()`)
    - `putGroupReturnsUpdatedView`
    - `unknownGroupIs422`
  - `ResearchIngestControllerTest`:
    - `ingestIgnoresGroupSourceManualFromBody`: the service is called with `AI`
    - `ingestRequiresGroupKey` → `400`
    - `ingestGroupsListsCatalog`
  - `PortfolioServiceTest`: `positionsCarryGroupFromSnapshotOrNull`
  - `PortfolioControllerTest`: `userViewHasGroupButNoHoldings`
  - `EndpointAuthorizationTest`: `PUT /api/research/snapshots/US0378331005/group` USER → `403`
- [ ] **Step 2: Run.** Expected: red. **Step 3: Implement.** **Step 4: Run** all non-container tests. Expected: green. Ask the user to run `CompanySnapshotRepositoryTest` and `ValueScreenerApplicationTests` locally.
- [ ] **Step 5: Update** `SKILL.md`.
- [ ] **Step 6: Commit (user):** `feat(research): one group per stock, AI-proposed with manual override; group filter; group in portfolio`

---

### Task 8: Export, live import and push script

**Concept:** The live system only accepts reviewed data, and only from the owner's machine. The import endpoint has its own token. The push script runs on the host, never in the sandbox, and passes the tokens without putting them on the command line.

**Files:**
- Create: `research/ResearchSyncController.java`, `scripts/push-research.sh` (executable)
- Modify:
  - `research/ResearchIngestController.java` (export)
  - `research/CompanySnapshotService.java`
  - `research/CompanySnapshotExceptionHandler.java` (`assignableTypes`)
- Modify: `README.md`, with a new section "Recherchedaten ins Live-System übertragen". It covers creating `~/.config/value-screener/live.env` (`chmod 600`) with `LIVE_BACKEND_URL`, `RESEARCH_SYNC_TOKEN` and `RESEARCH_INGEST_TOKEN`; generating the sync token with `MachineTokenGenerator`; that only the hash goes to SSM as `APP_RESEARCH_SYNC_TOKEN_HASH` together with `APP_RESEARCH_SYNC_ENABLED=true`; and that a group added later must be created live as well.
- Test: `CompanySnapshotServiceTest`, `ResearchIngestControllerTest`, `ResearchSyncControllerTest` (new), `MachineTokenSecurityTest` (sync enabled and disabled)

**Interfaces:**
- Consumes: `upsert(request, GroupSource)` (Task 7), the sync chain (Task 5)
- Produces:
  - `CompanySnapshotService.export(String isin) → UpsertCompanySnapshotRequest`:
    - `groupKey` and `groupSource` come from the stored values, the findings are mapped back to `FindingRequest`;
    - unknown ISIN → `404`;
    - a snapshot without a group → `StockGroupConflictException("Assign a group before exporting")` → `409`.
  - `GET /api/research/ingest/snapshots/{isin}/export` → `UpsertCompanySnapshotRequest`
  - `@ConditionalOnProperty(prefix = "app.research-sync", name = "enabled", havingValue = "true") ResearchSyncController`:
    - `POST /api/research/sync/snapshots` calls `upsert(request, request.groupSource() == null ? AI : request.groupSource())` → `200` `CompanySnapshotView`;
    - log `INFO research import isin={} group={} source={} findings={} via=sync-token`.
  - `scripts/push-research.sh <ISIN>`:
    - `set -euo pipefail`, never `set -x`;
    - exits 1 with a message if `VALUE_SCREENER_SANDBOX=1`;
    - sources `~/.config/value-screener/live.env` and exits 1 if the file is missing or readable by group or others;
    - requires `LIVE_BACKEND_URL` to start with `https://`; `http://localhost:<port>` and `http://127.0.0.1:<port>` are also accepted, for the local end-to-end check;
    - reads the export from `${LOCAL_BACKEND_URL:-http://localhost:8080}` and posts it to `$LIVE_BACKEND_URL/api/research/sync/snapshots`;
    - passes both tokens through `-H @<(printf …)`, never as arguments;
    - prints the ISIN and the HTTP status of both calls, and exits non-zero on any non-2xx.

- [ ] **Step 1: Write failing tests:**
  - `CompanySnapshotServiceTest`:
    - `exportRoundTripsThroughUpsert`: export, then upsert the result into an empty repository mock, gives equal views
    - `exportWithoutGroupIsConflict`
    - `exportCarriesManualSource`
  - `ResearchIngestControllerTest`: `exportReturnsImportPayload`, `exportWithoutGroupIs409`
  - New `ResearchSyncControllerTest` (`@WebMvcTest(ResearchSyncController.class)`, `addFilters = false`, `@TestPropertySource(properties = "app.research-sync.enabled=true")`):
    - `syncAppliesManualSourceFromBody`
    - `syncDefaultsMissingSourceToAi`
    - `syncUnknownGroupIs422`
  - `MachineTokenSecurityTest`, sync **enabled** (hash of `"test-sync-token"`):
    - `syncTokenCanImport`
    - `ingestTokenCannotImport` → `401`
    - `syncTokenCannotUseIngest` → `401`, or `404` when ingest is disabled
  - `MachineTokenSecurityTest`, sync **disabled**: `syncPathIs404EvenAnonymous`
- [ ] **Step 2: Run.** Expected: red. **Step 3: Implement** the Java parts. **Step 4: Run** all non-container tests. Expected: green.
- [ ] **Step 5: Write** `scripts/push-research.sh` and check it with `bash -n scripts/push-research.sh`. Also run `VALUE_SCREENER_SANDBOX=1 scripts/push-research.sh US0378331005`, which must exit 1 with the sandbox message. Ask the user for one end-to-end check on their machine: local backend (profile `local`), a second local backend instance on port 8081 with `APP_RESEARCH_SYNC_ENABLED=true` posing as "live" (`LIVE_BACKEND_URL=http://localhost:8081`); push one researched ISIN and read it back from the 8081 instance.
- [ ] **Step 6: Update** README.
- [ ] **Step 7: Commit (user):** `feat(research): export + token-protected live import; push script for the host`

---

## Finish

- [ ] Ask the user to run the full suite `cd backend && mvn test` on a machine with Docker. Expected: all green.
- [ ] Update `PROJECT-STATUS.md`: phase 1 backend done, frontend next. The spec's Section 9 lists the deployment prerequisites.
- [ ] Use superpowers:finishing-a-development-branch. The user does the git operations.
