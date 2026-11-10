# PlacementHub

> Status: backend v1 features done (Milestones 1–7). Frontend (Milestones 8–9): login, student registration, role-based routing, and all student pages (dashboard, drives with eligibility and apply, my applications with withdraw and round tracking, profile), and all admin pages: companies, drives (create/edit, open/close/complete), applicants with CSV export, rounds, bulk results, selection, placement policies, the audit log and stats. Block A (Milestones 10–12a): resume upload to a private Supabase bucket, a separate test database with API tests, and deploy files (Dockerfile, CI, [deploy reference](docs/deploy.md)). The actual deployment is next.

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

# 3. Start the backend. It also reads .env (SUPABASE_* for resumes, JWT_SECRET if you add one).
#    Without JWT_SECRET it uses a DEV-ONLY secret and logs a WARNING. Always set it outside your own machine.
#    Without the SUPABASE_* values everything works except resumes, which answer 503.
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
| Profile + Resume | `/student/profile` | `GET` / `PUT /api/students/me` (name and phone only), `POST /api/students/me/resume` (upload or replace a PDF), `GET /api/students/me/resume` (a 5-minute link) |

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

All tests use a separate database, `placementhub_test`, in the same Docker Postgres. Your dev database and the real resume bucket are never touched: the `test` profile (`application-test.yml`) fixes the database URL, uses port 8081, and keeps resumes in an in-memory fake.

**Create the test database first** (once, with Docker running). `mvnw test` fails until it exists. The same command resets it at any time, and it can only affect `placementhub_test`:

```powershell
docker exec placementhub-postgres psql -U placementhub -d postgres -c "DROP DATABASE IF EXISTS placementhub_test WITH (FORCE)" -c "CREATE DATABASE placementhub_test"
```

**JUnit** (unit, MockMvc and repository tests):

```powershell
cd backend
.\mvnw.cmd test
```

**Postman**, in two terminals. Start the app after a reset, because the tables are created when it starts:

```powershell
# Terminal 1: the app on the test database, port 8081
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=test"

# Terminal 2: the collection's default baseUrl is http://localhost:8081
npx newman run postman\PlacementHub.postman_collection.json
```

## Resumes (Milestone 10)

Resumes live in a **private** Supabase Storage bucket. The backend talks to its REST API (no SDK), behind a `ResumeStorage` interface; tests and the `test` profile use an in-memory fake instead.

| Endpoint | Who | What it does |
|---|---|---|
| `POST /api/students/me/resume` (multipart, field `file`) | Student | Upload or replace. PDF only (content type and the `%PDF` start), at most 2 MB; otherwise 400. The old file is deleted |
| `GET /api/students/me/resume` | Student | `{url, expiresInSeconds: 300}`: a signed link to your own resume, or 404 |
| `GET /api/students/{studentId}/resume` | TPO admin | The same for any student (the "Resume" link on the applicants page) |

The database stores only the file's path (`students/{id}/{uuid}.pdf`); responses show `hasResume`. If Supabase fails, the API answers 502 (an error answer) or 503 (unreachable or not configured) with the usual error JSON.

## Configuration

The backend reads these environment variables. In local dev it also reads the root `.env` (`spring.config.import` in `application.yml`); real environment variables win. The full list for production is in [docs/deploy.md](docs/deploy.md).

| Variable | Default if not set |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local dev values in `backend/src/main/resources/application.yml`. These **must match** `POSTGRES_*` in `.env`, which Docker Compose uses. |
| `JWT_SECRET` | a dev-only secret, with a startup **WARNING**. It **must** be set in production, and it must be at least 32 bytes or the app refuses to start. |
| `JWT_EXPIRY` | `PT2H` (2 hours) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`. Comma-separated browser origins allowed to call the API, no trailing slash. Not needed in dev (the Vite proxy is same-origin); set it to the deployed frontend URL in production. |
| `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `SUPABASE_RESUME_BUCKET` | empty: resume endpoints answer 503. The secret key is **backend only**. |
| `PORT` | `8080` (Render sets it) |

The frontend reads one build-time variable (see `frontend/.env.example`; put overrides in `frontend/.env.local`, which is git-ignored):

| Variable | Default if not set |
|---|---|
| `VITE_API_BASE_URL` | empty: requests go to `/api` on the same origin (the Vite dev proxy). In production it points at the deployed backend, e.g. `https://<your-backend>.onrender.com`. |

## Known limitations

**Blocking before production: remove or replace the seeded demo accounts.** The demo seed creates an admin and students with the public password `Demo@1234`, and Flyway would run it on a production database too. See [docs/deploy.md](docs/deploy.md).

Each of the others has a decision entry with the reasons and the planned fix.

- **Company names:** uniqueness is case-insensitive in the service but the DB constraint is case-sensitive, so two simultaneous creates could store both "Nimbus" and "nimbus" (decision 16).
- **Company dropdowns** on the drive pages list at most 100 companies, the API's maximum page size (decision 77).
- **An old resume link can outlive a replacement:** after a replace the old file is deleted, but a signed link already handed out may still be served from Supabase's CDN cache for a while (decision 103).
- **Resume storage clean-up:** if deleting a replaced file fails it stays in the bucket unused, and a student can replace a resume but not remove it (decision 103).
- The student dashboard's status counts cover the 100 most recent applications (decision 70).
- **Results sheet:** there is no endpoint to read one round's results, so a student with a result in a later round is read-only on earlier sheets, and those earlier remarks can't be viewed or edited (decision 80).
- **Selection has no preview:** a placement policy block appears only after you confirm, as a 409 naming each student (decision 81).
- **The audit log shows IDs, not names,** for example "Application #12" (decision 82).
- **Who changed a policy** is visible only in the audit log; the policies page shows when, not who (decision 83).

## Docs

- [Deployment reference](docs/deploy.md): every environment variable, and what blocks going live
- [Design decisions](docs/decisions.md)
- [Learning log](docs/learning-log.md)
- [Milestone history](docs/milestone-history.md)
