#!/bin/bash
# Restores the shipped release into the freshly initialised bundle database.
# The stock postgres image runs everything in /docker-entrypoint-initdb.d exactly once, on first boot.
set -euo pipefail

DUMP=/bundle/release.dump
if [ ! -f "$DUMP" ]; then
  echo "No $DUMP found. Mount the bundle data volume at /bundle." >&2
  exit 1
fi

echo "Restoring $DUMP into $POSTGRES_DB"
pg_restore -j "${RESTORE_JOBS:-4}" \
  --no-owner --no-privileges \
  -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$DUMP"

# pg_restore leaves the planner without statistics, and the app starts its first boot indexing right away
echo "Analyzing"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'ANALYZE'

# The marker comes last and the compose healthcheck requires it. The image runs this hook only once: a restore that
# is interrupted leaves a data directory behind, the next boot skips initialisation and would otherwise serve the half
# restored database - without the indexes, which are built last - as if it were complete.
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'CREATE TABLE bundle_restore AS SELECT now() AS finished'
echo "Restore done"
