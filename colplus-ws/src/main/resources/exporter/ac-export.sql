DROP TABLE IF EXISTS __scrutinizer;
DROP TABLE IF EXISTS __ref_keys;
DROP TABLE IF EXISTS __tax_keys;
DROP TABLE IF EXISTS __classification;
DROP TABLE IF EXISTS __classification2;
DROP SEQUENCE IF EXISTS __record_id_seq;
DROP SEQUENCE IF EXISTS __unassigned_seq;



COPY (
SELECT DISTINCT ON (d.key)
 coalesce(d.alias || ': ' || d.title, d.title) AS database_name_displayed,
 d.key AS record_id,
 coalesce(d.alias, d.title) AS database_name,
 d.title AS database_full_name,
 d.website AS web_site,
 array_to_string(d.organisations, '; ') AS organization,
 d.contact AS contact_person,
 d.group AS taxa,
 NULL AS taxonomic_coverage,
 d.description AS abstract,
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
 coverage AS coverage,
 completeness AS completeness,
 confidence AS confidence
FROM dataset d
    JOIN dataset_import i ON i.dataset_key=d.key
WHERE d.key IN (SELECT distinct dataset_key FROM sector)
ORDER BY d.key ASC, i.attempt DESC
) TO 'databases.csv';


-- create reference int keys
CREATE TABLE __ref_keys (key serial, id text UNIQUE);
INSERT INTO __ref_keys (id) SELECT id FROM reference_{{datasetKey}};

-- references
COPY (
  SELECT rk.key AS record_id, 
    csl->>'author' AS author, 
    csl#>>'{issued,literal}' AS year, 
    csl->>'title' AS title, 
    csl->>'containerTitle' AS source, 
    s.dataset_key AS database_id, 
    r.id AS reference_code
  FROM reference_{{datasetKey}} r
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.key
    
) TO 'references.csv';



-- create usage int keys using a reusable sequence
CREATE SEQUENCE __record_id_seq START 1000;
CREATE TABLE __tax_keys (key int PRIMARY KEY DEFAULT nextval('__record_id_seq'), id text UNIQUE);
INSERT INTO __tax_keys (id) SELECT id FROM name_usage_{{datasetKey}};

-- specialists aka scrutinizer
CREATE TABLE __scrutinizer (key serial, dataset_key int, name text, unique(dataset_key, name));
INSERT INTO __scrutinizer (name, dataset_key)
    SELECT DISTINCT t.according_to, s.dataset_key
        FROM name_usage_{{datasetKey}} t
            LEFT JOIN sector s ON t.sector_key=s.key
        WHERE t.according_to IS NOT NULL;
COPY (
    SELECT key AS record_id, name AS specialist_name, null AS specialist_code, dataset_key AS database_id FROM __scrutinizer
) TO 'specialists.csv';


-- lifezones
-- unnest with empty or null arrays removes the entire row
COPY (
    WITH lifezones_x AS (
        SELECT t.id, unnest(t.lifezones) AS lfz, s.dataset_key
        FROM name_usage_{{datasetKey}} t
            JOIN __tax_keys tk ON t.id=tk.id
            LEFT JOIN sector s ON t.sector_key=s.key
        WHERE t.lifezones IS NOT NULL
    )
    SELECT NULL AS record_id, 
        id AS name_code, 
        CASE WHEN lfz=0 THEN 'brackish' WHEN lfz=1 THEN 'freshwater' WHEN lfz=2 THEN 'marine' WHEN lfz=3 THEN 'terrestrial' END AS lifezone, 
        dataset_key AS database_id
    FROM lifezones_x
) TO 'lifezone.csv';


-- create a flattened classification table for all taxa
CREATE TABLE __classification AS (
WITH RECURSIVE tree AS(
    SELECT
        tk.key AS key,
        s.dataset_key AS dataset_key,
        t.id AS id,
        n.rank AS rank,
        CASE WHEN n.rank='kingdom' THEN n.scientific_name ELSE NULL END AS kingdom,
        CASE WHEN n.rank='phylum' THEN n.scientific_name ELSE NULL END AS phylum,
        CASE WHEN n.rank='class' THEN n.scientific_name ELSE NULL END AS "class",
        CASE WHEN n.rank='order' THEN n.scientific_name ELSE NULL END AS "order",
        CASE WHEN n.rank='superfamily' THEN n.scientific_name ELSE NULL END AS superfamily,
        CASE WHEN n.rank='family' THEN n.scientific_name ELSE NULL END AS family,
        CASE WHEN n.rank='family' THEN t.id ELSE NULL END AS family_id,
        CASE WHEN n.rank='species' THEN t.id ELSE NULL END AS species_id
    FROM name_usage_{{datasetKey}} t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_{{datasetKey}} n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.key
    WHERE t.parent_id IS NULL AND NOT t.is_synonym
  UNION
    SELECT
        tk.key,
        s.dataset_key,
        t.id,
        n.rank,
        CASE WHEN n.rank='kingdom' THEN n.scientific_name ELSE tree.kingdom END,
        CASE WHEN n.rank='phylum' THEN n.scientific_name ELSE tree.phylum END,
        CASE WHEN n.rank='class' THEN n.scientific_name ELSE tree.class END,
        CASE WHEN n.rank='order' THEN n.scientific_name ELSE tree."order" END,
        CASE WHEN n.rank='superfamily' THEN n.scientific_name ELSE tree.superfamily END,
        CASE WHEN n.rank='family' THEN n.scientific_name ELSE tree.family END,
        CASE WHEN n.rank='family' THEN t.id ELSE tree.family_id END,
        CASE WHEN n.rank='species' THEN t.id ELSE tree.species_id END AS species_id
    FROM name_usage_{{datasetKey}} t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_{{datasetKey}} n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.key
        JOIN tree ON (tree.id = t.parent_id) AND NOT t.is_synonym
)
SELECT * FROM tree
);

CREATE INDEX ON __classification (rank);
DELETE FROM __classification WHERE rank < 'family'::rank;
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
    SELECT DISTINCT dataset_key, 'family'::rank, kingdom, phylum ,class, "order", superfamily, 'Not assigned'
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

-- use __ranks table created in AcExporter java code!

-- families export
COPY (
SELECT key AS record_id,
      NULL AS hierarchy_code,
      kingdom, 
      phylum, 
      class, 
      "order", 
      family, 
      superfamily, 
      dataset_key AS database_id, 
      id AS family_code, 
      1 AS is_accepted_name
    FROM __classification
    WHERE rank='family'
) TO 'families.csv';


COPY (
SELECT
  c.key AS record_id,
  t.id AS name_code,
  t.webpage AS web_site,
  n.genus AS genus,
  n.infrageneric_epithet AS subgenus,
  n.specific_epithet AS species,
  c.species_id AS infraspecies_parent_name_code,
  n.infraspecific_epithet AS infraspecies,
  r.marker AS infraspecies_marker,
  n.authorship AS author,
  CASE WHEN t.is_synonym THEN t.parent_id ELSE t.id END AS accepted_name_code,
  t.remarks AS comment,
  t.according_to_date AS scrutiny_date,
  CASE WHEN t.status=0 THEN 1
       WHEN t.status=1 THEN 4
       WHEN t.status=2 THEN 5
       WHEN t.status=3 THEN 2
       WHEN t.status=4 THEN 3
  END AS sp2000_status_id, -- 1=ACCEPTED, 2=AMBIGUOUS_SYNONYM, 3=MISAPPLIED, 4=PROVISIONALLY_ACCEPTED, 5=SYNONYM
                           -- Java: ACCEPTED,PROVISIONALLY_ACCEPTED,SYNONYM,AMBIGUOUS_SYNONYM,MISAPPLIED
  c.dataset_key AS database_id,
  sc.key AS specialist_id,
  cf.key AS family_id,
  NULL AS specialist_code,
  c.family_id AS family_code,
  CASE WHEN t.is_synonym THEN 0 ELSE 1 END AS is_accepted_name,
  NULL AS GSDTaxonGUID,
  NULL AS GSDNameGUID,
  (NOT t.recent)::int AS is_extinct,
  t.fossil::int AS has_preholocene,
  t.recent::int AS has_modern
FROM name_{{datasetKey}} n
    JOIN name_usage_{{datasetKey}} t ON n.id=t.name_id
    JOIN __classification c ON t.id=c.id
    JOIN __classification cf ON c.family_id=cf.id
    LEFT JOIN __ranks r ON n.rank=r.key
    LEFT JOIN __scrutinizer sc ON t.according_to=sc.name AND c.dataset_key=sc.dataset_key
WHERE n.rank >= 'species'::rank

) TO 'scientific_names.csv';


-- common_names 
COPY (
  SELECT NULL AS record_id, 
    v.taxon_id AS name_code, 
    v.name AS common_name, 
    v.latin AS transliteration, 
    v.language, 
    v.country, 
    NULL AS area, 
    rk.key as reference_id,
    s.dataset_key AS database_id, 
    NULL AS is_infraspecies,
    r.id as reference_code 
  FROM vernacular_name_{{datasetKey}} v
    JOIN name_usage_{{datasetKey}} t ON t.id=v.taxon_id
    LEFT JOIN reference_{{datasetKey}} r ON r.id=v.reference_id
    LEFT JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON t.sector_key=s.key
) TO 'common_names.csv';


-- distribution
COPY (
  SELECT NULL AS record_id, 
    d.taxon_id AS name_code, 
    d.area AS distribution, 
    CASE WHEN d.gazetteer=0 THEN 'TDWG' WHEN d.gazetteer=1 THEN 'ISO' WHEN d.gazetteer=2 THEN 'FAO' ELSE 'TEXT' END AS StandardInUse,
    CASE WHEN d.status=0 THEN 'Native' WHEN d.status=1 THEN 'Domesticated' WHEN d.status=2 THEN 'Alien' WHEN d.status=3 THEN 'Uncertain' END AS DistributionStatus,
    s.dataset_key AS database_id
  FROM distribution_{{datasetKey}} d
      JOIN name_usage_{{datasetKey}} t ON t.id=d.taxon_id
      LEFT JOIN sector s ON t.sector_key=s.key
) TO 'distribution.csv';


-- scientific_name_references.csv
COPY (
  SELECT NULL AS record_id, 
    t.id AS name_code,
    'NomRef' AS reference_type, -- NomRef, TaxAccRef, ComNameRef
    rk.key AS reference_id,
    r.id AS reference_code,
    s.dataset_key AS database_id
  FROM name_{{datasetKey}} n
    JOIN name_usage_{{datasetKey}} t ON t.name_id=n.id
    JOIN reference_{{datasetKey}} r ON r.id=n.published_in_id
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.key

  UNION

  SELECT NULL AS record_id, 
    tr.taxon_id AS name_code,
    'TaxAccRef' AS reference_type, -- NomRef, TaxAccRef, ComNameRef
    rk.key AS reference_id,
    r.id AS reference_code,
    s.dataset_key AS database_id
  FROM usage_reference_{{datasetKey}} tr
    JOIN reference_{{datasetKey}} r ON r.id=tr.reference_id
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.key

) TO 'scientific_name_references.csv';


-- cleanup
DROP TABLE __scrutinizer;
DROP TABLE __ref_keys;
DROP TABLE __tax_keys;
DROP TABLE __classification;
DROP TABLE __classification2;
DROP TABLE IF EXISTS __ranks;
DROP SEQUENCE __record_id_seq;
DROP SEQUENCE __unassigned_seq;