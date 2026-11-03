# PlacementHub

> Status: backend v1 features done (Milestones 1–7). Frontend (Milestones 8–9): login, student registration, role-based routing, and all student pages (dashboard, drives with eligibility and apply, my applications with withdraw and round tracking, profile), and all admin pages: companies, drives (create/edit, open/close/complete), applicants with CSV export, rounds, bulk results, selection, placement policies, the audit log and stats. Resume upload is next (Milestone 10).

## Problem

Most tier-2/3 colleges run campus placements through WhatsApp groups, Google Forms and Excel sheets. Eligibility shortlisting is done by hand and is error-prone, placement policies such as "one offer per student" or "dream offer" are enforced inconsistently, and changing a drive's criteria midway causes disputes. **PlacementHub** automates eligibility checks and policy enforcement so that every application decision is correct, explainable (with a clear reason) and auditable.

## Tech stack

| Layer    | Technology |
|----------|------------|
| Backend  | Java 21, Spring Boot 4, Maven (wrapper), Spring Web, Spring Data JPA, Bean Validation |
| Database | PostgreSQL 17 (local via Docker Compose) |
| Frontend | React 19 + Vite (JavaScript), React Router, axios, plain CSS |
| Testing  | JUnit 5, Mockito, MockMvc |
| DevOps   | Docker, GitHub Actions *(planned)* |

## Prerequisites

- **JDK 21** (Eclipse Temurin recommended), with `JAVA_HOME` pointing to it
- **Docker Desktop** (includes Docker Compose v2), with the engine running
- **Node.js 20.19+ or 22.12+** (with npm), for the frontend
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
#    Optional locally: set a JWT signing secret (at least 32 bytes). Without it the app uses a
#    DEV-ONLY secret and logs a WARNING at startup. Always set it outside your own machine.
$env:JWT_SECRET = "paste-a-random-secret-of-at-least-32-bytes-here"
cd backend
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080/api/health>. You should see:

```json
{"status":"UP","database":"UP"}
```

If the database is unreachable, the endpoint returns HTTP **503** with `{"status":"DOWN","database":"DOWN"}`.

To stop the database: `docker compose down`. Your data is kept in the `pgdata` volume.

### Frontend (a second terminal, with the backend running on :8080)

```powershell
cd frontend
npm install      # only the first time, or after package.json changes
npm run dev
```

Open <http://localhost:5173>. Log in with a demo account below, or register a new student.

In dev, the Vite server forwards every `/api/...` request to `http://localhost:8080` (the proxy in `frontend/vite.config.js`). The browser sees one origin, so CORS isn't involved. `npm run build` writes a production build to `frontend/dist/`.

## Database and demo data

The schema is managed by **Flyway** migrations in `backend/src/main/resources/db/migration`, which run automatically when the app starts:

- `V1__init.sql`: all tables, constraints and indexes
- `V2__seed_demo_data.sql`: demo data: 1 admin, 6 students (CSE/ECE/ME), 3 fictional companies, 5 drives, default placement policies
- `V3__audit_types_and_longer_reason.sql`: audit rows for round results and policy changes; `status_reason` widened to 1000 characters

Migrations are never edited after they are applied; every schema change is a new `V<n>__...sql` file.

**Demo accounts (demo only, never use in production):**

| Role | Email | Password |
|---|---|---|
| TPO admin | `admin@placementhub.dev` | `Demo@1234` |
| Student | `aarav.sharma@student.placementhub.dev` (and 5 others in `V2__seed_demo_data.sql`) | `Demo@1234` |

> ⚠️ This password is public because it is in this README. Before deployment, the admin account must be created from environment variables (see [decisions](docs/decisions.md)).

To reset the local database to a fresh seeded state: `docker compose down -v`, then `docker compose up -d`, then start the app. **This deletes all local data.**

## Authentication

The API uses stateless **JWT** bearer tokens (HS256, valid for 2 hours).

| Endpoint | Who |
|---|---|
| `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/health` | public |
| `/api/students/me/**` | `STUDENT` |
| `/api/companies/**`, `/api/drives/**`, `/api/policies/**`, `/api/audit/**`, `/api/stats/**` | `TPO_ADMIN` |

- **Students** register themselves with `POST /api/auth/register`. Their academic details are self-reported at registration and read-only afterwards.
- **TPO admin accounts** can't be registered. The demo admin comes from the seed data.

```powershell
# Log in and keep the token
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body '{"email":"admin@placementhub.dev","password":"Demo@1234"}'

# Call a protected endpoint
Invoke-RestMethod -Uri http://localhost:8080/api/companies -Headers @{ Authorization = "Bearer $($login.accessToken)" }
```

Errors use the standard error JSON:
- **401**: no token, or an invalid or expired token.
- **403**: the wrong role for this endpoint.

The Postman collection (`postman/`) logs in automatically: run the **Auth** folder first.

## Admin features (Milestone 7)

| Endpoint | What it does |
|---|---|
| `GET /api/drives/{id}/applications?status=&branch=&minCgpa=&page=&size=` | Applicants with student details and their latest round (sorted by roll number) |
| `GET /api/drives/{id}/applications/export` | The same filters as a CSV download (formula injection escaped) |
| `PUT /api/drives/{id}` | Editing an OPEN drive's criteria re-checks every pending application; the response says how many became `INELIGIBLE` |
| `GET /api/audit?entityType=&entityId=&from=&to=` | The audit log, newest first (who, what, old → new, when) |
| `GET /api/stats` | Total and placed students, placement % (overall and per branch), offers per company, highest/average/median CTC, drives by status |

## Student pages (Milestone 9)

| Page | URL | API |
|---|---|---|
| Dashboard | `/student` | `GET /api/students/me`, `GET /api/students/me/applications?size=100` |
| Open drives | `/student/drives?eligibleOnly=true&page=2` | `GET /api/students/me/drives` |
| Drive detail + Apply | `/student/drives/{id}` | `GET /api/students/me/drives/{id}` (OPEN drives, or ones you applied to), `POST /api/students/me/applications` |
| My applications + Withdraw | `/student/applications` | `GET /api/students/me/applications`, `POST .../applications/{id}/withdraw` |
| Application detail (rounds) | `/student/applications/{id}` | `GET /api/students/me/applications/{id}` |
| Profile | `/student/profile` | `GET` / `PUT /api/students/me` (name and phone only) |

All times are shown in IST.

## Admin pages (Milestone 9)

| Page | URL | API |
|---|---|---|
| Companies (create, edit) | `/admin/companies?page=2` | `GET` / `POST /api/companies`, `PUT /api/companies/{id}` |
| Drives | `/admin/drives?status=OPEN&companyId=1` | `GET /api/drives` |
| New / edit drive | `/admin/drives/new`, `/admin/drives/{id}/edit` | `POST /api/drives`, `PUT /api/drives/{id}` (DRAFT or OPEN only) |
| Drive detail + status actions | `/admin/drives/{id}` | `GET /api/drives/{id}`, `POST .../open`, `.../close`, `.../complete` |
| Applicants + CSV export | `/admin/drives/{id}/applicants?status=&branch=&minCgpa=` | `GET /api/drives/{id}/applications`, `GET .../applications/export` |
| Rounds (list, add, edit) | `/admin/drives/{id}/rounds` | `GET` / `POST /api/drives/{id}/rounds`, `PUT .../rounds/{roundId}` (OPEN or CLOSED) |
| Bulk results | `/admin/drives/{id}/rounds/{roundId}/results` | `PUT .../rounds/{roundId}/results` (CLOSED only, all or nothing) |
| Selection | `/admin/drives/{id}/selection` | `POST /api/drives/{id}/selections` (CLOSED only, all or nothing, no undo) |
| Placement policies | `/admin/policies` | `GET /api/policies`, `PUT /api/policies/{type}` |
| Audit log | `/admin/audit?entityType=&entityId=&from=&to=&page=2` | `GET /api/audit` |
| Stats | `/admin` | `GET /api/stats` |

Times are typed and shown in IST, whatever the computer's timezone. The audit log's from and to are whole IST days (decision 85).

## Tests

```powershell
cd backend
.\mvnw.cmd test                 # unit, MockMvc and repository tests (needs the Docker Postgres running)
npx newman run ..\postman\PlacementHub.postman_collection.json   # API tests, with the app running on :8080
```

Repository tests (`*DbTest`) run against the local Docker Postgres inside a transaction that is rolled back, so your dev data is left unchanged. Moving them to a separate test database is planned for Milestone 11.

## Configuration

The backend reads these environment variables:

| Variable | Default if not set |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local dev values in `backend/src/main/resources/application.yml`. These **must match** `.env.example`, because Spring Boot does not read `.env` (only Docker Compose does). |
| `JWT_SECRET` | a dev-only secret, with a startup **WARNING**. It **must** be set in production, and it must be at least 32 bytes or the app refuses to start. |
| `JWT_EXPIRY` | `PT2H` (2 hours) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`. Comma-separated browser origins allowed to call the API, no trailing slash. Not needed in dev (the Vite proxy is same-origin); set it to the deployed frontend URL in Milestone 12. |

The frontend reads one build-time variable (see `frontend/.env.example`; put overrides in `frontend/.env.local`, which is git-ignored):

| Variable | Default if not set |
|---|---|
| `VITE_API_BASE_URL` | empty: requests go to `/api` on the same origin (the Vite dev proxy). In Milestone 12 it points at the deployed backend, e.g. `https://<your-backend>.onrender.com`. |

## Known limitations

Each one has a decision entry with the reasons and the planned fix.

- **Company names:** uniqueness is case-insensitive in the service but the DB constraint is case-sensitive, so two simultaneous creates could store both "Nimbus" and "nimbus" (decision 16).
- **Company dropdowns** on the drive pages list at most 100 companies, the API's maximum page size (decision 77).
- **Drive date before the registration deadline** returns 409 instead of 400 (decision 78, fix in Milestone 11).
- **Postman runs write to the dev database**, so test companies, drives and students pile up (decision 79, a separate test DB in Milestone 11).
- The student dashboard's status counts cover the 100 most recent applications (decision 70).
- **Results sheet:** there is no endpoint to read one round's results, so a student with a result in a later round is read-only on earlier sheets, and those earlier remarks can't be viewed or edited (decision 80).
- **Selection has no preview:** a placement policy block appears only after you confirm, as a 409 naming each student (decision 81).
- **The audit log shows IDs, not names,** for example "Application #12" (decision 82).
- **Who changed a policy** is visible only in the audit log; the policies page shows when, not who (decision 83).

## Docs

- [Design decisions](docs/decisions.md)
- [Learning log](docs/learning-log.md)
- [Milestone history](docs/milestone-history.md)
