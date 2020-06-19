-- recreate export schema exp{{datasetKey}}
DROP SCHEMA IF EXISTS exp{{datasetKey}} CASCADE;
CREATE SCHEMA exp{{datasetKey}};
SET search_path TO exp{{datasetKey}},public;

CREATE TABLE __coverage AS
    -- ATTACH MODE
    SELECT s.subject_dataset_key, subject_rank AS rank, subject_name AS name, array_to_string(array_remove(classification(3, target_id, true), 'Biota'), ' - ') AS classification
    FROM sector s JOIN name_usage_{{datasetKey}} t ON s.target_id=t.id
    WHERE s.mode='ATTACH' AND s.dataset_key={{datasetKey}}
  UNION ALL
    -- MERGE MODE
    SELECT s.subject_dataset_key, target_rank AS rank, target_name AS name, array_to_string(array_remove(classification(3, target_id, true), 'Biota'), ' - ') AS classification
    FROM sector s JOIN name_usage_{{datasetKey}} t ON s.target_id=t.id
    WHERE s.mode='UNION' AND s.dataset_key={{datasetKey}};

CREATE TABLE __coverage2 AS
    SELECT subject_dataset_key AS dataset_key, string_agg(classification || ' - ' || groups, ';\n') AS coverage FROM (
        SELECT subject_dataset_key, classification, string_agg(name, ', ') AS groups FROM __coverage GROUP BY subject_dataset_key, classification
    ) AS foo GROUP BY subject_dataset_key;
CREATE INDEX ON __coverage2 (dataset_key);


-- databases
COPY (
(
SELECT DISTINCT ON (d.key)
 d.key - 1000 AS record_id,
 CASE WHEN d.alias IS NOT NULL AND d.alias != d.title THEN d.alias || ': ' || d.title ELSE d.title END AS database_name_displayed,
 coalesce(d.alias, d.title) AS database_name,
 d.title AS database_full_name,
 d.website AS web_site,
 array_to_string(d.organisations, '; ') AS organization,
 d.contact AS contact_person,
 d.group AS taxa,
 cov.coverage AS taxonomic_coverage,
 repl_ws(d.description) AS abstract,
 d.version AS version,
 coalesce(d.released, CURRENT_DATE) AS release_date,
 coalesce((i.taxa_by_rank_count -> 'SPECIES')::int, 0) AS SpeciesCount,
 NULL AS SpeciesEst,
 array_to_string(d.authors_and_editors, '; ')  AS authors_editors,
 NULL AS accepted_species_names,
 NULL AS accepted_infraspecies_names,
 NULL AS species_synonyms,
 NULL AS infraspecies_synonyms,
 i.vernacular_count AS common_names,
 i.name_count AS total_names,
 0 AS is_new,
 'Global' AS coverage,
 completeness AS completeness,
 confidence AS confidence
FROM dataset d
    JOIN dataset_import i ON i.dataset_key=d.key
    LEFT JOIN __coverage2 cov ON cov.dataset_key=d.key
WHERE d.key IN (SELECT distinct subject_dataset_key FROM sector WHERE dataset_key={{datasetKey}})
ORDER BY d.key ASC, i.attempt DESC
)

UNION ALL

SELECT
 500 AS record_id,
 'CoL Management Classification' AS database_name_displayed,
 'CoL Management Classification' AS database_name,
 'CoL Management Classification' AS database_full_name,
 NULL AS web_site,
 'Species 2000' AS organization,
 NULL AS contact_person,
 'Biota' AS taxa,
 'Taxa of various ranks' AS taxonomic_coverage,
 'The management classification brings all contributed taxonomic checklists across all kingdoms into a coherent master view, and where possible enforces consistent nomenclature. To place a global species database within the Catalog of Life, a specific set of adjustments was be decided by the editors on where and how to insert it, to make it as consistent as possible, while not losing the essential taxonomic information.' AS abstract,
 '2020' AS version,
 '2020-01-10' AS release_date,
 NULL AS SpeciesCount,
 NULL AS SpeciesEst,
 'CoL editorial team' AS authors_editors,
 NULL AS accepted_species_names,
 NULL AS accepted_infraspecies_names,
 NULL AS species_synonyms,
 NULL AS infraspecies_synonyms,
 NULL AS common_names,
 NULL AS total_names,
 0 AS is_new,
 'Global' AS coverage,
 NULL AS completeness,
 NULL AS confidence

) TO 'databases.csv';

-- create reference int keys
CREATE TABLE __ref_keys (key serial, id text UNIQUE);
INSERT INTO __ref_keys (id) SELECT id FROM reference_{{datasetKey}};

-- references
COPY (
  SELECT rk.key AS record_id, 
    coalesce(
      csl-> 'author' ->0 ->> 'literal', (
       SELECT string_agg(coalesce(aJson->>'given', '') || CASE WHEN aJson ? 'given' AND aJson ? 'family' THEN ' ' ELSE '' END || coalesce(aJson->>'family', ''), ', ')
       FROM jsonb_array_elements(csl->'author') AS aJson
      )
    ) AS author,
    coalesce(
      csl#>>'{issued,literal}',
      (csl#> '{issued,date-parts}'->0->0)::text
    ) AS year,
    csl->>'title' AS title,
    csl->>'container-title' AS source,
    coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id,
    r.id AS reference_code
  FROM reference_{{datasetKey}} r
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.id
    
) TO 'references.csv';


-- estimates
COPY (
  SELECT e.target_id AS name_code,
    e.target_name AS name,
    e.target_rank AS rank,
    e.estimate,
    r.citation AS source,
    e.created AS inserted,
    e.modified AS updated
  FROM estimate e
    LEFT JOIN reference_{{datasetKey}} r ON r.id=e.reference_id
  WHERE e.target_id IS NOT NULL
) TO 'estimates.csv';


-- create usage int keys using a reusable sequence
CREATE SEQUENCE __record_id_seq START 1000;
CREATE TABLE __tax_keys (key int PRIMARY KEY DEFAULT nextval('__record_id_seq'), id text UNIQUE);
INSERT INTO __tax_keys (id) SELECT id FROM name_usage_{{datasetKey}};

-- specialists aka scrutinizer
CREATE TABLE __scrutinizer (key serial, dataset_key int, name text, unique(dataset_key, name));
INSERT INTO __scrutinizer (name, dataset_key)
    SELECT DISTINCT t.scrutinizer, s.subject_dataset_key
        FROM name_usage_{{datasetKey}} t
            LEFT JOIN sector s ON t.sector_key=s.id
        WHERE t.scrutinizer IS NOT NULL;
COPY (
    SELECT key AS record_id, name AS specialist_name, null AS specialist_code, coalesce(dataset_key, 1500) - 1000 AS database_id FROM __scrutinizer
) TO 'specialists.csv';


-- lifezones
-- unnest with empty or null arrays removes the entire row
COPY (
    SELECT
        nextval('__record_id_seq') AS record_id,
        t.id AS name_code,
        unnest(t.lifezones) AS lifezone,
        coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id
    FROM name_usage_{{datasetKey}} t
        JOIN __tax_keys tk ON t.id=tk.id
        LEFT JOIN sector s ON t.sector_key=s.id
    WHERE t.lifezones IS NOT NULL
) TO 'lifezone.csv';


-- create a flattened classification table for all usages incl taxa AND synonyms
CREATE TABLE __classification AS (
WITH RECURSIVE tree AS(
    SELECT
        tk.key AS key,
        s.subject_dataset_key AS dataset_key,
        t.id AS id,
        n.rank AS rank,
        CASE WHEN n.rank='KINGDOM' THEN n.scientific_name ELSE NULL END AS kingdom,
        CASE WHEN n.rank='PHYLUM' THEN n.scientific_name ELSE NULL END AS phylum,
        CASE WHEN n.rank='CLASS' THEN n.scientific_name ELSE NULL END AS "class",
        CASE WHEN n.rank='ORDER' THEN n.scientific_name ELSE NULL END AS "order",
        CASE WHEN n.rank='SUPERFAMILY' THEN n.scientific_name ELSE NULL END AS superfamily,
        CASE WHEN n.rank='FAMILY' THEN n.scientific_name ELSE NULL END AS family,
        CASE WHEN n.rank='GENUS' THEN n.scientific_name ELSE NULL END AS genus,
        CASE WHEN n.rank='FAMILY' THEN t.id ELSE NULL END AS family_id,
        CASE WHEN n.rank='SPECIES' THEN t.id ELSE NULL END AS species_id
    FROM name_usage_{{datasetKey}} t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_{{datasetKey}} n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.id
    WHERE t.parent_id IS NULL AND NOT t.is_synonym
  UNION
    SELECT
        tk.key,
        s.subject_dataset_key,
        t.id,
        n.rank,
        CASE WHEN n.rank='KINGDOM' THEN n.scientific_name ELSE tree.kingdom END,
        CASE WHEN n.rank='PHYLUM' THEN n.scientific_name ELSE tree.phylum END,
        CASE WHEN n.rank='CLASS' THEN n.scientific_name ELSE tree.class END,
        CASE WHEN n.rank='ORDER' THEN n.scientific_name ELSE tree."order" END,
        CASE WHEN n.rank='SUPERFAMILY' THEN n.scientific_name ELSE tree.superfamily END,
        CASE WHEN n.rank='FAMILY' THEN n.scientific_name ELSE tree.family END,
        CASE WHEN n.rank='GENUS' THEN n.scientific_name ELSE tree.genus END,
        CASE WHEN n.rank='FAMILY' THEN t.id ELSE tree.family_id END,
        CASE WHEN n.rank='SPECIES' THEN t.id ELSE tree.species_id END AS species_id
    FROM name_usage_{{datasetKey}} t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_{{datasetKey}} n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.id
        JOIN tree ON (tree.id = t.parent_id) AND NOT t.is_synonym
)
SELECT * FROM tree
);

CREATE INDEX ON __classification (rank);
DELETE FROM __classification WHERE rank < 'FAMILY'::rank;
-- now we have only families and below left

-- create incertae sedis families if missing
CREATE SEQUENCE __unassigned_seq START 1;
CREATE INDEX ON __classification (family_id);
CREATE INDEX ON __classification (dataset_key, coalesce(kingdom,''), coalesce(phylum,''), coalesce(class,''), coalesce("order",''), coalesce(superfamily))
    WHERE family_id=NULL;
CREATE TABLE __classification2 (LIKE __classification);
ALTER  TABLE __classification2 ALTER COLUMN key SET DEFAULT nextval('__record_id_seq');
ALTER  TABLE __classification2 ALTER COLUMN id  SET DEFAULT 'inc.sed-' || nextval('__unassigned_seq');
ALTER  TABLE __classification2 ALTER COLUMN family_id  SET DEFAULT 'inc.sed-' || currval('__unassigned_seq');
INSERT  INTO __classification2 (dataset_key, rank, kingdom, phylum ,class, "order", superfamily, family)
    SELECT DISTINCT dataset_key, 'FAMILY'::rank, kingdom, phylum ,class, "order", superfamily, 'Not assigned'
        FROM __classification
        WHERE family_id IS NULL;
CREATE UNIQUE INDEX ON __classification2 (dataset_key, coalesce(kingdom,''), coalesce(phylum,''), coalesce(class,''), coalesce("order",''), coalesce(superfamily));
UPDATE __classification c SET family_id=f.id
    FROM __classification2 f
    WHERE c.family_id IS NULL
        AND c.dataset_key=f.dataset_key
        AND coalesce(c.kingdom,'')    =coalesce(f.kingdom,'')
        AND coalesce(c.phylum,'')     =coalesce(f.phylum,'')
        AND coalesce(c.class,'')      =coalesce(f.class,'')
        AND coalesce(c."order",'')    =coalesce(f."order",'')
        AND coalesce(c.superfamily,'')=coalesce(f.superfamily,'');
INSERT INTO __classification SELECT * FROM __classification2;
CREATE UNIQUE INDEX ON __classification (id);


-- families export
COPY (
SELECT key AS record_id,
      NULL AS hierarchy_code,
      kingdom, 
      coalesce(phylum, 'Not assigned') AS phylum,
      coalesce(class, 'Not assigned') AS class,
      coalesce("order", 'Not assigned') AS "order",
      coalesce(family, 'Not assigned') AS family,
      superfamily, 
      coalesce(dataset_key, 1500) - 1000 AS database_id,
      id AS family_code, 
      1 AS is_accepted_name
    FROM __classification
    WHERE rank='FAMILY'
) TO 'families.csv';


-- scientific_names
COPY (
SELECT
  tk.key AS record_id,
  t.id AS name_code,
  t.link AS web_site,
  -- use the genus classification for virus type names
  CASE
    WHEN n.type='VIRUS' THEN coalesce(c.genus, 'Not assigned')
    ELSE (CASE WHEN n.notho='GENERIC' THEN '×' ELSE '' END) ||  n.genus
  END AS genus,
  n.infrageneric_epithet AS subgenus,
  CASE
    -- parsable names
    WHEN n.type IN ('SCIENTIFIC','INFORMAL') THEN (CASE WHEN n.notho='SPECIFIC' THEN '×' ELSE '' END) || n.specific_epithet
    -- unparsable ones
    ELSE n.scientific_name
  END AS species,
  CASE WHEN n.rank > 'SPECIES'::rank THEN c.species_id ELSE NULL END AS infraspecies_parent_name_code,
  CASE WHEN n.notho='INFRASPECIFIC' THEN '×' ELSE '' END || n.infraspecific_epithet AS infraspecies,
  CASE WHEN n.rank > 'SPECIES'::rank THEN r.marker ELSE NULL END AS infraspecies_marker,  -- uses __ranks table created in AcExporter java code!
  CASE
    WHEN n.type IN ('SCIENTIFIC','INFORMAL') THEN
        repl_ws(n.authorship)
    -- unparsable ones
    ELSE NULL
  END AS author,
  CASE WHEN t.is_synonym THEN t.parent_id ELSE t.id END AS accepted_name_code,
  t.remarks AS comment,
  t.scrutinizer_date AS scrutiny_date,
  CASE WHEN t.status='ACCEPTED' THEN 1
       WHEN t.status='PROVISIONALLY_ACCEPTED' THEN 4
       WHEN t.status='SYNONYM' THEN 5
       WHEN t.status='AMBIGUOUS_SYNONYM' THEN 2
       WHEN t.status='MISAPPLIED' THEN 3
  END AS sp2000_status_id, -- 1=ACCEPTED, 2=AMBIGUOUS_SYNONYM, 3=MISAPPLIED, 4=PROVISIONALLY_ACCEPTED, 5=SYNONYM
                           -- Java: ACCEPTED,PROVISIONALLY_ACCEPTED,SYNONYM,AMBIGUOUS_SYNONYM,MISAPPLIED
  CASE WHEN t.is_synonym THEN coalesce(cs.dataset_key, 1500) - 1000 ELSE coalesce(c.dataset_key, 1500) - 1000 END AS database_id,
  sc.key AS specialist_id,
  cf.key AS family_id,
  NULL AS specialist_code,
  c.family_id AS family_code,
  CASE WHEN t.is_synonym THEN 0 ELSE 1 END AS is_accepted_name,
  NULL AS GSDTaxonGUID,
  NULL AS GSDNameGUID,
  CASE WHEN t.extinct THEN 1 ELSE 0 END AS is_extinct,
  0 AS has_preholocene,
  0 AS has_modern
FROM name_{{datasetKey}} n
    JOIN name_usage_{{datasetKey}} t ON n.id=t.name_id
    LEFT JOIN __classification c  ON t.id=c.id
    LEFT JOIN __classification cs ON t.parent_id=cs.id
    LEFT JOIN __classification cf ON c.family_id=cf.id
    LEFT JOIN __ranks r ON n.rank=r.key
    LEFT JOIN __scrutinizer sc ON t.scrutinizer=sc.name AND c.dataset_key=sc.dataset_key
    LEFT JOIN __tax_keys tk ON t.id=tk.id
WHERE n.rank >= 'SPECIES'::rank

UNION
-- empty genera, see https://github.com/Sp2000/colplus-backend/issues/637
SELECT
  tk.key AS record_id,
  t.id AS name_code,
  t.link AS web_site,
  n.uninomial AS genus,
  NULL AS subgenus,
  NULL AS species,
  NULL AS infraspecies_parent_name_code,
  NULL AS infraspecies,
  NULL AS infraspecies_marker,
  NULL AS author,
  CASE WHEN t.is_synonym THEN t.parent_id ELSE t.id END AS accepted_name_code,
  t.remarks AS comment,
  t.scrutinizer_date AS scrutiny_date,
  1 AS sp2000_status_id, -- 1=ACCEPTED
  coalesce(c.dataset_key, 1500) - 1000 AS database_id,
  sc.key AS specialist_id,
  cf.key AS family_id,
  NULL AS specialist_code,
  c.family_id AS family_code,
  1 AS is_accepted_name,
  NULL AS GSDTaxonGUID,
  NULL AS GSDNameGUID,
  CASE WHEN t.extinct THEN 1 ELSE 0 END AS is_extinct,
  0 AS has_preholocene,
  0 AS has_modern
FROM name_{{datasetKey}} n
    JOIN name_usage_{{datasetKey}} t ON n.id=t.name_id
    LEFT JOIN name_usage_{{datasetKey}} tc ON tc.parent_id=t.id AND NOT t.is_synonym
    LEFT JOIN __classification c  ON t.id=c.id
    LEFT JOIN __classification cf ON c.family_id=cf.id
    LEFT JOIN __scrutinizer sc ON t.scrutinizer=sc.name AND c.dataset_key=sc.dataset_key
    LEFT JOIN __tax_keys tk ON t.id=tk.id

WHERE n.rank = 'GENUS'::rank
    AND NOT t.is_synonym
    AND tc.id IS NULL

) TO 'scientific_names.csv';


-- common_names 
COPY (
  SELECT nextval('__record_id_seq') AS record_id,
    v.taxon_id AS name_code, 
    v.name AS common_name, 
    v.latin AS transliteration, 
    trim(v.language) AS language,
    trim(v.country) AS country,
    trim(v.area) AS area,
    rk.key as reference_id,
    coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id,
    NULL AS is_infraspecies,
    r.id as reference_code 
  FROM vernacular_name_{{datasetKey}} v
    JOIN name_usage_{{datasetKey}} t ON t.id=v.taxon_id
    LEFT JOIN reference_{{datasetKey}} r ON r.id=v.reference_id
    LEFT JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON t.sector_key=s.id
) TO 'common_names.csv';


-- distribution
COPY (
  SELECT nextval('__record_id_seq') AS record_id,
    d.taxon_id AS name_code, 
    CASE WHEN d.gazetteer = 'ISO'::GAZETTEER THEN c.title ELSE d.area END AS distribution,
    d.gazetteer::text AS StandardInUse,
    initcap(d.status::text) AS DistributionStatus,
    coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id
  FROM distribution_{{datasetKey}} d
      JOIN name_usage_{{datasetKey}} t ON t.id=d.taxon_id
      LEFT JOIN sector s ON t.sector_key=s.id
      LEFT JOIN __country c ON c.code=d.area
) TO 'distribution.csv';


-- scientific_name_references.csv
COPY (
  SELECT nextval('__record_id_seq') AS record_id,
    t.id AS name_code,
    'NomRef' AS reference_type, -- NomRef, TaxAccRef, ComNameRef
    rk.key AS reference_id,
    r.id AS reference_code,
    coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id
  FROM name_{{datasetKey}} n
    JOIN name_usage_{{datasetKey}} t ON t.name_id=n.id
    JOIN reference_{{datasetKey}} r ON r.id=n.published_in_id
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.id

  UNION

  SELECT nextval('__record_id_seq') AS record_id,
    u.id AS name_code,
    'TaxAccRef' AS reference_type, -- NomRef, TaxAccRef, ComNameRef
    rk.key AS reference_id,
    r.id AS reference_code,
    coalesce(s.subject_dataset_key, 1500) - 1000 AS database_id
  FROM
    (SELECT id, UNNEST(reference_ids) AS rid FROM name_usage_{{datasetKey}}) u
    JOIN reference_{{datasetKey}} r ON r.id=u.rid
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.id

) TO 'scientific_name_references.csv';

