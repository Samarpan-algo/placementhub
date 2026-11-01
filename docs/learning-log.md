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
