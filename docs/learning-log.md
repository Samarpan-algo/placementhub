# Learning Log

Short notes, in plain words, on what I learned in each milestone.

---

## Milestone 1: Setup (2026-11-01)

**What I built:** A PostgreSQL database running in Docker, a Spring Boot 4 backend that connects to it, and `GET /api/health`. It returns `200 UP` when the DB is reachable and `503 DOWN` when it isn't.

**What I learned:**
- **Docker Compose** starts Postgres with one command. The named volume (`pgdata`) keeps my data when the container stops, and the healthcheck (`pg_isready`) tells me when the DB is actually ready, not just started.
- **`.env` vs `application.yml`:** Docker Compose reads `.env`, but Spring Boot doesn't. So the Spring config uses `${DB_URL:default}`, which means "use the env var if it's set, otherwise the default". The defaults must match `.env.example`. Real secrets go only in env vars, never in git.
- **Maven wrapper (`mvnw.cmd`):** the project downloads its own Maven version, so nobody has to install Maven.
- **Spring Boot version names:** start.spring.io calls a version `4.1.2.RELEASE`, but on Maven Central it's just `4.1.2`. Reading the "could not resolve parent POM" error told me exactly which artifact was missing.
- **Timezone bug:** Windows told Java my timezone was `Asia/Calcutta` (an old name), and Postgres refused it. Fix: run the JVM in UTC, both in `main()` (normal runs) and in the Surefire config (tests don't call `main()`).
- **Reading a long stack trace:** the useful line is usually the first `FATAL`/`Caused by`, not the last one. Errors like "Unable to determine Dialect" were only knock-on effects of not having a DB connection.
- **Connection pool (HikariCP):** Spring keeps a pool of open DB connections. `try-with-resources` returns the connection to the pool. When the DB is down, the pool waits up to `connection-timeout` (I set it to 5 s) before failing.
- **Health endpoint:** `connection.isValid(2)` asks the driver to really talk to the DB within 2 seconds. A 503 status means "the service is temporarily unavailable", so monitoring tools can tell it apart from a bug (500).
- **Constructor injection:** Spring passes the `DataSource` into the constructor. The field can be `final`, and in tests I can pass a mock directly.

---

## Milestone 2: Database schema with Flyway (2026-11-01)

**What I built:** The full v1 schema (10 tables) as `V1__init.sql`, and demo data as `V2__seed_demo_data.sql`. Both run automatically through Flyway when the app starts.

**What I learned:**
- **Flyway** runs each `V<n>__name.sql` file once, in order, and records it in `flyway_schema_history`. **Never edit a migration that has already run.** Flyway stores a checksum and will refuse to start. To change the schema, add `V3__...`.
- **Spring Boot 4 + Flyway** needs `spring-boot-starter-flyway`, plus `flyway-database-postgresql`, because Flyway 10+ ships each database's support separately.
- **Flyway runs before Hibernate**, so the tables exist before JPA starts. With `ddl-auto: none`, Hibernate never touches the schema.
- **Postgres doesn't index foreign keys automatically**, only PKs and UNIQUE constraints. A `UNIQUE (drive_id, student_id)` index also works as an index on `drive_id`, because it's the first column.
- **CHECK constraints** are a safety net in the DB itself: `cgpa = 11` fails with `ck_student_profiles_cgpa` even if the Java validation has a bug. Naming constraints makes these errors readable.
- **The UNIQUE constraint is my concurrency guard.** Two simultaneous applies can't both succeed. The second one gets `duplicate key ... uq_applications_drive_student`, which I'll turn into a 409 later.
- **ON DELETE RESTRICT vs CASCADE:** RESTRICT refuses to delete a parent that still has children. CASCADE deletes the children too. CASCADE is only safe for rows that mean nothing alone, like `drive_branches`.
- **`INELIGIBLE` vs `REJECTED`:** "the rules changed after you applied" and "the company said no" are different stories, so they get different statuses, plus a `status_reason`.
- **BCrypt** hashes include the cost and a random salt (`$2a$10$<salt><hash>`). The same password gives a different hash each time, and you check it with `checkpw`, not by comparing strings.
- **Testing constraints safely:** `BEGIN; ...; ROLLBACK;` in psql lets me try bad inserts without changing any data.

---

## Milestone 3, Session A: Entities, error handling, pagination, Companies CRUD (2026-11-01)

**What I built:**
- JPA entities for `users`, `companies` and `drives` (with `drive_branches` as an element collection).
- One global error handler.
- A `PageResponse<T>` wrapper.
- A full Companies CRUD API at `/api/companies`.

**What I learned:**
- **`ddl-auto: validate`:** Hibernate checks my entities against the real tables at startup and won't start if they don't match. `Integer` vs `SMALLINT` fails, so I use `Short`.
- **`@ElementCollection`** stores a simple list (here `Set<Branch>`) in its own table without needing a separate entity class.
- **LAZY loading + `open-in-view: false`:** a lazy field can only be loaded while the transaction is open. That's why the service maps entities to DTOs before returning. Otherwise I'd get `LazyInitializationException`.
- **`@RestControllerAdvice`** catches exceptions from every controller in one place. Every error gets the same JSON shape, and a 500 never leaks a stack trace. It's logged on the server instead.
- **Check first, constraint as backstop:** the service checks "name already exists?" for a nice message. The DB UNIQUE constraint still protects me when two requests race. I read the constraint name from the exception to give a friendly 409.
- **201 + `Location` header** is the REST convention for "created". It tells the client the URL of the new resource. Delete returns **204 No Content**.
- **`@Valid` + Bean Validation** (`@NotBlank`, `@Size`, `@URL`) rejects bad input before my service even runs. The handler lists every bad field in one message.
- **Pagination:** `Pageable` reads `?page=0&size=20&sort=name`. I wrap the `Page` in my own `PageResponse` record so the JSON shape is stable. Page size is capped at 100.
- **Testing a race:** I sent 10 identical creates at once with `HttpClient`. 1 was created and 9 got 409, so duplicates were never saved.

---

## Milestone 3, Session B: Drives API (2026-11-01)

**What I built:**
- The Drives API: create (always DRAFT), get, paginated list with `status`/`companyId` filters, and update.
- Status endpoints: `/open`, `/close`, `/complete`.
- 28 unit tests for the drive rules.
- A Postman collection for every Company and Drive endpoint.

**What I learned:**
- **The N+1 problem:** I load a list with 1 query, then Hibernate runs 1 more query *per row* for each lazy field I touch (company, branches). That's 1 + N queries. It looks fine with 5 rows and gets very slow with 100. SQL logging showed 9 queries, and after the fix there were 2.
- **The fix depends on the relationship type:** a many-to-one (Drive → Company) can be JOINed into the same query with `@EntityGraph`. A collection (branches) can't be JOINed when paging, because Hibernate would page in memory. `@BatchSize` loads it for all rows on the page in one `IN (...)` query.
- **State machine in one place:** a `Map` of allowed transitions plus one `changeStatus` method is easier to read and test than `if`s spread across methods. A blocked move gives a 409 that says what *is* allowed.
- **Parameterised tests:** `@ParameterizedTest` + `@MethodSource` ran all 12 from → to combinations from one test method. The expected answers are written separately from the code, so the test really checks the rules.
- **Mockito unit tests** run with no DB and no Spring context, in milliseconds. I mock the repositories, use the real mapper, and use a fixed `Clock`.
- **An injected `Clock`** makes time-based rules testable. "Deadline is exactly now" is a real edge case that I can only test with a fixed time.
- **Program to an interface:** `CurrentUserProvider` hides *how* we know the user. Swapping the demo version for JWT in Milestone 5 won't touch `DriveService`.
- **`getReferenceById`** gives a lazy proxy that's enough to set a foreign key, without a SELECT.
- **Exceptions roll back the transaction:** when an edit failed with a 409 after I had already changed fields on the entity, nothing was saved, because Spring rolls back on a `RuntimeException`.
- **Postman collections:** variables (`{{baseUrl}}`, `{{driveId}}`) plus small test scripts turn a list of requests into a repeatable API test run.

---

## Milestone 4, Session A: Student profile and EligibilityService (2026-11-01)

**What I built:**
- The `StudentProfile` and `Application` entities.
- A temporary `X-Demo-User-Id` header identity.
- `GET`/`PUT /api/students/me`, with only name and phone editable.
- `EligibilityService`, with 6 rules and readable reasons.
- `GET /api/students/me/drives?eligibleOnly=` showing `eligible` and `reasons` per drive.

**What I learned:**
- **A pure function is the easiest thing to test.** `EligibilityService` takes everything as parameters (student, drive, now) and returns a result. Its 23 tests need no Spring, no mocks and no DB, and run in milliseconds.
- **Collect all reasons, don't stop at the first.** A student who fails on branch, CGPA *and* backlogs should see all three at once (Karan did).
- **`BigDecimal`: use `compareTo`, not `equals`.** `7.5` and `7.50` are not `equals` because the scales differ, but `compareTo` says they're equal. Money and grades should always be compared with `compareTo`.
- **Boundaries are where bugs hide.** I tested CGPA exactly at the minimum (passes), backlogs exactly at the maximum (passes) and the deadline exactly now (fails). "At least" means `>=`, and "before" means strictly `<`.
- **Security by structure:** the update DTO simply has no `cgpa` field, so there's no way to change it. The demo sent `cgpa: 9.9`, and Jackson ignored it.
- **Show times in the user's zone, store them in UTC.** The deadline is stored as a UTC `Instant` but shown in messages as "11 Nov 2026, 3:30 PM IST". The zone ID must be `Asia/Kolkata`; the old `Asia/Calcutta` was the cause of the Milestone 1 bug.
- **Filter before you page.** If eligibility is calculated in Java, I must filter the whole list first and *then* cut out the page. Otherwise page 2 of the "eligible only" list could be missing items or come up short. A unit test checks this.
- **`RequestContextHolder`** lets a normal Spring bean read the current HTTP request (here, a header) without passing it through every method.
- **A temporary design should be easy to delete:** the header logic lives in one class marked TODO. Milestone 5 swaps it out, and nothing else changes.

---

## Milestone 4, Session B: Apply, withdraw, my applications (2026-11-01)

**What I built:**
- `POST /api/students/me/applications` (apply), `POST .../{id}/withdraw`, `GET .../applications` and `GET .../applications/{id}`.
- `applicationStatus` in the browse list.
- An optional `details` array in the error JSON.
- 27 new unit tests, and a Students folder in Postman.

**What I learned:**
- **Check-then-insert has a race condition.** Two requests can both check "no application yet" before either inserts. Only the database's `UNIQUE` constraint is atomic, so it is the real guard. My check is just for a friendlier message in the normal case.
- **`saveAndFlush` vs `save`:** `flush` sends the INSERT to the DB *now*, so a constraint error is thrown inside my `try`, where I can turn it into a 409. Otherwise it could only appear at commit time.
- **Testing a race without threads:** I made the mocked repository throw the same `DataIntegrityViolationException` Postgres would cause, and checked that it becomes a 409. A second test checks that a *different* constraint error isn't disguised as "already applied".
- **404 instead of 403 for other people's data:** a 403 confirms the thing exists. Filtering by owner in the query (`findByIdAndStudentId`) makes "not yours" and "doesn't exist" look the same, and the check can't be forgotten.
- **422 vs 409 vs 400:** 400 means a malformed request, 409 a conflict with existing state (a duplicate), and 422 a valid request that business rules reject (not eligible).
- **Extending an API without breaking it:** `@JsonInclude(NON_NULL)` on a new optional field means old responses look exactly the same.
- **Order of checks matters for messages:** checking "already applied" before eligibility avoids telling a student who has already applied that they're "not eligible".
- **Constructor-expression queries** (`SELECT new ...Dto(a.drive.id, a.status)`) load only the columns I need, without entities, which is perfect for "status per drive" lookups.
- **Leave clear hooks for later work:** a marked `POLICY ENGINE HOOK (Milestone 6)` comment shows exactly where the next feature plugs in.
- **Making Postman runs repeatable:** because withdrawal is final, the Students folder first creates a fresh drive, so every run starts from a clean state.
