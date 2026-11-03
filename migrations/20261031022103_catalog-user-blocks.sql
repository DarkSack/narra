CREATE TABLE public.catalog_blocks (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  blocked_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  label text NOT NULL CHECK (length(label) BETWEEN 1 AND 160),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,blocked_id), CHECK (user_id <> blocked_id)
);
ALTER TABLE public.catalog_blocks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.catalog_blocks FROM anon, authenticated;
GRANT SELECT ON public.catalog_blocks TO authenticated;
CREATE POLICY own_blocks ON public.catalog_blocks FOR SELECT TO authenticated USING (user_id=(SELECT auth.uid()));
DROP POLICY catalog_read ON public.catalog_books;
CREATE POLICY catalog_read ON public.catalog_books FOR SELECT TO authenticated
USING (owner_id=(SELECT auth.uid()) OR (status='published' AND NOT EXISTS (
  SELECT 1 FROM public.catalog_blocks b WHERE b.user_id=(SELECT auth.uid()) AND b.blocked_id=owner_id
)));
