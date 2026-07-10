#!/usr/bin/env bash
#
# One-time migration: re-sort every stored `{attempt}-names.txt.gz` metrics file into byte order
# (C collation), so the on-disk order matches the streaming names-diff comparator (DiffOptions.CODEPOINT
# == Unicode code point == UTF-8 byte order == Postgres LC_COLLATE 'C').
#
# Older files were written by `processNameStrings` with `ORDER BY scientific_name, authorship` (tuple
# order), which disagrees with the byte order of the space-concatenated line at name-prefix boundaries
# (~1% of lines). After the mapper's ORDER BY was changed to sort by the concatenated value under
# COLLATE "C", NEW files are already byte-exact; this script fixes the pre-existing ones.
#
# Only the LINE ORDER changes — the set of lines (and their multiplicity) is preserved. The script is
# IDEMPOTENT: running it on an already-byte-sorted file leaves it unchanged, so it is safe to re-run
# and safe to run over a mix of old and new files.
#
# Usage:  migrate-names-byte-order.sh <file-metrics-repo-root>
#   e.g.  migrate-names-byte-order.sh /var/lib/checklistbank/metrics
#
# Run it on each app host that holds the file-metrics repo, ideally while imports/releases are paused.
set -euo pipefail

root="${1:?usage: $0 <file-metrics-repo-root>}"
[ -d "$root" ] || { echo "not a directory: $root" >&2; exit 2; }

export LC_ALL=C   # byte-order sort, matching Postgres COLLATE "C" and DiffOptions.CODEPOINT

n=0
fail=0
while IFS= read -r -d '' f; do
  tmp="$f.resort.$$"
  if gunzip -c -- "$f" | sort | gzip > "$tmp"; then
    mv -- "$tmp" "$f"        # atomic within the same directory/filesystem
    n=$((n + 1))
    (( n % 1000 == 0 )) && echo "…re-sorted $n files"
  else
    rm -f -- "$tmp"
    echo "FAILED: $f" >&2
    fail=$((fail + 1))
  fi
done < <(find "$root" -type f -name '*-names.txt.gz' -print0)

echo "re-sorted $n names files under $root ($fail failed)"
[ "$fail" -eq 0 ]
