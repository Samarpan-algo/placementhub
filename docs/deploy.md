# Deployment runbook

Step by step, for Windows: the database on **Supabase**, the backend on **Render**, the frontend on **Vercel**. Do the steps in order; each one needs something from the one before.

Rules for the whole runbook:
- Everything in `<angle brackets>` is a placeholder: type your own value, without the brackets.
- Real values go only into the Supabase, Render and Vercel dashboards. Never into a file in this repository, never into a commit.
- Button names are as of writing. If a dashboard looks different, look for the same words nearby.

## Before you start

- [ ] The code you want to deploy is merged into `main` and pushed, and CI is green.
- [ ] A Supabase project exists, with a **private** Storage bucket named `resumes` (Milestone 10).
- [ ] You have accounts on Render and Vercel that can see your GitHub repository.
- [ ] A notepad (not a file in the repo) to collect the values below.

**Generate the JWT secret** in PowerShell, and keep the output for step (b):

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

It prints 64 random characters (48 random bytes). Use a new one per deployment; never reuse the example values from any doc.

**Choose the admin login**: an email and a strong password of 8 to 72 characters. The app creates this TPO admin on its first start. The demo accounts (`Demo@1234`) never exist in production: the demo seed runs only in the dev and test profiles.

## (a) Supabase: the database connection

1. Open your project on <https://supabase.com/dashboard>.
2. Click **Connect** (top of the project page).
3. Choose the **Session pooler** connection (not "Direct connection", not "Transaction pooler"). The direct connection may need IPv6, which Render doesn't offer; the transaction pooler doesn't suit Flyway migrations.
4. Supabase shows a URI like:

   ```text
   postgresql://postgres.<project-ref>:[YOUR-PASSWORD]@<pooler-host>:5432/postgres
   ```

5. Split it into three values for Render:

   | Render variable | What to type | From the URI |
   |---|---|---|
   | `DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres` | `jdbc:` + `postgresql://` + the part after `@` |
   | `DB_USERNAME` | `postgres.<project-ref>` | the part before `:` |
   | `DB_PASSWORD` | your database password | the one you set when creating the project. Forgot it? **Project Settings → Database → Reset database password** |

Don't add `?sslmode=...` yourself: the backend always requires SSL in production.

Also note the **Project URL** (`https://<project-ref>.supabase.co`) and the **secret key** (Project Settings → API keys) for resume storage.

## (b) Render: the backend

1. On <https://dashboard.render.com>: **New → Web Service**, connect GitHub and choose this repository.
2. Fill in:

   | Field | Value |
   |---|---|
   | Name | `<placementhub-api>` (it becomes part of the URL) |
   | Branch | `main` |
   | Region | the one closest to your Supabase region (Render has no Sydney: for Supabase `ap-southeast-2` choose **Singapore**). It can't be changed later without a new service |
   | Root Directory | `backend` |
   | Language / Runtime | **Docker** (Render finds `backend/Dockerfile`) |
   | Instance type | Free |

3. Under **Environment Variables**, add each of these (**Add Environment Variable**, name on the left, value on the right):

   | Name | What to type | What it is for |
   |---|---|---|
   | `DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres` | the database, from step (a) |
   | `DB_USERNAME` | `postgres.<project-ref>` | database user |
   | `DB_PASSWORD` | `<database-password>` | database password |
   | `JWT_SECRET` | the output of the PowerShell command | signs login tokens. Required: without it the app refuses to start |
   | `ADMIN_EMAIL` | `<your-admin-email>` | the TPO admin created on the first start |
   | `ADMIN_PASSWORD` | `<your-admin-password>` | that admin's password (stored only as a hash, never logged) |
   | `CORS_ALLOWED_ORIGINS` | `https://placeholder.invalid` for now | the frontend's address; you replace it in step (d) |
   | `SUPABASE_URL` | `https://<project-ref>.supabase.co` | resume storage |
   | `SUPABASE_SECRET_KEY` | `<supabase-secret-key>` | full access to the bucket. **Backend only**: never in Vercel, never in the frontend |
   | `SUPABASE_RESUME_BUCKET` | `resumes` | the bucket's name |

   **Do not add** `SPRING_PROFILES_ACTIVE` (the Docker image already runs as `prod`), `PORT` (Render sets it), or `DB_SSLMODE` (local checks only). `JWT_EXPIRY` is optional (default `PT2H`, 2 hours). The two `SPRING_FLYWAY_...` variables are needed only in one case, see [First start on a Supabase database](#first-start-on-a-supabase-database).

4. Open **Advanced** and set **Health Check Path** to `/api/health`.
5. Click **Create Web Service** (or **Deploy**). The first build takes several minutes. On the free instance **every** deploy (also "Save and deploy" after an environment change) takes about 10 minutes: the app needs about 2 minutes to start, then the deploy stays **In progress** ("Waiting for internal health check") until Render switches traffic over. Meanwhile the old instance keeps serving, so the site stays up. Wait for **Live**; only after about 15 minutes without it is something wrong.
6. Watch **Logs**. A good start shows, in this order:
   - `The following 1 profile is active: "prod"`
   - Flyway migrating to version 5
   - `Created the TPO admin account <your-admin-email> from ADMIN_EMAIL`
   - `Tomcat started on port ...`
7. Note the service URL at the top: `https://<placementhub-api>.onrender.com`.

If it stops instead, the last lines say why. The usual ones:

| Log says | Fix |
|---|---|
| `JWT_SECRET is required in production` | add `JWT_SECRET` (at least 32 bytes) |
| `Could not resolve placeholder 'DB_URL'` (or `CORS_ALLOWED_ORIGINS`, ...) | that variable is missing |
| `set ADMIN_EMAIL and ADMIN_PASSWORD` | the database has no admin yet: add both |
| `password authentication failed` / `Tenant or user not found` | `DB_USERNAME` must be `postgres.<project-ref>`; check `DB_PASSWORD` |
| `Connection refused` / timeout to the database | use the **session pooler** host from step (a), port 5432 |
| `Found non-empty schema(s) "public" but no schema history table` | Supabase put a function in `public`: see the next section |

### First start on a Supabase database

New Supabase projects can create the function `public.rls_auto_enable` and an event trigger that turns on Row Level Security for every new table in `public`. Flyway sees that function, finds `public` "not empty" without its history table, and refuses to run (the log line in the table above).

**Keep the function.** It only *enables* RLS (it doesn't force it). The tables belong to `postgres`, the user the backend logs in as, and an owner bypasses non-forced RLS, so the app works normally. Meanwhile Supabase's Data API (anon/publishable key) can't read our tables, which is a free security bonus. Check it in the SQL Editor:

```sql
select pg_get_functiondef('public.rls_auto_enable'::regproc);  -- expect "enable row level security", not "force"
```

**Fix, for the first start only.** Add these two variables on Render (Environment), then **Save and deploy**:

| Name | Value |
|---|---|
| `SPRING_FLYWAY_BASELINEONMIGRATE` | `true` |
| `SPRING_FLYWAY_BASELINEVERSION` | `0` |

Flyway then records a baseline at version **0** and runs every migration after it (1, 3, 4, 5); the log shows `Successfully baselined schema with version: 0` and `Successfully applied 4 migrations`. Don't set only `BASELINEONMIGRATE`: its default baseline version is 1, which would mark V1 as applied and **skip creating the tables**.

**After the first successful start, delete both variables** and **Save and deploy**. `flyway_schema_history` exists now, so they would do nothing, but leaving them is a trap: pointed at a wrong, non-empty database, they would make Flyway run on top of it instead of stopping.

After the first successful start the admin exists and the app no longer needs `ADMIN_PASSWORD`. You can delete `ADMIN_EMAIL` and `ADMIN_PASSWORD` from Render then (Render redeploys). Changing them later does **not** change the existing admin's password.

## (c) Vercel: the frontend

1. On <https://vercel.com>: **Add New… → Project**, import this repository.
2. Fill in:

   | Field | Value |
   |---|---|
   | Root Directory | **Edit** → `frontend` |
   | Framework Preset | **Vite** |
   | Build Command | `npm run build` (the default) |
   | Output Directory | `dist` (the default) |

3. Under **Environment Variables** add one:

   | Name | What to type | What it is for |
   |---|---|---|
   | `VITE_API_BASE_URL` | `https://<placementhub-api>.onrender.com` (no trailing slash) | where the browser sends API requests. Read at **build** time and visible in the public JavaScript, so it must never be a secret |

4. Click **Deploy**. Note the production URL, e.g. `https://<your-app>.vercel.app`.

`frontend/vercel.json` makes page refreshes on deep links (such as `/admin/drives/5`) work.

## (d) Render: allow the frontend (CORS)

1. Render → your service → **Environment**.
2. Edit `CORS_ALLOWED_ORIGINS` to exactly the Vercel URL: `https://<your-app>.vercel.app`. No trailing slash, no path. Several origins: comma-separated.
3. **Save Changes** and choose **Save and deploy** (no rebuild needed: the code didn't change).
4. Wait until the deploy shows **Live** (about 10 minutes on the free instance, see step (b)). Until then the old instance, with the old value, still answers, so CORS errors in that window are expected.

Check it from PowerShell: the answer must contain `access-control-allow-origin: https://<your-app>.vercel.app`.

```powershell
curl.exe -s -o NUL -D - -X OPTIONS https://<placementhub-api>.onrender.com/api/auth/login -H "Origin: https://<your-app>.vercel.app" -H "Access-Control-Request-Method: POST"
```

If you change `VITE_API_BASE_URL` on Vercel later, redeploy on Vercel; if the Vercel URL changes, repeat this step.

## (e) Live smoke test

The free Render instance sleeps after inactivity; the first request can take about a minute.

| # | Do | Expect |
|---|---|---|
| 1 | Open `https://<placementhub-api>.onrender.com/api/health` | `{"status":"UP","database":"UP"}` |
| 2 | Render logs | profile `prod`, no `DEV-ONLY` JWT warning, no "SUPABASE ... is not set" warning |
| 3 | Open the Vercel URL, log in as `admin@placementhub.dev` / `Demo@1234` | **refused**: the demo accounts don't exist in production |
| 4 | Log in with your `ADMIN_EMAIL` / `ADMIN_PASSWORD` | the admin home page loads |
| 5 | Browser DevTools (F12) → Console | no CORS errors |
| 6 | Admin → Policies | three policies, all **off** |
| 7 | Admin → Companies → create one; Drives → create a drive and open it | both saved |
| 8 | Log out, register a new student whose branch, CGPA and batch match the drive | account created, student pages load |
| 9 | Student → Profile → upload a small PDF → View my resume | the PDF opens in a new tab |
| 10 | Supabase → Storage → `resumes` | one file under `students/<id>/`; the bucket is still **private** |
| 11 | Student → Drives → apply to the drive | application APPLIED |
| 12 | Admin → the drive's applicants → **Resume** | the student is listed; the resume opens |
| 13 | Refresh the browser on a deep page (e.g. `/admin/drives/1`) | the page loads, no 404 |
| 14 | Render → Environment | `ADMIN_PASSWORD` deleted, if you chose to (step b) |

## Good to know

- **Free-tier sleep:** Render's free instance sleeps after about 15 minutes without traffic; Supabase pauses a free project after a week of inactivity (resume it in the dashboard).
- **Memory:** the image limits Java to about 60% of the instance's memory (`JAVA_TOOL_OPTIONS` in the Dockerfile). On a bigger instance you can override `JAVA_TOOL_OPTIONS` on Render.
- **Secrets that leaked** (pasted somewhere public): rotate them. A new `JWT_SECRET` logs everyone out; a new Supabase key or database password must be updated on Render.
