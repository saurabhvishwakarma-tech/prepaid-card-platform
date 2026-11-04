#!/usr/bin/env bash
# Runs once, only when the Postgres data volume is empty (official image behaviour).
# Creates databases and login users only; schemas belong to Flyway.
set -e

: "${CORE_DB_PASSWORD:?CORE_DB_PASSWORD is not set}"
: "${HISTORY_DB_PASSWORD:?HISTORY_DB_PASSWORD is not set}"
: "${REWARDS_DB_PASSWORD:?REWARDS_DB_PASSWORD is not set}"
: "${NOTIFICATION_DB_PASSWORD:?NOTIFICATION_DB_PASSWORD is not set}"

create_database() {
  # Values go in as psql variables, so passwords are never spliced into SQL text by bash
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v db="$1" -v usr="$2" -v pw="$3" <<'EOSQL'
CREATE ROLE :"usr" WITH LOGIN PASSWORD :'pw';
CREATE DATABASE :"db" OWNER :"usr";
-- By default every role may connect to every database; only the owner should
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

create_database core_db         core_user         "$CORE_DB_PASSWORD"
create_database history_db      history_user      "$HISTORY_DB_PASSWORD"
create_database rewards_db      rewards_user      "$REWARDS_DB_PASSWORD"
create_database notification_db notification_user "$NOTIFICATION_DB_PASSWORD"