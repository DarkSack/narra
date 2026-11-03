-- Archivos en Storage; nunca PDF, texto ni audio dentro de Postgres.
CREATE TABLE public.catalog_books (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  title text NOT NULL CHECK (length(title) BETWEEN 1 AND 160),
  author text NOT NULL DEFAULT '' CHECK (length(author) <= 160),
  description text NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
  status text NOT NULL DEFAULT 'uploading' CHECK (status IN ('uploading','queued','processing','review','published','rejected','blocked')),
  reason text,
  source_key text,
  pdf_key text,
  original_bytes bigint CHECK (original_bytes BETWEEN 1 AND 104857600),
  pdf_bytes bigint CHECK (pdf_bytes BETWEEN 1 AND 104857600),
  sha256 text CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  pages integer CHECK (pages BETWEEN 1 AND 500),
  checks_passed boolean NOT NULL DEFAULT false,
  checked_at timestamptz,
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (status <> 'published' OR (checks_passed AND checked_at IS NOT NULL AND reviewed_at IS NOT NULL AND pdf_key IS NOT NULL AND sha256 IS NOT NULL AND pages IS NOT NULL AND pdf_bytes IS NOT NULL))
);
CREATE INDEX catalog_published ON public.catalog_books(created_at DESC, id) WHERE status = 'published';
CREATE INDEX catalog_owner ON public.catalog_books(owner_id, created_at DESC);
CREATE INDEX catalog_queue ON public.catalog_books(created_at) WHERE status = 'queued';
ALTER TABLE public.catalog_books ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.catalog_books FROM anon, authenticated;
GRANT SELECT (id,owner_id,title,author,description,status,reason,original_bytes,pdf_bytes,sha256,pages,created_at) ON public.catalog_books TO authenticated;
CREATE POLICY catalog_read ON public.catalog_books FOR SELECT TO authenticated
  USING (status = 'published' OR owner_id = (SELECT auth.uid()));

CREATE TABLE public.catalog_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  book_id uuid NOT NULL REFERENCES public.catalog_books(id) ON DELETE CASCADE,
  reporter_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  reason text NOT NULL CHECK (length(reason) BETWEEN 3 AND 1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(book_id, reporter_id)
);
ALTER TABLE public.catalog_reports ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.catalog_reports FROM anon, authenticated;

-- Solo el backend puede reservar, aprobar, subir o cambiar estados. Un JWT de
-- usuario nunca puede saltarse antivirus o moderación con el API de datos.
CREATE FUNCTION public.reserve_catalog_upload(p_owner uuid, p_title text, p_author text, p_description text)
RETURNS uuid LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE new_id uuid;
BEGIN
  PERFORM pg_advisory_xact_lock(hashtextextended(p_owner::text, 0));
  IF (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner AND created_at > now()-interval '1 day') >= 5
     OR (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner AND status IN ('uploading','queued','processing','review')) >= 3
     OR (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner) >= 100 THEN
    RAISE EXCEPTION 'upload_quota_exceeded';
  END IF;
  INSERT INTO public.catalog_books(owner_id,title,author,description)
    VALUES(p_owner,p_title,p_author,p_description) RETURNING id INTO new_id;
  RETURN new_id;
END $$;
REVOKE ALL ON FUNCTION public.reserve_catalog_upload(uuid,text,text,text) FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.claim_catalog_job() RETURNS SETOF public.catalog_books
LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
BEGIN
  RETURN QUERY UPDATE public.catalog_books SET status='processing', checked_at=now()
    WHERE id=(SELECT id FROM public.catalog_books WHERE status='queued' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
    RETURNING *;
END $$;
REVOKE ALL ON FUNCTION public.claim_catalog_job() FROM PUBLIC, anon, authenticated;

-- Buckets privados sin políticas para clientes. Solo el servidor sirve la copia
-- reconstruida cuando el registro está publicado; jamás expone el original.
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;
