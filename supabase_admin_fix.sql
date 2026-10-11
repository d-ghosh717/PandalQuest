-- ==============================================================================
-- PANDALQUEST — SUPABASE APPLICATION DATABASE & ADMIN AUTHORIZATION FIX
-- ==============================================================================
-- Instructions:
-- 1. Run this script in Supabase Dashboard -> SQL Editor (project: ittdzzuzgkavkeiookin).
-- 2. This script executes cleanly under the default 'postgres' database role.
-- 3. It manages ONLY the application-owned 'public' schema, avoiding Error 42501
--    ("must be owner of table objects") caused by direct DDL on internal 'storage.objects'.
-- ==============================================================================

-- 1. Create public.admin_users table if it does not exist
CREATE TABLE IF NOT EXISTS public.admin_users (
    firebase_uid TEXT PRIMARY KEY,
    role TEXT NOT NULL DEFAULT 'admin',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Enable Row Level Security (RLS) on public.admin_users
ALTER TABLE public.admin_users ENABLE ROW LEVEL SECURITY;

-- 3. Deny client writes on admin_users (can only be managed via Supabase Dashboard / SQL editor)
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

-- 5. Helper function to check if the caller's Firebase UID is an authorized, enabled admin.
-- - SECURITY DEFINER: Executes with creator (postgres) permissions to safely query public.admin_users.
-- - SET search_path = public: Hardens function against search_path hijacking.
-- - auth.jwt() ->> 'sub': Contains the Firebase Authenticated UID verified by Supabase Third-Party Auth.
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

-- Grant execution to authenticated, anon, and service_role
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, anon, service_role;

-- 6. Seed the primary Firebase Admin UID
INSERT INTO public.admin_users (firebase_uid, role, enabled)
VALUES ('mc8obi7qRDhU4Zbk0ViVEWRVvqF2', 'admin', true)
ON CONFLICT (firebase_uid) DO UPDATE
SET role = 'admin', enabled = true;
