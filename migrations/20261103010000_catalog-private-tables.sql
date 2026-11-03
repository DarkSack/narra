-- Toda lectura del catálogo pasa por su API, que no expone al propietario. Con el JWT de un usuario
-- ya no se leen estas tablas: nadie obtiene owner_id (ni su perfil público en /api/auth/profiles)
-- ni a quién bloqueó otra persona. El servidor usa la clave de administrador.
DROP POLICY IF EXISTS catalog_read ON public.catalog_books;
DROP POLICY IF EXISTS own_blocks ON public.catalog_blocks;
REVOKE ALL ON public.catalog_books FROM anon, authenticated;
REVOKE ALL ON public.catalog_blocks FROM anon, authenticated;

-- Solo las cuentas con una semana de antigüedad cuentan para retirar un libro sin revisión humana;
-- las demás denuncias se guardan igual para el operador (npm run review -- list).
ALTER TABLE public.catalog_reports ADD COLUMN trusted boolean NOT NULL DEFAULT false;
