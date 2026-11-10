# Deployment reference

What each part of a deployment needs: the backend on Render, the database on Supabase Postgres, resumes in Supabase Storage, and the frontend on Vercel. Every value below is a placeholder. Put real values in each platform's settings, never in Git.

Nothing has been deployed yet. The actual deployment is the rest of Milestone 12.

## Blocking before production

**Remove or replace the seeded demo accounts.** `V2__seed_demo_data.sql` creates an admin (`admin@placementhub.dev`) and demo students, all with the password `Demo@1234`, which is public in this repository. Flyway runs that migration on any new database, production included, so anyone could log in as the production admin.

Do not go live until this is fixed and checked: logging in with `admin@placementhub.dev` / `Demo@1234` on the live site must fail.

## Backend (Render)

Create a Web Service from the repo: runtime Docker, root directory `backend`. The `Dockerfile` there builds the image. The health check path is `/api/health`.

| Variable | Required | Example | What it is for |
|---|---|---|---|
| `DB_URL` | Yes | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` | The database, in JDBC format (see the next section) |
| `DB_USERNAME` | Yes | `postgres.<project-ref>` | Database user |
| `DB_PASSWORD` | Yes | `<database-password>` | Database password |
| `JWT_SECRET` | Yes | `<output of: openssl rand -base64 48>` | Signs login tokens. At least 32 bytes. Without it, the app uses a public dev-only secret, so anyone could forge tokens |
| `CORS_ALLOWED_ORIGINS` | Yes | `https://<your-app>.vercel.app` | The browser origins allowed to call the API. Comma-separated, no trailing slash |
| `SUPABASE_URL` | Yes | `https://<project-ref>.supabase.co` | Supabase project URL, for resume storage |
| `SUPABASE_SECRET_KEY` | Yes | `<secret-key>` | Lets the backend read and delete every resume. **Backend only:** never in the frontend, logs or Git |
| `SUPABASE_RESUME_BUCKET` | Yes | `resumes` | The bucket's name. Without these three Supabase values, resume endpoints answer 503 |
| `JWT_EXPIRY` | No | `PT2H` | How long a login lasts (ISO-8601 duration). Default 2 hours |
| `PORT` | Set by Render | | The port the app listens on. Don't set it yourself. Default 8080 |

Never set `SPRING_PROFILES_ACTIVE=test` here. That profile is for tests: it expects a local database and keeps resumes only in memory. `RESUME_STORAGE` should also stay unset.

## Database (Supabase Postgres)

No variables are set on Supabase itself. Take the connection details from the project's **Connect** page and give them to the backend as `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

- Use the **session pooler** connection (port 5432). The direct connection may need IPv6, which Render may not offer, and the transaction pooler (port 6543) doesn't suit Flyway migrations.
- Supabase shows a `postgresql://...` URI. Spring needs the `jdbc:postgresql://host:port/database` form, with the user and password kept in their own variables.
- Flyway creates the tables on the first start. See the blocking item above about the seed data.

## Resume storage (Supabase Storage)

- Create a bucket named `resumes` and keep it **private**. Do not turn on "public bucket".
- The backend stores only each file's path. Viewing a resume uses a signed link that lasts 5 minutes.

## Frontend (Vercel)

Import the repo with root directory `frontend`, build command `npm run build`, output directory `dist`.

| Variable | Required | Example | What it is for |
|---|---|---|---|
| `VITE_API_BASE_URL` | Yes | `https://<your-backend>.onrender.com` | The deployed backend, no trailing slash. It is read at **build** time, so redeploy after changing it. It ends up in public JavaScript, so it must never hold a secret |

`frontend/vercel.json` sends every path to `index.html`, so refreshing a page such as `/admin/drives/5` works. Local development leaves this variable empty and uses the Vite proxy.

## Check after deploying

1. `https://<your-backend>.onrender.com/api/health` shows `{"status":"UP","database":"UP"}`.
2. The Render log has no warning about a dev-only JWT secret or missing Supabase settings.
3. The seeded admin login fails (the blocking item above).
4. Register a student on the live site, upload a resume, and open it.
5. The Supabase bucket still shows as private.
