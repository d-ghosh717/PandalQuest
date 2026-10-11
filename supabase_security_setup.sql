-- ==============================================================================
-- PANDALFINDER / PANDALQUEST — SUPABASE SECURITY & STORAGE ARCHITECTURE
-- ==============================================================================
-- Part 1: Run in Supabase SQL Editor (Application-owned 'public' schema)
-- Part 2: Verify in Supabase Dashboard -> Storage -> Policies (Storage UI)
-- ==============================================================================

-- ── PART 1: APPLICATION DATABASE SETUP (Run in SQL Editor) ──

-- 1. Create the Admin Users allowlist table
CREATE TABLE IF NOT EXISTS public.admin_users (
    firebase_uid TEXT PRIMARY KEY,
    role TEXT NOT NULL DEFAULT 'admin',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Enable RLS on admin_users table
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

-- 3. Deny client writes on admin_users
DROP POLICY IF EXISTS "Deny client writes on admin_users" ON public.admin_users;
CREATE POLICY "Deny client writes on admin_users"
ON public.admin_users
FOR ALL
TO anon, authenticated
USING (false);

-- 4. Allow authenticated reading of admin status
DROP POLICY IF EXISTS "Allow authenticated read on admin_users" ON public.admin_users;
CREATE POLICY "Allow authenticated read on admin_users"
ON public.admin_users
FOR SELECT
TO anon, authenticated
USING (true);

-- 5. Helper function to check if the current user is an authorized admin
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.admin_users
    WHERE firebase_uid = (auth.jwt() ->> 'sub')
      AND role = 'admin'
      AND enabled = true
  );
$$;

GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, anon, service_role;

-- 6. Seed the primary Firebase Admin UID
INSERT INTO public.admin_users (firebase_uid, role, enabled)
VALUES ('mc8obi7qRDhU4Zbk0ViVEWRVvqF2', 'admin', true)
ON CONFLICT (firebase_uid) DO UPDATE
SET role = 'admin', enabled = true;


-- ── PART 2: STORAGE POLICIES SPECIFICATION ──
-- NOTE: In Supabase, 'storage.objects' is owned by 'supabase_storage_admin'.
-- Direct 'ALTER TABLE storage.objects' or 'CREATE POLICY ON storage.objects'
-- from the SQL editor will produce "ERROR: 42501: must be owner of table objects".
--
-- To view or configure these policies, use the Supabase Dashboard:
-- Storage -> Policies -> pandal-photos bucket:
--
-- 1. SELECT (Public Read):
--    Name: "Public Read on Pandal Photos"
--    Target roles: anon, authenticated
--    USING: bucket_id = 'pandal-photos'
--
-- 2. INSERT (Owner or Admin):
--    Name: "Authenticated Insert on Own Folder"
--    Target roles: authenticated
--    WITH CHECK: bucket_id = 'pandal-photos' AND ((storage.foldername(name))[1] = (auth.jwt() ->> 'sub') OR public.is_admin())
--
-- 3. UPDATE (Owner or Admin):
--    Name: "Owner or Admin Update on Photos"
--    Target roles: authenticated
--    USING: bucket_id = 'pandal-photos' AND ((storage.foldername(name))[1] = (auth.jwt() ->> 'sub') OR public.is_admin())
--
-- 4. DELETE (Owner or Admin):
--    Name: "Owner or Admin Delete on Photos"
--    Target roles: authenticated
--    USING: bucket_id = 'pandal-photos' AND ((storage.foldername(name))[1] = (auth.jwt() ->> 'sub') OR public.is_admin())
