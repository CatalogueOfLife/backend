DROP TABLE IF EXISTS __scrutinizer;
DROP TABLE IF EXISTS __ref_keys;
DROP TABLE IF EXISTS __tax_keys;
DROP TABLE IF EXISTS __classification;
DROP TABLE IF EXISTS __classification2;
DROP TABLE IF EXISTS __coverage;
DROP TABLE IF EXISTS __coverage2;
DROP SEQUENCE IF EXISTS __record_id_seq;
DROP SEQUENCE IF EXISTS __unassigned_seq;


CREATE TABLE __coverage AS
    -- ATTACH MODE
    SELECT s.dataset_key, subject_rank AS rank, subject_name AS name, array_to_string(classification(3, target_id, true), ' - ') AS classification
    FROM sector s JOIN name_usage_3 t ON s.target_id=t.id
    WHERE s.mode=0
  UNION ALL
    -- MERGE MODE
    SELECT s.dataset_key, target_rank AS rank, target_name AS name, array_to_string(classification(3, target_id, false), ' - ') AS classification
    FROM sector s JOIN name_usage_3 t ON s.target_id=t.id
    WHERE s.mode=1;

CREATE TABLE __coverage2 AS
    SELECT dataset_key, string_agg(classification || ' - ' || groups, ';\n') AS coverage FROM (
        SELECT dataset_key, classification, string_agg(name, ', ') AS groups FROM __coverage GROUP BY dataset_key, classification
    ) AS foo GROUP BY dataset_key;
CREATE INDEX ON __coverage2 (dataset_key);



COPY (
(
SELECT DISTINCT ON (d.key)
 d.key - 1000 AS record_id,
 coalesce(d.alias || ': ' || d.title, d.title) AS database_name_displayed,
 coalesce(d.alias, d.title) AS database_name,
 d.title AS database_full_name,
 d.website AS web_site,
 array_to_string(d.organisations, '; ') AS organization,
 d.contact AS contact_person,
 d.group AS taxa,
 cov.coverage AS taxonomic_coverage,
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
 d.coverage AS coverage,
 completeness AS completeness,
 confidence AS confidence
FROM dataset d
    JOIN dataset_import i ON i.dataset_key=d.key
    LEFT JOIN __coverage2 cov ON cov.dataset_key=d.key
WHERE d.key IN (SELECT distinct dataset_key FROM sector)
ORDER BY d.key ASC, i.attempt DESC
)

UNION ALL

SELECT
 500 AS record_id,
 'CoL Management Classification' AS database_name_displayed,
 '' AS database_name,
 'A Higher Level Classification of All Living Organisms' AS database_full_name,
 'http://journals.plos.org/plosone/article?id=10.1371/journal.pone.0119248' AS web_site,
 'CoL Hierarchy Panel' AS organization,
 NULL AS contact_person,
 'Biota' AS taxa,
 'Animalia, Archaea, Bacteria, Chromista, Fungi, Plantae, Protozoa' AS taxonomic_coverage,
 --'abs' AS abstract,
 '"We present a consensus classification of life to embrace the more than 1.6 million species already provided by more than 3,000 taxonomists? expert opinions in a unified and coherent, hierarchically ranked system known as the Catalogue of Life (CoL). The intent of this collaborative effort is to provide a hierarchical classification serving not only the needs of the CoL''s database providers but also the diverse public-domain user community, most of whom are familiar with the Linnaean conceptual system of ordering taxon relationships. This classification is neither phylogenetic nor evolutionary but instead represents a consensus view that accommodates taxonomic choices and practical compromises among diverse expert opinions, public usages, and conflicting evidence about the boundaries between taxa and the ranks of major taxa, including kingdoms. Certain key issues, some not fully resolved, are addressed in particular. Beyond its immediate use as a management tool for the CoL and ITIS (Integrated Taxonomic Information System), it is immediately valuable as a reference for taxonomic and biodiversity research, as a tool for societal communication, and as a classificatory ""backbone"" for biodiversity databases, museum collections, libraries, and textbooks. Such a modern comprehensive hierarchy has not previously existed at this level of specificity." <i>PLoS ONE 10(4): e0119248. doi:10.1371/jou</i>' AS abstract,
 '2015' AS version,
 '2015-04-29' AS release_date,
 NULL AS SpeciesCount,
 NULL AS SpeciesEst,
 'Ruggiero M.A., Gordon D.P., Orrell T.M., Bailly N., Bourgoin T., Brusca R.C., Cavalier-Smith T., Guiry M.D., Kirk P.M.' AS authors_editors,
 NULL AS accepted_species_names,
 NULL AS accepted_infraspecies_names,
 NULL AS species_synonyms,
 NULL AS infraspecies_synonyms,
 NULL AS common_names,
 NULL AS total_names,
 0 AS is_new,
 NULL AS coverage,
 NULL AS completeness,
 NULL AS confidence

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
    coalesce(s.dataset_key, 1500) - 1000 AS database_id,
    r.id AS reference_code
  FROM reference_{{datasetKey}} r
    JOIN __ref_keys rk ON rk.id=r.id
    LEFT JOIN sector s ON r.sector_key=s.key
    
) TO 'references.csv';


-- estimates
COPY (
  SELECT e.subject_id AS name_code,
    e.subject_kingdom AS kingdom,
    e.subject_name AS name,
    e.subject_rank AS rank,
    e.estimate,
    r.citation AS source,
    e.created AS inserted,
    e.modified AS updated
  FROM estimate e
    LEFT JOIN reference_{{datasetKey}} r ON r.id=e.reference_id
) TO 'estimates.csv';


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
    SELECT key AS record_id, name AS specialist_name, null AS specialist_code, coalesce(dataset_key, 1500) - 1000 AS database_id FROM __scrutinizer
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
        coalesce(dataset_key, 1500) - 1000 AS database_id
    FROM lifezones_x
) TO 'lifezone.csv';


-- create a flattened classification table for all usages incl taxa AND synonyms
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
  CASE WHEN n.rank > 'species'::rank THEN c.species_id ELSE NULL END AS infraspecies_parent_name_code,
  n.infraspecific_epithet AS infraspecies,
  CASE WHEN n.rank > 'species'::rank THEN r.marker ELSE NULL END AS infraspecies_marker,  -- uses __ranks table created in AcExporter java code!
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
  CASE WHEN t.is_synonym THEN coalesce(cs.dataset_key, 1500) - 1000 ELSE coalesce(c.dataset_key, 1500) - 1000 END AS database_id,
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
    LEFT JOIN __classification c  ON t.id=c.id
    LEFT JOIN __classification cs ON t.parent_id=cs.id
    LEFT JOIN __classification cf ON c.family_id=cf.id
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
    coalesce(s.dataset_key, 1500) - 1000 AS database_id,
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
    coalesce(s.dataset_key, 1500) - 1000 AS database_id
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
    coalesce(s.dataset_key, 1500) - 1000 AS database_id
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
    coalesce(s.dataset_key, 1500) - 1000 AS database_id
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
DROP TABLE __coverage;
DROP TABLE __coverage2;
DROP TABLE IF EXISTS __ranks;
DROP SEQUENCE __record_id_seq;
DROP SEQUENCE __unassigned_seq;