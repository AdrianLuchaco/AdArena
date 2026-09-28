-- =====================================================================
-- V12 · Cerrar la "API automática" de Supabase sobre nuestras tablas
--
-- Supabase publica automáticamente una API REST (PostgREST) sobre las tablas del esquema public,
-- con los roles "anon" y "authenticated". AdArena NO la usa: todo pasa por nuestro backend, que
-- comprueba permisos. Si se quedara abierta, alguien con la dirección del proyecto y su clave
-- pública podría leer o cambiar tablas directamente (emails, puntos…).
--
-- Aquí se quitan todos los permisos de esos roles sobre nuestras tablas, secuencias y funciones, y
-- también sobre las que se creen en el futuro. Es una segunda barrera: en la guía de despliegue
-- además se desactiva esa API en el panel de Supabase.
--
-- En una base de datos PostgreSQL normal (tu ordenador, los tests) esos roles no existen y esta
-- migración no hace nada.
-- =====================================================================

DO $$
DECLARE
    api_role text;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', api_role);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', api_role);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', api_role);
        END IF;
    END LOOP;
END $$;
