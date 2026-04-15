-- Runs once on first container startup as the postgres superuser.
-- POSTGRES_USER (paymentgateway) is already created by the Docker entrypoint
-- before this script executes, so we can grant directly to it.

CREATE SCHEMA IF NOT EXISTS public;

GRANT USAGE  ON SCHEMA public TO paymentgateway;
GRANT CREATE ON SCHEMA public TO paymentgateway;
GRANT ALL PRIVILEGES ON DATABASE paymentgateway TO paymentgateway;
