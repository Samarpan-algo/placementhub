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

---

## Milestone 5, Session A: Authentication with JWT (2026-11-01)

**What I built:**
- Spring Security with stateless JWT (HS256).
- `POST /api/auth/register` (students only) and `POST /api/auth/login`.
- Role rules: `STUDENT` → `/api/students/me/**`; `TPO_ADMIN` → companies and drives.
- JSON 401/403 responses.
- `JwtCurrentUserProvider` replacing the demo header.
- 68 new tests (152 in total), and Postman switched to tokens (61/61).

**What I learned:**
- **A JWT is signed, not encrypted.** Anyone can base64-decode the payload and read `sub`, `role` and `email`, so a token must never hold secrets. The signature means nobody can *change* it: when I edited the payload to say `TPO_ADMIN`, the server answered 401.
- **The request flow:** login → the server signs a token → the client sends `Authorization: Bearer <token>` every time → Spring's filter checks the signature, `exp` and `iss` → the `role` claim becomes `ROLE_…` → the URL rules decide 401, 403 or allowed → my code reads the user id from `sub`.
- **401 vs 403:** 401 means "I don't know who you are" (no token, or a bad one). 403 means "I know who you are, but you're not allowed".
- **Use the framework's security code:** Spring's bearer-token filter and `DaoAuthenticationProvider` handle subtle things I'd probably get wrong, like constant-time comparisons, a dummy hash for unknown emails, and expiry with clock skew.
- **Why CSRF can be off:** CSRF relies on the browser sending cookies by itself. A header token is never sent automatically.
- **Stateless means no logout.** The server keeps nothing, so a token stays valid until it expires. That's why the expiry is short (2 hours).
- **Same error for "unknown email" and "wrong password"** stops attackers from finding real accounts through login. But register's 409 still reveals them, which is a known trade-off.
- **BCrypt's 72-byte limit:** anything longer is silently ignored, so I cap passwords at 72 characters.
- **Normalise input in the record's compact constructor**, so it happens *before* validation (`" A@B.com "` → `"a@b.com"`).
- **Read errors before fixing:** the 401 bodies looked empty in PowerShell, but `curl -i` showed `Content-Length: 142`. The problem was the client, not my code.
- **Programming to an interface paid off:** swapping the header for JWT changed one class, and no service or service test changed.

---

## Milestone 6: Placement Policy Engine (2026-11-02)

**What I built:**
- Three policy rules (`ONE_OFFER`, `DREAM_OFFER`, `MAX_OFFERS`) using the Strategy pattern, plus a `PolicyEngine` that runs every active rule.
- Policies plugged into apply (one 422 with every reason) and into the student browse list.
- Admin endpoints `GET /api/policies` and `PUT /api/policies/{type}`, with per-type validation and a 409 when ONE_OFFER and DREAM_OFFER would both be active.
- 76 new tests (228 in total), and a Policies folder in Postman.

**What I learned:**
- **The Strategy pattern in Spring:** each rule is its own `@Component` implementing `PolicyRule`. Spring injects them all as a `List<PolicyRule>`, and the engine picks one by `type()`. Adding a rule means adding a class; nothing else changes.
- **Interfaces should fit the data:** the first signature had no place for the threshold or limit, and took a `StudentProfile` no rule used. Passing `PolicySettings` in and leaving the profile out made every rule simpler to write and test.
- **Fail fast at startup:** the engine checks that every `PolicyType` has exactly one rule bean. A missing rule crashes the app on boot, not on some student's apply weeks later.
- **Load once, evaluate many times:** browse loads the policies and offers *once* and checks every drive in memory, because the engine is pure logic. The query count stays fixed however many drives there are, and a unit test proves `findSelectedOffers` is called exactly once.
- **Boundaries need their own tests:** "≥ threshold" and "≥ limit" are where bugs hide. I tested a CTC of exactly 10 vs 10.00 (`compareTo`, not `equals`), 9.99, and offers exactly equal to the limit.
- **`SELECT … FOR UPDATE` for check-then-write between rows:** two admins could each see the other policy as inactive and both activate theirs. Locking the rows (in a fixed order, so there's no deadlock) makes the second request wait and then see the conflict. A UNIQUE constraint can't do this, because the rule spans two rows.
- **Reject what doesn't apply instead of ignoring it:** silently dropping `maxOffers` on ONE_OFFER would make an admin think a limit exists. A 400 tells them right away.
- **Know where a rule *can't* be enforced:** policies are checked at apply time, but offers are created later when an admin marks SELECTED. A student can apply to two drives and be selected by both. That's written down as a known limitation, and Milestone 7 must re-check when marking SELECTED.
- **Demo data with SQL, then clean up:** there's no API to mark SELECTED yet, so I inserted one SELECTED row with psql, saw the real 422 messages, and then deleted it and restored the policy rows exactly.

---

## Milestone 7, Session A: Rounds, results, selection, audit log (2026-11-02)

**What I built:**
- An audit log: every application status change (apply, withdraw, round results, selection, policy, completion) writes who, old → new, the reason and the trigger, in the same transaction.
- Rounds for a drive (numbered 1, 2, 3…, fixed after creation), and bulk round results that save all or nothing, with corrections.
- Final selection: it checks the final round and the policies, then re-checks the student's other pending applications. This closes the gap from decision 45.
- Completing a drive rejects whatever is still pending. Students see round-by-round results and remarks.
- 83 new tests (311 in total), and a repeatable Postman lifecycle folder (full collection: 172/172 assertions, run twice).

**What I learned:**
- **Audit in the same transaction:** `Propagation.MANDATORY` makes the audit method refuse to run outside a transaction. A rolled-back change can't leave an audit row behind, and a committed change always has one.
- **One door for every status change:** putting "set status + write audit" in one helper (`StatusChanges`) means no code path can forget the audit. "No change → no row" is decided in one place too.
- **Derive, don't remember:** an application's status is *calculated from all its round results* (a small pure function). So a correction in any round gives the right status, with no "undo" code.
- **All-or-nothing batches:** first validate every item and collect every problem, then save. The admin gets one list of everything wrong, and the DB is never half-updated. Upsert makes resending the fixed sheet safe.
- **400 vs 409 in a batch:** "this id isn't part of this drive" is a bad request. "This application is already SELECTED" is a conflict with current state. The batch returns 400 if any item is a bad request.
- **Reuse the rule engine for a new question:** "may this pending application continue after an offer?" is the same as "could the student apply now?", so the M6 `PolicyEngine` answered it unchanged. With DREAM_OFFER, applications to drives above the threshold correctly stay active.
- **Row locks as a mutex:** `SELECT … FOR UPDATE` on the student's row makes two selections for the same student run one after the other, so both can't pass ONE_OFFER. A UNIQUE constraint can't do that, because the rule spans different drives.
- **Lock first, then load:** if Hibernate has already loaded an application before the lock, it keeps the old copy. So batches read only the ids, lock, and then load the entities fresh.
- **Same lock order everywhere = no deadlocks:** always the drive, then students by ascending id.
- **IDENTITY ids insert immediately:** `save()` on a new entity runs the INSERT right away, so every NOT NULL field must be set *before* `save()`. I caught this before the tests ran.
- **Column limits apply to generated text too:** several joined policy reasons can exceed `VARCHAR(300)`, so the helper cuts them instead of failing the transaction.

---

## Milestone 7, Session B: Admin views, CSV, criteria changes, audit view, stats (2026-11-02)

**What I built:**
- A V3 migration: audit rows for round results and policy changes, and `status_reason` widened to 1000 characters. The audit always keeps the full reason.
- The applicants list for a drive (filters, paging, student details, latest round), plus a CSV export that is safe against formula injection.
- Criteria-change re-evaluation: editing an OPEN drive's criteria re-checks every pending application. The ones that now fail become INELIGIBLE with the reason, and the edit itself is audited (only the changed fields, old → new).
- `jobType` can now change only while a drive is DRAFT. Apply takes a shared lock on the drive.
- `GET /api/audit` (filters, newest first) and `GET /api/stats` (aggregate SQL, median included).
- 108 new tests (419 in total), including repository tests on the real Postgres, and a Postman folder (full collection: 239/239 assertions, run twice).

**What I learned:**
- **Never edit an applied migration.** Flyway checksums them. A change is a new `V3` file that drops and re-adds the CHECK constraints.
- **CSV injection is real:** Excel runs a cell that starts with `=`, `+`, `-` or `@` as a formula. A leading `'` makes it text. Escaping *every* cell is safer than remembering which columns are "safe", because the phone rule lives in the API, not in the DB.
- **Proving "no N+1" instead of claiming it:** Hibernate statistics count the SQL statements. The applicants page runs 5 queries whether it has 1 row or 20.
- **Mocks can't find SQL bugs.** The real-DB tests caught two bugs: PostgreSQL can't guess the type of a null timestamp parameter (`? IS NULL`, fixed with a `CAST`), and `Map.of().get(null)` throws a `NullPointerException`.
- **Rolled-back tests:** a test can even delete rows inside its transaction to start clean; the rollback puts everything back. (It's still the dev DB, so Milestone 11 moves this to a test DB.)
- **`FOR SHARE` vs `FOR UPDATE`:** many applies can hold the shared drive lock at once, but an admin's edit (`FOR UPDATE`) waits for them, and they wait for it. That closed a race where an apply could commit against criteria that had just changed.
- **Reuse the rule engine again:** re-evaluation calls the same `EligibilityService` as apply, so the INELIGIBLE reason reads exactly like an apply rejection.
- **Compare normalised values:** `12` and `12.00` are equal as numbers but not with `BigDecimal.equals`. The drive snapshot fixes the scale to 2, so an edit that changes nothing writes no audit row.
- **Let the database aggregate:** `COUNT … FILTER`, `AVG` and `percentile_cont(0.5)` return a handful of rows; Java only divides counts into percentages.
- **"Average package" needs a definition:** I use each placed student's *best* offer, so a student with two offers isn't counted twice.
- **Postman variable scopes:** `pm.collectionVariables.get('baseUrl')` ignores a `--env-var` override; `pm.variables.get` respects it. Three failing assertions came from scripts calling the *old* app on another port.
