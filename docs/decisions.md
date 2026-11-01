# Design Decisions

Each entry records what we decided, what else we considered, and why.

---

## 1. Run PostgreSQL locally via Docker Compose
**Milestone:** 1 · **Date:** 2026-11-01

**Decision:** Run the dev database as a `postgres` container defined in `docker-compose.yml`, with credentials taken from a git-ignored `.env` file and data kept in a named volume (`pgdata`).

**Alternatives:**
- Install PostgreSQL directly on Windows.
- Use a cloud database (Neon/Supabase) during development.

**Why:** One command (`docker compose up -d`) gives every developer the same database version and settings, with nothing installed on the OS. A cloud DB would need internet access and would be shared. The named volume keeps data across restarts, and the healthcheck tells us when Postgres is actually ready to accept connections.

---

## 2. PostgreSQL 17 instead of 18
**Milestone:** 1 · **Date:** 2026-11-01

**Decision:** Pin the image to `postgres:17` (a major version, so we still get minor/security updates).

**Alternatives:**
- `postgres:18` (newest major).
- `postgres:latest` (unpinned).

**Why:** Our planned production hosts (Neon/Supabase) fully support 17, so dev and prod stay on the same major version. The 17 image also uses the familiar data directory (`/var/lib/postgresql/data`), whereas 18 changed the volume layout. `latest` was rejected because a silent major upgrade could break the existing data volume.

---

## 3. `spring.jpa.hibernate.ddl-auto: none`. Flyway will own the schema
**Milestone:** 1 · **Date:** 2026-11-01

**Decision:** Hibernate never creates or alters tables. From Milestone 2, the schema is defined only by versioned Flyway SQL migrations (`V1__init.sql`, …).

**Alternatives:**
- `update`: Hibernate changes tables to match the entities.
- `create-drop`: Hibernate rebuilds the schema on every start.

**Why:** `update` can't safely rename or drop columns and leaves no history, and `create-drop` wipes data. Versioned migrations give a reviewable, repeatable schema history that runs the same way locally and in production. We also set `open-in-view: false` so database access happens only inside services, not while the web response is being written.

---

## 4. JVM default timezone set to UTC
**Milestone:** 1 · **Date:** 2026-11-01

**Decision:** Set the JVM timezone to UTC in two places:
- `TimeZone.setDefault(UTC)` at the start of `main()`, which covers VS Code, `java -jar`, Docker and `spring-boot:run`.
- `-Duser.timezone=UTC` in the Surefire `argLine`, which covers tests, because `@SpringBootTest` never calls `main()`.

**Alternatives:**
- Set the timezone to `Asia/Kolkata`.
- Pass `-Duser.timezone` only on the command line.
- Leave the default.

**Why:** On Windows in India, Java reports the legacy zone ID `Asia/Calcutta`. The PostgreSQL JDBC driver sends that ID when it connects, and Postgres 17 rejects it (`FATAL: invalid value for parameter "TimeZone"`), so the app couldn't start. UTC is the usual choice for servers: timestamps are stored the same way whether the app runs on a laptop or on a cloud host, and the frontend converts them to IST for display. A command-line-only flag would be easy to forget.

---

## 5. Hikari connection timeout of 5 seconds
**Milestone:** 1 · **Date:** 2026-11-01

**Decision:** `spring.datasource.hikari.connection-timeout: 5000` (ms).

**Alternatives:** Keep Hikari's default of 30 seconds.

**Why:** When the database is down, any request that needs a connection waits for this timeout. With 30 s, `/api/health` and every other request would hang for half a minute before failing. With 5 s, it returns `503` quickly, which is better for users and for health checks run by the hosting platform.
