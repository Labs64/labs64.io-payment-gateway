-- Shared utility function: auto-update updated_at on every row modification.
-- Called by triggers on all tables that carry an updated_at column.
-- Schema setup (CREATE SCHEMA, GRANT) is handled by docker/initdb/01_init_schema.sql
-- which runs as the postgres superuser before the app connects.
CREATE OR REPLACE FUNCTION public.set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
