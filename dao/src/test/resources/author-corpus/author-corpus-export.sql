-- Flat export of authored names for the author comparison corpus. Read only, safe on a hot standby.
-- See docs/AUTHOR-CORPUS.md
--
--   psql -X -q -v ON_ERROR_STOP=1 -v keys='<k1>,<k2>,...' -f author-corpus-export.sql "<conninfo>" | gzip > author-corpus.tsv.gz
--
-- \copy cannot be used: psql interpolates no variables into it, and a temp table to work around that is refused on a standby.
\if :{?keys}
\else
  \warn 'missing: -v keys=<comma separated dataset keys>'
  \quit
\endif
SET statement_timeout = 0;
COPY (
  SELECT index_id, dataset_key, name_id, rank, code, nom_status, scientific_name, authorship,
         combination_authors, combination_ex_authors, combination_year,
         basionym_authors, basionym_ex_authors, basionym_year, sanctioning_author
  FROM (
    SELECT nm.index_id, n.dataset_key, n.id AS name_id, n.rank, n.code, n.nom_status, n.scientific_name, n.authorship,
           array_to_string(n.combination_authors, '|')    AS combination_authors,
           array_to_string(n.combination_ex_authors, '|') AS combination_ex_authors,
           n.combination_year,
           array_to_string(n.basionym_authors, '|')       AS basionym_authors,
           array_to_string(n.basionym_ex_authors, '|')    AS basionym_ex_authors,
           n.basionym_year, n.sanctioning_author,
           count(*) OVER (PARTITION BY nm.index_id, n.rank) AS grp
    FROM name n
      JOIN name_match nm ON nm.dataset_key = n.dataset_key AND nm.name_id = n.id
    WHERE n.dataset_key IN (:keys)
      AND nm.dataset_key IN (:keys)   -- repeated on purpose: IN lists do not propagate across the join, pruning needs it per table
      AND n.type = 'SCIENTIFIC'
      AND n.authorship IS NOT NULL
      AND (cardinality(n.combination_authors) > 0 OR cardinality(n.basionym_authors) > 0)
      -- the pipe separates the authors of a team below, a name holding one is parser garbage anyway
      AND strpos(array_to_string(n.combination_authors || n.combination_ex_authors
                                 || n.basionym_authors || n.basionym_ex_authors, ''), '|') = 0
  ) x
  WHERE grp > 1
  ORDER BY index_id, rank, dataset_key, name_id
) TO STDOUT WITH (FORMAT text, NULL '', HEADER);
