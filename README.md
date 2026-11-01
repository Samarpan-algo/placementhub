# PlacementHub

> Status: v0 — project skeleton (Milestone 1). Features are being built milestone by milestone.

## Problem

Most tier-2/3 colleges run campus placements through WhatsApp groups, Google Forms and Excel sheets. Eligibility shortlisting is done by hand and is error-prone, placement policies such as "one offer per student" or "dream offer" are enforced inconsistently, and changing a drive's criteria midway causes disputes. **PlacementHub** automates eligibility checks and policy enforcement so that every application decision is correct, explainable (with a clear reason) and auditable.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 21, Spring Boot 4, Maven (wrapper), Spring Web, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 17 (local via Docker Compose) |
| Frontend | React + Vite *(planned)* |
| Testing  | JUnit 5, Mockito, MockMvc |
| DevOps   | Docker, GitHub Actions *(planned)* |

## Prerequisites

- **JDK 21** (Eclipse Temurin recommended), with `JAVA_HOME` pointing to it
- **Docker Desktop** (includes Docker Compose v2), with the engine running
- **Git**

Maven does not need to be installed. The project uses the Maven wrapper (`mvnw.cmd`).

## Run locally (Windows, PowerShell)

From the repository root:

```powershell
# 1. Create your local env file (only once). .env is git-ignored.
Copy-Item .env.example .env

# 2. Start PostgreSQL and wait until STATUS shows "(healthy)"
docker compose up -d
docker compose ps

# 3. Start the backend
cd backend
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080/api/health>. You should see:

```json
{"status":"UP","database":"UP"}
```

If the database is unreachable, the endpoint returns HTTP **503** with `{"status":"DOWN","database":"DOWN"}`.

To stop the database: `docker compose down`. Your data is kept in the `pgdata` volume.

## Database and demo data

The schema is managed by **Flyway** migrations in `backend/src/main/resources/db/migration`, which run automatically when the app starts:

- `V1__init.sql`: all tables, constraints and indexes
- `V2__seed_demo_data.sql`: demo data: 1 admin, 6 students (CSE/ECE/ME), 3 fictional companies, 5 drives, default placement policies

**Demo accounts (demo only, never use in production):**

| Role | Email | Password |
|---|---|---|
| TPO admin | `admin@placementhub.dev` | `Demo@1234` |
| Student | `aarav.sharma@student.placementhub.dev` (and 5 others in `V2__seed_demo_data.sql`) | `Demo@1234` |

> ⚠️ This password is public because it is in this README. Before deployment, the admin account must be created from environment variables (see [decisions](docs/decisions.md)).

To reset the local database to a fresh seeded state: `docker compose down -v`, then `docker compose up -d`, then start the app. **This deletes all local data.**

## Configuration

The backend reads `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` from environment variables. If they are not set, it falls back to local dev defaults in `backend/src/main/resources/application.yml`. Those defaults **must match** `.env.example`, because Spring Boot does not read `.env` (only Docker Compose does).

## Docs

- [Design decisions](docs/decisions.md)
- [Learning log](docs/learning-log.md)
