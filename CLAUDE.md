# PlacementHub — Project Guide for Claude

## About me
- Final-year B.Tech CSE student (India). Strong in Java and DSA; basic HTML/CSS; beginner AI/ML.
- New to Spring Boot, React, databases-in-practice, and deployment.
- Goal: LEARN real development AND finish a solid GitHub project I can explain in interviews.
- Limited time: ~2–3 hours/day. Prefer a working v1 with honest known limitations over perfection.

## How to work with me (most important section)
1. **Before each feature:** explain the plan in simple language: what we'll build, which files change, and why. Keep it short.
2. **Boilerplate — you write it:** project setup, config, Docker, entities, DTOs, mappers, simple CRUD, React pages/styling, security config, test scaffolding.
3. **Claude Code writes all code, including core logic** (schema, EligibilityService,
   policy engine, apply/withdraw flow). For core logic: explain the plan and rules
   before writing, write unit tests for every rule, then explain the code line by line.
4. **After each feature, always give me:**
   - A 3–5 line summary of what was built and the request flow (frontend → controller → service → repository → DB → back).
   - 2–3 interview questions about it that I should be able to answer.
   - Append a short entry to `docs/learning-log.md` (what I learned, in plain words).
   - Record any significant design choice in `docs/decisions.md` (decision, alternatives, why).
5. **Git — Claude Code handles commits:**
   - Commit in small steps after each verified, working step, with conventional messages (`feat:`, `fix:`, `test:`, `docs:`, `chore:`).
   - Always work on the milestone branch (one per milestone); never commit directly to `main`.
   - Before every commit, check that `.env` and other secrets are not staged.
   - Never merge into `main` or push to GitHub without asking me first.
   - Before switching branches or merging, tell me so I can stop my running dev servers (backend and frontend); after merging, remind me to restart them.
   - After committing, show me `git log --oneline` for the new commits.
   - Do not add Co-Authored-By, Claude-Session, or any other AI attribution trailers to commit messages.
6. **Never:** commit secrets (use env vars plus `.env.example`), add technologies outside the stack below, or expand scope without asking me.
7. **When I'm stuck on an error:** help me read the error and form a hypothesis first, then fix it.
8. Keep explanations simple and practical. No long theory dumps.

## Problem statement
Most tier-2/3 colleges run placements through WhatsApp, Google Forms, and Excel. Eligibility shortlisting is manual and error-prone, placement policies (one-offer, dream-offer) are enforced inconsistently, and changes to criteria mid-drive cause disputes.
**PlacementHub** automates eligibility and policy enforcement so every application decision is correct, explainable (with a clear reason), and auditable.

## Tech stack (do not add others)
- Backend: Java 21, latest stable Spring Boot, Maven, Spring Web, Spring Data JPA, Spring Security + JWT, Bean Validation, Flyway
- Database: PostgreSQL (local via docker-compose)
- Frontend: React + Vite, React Router, axios. Plain CSS or one simple UI library only.
- Testing: JUnit 5, Mockito, MockMvc
- DevOps: Docker, GitHub Actions (run tests), deploy backend to Render/Railway, DB to Neon/Supabase, frontend to Vercel/Netlify
- Later (v2 only): LLM API for extracting criteria from JD PDFs

## Roles
- `STUDENT`
- `TPO_ADMIN` (seeded via migration/env vars, not public registration)

## v1 scope
**Student**
- Register/login, edit profile, upload resume
- Browse open drives with an "eligible only" filter
- Apply; on rejection, see the exact reason
- Withdraw before the deadline
- Track round-by-round status

**Admin**
- Manage companies; create/edit drives with criteria; open/close drives
- View and filter applicants; add rounds; bulk-update results
- Export applicants to CSV
- Configure placement policies
- View the audit log and basic stats

**Edge features (the "novelty")**
1. **Placement Policy Engine:** configurable rules enforced on every application, each returning allowed/blocked plus a reason. Implement with the Strategy pattern:
   - `interface PolicyRule { PolicyType type(); PolicyResult evaluate(PolicySettings settings, Drive drive, List<Offer> offers); }`
     (Changed in M6: settings such as the threshold and limit come from the DB at runtime; no rule needs `StudentProfile`; `Offer` is a small record with the company and CTC, not a lazy entity. See decisions.md #41.)
   - Rules: `ONE_OFFER`, `DREAM_OFFER` (one upgrade allowed if the new CTC ≥ threshold), `MAX_OFFERS`
2. **Criteria change handling:** when an admin edits drive criteria, re-evaluate eligibility and record the change in the audit log.
3. **Audit log:** record drive criteria edits and application status changes (who, what, old → new, when).
4. **Duplicate-safe applies under concurrency:** a database unique constraint plus a clean 409 response, verified by a simple load test (k6) later.

**Out of scope (do not build)**
Chat, video interviews, recruiter role (v2), a full college ERP, microservices, Kafka, Kubernetes, refresh tokens (v2), email notifications (v2).

## Architecture
- Package-by-feature: `auth`, `user`, `student`, `company`, `drive`, `application`, `round`, `policy`, `audit`, `stats`, `common`, `config`
- Each feature is layered: Controller → Service → Repository. Business rules live in services only.
- Request/response DTOs; never return entities from controllers.
- Map entities to DTOs inside the service's `@Transactional` methods, so controllers only ever receive DTOs.
  With `open-in-view: false` and LAZY relationships (e.g. Drive → Company), mapping outside the
  transaction throws `LazyInitializationException`.
- One global exception handler with a consistent error JSON: `{timestamp, status, error, message, path}`
- Flyway migrations in `src/main/resources/db/migration`

```
placementhub/
├── backend/            Spring Boot app (package-by-feature)
├── frontend/           React + Vite (api/, auth/, components/, pages/student, pages/admin)
├── docs/               requirements.md, problem-research.md, decisions.md, learning-log.md, diagrams
├── postman/            API collection
├── .github/workflows/  ci.yml
├── docker-compose.yml
└── README.md
```

## Database (starting design; I may refine it)
```
users             (id, email UNIQUE, password_hash, role, created_at)
student_profiles  (id, user_id UNIQUE FK, full_name, roll_no UNIQUE, branch, graduation_year,
                   cgpa NUMERIC(4,2), active_backlogs, phone, resume_path, updated_at)
companies         (id, name UNIQUE, website, description, created_at)
drives            (id, company_id FK, role_title, job_type, ctc_lpa NUMERIC, location, description,
                   min_cgpa, max_backlogs, graduation_year, registration_deadline, drive_date,
                   status, created_by FK, created_at, updated_at)
drive_branches    (drive_id FK, branch)                       PK(drive_id, branch)
applications      (id, drive_id FK, student_id FK, status, status_reason, applied_at, updated_at)  UNIQUE(drive_id, student_id)
drive_rounds      (id, drive_id FK, round_no, name, scheduled_at)       UNIQUE(drive_id, round_no)
round_results     (id, round_id FK, application_id FK, result, remarks) UNIQUE(round_id, application_id)
placement_policies(id, type, ctc_threshold_lpa, max_offers, active, updated_by FK, updated_at)
audit_logs        (id, entity_type, entity_id, action, old_value JSONB, new_value JSONB,
                   actor_id FK, created_at)
```

Enums:
- Drive status: `DRAFT/OPEN/CLOSED/COMPLETED`
- Application status: `APPLIED/SHORTLISTED/REJECTED/SELECTED/WITHDRAWN/INELIGIBLE`
  (`REJECTED` = rejected by the company; `INELIGIBLE` = no longer meets criteria after an admin edit, reason in `status_reason`)
- Round result: `CLEARED/NOT_CLEARED/ABSENT`

## Milestones (build in this order; one branch each)
Each milestone lists what "done" means.

1. **Setup:** repo, docker-compose Postgres, Spring Boot skeleton, `/api/health`, README v0.
   - Done when: the app runs and connects to the DB.
2. **Schema:** Flyway `V1__init.sql` (I design, you review) plus seed data (one admin, sample students, drives).
3. **Companies & Drives CRUD:** DTOs, validation, global error handling, pagination.
   - Done when: everything is testable in Postman.
4. **Student profile and the apply flow:** `EligibilityService` (I write) and apply/withdraw (I write).
   - Done when: unit tests cover all rules.
5. **Security:** register/login, JWT filter, role rules, ownership checks (students touch only their own data).
6. **Policy engine:** Strategy-pattern rules (I write the core), admin policy config, integrated into apply, with tests.
7. **Admin features:** applicants list/filter, rounds and bulk results, CSV export, criteria-change re-evaluation, audit log, stats.
8. **Frontend auth:** Vite setup, API client with token, AuthContext, protected routes, login/register pages.
9. **Frontend pages:** student pages first, then admin pages. Handle loading/error/empty states and show backend error messages.
10. **Resume upload:** to cloud storage, not local disk.
11. **Testing pass:** MockMvc API tests (201/400/401/403/409) and a Postman collection.
12. **Deploy:** Dockerfile, env vars, CI workflow, live URLs.
13. **Polish:** README (problem, features, architecture and ER diagrams, decisions, how to run, testing, known limitations, roadmap), screenshots, seed script.
    - Optional: a k6 load test with real numbers.

## Current status
Block A (M10, M11, M12 deploy files) merged and pushed; CI green. Block B part 1 in progress on
`milestone-12-deploy`: production safety (seed split, env-var admin, prod profile, deploy runbook). Nothing deployed yet.
577 tests, Postman 290/290 (on the test DB). What each milestone delivered: `docs/milestone-history.md`.
