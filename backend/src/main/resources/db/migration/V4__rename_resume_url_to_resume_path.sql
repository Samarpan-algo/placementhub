-- V4: a resume now lives in a PRIVATE storage bucket, so the column holds the object's path
-- (students/{studentId}/{uuid}.pdf), not a URL. Viewing it needs a short-lived signed URL.
ALTER TABLE student_profiles RENAME COLUMN resume_url TO resume_path;
