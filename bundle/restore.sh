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
echo "Restore done"
