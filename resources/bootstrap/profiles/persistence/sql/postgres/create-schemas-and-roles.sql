CREATE ROLE ${APP_DB_USER} WITH LOGIN PASSWORD '${APP_DB_PASSWORD}';
GRANT ${APP_DB_USER} TO ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS ${APP_DB_SCHEMA} AUTHORIZATION ${APP_DB_USER};
ALTER ROLE ${APP_DB_USER} SET search_path TO ${APP_DB_SCHEMA};{{#keycloak-oidc?}}

CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASSWORD}';
GRANT ${DB_USER} TO ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS ${DB_SCHEMA} AUTHORIZATION ${DB_USER};
ALTER ROLE ${DB_USER} SET search_path TO ${DB_SCHEMA};{{/keycloak-oidc?}}{{#grafana-support?}}

CREATE ROLE ${GF_DATABASE_USER} WITH LOGIN PASSWORD '${GF_DATABASE_PASSWORD}';
GRANT ${GF_DATABASE_USER} TO ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS ${GF_DATABASE_SCHEMA} AUTHORIZATION ${GF_DATABASE_USER};
ALTER ROLE ${GF_DATABASE_USER} SET search_path TO ${GF_DATABASE_SCHEMA};

CREATE ROLE ${GF_DATASOURCE_USER} WITH LOGIN PASSWORD '${GF_DATASOURCE_PASSWORD}';
GRANT USAGE ON SCHEMA ${APP_DB_SCHEMA} TO ${GF_DATASOURCE_USER};
GRANT SELECT ON ALL TABLES IN SCHEMA ${APP_DB_SCHEMA} TO ${GF_DATASOURCE_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${APP_DB_USER} IN SCHEMA ${APP_DB_SCHEMA} GRANT SELECT ON TABLES TO ${GF_DATASOURCE_USER};
ALTER ROLE ${GF_DATASOURCE_USER} SET search_path TO ${APP_DB_SCHEMA};{{/grafana-support?}}
