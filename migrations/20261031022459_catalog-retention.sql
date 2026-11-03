CREATE OR REPLACE FUNCTION public.reserve_catalog_upload(p_owner uuid, p_title text, p_author text, p_description text)
RETURNS uuid LANGUAGE plpgsql SET search_path = pg_catalog, public, pg_temp AS $$
DECLARE new_id uuid := gen_random_uuid(); total_bytes bigint;
BEGIN
  -- Reserva global + por usuario en el mismo orden, para acotar Storage sin carreras.
  PERFORM pg_advisory_xact_lock(hashtextextended('narra.catalog.storage', 0));
  PERFORM pg_advisory_xact_lock(hashtextextended(p_owner::text, 0));
  SELECT coalesce(sum(
    CASE WHEN source_key IS NOT NULL THEN coalesce(original_bytes,104857600) ELSE 0 END +
    CASE WHEN pdf_key IS NOT NULL THEN coalesce(pdf_bytes,104857600) ELSE 0 END
  ),0) INTO total_bytes FROM public.catalog_books;
  IF total_bytes + 104857600 > 10737418240 OR (SELECT count(*) FROM public.catalog_books) >= 100000 THEN
    RAISE EXCEPTION 'catalog_storage_quota_exceeded';
  END IF;
  IF (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner AND created_at > now()-interval '1 day') >= 5
     OR (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner AND status IN ('uploading','queued','processing','review')) >= 3
     OR (SELECT count(*) FROM public.catalog_books WHERE owner_id=p_owner) >= 100 THEN
    RAISE EXCEPTION 'upload_quota_exceeded';
  END IF;
  -- Conocer la clave ANTES de subir permite limpiar incluso una caída entre Storage y Postgres.
  INSERT INTO public.catalog_books(id,owner_id,title,author,description,source_key)
    VALUES(new_id,p_owner,p_title,p_author,p_description,new_id::text || '/source.pdf');
  RETURN new_id;
END $$;
REVOKE ALL ON FUNCTION public.reserve_catalog_upload(uuid,text,text,text) FROM PUBLIC, anon, authenticated;
