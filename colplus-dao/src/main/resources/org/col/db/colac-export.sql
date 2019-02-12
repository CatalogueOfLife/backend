DROP TABLE IF EXISTS __scrutinizer;
DROP TABLE IF EXISTS __tax_keys;
DROP TABLE IF EXISTS __syn_keys;
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
 d.released AS release_date,
 coalesce((i.taxa_by_rank_count -> 'SPECIES')::int, 0) AS SpeciesCount,
 NULL AS SpeciesEst,
 array_to_string(d.authors_and_editors, '; ')  AS authors_editors,
 NULL AS accepted_species_names,
 NULL AS accepted_infraspecies_names,
 NULL AS species_synonyms,
 NULL AS infraspecies_synonyms,
 i.vernacular_count AS common_names,
 i.name_count AS total_names,
 FALSE AS is_new,
 coverage AS coverage,
 completeness AS completeness,
 confidence AS confidence
FROM dataset d
    JOIN dataset_import i ON i.dataset_key=d.key
WHERE d.key IN (SELECT distinct dataset_key FROM sector)
ORDER BY d.key ASC, i.attempt DESC
) TO '/tmp/colac/databases.csv' CSV;


-- create taxon int keys using a reusable sequence
CREATE SEQUENCE __record_id_seq START 1000;
CREATE TABLE __tax_keys (key int PRIMARY KEY DEFAULT nextval('__record_id_seq'), id text UNIQUE);
INSERT INTO __tax_keys (id) SELECT id FROM taxon_1000;

-- create synonym int keys
CREATE TABLE __syn_keys (key int PRIMARY KEY DEFAULT nextval('__record_id_seq'), nid text, tid text);
INSERT INTO __syn_keys (nid, tid) SELECT name_id, taxon_id FROM synonym_1000;
CREATE UNIQUE INDEX __syn_keys_unique ON __syn_keys (nid, tid);

-- specialists aka scrutinizer
CREATE TABLE __scrutinizer (key serial, dataset_key int, name text unique);
INSERT INTO __scrutinizer (name, dataset_key)
    SELECT DISTINCT t.according_to, s.dataset_key
        FROM taxon_1000 t
            LEFT JOIN sector s ON t.sector_key=s.key
        WHERE t.according_to IS NOT NULL;
COPY (
    SELECT key, name, null, dataset_key  FROM __scrutinizer
) TO '/tmp/colac/specialists.csv' CSV;


-- lifezones
-- unnest with empty or null arrays removes the entire row
COPY (
    WITH lifezones_x AS (
        SELECT tk.key, t.id, unnest(t.lifezones) AS lfz, s.dataset_key
        FROM taxon_1000 t
            JOIN __tax_keys tk ON t.id=tk.id
            LEFT JOIN sector s ON t.sector_key=s.key
    )
    SELECT key, id,
        CASE WHEN lfz=0 THEN 'brackish' WHEN lfz=1 THEN 'freshwater' WHEN lfz=2 THEN 'marine' WHEN lfz=3 THEN 'terrestrial' END AS lifezone, dataset_key
        FROM lifezones_x
) TO '/tmp/colac/lifezone.csv' CSV;


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
    FROM taxon_1000 t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_1000 n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.key
    WHERE t.parent_id IS NULL
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
    FROM taxon_1000 t
        JOIN __tax_keys tk ON t.id=tk.id
        JOIN name_1000 n ON n.id=t.name_id
        LEFT JOIN sector s ON t.sector_key=s.key
        JOIN tree ON (tree.id = t.parent_id)
)
SELECT * FROM tree
);

CREATE INDEX ON __classification (rank);
DELETE FROM __classification WHERE rank < 'family'::rank;
-- now we have only families and below left

-- create incertae sedis families if missing
CREATE TABLE __classification2 (LIKE __classification);
CREATE SEQUENCE __unassigned_seq START 1;
CREATE INDEX ON __classification (family_id);
INSERT INTO __classification2 (key, dataset_key, id, family_id, rank, kingdom, phylum ,class, "order", superfamily, family)
    SELECT DISTINCT nextval('__record_id_seq'), dataset_key, 'inc.sed-' || nextval('__unassigned_seq'), 'inc.sed-' || currval('__unassigned_seq'), 'family'::rank, kingdom, phylum ,class, "order", superfamily, 'Not assigned'
        FROM __classification
        WHERE family_id IS NULL;
CREATE INDEX ON __classification (dataset_key, coalesce(kingdom,''), coalesce(phylum,''), coalesce(class,''), coalesce("order",''), coalesce(superfamily))
    WHERE family_id=NULL;
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
CREATE INDEX ON __classification (id);

-- TODO rank marker table !!!

-- families export
COPY (
SELECT key,
    NULL AS hierarchy_code, -- TODO
    kingdom, phylum, class, "order", family, superfamily, dataset_key, id, true
    FROM __classification
    WHERE rank='family'
) TO '/tmp/colac/families.csv' CSV;


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
  n.rank AS infraspecies_marker, -- TODO
  NULL AS author, -- TODO
  t.id AS accepted_name_code,
  t.remarks AS comment,
  t.according_to_date AS scrutiny_date,
  CASE WHEN t.provisional THEN 4 ELSE 1 END AS sp2000_status_id, -- 1=ACCEPTED, 2=AMBIGUOUS_SYNONYM, 3=MISAPPLIED, 4=PROVISIONALLY_ACCEPTED, 5=SYNONYM
  c.dataset_key AS database_id,
  sc.key AS specialist_id,
  cf.key AS family_id,
  NULL AS specialist_code,
  c.family_id AS family_code,
  TRUE AS is_accepted_name,
  NULL AS GSDTaxonGUID,
  NULL AS GSDNameGUID,
  NOT t.recent AS is_extinct,
  t.fossil AS has_preholocene,
  t.recent AS has_modern
FROM name_1000 n
    JOIN taxon_1000 t ON n.id=t.name_id
    JOIN __classification c ON t.id=c.id
    JOIN __classification cf ON c.family_id=cf.id
    LEFT JOIN __scrutinizer sc ON t.according_to=sc.name
WHERE n.rank >= 'species'::rank

UNION

SELECT
  sk.key AS record_id,
  n.id AS name_code,
  null AS web_site,
  n.genus AS genus,
  n.infrageneric_epithet AS subgenus,
  n.specific_epithet AS species,
  NULL AS infraspecies_parent_name_code,
  n.infraspecific_epithet AS infraspecies,
  n.rank AS infraspecies_marker, -- TODO
  NULL AS author, -- TODO
  t.id AS accepted_name_code,
  n.remarks AS comment,
  t.according_to_date AS scrutiny_date,
  CASE WHEN s.status=2 THEN 5
       WHEN s.status=3 THEN 2
       WHEN s.status=4 THEN 3
  END AS sp2000_status_id, -- 1=ACCEPTED, 2=AMBIGUOUS_SYNONYM, 3=MISAPPLIED, 4=PROVISIONALLY_ACCEPTED, 5=SYNONYM
                           -- Java: ACCEPTED,PROVISIONALLY_ACCEPTED,SYNONYM,AMBIGUOUS_SYNONYM,MISAPPLIED
  sec.dataset_key AS database_id,
  NULL AS specialist_id,
  NULL AS family_id,
  NULL AS specialist_code,
  NULL AS family_code,
  FALSE AS is_accepted_name,
  NULL AS GSDTaxonGUID,
  NULL AS GSDNameGUID,
  NULL AS is_extinct,
  NULL AS has_preholocene,
  NULL AS has_modern
FROM name_1000 n
    JOIN synonym_1000 s ON n.id=s.name_id
    JOIN taxon_1000 t ON t.id=s.taxon_id
    JOIN __syn_keys sk ON s.name_id=sk.nid AND s.taxon_id=sk.tid
    LEFT JOIN sector sec ON t.sector_key=sec.key
WHERE n.rank >= 'species'::rank

) TO '/tmp/colac/scientific_names.csv' CSV;


-- TODO common_names.csv
-- TODO distribution.csv
-- TODO references.csv
-- TODO scientific_name_references.csv


-- cleanup
DROP TABLE __scrutinizer;
DROP TABLE __tax_keys;
DROP TABLE __syn_keys;
DROP TABLE __classification;
DROP TABLE __classification2;
DROP SEQUENCE __record_id_seq;
DROP SEQUENCE __unassigned_seq;