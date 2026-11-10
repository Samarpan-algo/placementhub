# Milestone history

What each finished milestone delivered, moved here from `CLAUDE.md` so that file stays short. The reasons behind each choice are in [decisions.md](decisions.md).

| Milestone | What was built | Tests |
|---|---|---|
| **M1 Setup** | Docker Postgres 17, Spring Boot 4.1.2 skeleton, `/api/health` with a DB check, README v0. | |
| **M2 Schema** | Flyway V1 schema (10 tables, CHECKs, FK indexes, INELIGIBLE + `status_reason`) and V2 demo seed (1 admin, 6 students, 3 companies, 5 drives, policies). | |
| **M3 Companies & Drives** | Entities (`ddl-auto: validate`), global error handler, `PageResponse`, Companies + Drives CRUD with status transitions, N+1-safe list, `CurrentUserProvider` (demo admin until M5), Postman collection. | 28 unit tests |
| **M4 Profile & apply** | Student profile (name and phone only), `EligibilityService` (6 rules, all reasons), browse with eligible/reasons/applicationStatus, apply (422 + details, race-safe 409), withdraw (OPEN + before the deadline, owner-only 404), `X-Demo-User-Id` header (replaced by JWT in M5). | 84 tests, Postman 76/76 |
| **M5 Security** | Stateless JWT (Spring Resource Server, HS256, 2h, `JWT_SECRET` + dev-only fallback warning), student register (STUDENT only, 409s), login (same 401), role rules + JSON 401/403, `JwtCurrentUserProvider`. | 152 tests, Postman 93/93 |
| **M6 Policy engine** | Strategy-pattern policy engine (offer = SELECTED + FULL_TIME) in apply and browse, admin `/api/policies` (400s, ONE/DREAM 409 under a row lock). | 228 tests, Postman passed |
| **M7 Admin features** | Audit log (results, policies, criteria edits), rounds, bulk results and selection with row locks, applicants list + safe CSV, criteria-change re-evaluation, `/api/audit`, `/api/stats` (decisions 46-61). | 419 tests, Postman 239/239 |
| **M8 Frontend auth** | React + Vite, dev proxy + CORS env var, axios client (Bearer, global 401), AuthContext + role routes, login and register (review step, roll number rule) (decisions 62-66). | 440 tests, Postman 239/239 |
| **M9 Session A** | Student drive detail endpoint (OPEN or applied, else 404) + `driveStatus` on applications, `useApi` hook + shared components (IST dates, badges, pagination, confirm dialog, role nav), all student pages (decisions 67-71). | 459 tests, Postman 251/251 |
| **M9 Session B** | Admin companies, drives (IST deadline, status actions), applicants + CSV; IST drive-date fix (`AppTime.IST`) (decisions 72-79). | 461 tests, Postman 251/251 |
| **M9 Session C** | Admin rounds (add, edit), bulk results, selection, policies, audit log (IST day filters), stats on the admin home; `useFormErrors` for all forms (decisions 80-88). Frontend only. | 461 tests, Postman 251/251 |
| **Block A: M10 Resume upload** | Private Supabase bucket via REST behind `ResumeStorage` (fake for tests), PDF checks (type + `%PDF`, 2 MB, 400s), store → swap path under row lock → delete old, 5-minute signed links (student via `/me`, admin by id), 502/503, `V4` `resume_path`, Profile and applicants pages (decisions 89-97, 103). | |
| **Block A: M11 Testing pass** | `test` profile and `placementhub_test` (port 8081, fake storage, reset command), MockMvc API tests, real-server upload limit test, drive date 400, Postman resume folder (decisions 98-100). | 577 tests, Postman 290/290 |
| **Block A: M12 files** | Multi-stage Dockerfile, CI (Postgres service + frontend build), Vercel rewrite, `docs/deploy.md`; seeded demo accounts recorded as blocking (decisions 101, 102, 104). No deploy yet. | |
