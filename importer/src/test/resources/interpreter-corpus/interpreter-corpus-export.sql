-- Name terms of verbatim records for the interpreter corpus. Read only, safe on a hot standby.
-- See docs/INTERPRETER-CORPUS.md
--
--   psql -X -q -v ON_ERROR_STOP=1 [-v keys=<k1,k2,...>] [-v sample=<percent>] -f interpreter-corpus-export.sql "<conninfo>" | gzip > interpreter-corpus.tsv.gz
--
-- Writes the name settings of every dataset first, as rows of type 'dataset', then the records of DwC and ColDP:
-- only the terms the name interpretation reads, identical ones of a dataset and row type collapsed into one row that
-- counts them in n. ACEF is left out, its infraspecific records take their species from another record.
-- keys restricts the export to some datasets, sample to a repeatable percentage of the verbatim records.
-- \copy cannot be used: psql interpolates no variables into it.
\if :{?keys}
  \set datasets 'AND dataset_key IN (' :keys ')'
\else
  \set datasets ''
\endif
\if :{?sample}
  \set tablesample 'TABLESAMPLE BERNOULLI (' :sample ') REPEATABLE (42)'
\else
  \set tablesample ''
\endif
SET statement_timeout = 0;
COPY (
  SELECT d.dataset_key, 'dataset' AS type, s.terms, 0 AS n
  FROM (SELECT key AS dataset_key, settings FROM dataset) d
    CROSS JOIN LATERAL (
      SELECT jsonb_object_agg(e.key, e.value) AS terms
      FROM jsonb_each(d.settings) e
      WHERE e.key IN ('nomenclatural code', 'prefer name atoms', 'dont infer ranks', 'epithet add hyphen')
    ) s
  WHERE s.terms IS NOT NULL :datasets
  ORDER BY d.dataset_key
) TO STDOUT WITH (FORMAT text, HEADER);
COPY (
  SELECT v.dataset_key, v.type, t.terms, count(*) AS n
  FROM verbatim v :tablesample
    CROSS JOIN LATERAL (
      SELECT jsonb_object_agg(e.key, e.value) AS terms
      FROM jsonb_each_text(v.terms) e
      WHERE e.value <> '' AND e.key = ANY (CASE v.type
        WHEN 'dwc:Taxon' THEN ARRAY['dwc:scientificName', 'dwc:scientificNameAuthorship', 'dwc:taxonRank',
          'dwc:verbatimTaxonRank', 'dwc:namePublishedInYear', 'dwc:genericName', 'dwc:genus', 'dwc:infragenericEpithet',
          'dwc:specificEpithet', 'dwc:infraspecificEpithet', 'dwc:cultivarEpithet', 'dwc:nomenclaturalCode',
          'dwc:nomenclaturalStatus']
        ELSE ARRAY['col:scientificName', 'col:authorship', 'col:rank', 'col:uninomial', 'col:genus', 'col:genericName',
          'col:infragenericEpithet', 'col:specificEpithet', 'col:infraspecificEpithet', 'col:cultivarEpithet',
          'col:combinationAuthorship', 'col:combinationExAuthorship', 'col:combinationAuthorshipYear',
          'col:basionymAuthorship', 'col:basionymExAuthorship', 'col:basionymAuthorshipYear', 'col:notho',
          'col:originalSpelling', 'col:code', 'col:publishedInYear', 'col:namePublishedInYear',
          -- the name status of a Name record, but the taxonomic status of a NameUsage record
          CASE v.type WHEN 'col:Name' THEN 'col:status' ELSE 'col:nameStatus' END]
      END)
    ) t
  WHERE v.type IN ('dwc:Taxon', 'col:Name', 'col:NameUsage') :datasets
    AND t.terms IS NOT NULL
  GROUP BY v.dataset_key, v.type, t.terms
) TO STDOUT WITH (FORMAT text);
