-- V2: demo seed data for local development and demos.
-- DEMO ONLY. Every account's password is "Demo@1234"
-- (BCrypt, cost 10, generated once with Spring Security's BCrypt class).
-- Before deployment (Milestone 12) the admin must come from env vars, not this file.
--
-- Rows are linked by natural keys (email, company name, role title) instead of
-- hard-coded IDs, so the script does not depend on identity values.
-- Deadlines are relative to now() so OPEN drives are still open on a fresh DB.

-- ---------------------------------------------------------------------------
-- Users: 1 TPO admin + 6 students
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, role) VALUES
    ('admin@placementhub.dev',               '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'TPO_ADMIN'),
    ('aarav.sharma@student.placementhub.dev', '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT'),
    ('priya.nair@student.placementhub.dev',   '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT'),
    ('ananya.gupta@student.placementhub.dev', '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT'),
    ('sneha.iyer@student.placementhub.dev',   '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT'),
    ('rohan.verma@student.placementhub.dev',  '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT'),
    ('karan.mehta@student.placementhub.dev',  '$2a$10$hzDfvn4vsA5YCvM/MKCUHujuyX8kpETksLFrjyhZFSrMQ/j320RbS', 'STUDENT');

-- ---------------------------------------------------------------------------
-- Student profiles: 3 branches, varied CGPA / backlogs / graduation year
-- ---------------------------------------------------------------------------
--   Aarav  CSE 2027 8.70 0 -> strong: eligible for most drives
--   Priya  CSE 2027 6.80 0 -> fails the 7.50 CGPA cut-off
--   Ananya CSE 2026 8.00 0 -> wrong graduation year for 2027 drives
--   Sneha  ECE 2027 9.10 0 -> top scorer, non-CSE branch
--   Rohan  ECE 2027 7.20 1 -> blocked where 0 backlogs are allowed
--   Karan  ME  2027 6.20 2 -> core-branch drives only, borderline CGPA
INSERT INTO student_profiles (user_id, full_name, roll_no, branch, graduation_year, cgpa, active_backlogs, phone)
SELECT u.id, s.full_name, s.roll_no, s.branch, s.graduation_year, s.cgpa, s.active_backlogs, s.phone
FROM (VALUES
    ('aarav.sharma@student.placementhub.dev', 'Aarav Sharma', '23CSE001', 'CSE', 2027, 8.70, 0, '9876500001'),
    ('priya.nair@student.placementhub.dev',   'Priya Nair',   '23CSE014', 'CSE', 2027, 6.80, 0, '9876500002'),
    ('ananya.gupta@student.placementhub.dev', 'Ananya Gupta', '22CSE045', 'CSE', 2026, 8.00, 0, '9876500003'),
    ('sneha.iyer@student.placementhub.dev',   'Sneha Iyer',   '23ECE007', 'ECE', 2027, 9.10, 0, '9876500004'),
    ('rohan.verma@student.placementhub.dev',  'Rohan Verma',  '23ECE022', 'ECE', 2027, 7.20, 1, '9876500005'),
    ('karan.mehta@student.placementhub.dev',  'Karan Mehta',  '23ME031',  'ME',  2027, 6.20, 2, '9876500006')
) AS s(email, full_name, roll_no, branch, graduation_year, cgpa, active_backlogs, phone)
JOIN users u ON u.email = s.email;

-- ---------------------------------------------------------------------------
-- Companies (fictional)
-- ---------------------------------------------------------------------------
INSERT INTO companies (name, website, description) VALUES
    ('Nimbus Systems',         'https://nimbus.example.com',     'Product company building cloud developer tools.'),
    ('Kaveri Analytics',       'https://kaveri.example.com',     'Data and analytics consulting firm.'),
    ('Ironbridge Engineering', 'https://ironbridge.example.com', 'Infrastructure and manufacturing engineering.');

-- ---------------------------------------------------------------------------
-- Drives: 3 OPEN, 1 DRAFT, 1 CLOSED
-- ---------------------------------------------------------------------------
INSERT INTO drives (company_id, role_title, job_type, ctc_lpa, location, description,
                    min_cgpa, max_backlogs, graduation_year, registration_deadline, drive_date,
                    status, created_by)
SELECT c.id, d.role_title, d.job_type, d.ctc_lpa, d.location, d.description,
       d.min_cgpa, d.max_backlogs, d.graduation_year, d.registration_deadline, d.drive_date,
       d.status, (SELECT id FROM users WHERE email = 'admin@placementhub.dev')
FROM (VALUES
    -- Dream-level CTC (>= 10 LPA), strict criteria
    ('Nimbus Systems', 'Software Engineer', 'FULL_TIME', 12.00, 'Bengaluru',
     'Backend and platform engineering.',
     7.50, 0, 2027, now() + INTERVAL '20 days', (current_date + 30), 'OPEN'),
    -- Mid CTC, allows 1 backlog
    ('Kaveri Analytics', 'Data Analyst', 'FULL_TIME', 6.50, 'Hyderabad',
     'SQL, dashboards and reporting.',
     6.50, 1, 2027, now() + INTERVAL '14 days', (current_date + 21), 'OPEN'),
    -- Core branches, lenient criteria
    ('Ironbridge Engineering', 'Graduate Engineer Trainee', 'FULL_TIME', 4.50, 'Pune',
     'Rotational trainee programme for core engineers.',
     6.00, 2, 2027, now() + INTERVAL '25 days', (current_date + 35), 'OPEN'),
    -- Not yet published
    ('Nimbus Systems', 'SDE Intern', 'INTERNSHIP', 3.60, 'Remote',
     '6-month internship with a PPO opportunity.',
     7.00, 0, 2027, now() + INTERVAL '40 days', (current_date + 50), 'DRAFT'),
    -- Registration already over
    ('Kaveri Analytics', 'Business Analyst', 'FULL_TIME', 5.00, 'Chennai',
     'Client-facing analytics role.',
     6.00, 0, 2027, now() - INTERVAL '5 days', (current_date + 3), 'CLOSED')
) AS d(company_name, role_title, job_type, ctc_lpa, location, description,
       min_cgpa, max_backlogs, graduation_year, registration_deadline, drive_date, status)
JOIN companies c ON c.name = d.company_name;

-- ---------------------------------------------------------------------------
-- Drive branches
-- ---------------------------------------------------------------------------
INSERT INTO drive_branches (drive_id, branch)
SELECT dr.id, b.branch
FROM (VALUES
    ('Software Engineer',         ARRAY['CSE', 'IT', 'ECE']),
    ('Data Analyst',              ARRAY['CSE', 'IT', 'ECE', 'EEE']),
    ('Graduate Engineer Trainee', ARRAY['ME', 'CE', 'EEE']),
    ('SDE Intern',                ARRAY['CSE', 'IT']),
    ('Business Analyst',          ARRAY['CSE', 'ECE', 'ME'])
) AS x(role_title, branches)
JOIN drives dr ON dr.role_title = x.role_title
CROSS JOIN LATERAL unnest(x.branches) AS b(branch);

-- ---------------------------------------------------------------------------
-- Placement policies: one row per type
-- ---------------------------------------------------------------------------
-- DREAM_OFFER active (one upgrade allowed if new CTC >= 10 LPA); others off.
INSERT INTO placement_policies (type, ctc_threshold_lpa, max_offers, active) VALUES
    ('ONE_OFFER',   NULL,  NULL, FALSE),
    ('DREAM_OFFER', 10.00, NULL, TRUE),
    ('MAX_OFFERS',  NULL,  2,    FALSE);
