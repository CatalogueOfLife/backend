
## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

### 2021-11-04 non dropping particle in cslname
```
ALTER TYPE cslname DROP ATTRIBUTE literal;
ALTER TYPE cslname ADD ATTRIBUTE particle text;

CREATE OR REPLACE FUNCTION text2cslname(text) RETURNS cslname AS
$$
SELECT ROW(null, $1, null)::cslname
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;
```

### 2021-10-15 new issue
```
ALTER TYPE ISSUE ADD VALUE 'INVISIBLE_CHARACTERS';
ALTER TYPE ISSUE ADD VALUE 'HOMOGLYPH_CHARACTERS';

ALTER TYPE DATAFORMAT ADD VALUE 'NEWICK';
ALTER TYPE DATAFORMAT ADD VALUE 'DOT';
```

### 2021-06-14 dataset NG
```
CREATE TYPE agent AS (orcid text, given text, family text,
  rorid text, organisation text, department text, city text, state text, country CHAR(2),
  email text, url text, note text
);

CREATE TYPE cslname AS (given text, family text, literal text);

CREATE OR REPLACE FUNCTION cslname_str(cslname) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION cslname_str(cslname[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION text2cslname(text) RETURNS cslname AS
$$
SELECT ROW(null, null, $1)::cslname
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS cslname) WITH FUNCTION text2cslname;


CREATE OR REPLACE FUNCTION agent_str(agent) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION agent_str(agent[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION text2agent(text) RETURNS agent AS
$$
SELECT ROW(null, null, $1, null, null, null, null, null, null, null, null, null)::agent
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS agent) WITH FUNCTION text2agent;

CREATE OR REPLACE FUNCTION p2agent(person) RETURNS agent AS
$$
SELECT ROW($1.orcid, $1.given, $1.family, null, null, null, null, null, null, $1.email, null, null)::agent
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION o2agent(organisation) RETURNS agent AS
$$
SELECT ROW(null, null, null,  null, $1.name, $1.department, $1.city, $1.state, $1.country, null, null, null)::agent
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (person AS agent) WITH FUNCTION p2agent;
CREATE CAST (organisation AS agent) WITH FUNCTION o2agent;

--
-- DATASET TABLE
--
ALTER TABLE dataset RENAME COLUMN contact TO contact_old;
ALTER TABLE dataset 
  ADD COLUMN identifier HSTORE,
  ADD COLUMN issn TEXT,
  ADD COLUMN temporal_scope TEXT,
  ADD COLUMN contact agent,
  ADD COLUMN creator agent[],
  ADD COLUMN editor agent[],
  ADD COLUMN publisher agent,
  ADD COLUMN contributor agent[];
  
UPDATE dataset SET contact=contact_old::agent, creator=authors::agent[], editor=editors::agent[], contributor=organisations::agent[];

ALTER TABLE dataset RENAME COLUMN website TO url;
ALTER TABLE dataset RENAME COLUMN released TO issued;
ALTER TABLE dataset ALTER COLUMN issued TYPE TEXT;

ALTER TABLE dataset RENAME COLUMN "group" TO taxonomic_scope;
ALTER TABLE dataset RENAME COLUMN import_attempt TO attempt;
ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset 
  DROP COLUMN contact_old,
  DROP COLUMN authors,
  DROP COLUMN editors,
  DROP COLUMN organisations,
  DROP COLUMN citation;
ALTER TABLE dataset ALTER COLUMN alias DROP NOT NULL;
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(issn, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(identifier::text, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(creator), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(version, '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(geographic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(taxonomic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(temporal_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contact), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(editor), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(publisher), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contributor), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
  ) STORED;

CREATE TABLE dataset_citation (
  dataset_key INTEGER REFERENCES dataset,
  id TEXT,
  type TEXT,
  doi TEXT,
  author cslname[],
  editor cslname[],
  title TEXT,
  container_author cslname[],
  container_title TEXT,
  issued TEXT,
  accessed TEXT,
  collection_editor cslname[],
  collection_title TEXT,
  volume TEXT,
  issue TEXT,
  edition TEXT,
  page TEXT,
  publisher TEXT,
  publisher_place TEXT,
  version TEXT,
  isbn TEXT,
  issn TEXT,
  url TEXT,
  note TEXT
);
CREATE INDEX ON dataset_citation (dataset_key);


--
-- DATASET_ARCHIVE TABLE
--
ALTER TABLE dataset_archive RENAME COLUMN contact TO contact_old;
ALTER TABLE dataset_archive 
  ADD COLUMN identifier HSTORE,
  ADD COLUMN issn TEXT,
  ADD COLUMN temporal_scope TEXT,
  ADD COLUMN contact agent,
  ADD COLUMN creator agent[],
  ADD COLUMN editor agent[],
  ADD COLUMN publisher agent,
  ADD COLUMN contributor agent[];
  
UPDATE dataset_archive SET contact=contact_old::agent, creator=authors::agent[], editor=editors::agent[], contributor=organisations::agent[];

ALTER TABLE dataset_archive RENAME COLUMN website TO url;
ALTER TABLE dataset_archive RENAME COLUMN released TO issued;
ALTER TABLE dataset_archive ALTER COLUMN issued TYPE TEXT;
ALTER TABLE dataset_archive RENAME COLUMN "group" TO taxonomic_scope;
ALTER TABLE dataset_archive RENAME COLUMN import_attempt TO attempt;
ALTER TABLE dataset_archive 
  DROP COLUMN contact_old,
  DROP COLUMN authors,
  DROP COLUMN editors,
  DROP COLUMN organisations,
  DROP COLUMN citation;

ALTER TABLE dataset_archive ALTER COLUMN attempt SET NOT NULL;
ALTER TABLE dataset_archive ADD FOREIGN KEY (key) REFERENCES dataset;
ALTER TABLE dataset_archive DROP CONSTRAINT dataset_archive_key_import_attempt_key;
ALTER TABLE dataset_archive ADD PRIMARY KEY (key, attempt);

CREATE TABLE dataset_archive_citation (LIKE dataset_citation INCLUDING INDEXES);
ALTER TABLE dataset_archive_citation
  ADD COLUMN attempt INTEGER NOT NULL;
CREATE INDEX ON dataset_archive_citation (dataset_key, attempt);


--
-- DATASET_SOURCE TABLE
--
ALTER TABLE project_source DROP CONSTRAINT project_source_key_dataset_key_key;
ALTER TABLE project_source RENAME TO dataset_source;
ALTER TABLE dataset_source RENAME COLUMN contact TO contact_old;
ALTER TABLE dataset_source 
  ADD COLUMN identifier HSTORE,
  ADD COLUMN issn TEXT,
  ADD COLUMN temporal_scope TEXT,
  ADD COLUMN contact agent,
  ADD COLUMN creator agent[],
  ADD COLUMN editor agent[],
  ADD COLUMN publisher agent,
  ADD COLUMN contributor agent[];
  
UPDATE dataset_source SET contact=contact_old::agent, creator=authors::agent[], editor=editors::agent[], contributor=organisations::agent[];

ALTER TABLE dataset_source RENAME COLUMN website TO url;
ALTER TABLE dataset_source RENAME COLUMN released TO issued;
ALTER TABLE dataset_source ALTER COLUMN issued TYPE TEXT;
ALTER TABLE dataset_source RENAME COLUMN "group" TO taxonomic_scope;
ALTER TABLE dataset_source RENAME COLUMN import_attempt TO attempt;
ALTER TABLE dataset_source 
  DROP COLUMN contact_old,
  DROP COLUMN authors,
  DROP COLUMN editors,
  DROP COLUMN organisations,
  DROP COLUMN citation;

ALTER TABLE dataset_source ADD PRIMARY KEY (key, dataset_key);
ALTER TABLE dataset_source ADD FOREIGN KEY (key) REFERENCES dataset;

CREATE TABLE dataset_source_citation (LIKE dataset_citation INCLUDING INDEXES);
ALTER TABLE dataset_source_citation
  ADD COLUMN release_key INTEGER REFERENCES dataset;
CREATE INDEX ON dataset_source_citation (dataset_key, release_key);

--
-- DATASET_PATCH TABLE
--
ALTER TABLE dataset_patch RENAME COLUMN contact TO contact_old;
ALTER TABLE dataset_patch 
  ADD COLUMN identifier HSTORE,
  ADD COLUMN issn TEXT,
  ADD COLUMN temporal_scope TEXT,
  ADD COLUMN contact agent,
  ADD COLUMN creator agent[],
  ADD COLUMN editor agent[],
  ADD COLUMN publisher agent,
  ADD COLUMN contributor agent[];
  
UPDATE dataset_patch SET contact=contact_old::agent, creator=authors::agent[], editor=editors::agent[], contributor=organisations::agent[];

ALTER TABLE dataset_patch RENAME COLUMN website TO url;
ALTER TABLE dataset_patch RENAME COLUMN released TO issued;
ALTER TABLE dataset_patch ALTER COLUMN issued TYPE TEXT;
ALTER TABLE dataset_patch RENAME COLUMN "group" TO taxonomic_scope;
ALTER TABLE dataset_patch 
  DROP COLUMN type,
  DROP COLUMN contact_old,
  DROP COLUMN authors,
  DROP COLUMN editors,
  DROP COLUMN organisations,
  DROP COLUMN citation;

ALTER TABLE dataset_patch ADD COLUMN notes TEXT;


--
-- OTHER
--
ALTER TABLE dataset_export RENAME COLUMN import_attempt TO attempt;
ALTER TABLE sector RENAME COLUMN dataset_import_attempt TO dataset_attempt;


DROP FUNCTION p2agent CASCADE;
DROP FUNCTION o2agent CASCADE; 
DROP TYPE person CASCADE;
DROP TYPE organisation CASCADE;
```

### 2021-05-19 dataset dois
```
ALTER TABLE dataset ADD COLUMN doi TEXT;
ALTER TABLE dataset ADD UNIQUE (doi);
ALTER TABLE dataset_archive ADD COLUMN doi TEXT;
ALTER TABLE project_source ADD COLUMN doi TEXT;
ALTER TABLE dataset_patch ADD COLUMN doi TEXT;
```

### 2021-05-07 truncated exports
```
ALTER TABLE dataset_export ADD COLUMN truncated TEXT[];
```

### 2021-05-05 dataset exports
```
DROP FUNCTION classification_sn;
DROP TYPE simple_name;

CREATE TYPE simple_name AS (id text, rank rank, name text, authorship text);

CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS simple_name[] AS $$
	declare seql TEXT;
	declare parents simple_name[];
BEGIN
    seql := 'WITH RECURSIVE x AS ('
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = $1'
        || ' UNION ALL '
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = x.parent_id'
        || ') SELECT array_agg(sn) FROM x';

    IF NOT v_inc_self THEN
        seql := seql || ' WHERE id != $1';
    END IF;

    EXECUTE seql
    INTO parents
    USING v_id;
    RETURN (array_reverse(parents));
END;
$$ LANGUAGE plpgsql;

CREATE TYPE JOBSTATUS AS ENUM (
  'WAITING',
  'BLOCKED',
  'RUNNING',
  'FINISHED',
  'CANCELED',
  'FAILED'
);

CREATE TABLE dataset_export (
  key UUID PRIMARY KEY,
  -- request
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  format DATAFORMAT NOT NULL,
  excel BOOLEAN NOT NULL,
  root SIMPLE_NAME,
  synonyms BOOLEAN NOT NULL,
  min_rank RANK,
  created_by INTEGER NOT NULL REFERENCES "user",
  created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  modified_by INTEGER,
  modified TIMESTAMP WITHOUT TIME ZONE,
  -- results
  import_attempt INTEGER,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  deleted TIMESTAMP WITHOUT TIME ZONE,
  classification SIMPLE_NAME[],
  status JOBSTATUS NOT NULL,
  error TEXT,
  md5 TEXT,
  size INTEGER,
  synonym_count INTEGER,
  taxon_count INTEGER,
  taxa_by_rank_count HSTORE
);

CREATE INDEX ON dataset_export (created);
CREATE INDEX ON dataset_export (created_by, created);
CREATE INDEX ON dataset_export (dataset_key, import_attempt, format, excel, synonyms, min_rank, status);
```

### 2021-04-20 All CC licenses
```
ALTER TYPE LICENSE ADD VALUE 'CC_BY_SA' AFTER 'CC_BY';
ALTER TYPE LICENSE ADD VALUE 'CC_BY_ND' AFTER 'CC_BY_NC';
ALTER TYPE LICENSE ADD VALUE 'CC_BY_NC_SA' AFTER 'CC_BY_ND';
ALTER TYPE LICENSE ADD VALUE 'CC_BY_NC_ND' AFTER 'CC_BY_NC_SA';
```

### 2021-04-01 add missing nidx index
```
CREATE INDEX ON names_index (canonical_id);
```

### 2021-03-31 fix sector metrics
```
ALTER TABLE sector_import DROP CONSTRAINT sector_import_dataset_key_sector_key_fkey;
```

The COL prod data should be fixed by running the UpdReleaseMetricCmd !!!


### 2021-03-23 ignore decision
```
ALTER TYPE EDITORIALDECISION_MODE ADD VALUE 'IGNORE' AFTER 'UPDATE_RECURSIVE';
```

### 2021-03-16 like escape function
```
CREATE OR REPLACE FUNCTION escape_like(text) RETURNS text AS $$
SELECT replace(replace(replace($1
, '\', '\\')  -- must come 1st
, '%', '\%')
, '_', '\_');
$$
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE;
```


### 2021-03-11 remove match type INSERTED
```
ALTER TYPE RANK ADD VALUE 'SUBTERCLASS' AFTER 'INFRACLASS';

UPDATE name_match SET type = 'EXACT' WHERE type = 'INSERTED';
ALTER TABLE name_match ALTER COLUMN type TYPE text;
DROP TYPE MATCHTYPE;
CREATE TYPE MATCHTYPE AS ENUM (
  'EXACT',
  'VARIANT',
  'CANONICAL',
  'AMBIGUOUS',
  'NONE'
);
ALTER TABLE name_match ALTER COLUMN type TYPE MATCHTYPE USING type::MATCHTYPE;
```

### 2021-03-01 add SELF_REFERENCED_RELATION
```
ALTER TYPE ISSUE ADD VALUE 'SELF_REFERENCED_RELATION' AFTER 'PREVIOUS_LINE_SKIPPED';
```

### 2021-02-01 remove 4 name match issues
```
UPDATE dataset_import SET issues_by_issue_count = delete(issues_by_issue_count, array['NAME_MATCH_INSERTED', 'NAME_MATCH_VARIANT', 'NAME_MATCH_AMBIGUOUS', 'NAME_MATCH_NONE']);
UPDATE sector_import SET issues_by_issue_count = delete(issues_by_issue_count, array['NAME_MATCH_INSERTED', 'NAME_MATCH_VARIANT', 'NAME_MATCH_AMBIGUOUS', 'NAME_MATCH_NONE']);

UPDATE verbatim SET issues = array_remove(array_remove(array_remove(array_remove(issues, 'NAME_MATCH_INSERTED'::ISSUE), 'NAME_MATCH_VARIANT'::ISSUE), 'NAME_MATCH_AMBIGUOUS'::ISSUE), 'NAME_MATCH_NONE'::ISSUE)
 WHERE issues && ARRAY['NAME_MATCH_INSERTED'::ISSUE, 'NAME_MATCH_VARIANT'::ISSUE, 'NAME_MATCH_AMBIGUOUS'::ISSUE, 'NAME_MATCH_NONE'::ISSUE];
DROP INDEX verbatim_issues_idx;
ALTER TABLE verbatim ALTER COLUMN issues DROP DEFAULT;
ALTER TABLE verbatim ALTER COLUMN issues TYPE text[];

UPDATE verbatim_source SET issues = array_remove(array_remove(array_remove(array_remove(issues, 'NAME_MATCH_INSERTED'::ISSUE), 'NAME_MATCH_VARIANT'::ISSUE), 'NAME_MATCH_AMBIGUOUS'::ISSUE), 'NAME_MATCH_NONE'::ISSUE)
 WHERE issues && ARRAY['NAME_MATCH_INSERTED'::ISSUE, 'NAME_MATCH_VARIANT'::ISSUE, 'NAME_MATCH_AMBIGUOUS'::ISSUE, 'NAME_MATCH_NONE'::ISSUE];
DROP INDEX verbatim_source_issues_idx;
ALTER TABLE verbatim_source ALTER COLUMN issues DROP DEFAULT;
ALTER TABLE verbatim_source ALTER COLUMN issues TYPE text[];

DROP TYPE ISSUE;
CREATE TYPE ISSUE AS ENUM (
  'NOT_INTERPRETED',
  'ESCAPED_CHARACTERS',
  'REFERENCE_ID_INVALID',
  'ID_NOT_UNIQUE',
  'URL_INVALID',
  'PARTIAL_DATE',
  'PREVIOUS_LINE_SKIPPED',
  'UNPARSABLE_NAME',
  'PARTIALLY_PARSABLE_NAME',
  'UNPARSABLE_AUTHORSHIP',
  'DOUBTFUL_NAME',
  'INCONSISTENT_AUTHORSHIP',
  'INCONSISTENT_NAME',
  'PARSED_NAME_DIFFERS',
  'UNUSUAL_NAME_CHARACTERS',
  'MULTI_WORD_EPITHET',
  'UPPERCASE_EPITHET',
  'CONTAINS_REFERENCE',
  'NULL_EPITHET',
  'BLACKLISTED_EPITHET',
  'SUBSPECIES_ASSIGNED',
  'LC_MONOMIAL',
  'INDETERMINED',
  'HIGHER_RANK_BINOMIAL',
  'QUESTION_MARKS_REMOVED',
  'REPL_ENCLOSING_QUOTE',
  'MISSING_GENUS',
  'NOMENCLATURAL_STATUS_INVALID',
  'AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE',
  'CONFLICTING_NOMENCLATURAL_STATUS',
  'NOMENCLATURAL_CODE_INVALID',
  'BASIONYM_AUTHOR_MISMATCH',
  'BASIONYM_DERIVED',
  'CONFLICTING_BASIONYM_COMBINATION',
  'CHAINED_BASIONYM',
  'NAME_NOT_UNIQUE',
  'POTENTIAL_CHRESONYM',
  'PUBLISHED_BEFORE_GENUS',
  'BASIONYM_ID_INVALID',
  'RANK_INVALID',
  'UNMATCHED_NAME_BRACKETS',
  'TRUNCATED_NAME',
  'DUPLICATE_NAME',
  'NAME_VARIANT',
  'AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE',
  'TYPE_STATUS_INVALID',
  'LAT_LON_INVALID',
  'ALTITUDE_INVALID',
  'COUNTRY_INVALID',
  'TAXON_VARIANT',
  'TAXON_ID_INVALID',
  'NAME_ID_INVALID',
  'PARENT_ID_INVALID',
  'ACCEPTED_ID_INVALID',
  'ACCEPTED_NAME_MISSING',
  'PARENT_SPECIES_MISSING',
  'TAXONOMIC_STATUS_INVALID',
  'PROVISIONAL_STATUS_INVALID',
  'ENVIRONMENT_INVALID',
  'IS_EXTINCT_INVALID',
  'NAME_CONTAINS_EXTINCT_SYMBOL',
  'GEOTIME_INVALID',
  'SCRUTINIZER_DATE_INVALID',
  'CHAINED_SYNONYM',
  'PARENT_CYCLE',
  'SYNONYM_PARENT',
  'CLASSIFICATION_RANK_ORDER_INVALID',
  'CLASSIFICATION_NOT_APPLIED',
  'PARENT_NAME_MISMATCH',
  'DERIVED_TAXONOMIC_STATUS',
  'TAXONOMIC_STATUS_DOUBTFUL',
  'SYNONYM_DATA_MOVED',
  'SYNONYM_DATA_REMOVED',
  'REFTYPE_INVALID',
  'ACCORDING_TO_CONFLICT',
  'VERNACULAR_NAME_INVALID',
  'VERNACULAR_LANGUAGE_INVALID',
  'VERNACULAR_SEX_INVALID',
  'VERNACULAR_COUNTRY_INVALID',
  'VERNACULAR_NAME_TRANSLITERATED',
  'DISTRIBUTION_INVALID',
  'DISTRIBUTION_AREA_INVALID',
  'DISTRIBUTION_STATUS_INVALID',
  'DISTRIBUTION_GAZETEER_INVALID',
  'MEDIA_CREATED_DATE_INVALID',
  'UNPARSABLE_YEAR',
  'UNLIKELY_YEAR',
  'MULTIPLE_PUBLISHED_IN_REFERENCES',
  'UNPARSABLE_REFERENCE',
  'UNPARSABLE_REFERENCE_TYPE',
  'UNMATCHED_REFERENCE_BRACKETS',
  'CITATION_CONTAINER_TITLE_UNPARSED',
  'CITATION_DETAILS_UNPARSED',
  'CITATION_AUTHORS_UNPARSED',
  'CITATION_UNPARSED',
  'UNPARSABLE_TREATMENT',
  'UNPARSABLE_TREAMENT_FORMAT',
  'ESTIMATE_INVALID',
  'ESTIMATE_TYPE_INVALID'
);

ALTER TABLE verbatim ALTER COLUMN issues TYPE ISSUE[] USING issues::ISSUE[];
CREATE INDEX ON verbatim using GIN (issues);

ALTER TABLE verbatim_source ALTER COLUMN issues TYPE ISSUE[] USING issues::ISSUE[];
CREATE INDEX ON verbatim_source using GIN (issues);
```


### 2021-01-13 new types
```
ALTER TYPE ISSUE ADD VALUE 'PARENT_SPECIES_MISSING' AFTER 'ACCEPTED_NAME_MISSING';
ALTER TYPE TYPESTATUS ADD VALUE 'PLESIOTYPE' AFTER 'PLASTOTYPE';
ALTER TYPE TYPESTATUS ADD VALUE 'HOMOEOTYPE' AFTER 'PLESIOTYPE';
```


### 2020-12-13 add missing GIN indices
```
CREATE INDEX ON dataset USING GIN (doc);
CREATE INDEX ON vernacular_name USING GIN (doc);
CREATE INDEX ON reference USING GIN (doc);
CREATE INDEX ON verbatim USING GIN (doc);
```

### 2020-11-12 separate name match table
```
CREATE TABLE verbatim_source (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,  
  issues ISSUE[] DEFAULT '{}'
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim_source USING GIN(issues);
```

and for all MANAGED data partitions `./exec-sql {YOURFILE} --managed true`
```
CREATE TABLE verbatim_source_{KEY} (LIKE verbatim_source INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
ALTER TABLE verbatim_source_{KEY} ADD PRIMARY KEY (id);
ALTER TABLE verbatim_source_{KEY} ADD FOREIGN KEY (id) REFERENCES name_usage_{KEY} ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE verbatim_source ATTACH PARTITION verbatim_source_{KEY} FOR VALUES IN ( {KEY} );

INSERT INTO verbatim_source_{KEY} (dataset_key, id, source_dataset_key, source_id)
  SELECT distinct {KEY}, u.id, (terms->>'dwc:datasetID')::int, terms->>'col:ID' FROM name_{KEY} n JOIN name_usage_{KEY} u ON n.id=u.name_id JOIN verbatim_{KEY} v ON n.verbatim_key=v.id
  WHERE u.sector_key IS NOT NULL;
```

### 2020-11-12 separate name match table
```
ALTER TABLE name DROP COLUMN name_index_id;
ALTER TABLE name DROP COLUMN name_index_match_type;
TRUNCATE names_index RESTART IDENTITY CASCADE;

CREATE TABLE name_match (
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  type MATCHTYPE,
  index_id INTEGER NOT NULL REFERENCES names_index,
  name_id TEXT NOT NULL,
  PRIMARY KEY (dataset_key, name_id)
);
CREATE INDEX ON name_match (dataset_key, sector_key);
CREATE INDEX ON name_match (dataset_key, index_id);
CREATE INDEX ON name_match (index_id);
```

### 2020-11-03 concept rels and species interactions
```
DROP TYPE AREASTANDARD;
ALTER TYPE ENTITYTYPE RENAME VALUE 'TAXON_RELATION' TO 'TAXON_CONCEPT_RELATION';
ALTER TYPE ENTITYTYPE ADD VALUE 'SPECIES_INTERACTION';

CREATE TYPE SPECIESINTERACTIONTYPE AS ENUM (
  'RELATED_TO',
  'CO_OCCURS_WITH',
  'INTERACTS_WITH',
  'ADJACENT_TO',
  'SYMBIONT_OF',
  'EATS',
  'EATEN_BY',
  'KILLS',
  'KILLED_BY',
  'PREYS_UPON',
  'PREYED_UPON_BY',
  'HOST_OF',
  'HAS_HOST',
  'PARASITE_OF',
  'HAS_PARASITE',
  'PATHOGEN_OF',
  'HAS_PATHOGEN',
  'VECTOR_OF',
  'HAS_VECTOR',
  'ENDOPARASITE_OF',
  'HAS_ENDOPARASITE',
  'ECTOPARASITE_OF',
  'HAS_ECTOPARASITE',
  'HYPERPARASITE_OF',
  'HAS_HYPERPARASITE',
  'KLEPTOPARASITE_OF',
  'HAS_KLEPTOPARASITE',
  'PARASITOID_OF',
  'HAS_PARASITOID',
  'HYPERPARASITOID_OF',
  'HAS_HYPERPARASITOID',
  'VISITS',
  'VISITED_BY',
  'VISITS_FLOWERS_OF',
  'FLOWERS_VISITED_BY',
  'POLLINATES',
  'POLLINATED_BY',
  'LAYS_EGGS_ON',
  'HAS_EGGS_LAYED_ON_BY',
  'EPIPHYTE_OF',
  'HAS_EPIPHYTE',
  'COMMENSALIST_OF',
  'MUTUALIST_OF'
);

CREATE TYPE TAXONCONCEPTRELTYPE AS ENUM (
  'EQUALS',
  'INCLUDES',
  'INCLUDED_IN',
  'OVERLAPS',
  'EXCLUDES'
);

ALTER TABLE dataset_import ADD COLUMN species_interactions_by_type_count HSTORE;
ALTER TABLE dataset_import ADD COLUMN taxon_concept_relations_by_type_count HSTORE;
ALTER TABLE dataset_import DROP COLUMN taxon_relations_by_type_count;

ALTER TABLE sector_import ADD COLUMN species_interactions_by_type_count HSTORE;
ALTER TABLE sector_import ADD COLUMN taxon_concept_relations_by_type_count HSTORE;
ALTER TABLE sector_import DROP COLUMN taxon_relations_by_type_count;

CREATE INDEX ON estimate (dataset_key, target_id);
CREATE INDEX ON estimate (dataset_key, reference_id);

ALTER TABLE name_rel RENAME COLUMN published_in_id TO reference_id;
CREATE INDEX ON name_rel (reference_id);

CREATE TABLE taxon_concept_rel (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  type TAXONCONCEPTRELTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  related_taxon_id TEXT NOT NULL,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON taxon_concept_rel (taxon_id, type);
CREATE INDEX ON taxon_concept_rel (sector_key);
CREATE INDEX ON taxon_concept_rel (verbatim_key);
CREATE INDEX ON taxon_concept_rel (reference_id);

CREATE TABLE species_interaction (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  type SPECIESINTERACTIONTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  related_taxon_id TEXT,
  related_taxon_scientific_name TEXT,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON species_interaction (taxon_id, type);
CREATE INDEX ON species_interaction (sector_key);
CREATE INDEX ON species_interaction (verbatim_key);
CREATE INDEX ON species_interaction (reference_id);

CREATE INDEX ON vernacular_name (reference_id);
CREATE INDEX ON distribution (reference_id);
CREATE INDEX ON media (reference_id);
```

and for all data partitions
```
DROP TABLE taxon_rel_{KEY};
CREATE TABLE taxon_concept_rel_{KEY} (LIKE taxon_concept_rel INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE species_interaction_{KEY} (LIKE species_interaction INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
``` 

And then once:
```
DROP TABLE taxon_rel;
DROP TYPE TAXRELTYPE;
```

### 2020-10-29 new gazetteer 
```
ALTER TYPE GAZETTEER ADD VALUE 'MRGID' AFTER 'IHO';
ALTER TYPE ENTITYTYPE ADD VALUE 'ESTIMATE';
ALTER TYPE ISSUE ADD VALUE 'ESTIMATE_INVALID';
ALTER TYPE ISSUE ADD VALUE 'ESTIMATE_TYPE_INVALID';
```

### 2020-10-29 estimate imports
```
ALTER TABLE dataset_import ADD COLUMN estimate_count INTEGER;
ALTER TABLE sector_import ADD COLUMN estimate_count INTEGER;
ALTER TABLE estimate ADD COLUMN verbatim_key INTEGER;
ALTER TABLE estimate ALTER COLUMN target_name DROP NOT NULL;
``` 

We also need estimate id sequences on all tables, not just for managed datasets.
Run the following with the `execSql --sqlfile add-estimate-seq.sql` command using this template:

```
CREATE SEQUENCE IF NOT EXISTS estimate_{KEY}_id_seq START 1;
```

### 2020-10-28 new type status 
```
ALTER TYPE TYPESTATUS ADD VALUE 'ISOPARATYPE' AFTER 'ISONEOTYPE';
```

### 2020-10-26 project sources not requiring import attempt 
```
ALTER TABLE project_source ALTER COLUMN import_attempt DROP NOT NULL;
```

### 2020-10-21 organisations class 
https://github.com/CatalogueOfLife/backend/issues/882
```
CREATE TYPE organisation AS (name text, department text, city text, state text, country CHAR(2));

CREATE OR REPLACE FUNCTION organisation_str(organisation[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION text2person(text) RETURNS person AS
$$
SELECT ROW(null, $1, null, null)::person
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION text2organisation(text) RETURNS organisation AS
$$
SELECT ROW($1, null, null, null, null)::organisation
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS person) WITH FUNCTION text2person;
CREATE CAST (text AS organisation) WITH FUNCTION text2organisation;

-- DATASET TABLE
ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset ALTER COLUMN organisations SET DEFAULT NULL;
ALTER TABLE dataset ALTER COLUMN organisations TYPE organisation[] USING organisations::organisation[];
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(organisation_str(organisations), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(contact), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(authors), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(editors), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
  ) STORED;

-- others
ALTER TABLE dataset_archive ALTER COLUMN organisations TYPE organisation[] USING organisations::organisation[];
ALTER TABLE dataset_patch ALTER COLUMN organisations TYPE organisation[] USING organisations::organisation[];
ALTER TABLE project_source ALTER COLUMN organisations TYPE organisation[] USING organisations::organisation[];
```

### 2020-10-15 bare names and sectors for all
https://github.com/CatalogueOfLife/checklistbank/issues/749
```
ALTER TYPE TAXONOMICSTATUS ADD VALUE 'BARE_NAME' AFTER 'MISAPPLIED';

-- missing reference_id and name_id indices caused (cascading) deletions from references, usages or name to be really slow
-- also need to delete orphaned refs 
CREATE INDEX ON distribution (reference_id);
CREATE INDEX ON media (reference_id);
CREATE INDEX ON taxon_rel (reference_id);
--CREATE INDEX ON type_material (reference_id);
CREATE INDEX ON vernacular_name (reference_id);
--CREATE INDEX ON name_usage (according_to_id);
--CREATE INDEX ON name (published_in_id);
CREATE INDEX ON name_rel (published_in_id);
CREATE INDEX ON name_rel (name_id);
CREATE INDEX ON name_rel (related_name_id);
CREATE INDEX ON taxon_rel (taxon_id);
CREATE INDEX ON taxon_rel (related_taxon_id);

-- add sector_key to all entities
-- https://github.com/CatalogueOfLife/backend/issues/335
ALTER TABLE name_rel ADD COLUMN sector_key INTEGER;
CREATE INDEX ON name_rel (sector_key);

CREATE INDEX ON type_material (sector_key);

ALTER TABLE taxon_rel ADD COLUMN sector_key INTEGER;
CREATE INDEX ON taxon_rel (sector_key);

ALTER TABLE vernacular_name ADD COLUMN sector_key INTEGER;
CREATE INDEX ON vernacular_name (sector_key);

ALTER TABLE distribution ADD COLUMN sector_key INTEGER;
CREATE INDEX ON distribution (sector_key);

ALTER TABLE treatment ADD COLUMN sector_key INTEGER;
CREATE INDEX ON treatment (sector_key);
CREATE INDEX ON treatment (verbatim_key);

ALTER TABLE media ADD COLUMN sector_key INTEGER;
CREATE INDEX ON media (sector_key); 
```

### 2020-10-15 partition indices
per dataset via `execSql --sqlfile indices.sql`:
```
DROP INDEX IF EXISTS taxon_rel_{KEY}_taxon_id_type_idx;
DROP INDEX IF EXISTS taxon_rel_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS distribution_{KEY}_taxon_id_idx;
DROP INDEX IF EXISTS distribution_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS media_{KEY}_taxon_id_idx;
DROP INDEX IF EXISTS media_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS vernacular_name_{KEY}_lower_idx;
DROP INDEX IF EXISTS vernacular_name_{KEY}_taxon_id_idx;
DROP INDEX IF EXISTS vernacular_name_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS name_usage_{KEY}_name_id_idx;
DROP INDEX IF EXISTS name_usage_{KEY}_parent_id_idx;
DROP INDEX IF EXISTS name_usage_{KEY}_sector_key_idx;
DROP INDEX IF EXISTS name_usage_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS name_rel_{KEY}_name_id_type_idx;
DROP INDEX IF EXISTS name_rel_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS type_material_{KEY}_name_id_idx;
DROP INDEX IF EXISTS type_material_{KEY}_reference_id_idx;
DROP INDEX IF EXISTS type_material_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS name_{KEY}_homotypic_name_id_idx;
DROP INDEX IF EXISTS name_{KEY}_lower_idx;
DROP INDEX IF EXISTS name_{KEY}_published_in_id_idx;
DROP INDEX IF EXISTS name_{KEY}_scientific_name_normalized_idx;
DROP INDEX IF EXISTS name_{KEY}_sector_key_idx;
DROP INDEX IF EXISTS name_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS name_{KEY}_name_index_id_idx;
DROP INDEX IF EXISTS reference_{KEY}_sector_key_idx;
DROP INDEX IF EXISTS reference_{KEY}_verbatim_key_idx;
DROP INDEX IF EXISTS verbatim_{KEY}_issues_idx;
DROP INDEX IF EXISTS verbatim_{KEY}_terms_idx;
DROP INDEX IF EXISTS verbatim_{KEY}_type_idx;
```

then once:
```
CREATE INDEX ON verbatim USING GIN(issues);
CREATE INDEX ON verbatim (type);
CREATE INDEX ON verbatim USING GIN (terms jsonb_path_ops);
CREATE INDEX ON reference (verbatim_key);
CREATE INDEX ON reference (sector_key);
CREATE INDEX ON name (sector_key);
CREATE INDEX ON name (verbatim_key);
CREATE INDEX ON name (homotypic_name_id);
CREATE INDEX ON name (name_index_id);
CREATE INDEX ON name (published_in_id);
CREATE INDEX ON name (lower(scientific_name));
CREATE INDEX ON name (scientific_name_normalized);
CREATE INDEX ON name_rel (name_id, type);
CREATE INDEX ON name_rel (verbatim_key);
CREATE INDEX ON type_material (name_id);
CREATE INDEX ON type_material (reference_id);
CREATE INDEX ON type_material (verbatim_key);
CREATE INDEX ON name_usage (name_id);
CREATE INDEX ON name_usage (parent_id);
CREATE INDEX ON name_usage (verbatim_key);
CREATE INDEX ON name_usage (sector_key);
CREATE INDEX ON name_usage (according_to_id);
CREATE INDEX ON taxon_rel (taxon_id, type);
CREATE INDEX ON taxon_rel (verbatim_key);
CREATE INDEX ON distribution (taxon_id);
CREATE INDEX ON distribution (verbatim_key);
CREATE INDEX ON media (taxon_id);
CREATE INDEX ON media (verbatim_key);
CREATE INDEX ON vernacular_name (taxon_id);
CREATE INDEX ON vernacular_name (verbatim_key);
```

### 2020-10-13 generated doc cols
```
-- immutable person casts to text function to be used in indexes
CREATE OR REPLACE FUNCTION person_str(person) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION person_str(person[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION array_str(text[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

DROP TRIGGER dataset_trigger ON dataset;
DROP FUNCTION dataset_doc_update;
ALTER TABLE  DROP COLUMN doc;
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
  setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
  setweight(to_tsvector('simple2', coalesce(array_str(organisations), '')), 'B') ||
  setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
  setweight(to_tsvector('simple2', coalesce(person_str(contact), '')), 'C') ||
  setweight(to_tsvector('simple2', coalesce(person_str(authors), '')), 'C') ||
  setweight(to_tsvector('simple2', coalesce(person_str(editors), '')), 'C') ||
  setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
) STORED;
CREATE INDEX ON dataset USING gin(doc);

DROP FUNCTION verbatim_doc_update CASCADE;
ALTER TABLE verbatim DROP COLUMN doc;
ALTER TABLE verbatim ADD COLUMN doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('simple2', coalesce(terms,'{}'::jsonb), '["string", "numeric"]')) STORED;
CREATE INDEX ON verbatim USING gin(doc);

DROP FUNCTION reference_doc_update CASCADE;
ALTER TABLE reference DROP COLUMN doc;
ALTER TABLE reference ADD COLUMN doc tsvector GENERATED ALWAYS AS (
    jsonb_to_tsvector('simple2', coalesce(csl,'{}'::jsonb), '["string", "numeric"]') ||
          to_tsvector('simple2', coalesce(citation,'')) ||
          to_tsvector('simple2', coalesce(year::text,''))
) STORED;
CREATE INDEX ON reference USING gin(doc);
```

### 2020-10-13 vernacular search
```
ALTER TABLE vernacular_name ADD COLUMN doc tsvector GENERATED ALWAYS AS (to_tsvector('simple2', coalesce(name, '') || ' ' || coalesce(latin, ''))) STORED;
```

### 2020-10-09 person custom type
```
CREATE TYPE person AS (given text, family text, email text, orcid text);

CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

--
-- DATASET TABLE
--
ALTER TABLE dataset ADD COLUMN contact2 person;
UPDATE dataset SET contact2=ROW(contact->>'givenName', contact->>'familyName', contact->>'email', contact->>'orcid')::person WHERE contact IS NOT NULL;
ALTER TABLE dataset DROP COLUMN contact;
ALTER TABLE dataset RENAME COLUMN contact2 TO contact;

ALTER TABLE dataset ADD COLUMN authors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(authors) AS a  
    FROM dataset WHERE authors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset d SET authors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset DROP COLUMN authors;
ALTER TABLE dataset RENAME COLUMN authors2 TO authors;
DROP TABLE people;

ALTER TABLE dataset ADD COLUMN editors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(editors) AS a  
    FROM dataset WHERE editors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset d SET editors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset DROP COLUMN editors;
ALTER TABLE dataset RENAME COLUMN editors2 TO editors;
DROP TABLE people;


--
-- DATASET_ARCHIVE TABLE
--
ALTER TABLE dataset_archive ADD COLUMN contact2 person;
UPDATE dataset_archive SET contact2=ROW(contact->>'givenName', contact->>'familyName', contact->>'email', contact->>'orcid')::person WHERE contact IS NOT NULL;
ALTER TABLE dataset_archive DROP COLUMN contact;
ALTER TABLE dataset_archive RENAME COLUMN contact2 TO contact;

ALTER TABLE dataset_archive ADD COLUMN authors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(authors) AS a  
    FROM dataset_archive WHERE authors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset_archive d SET authors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset_archive DROP COLUMN authors;
ALTER TABLE dataset_archive RENAME COLUMN authors2 TO authors;
DROP TABLE people;

ALTER TABLE dataset_archive ADD COLUMN editors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(editors) AS a  
    FROM dataset_archive WHERE editors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset_archive d SET editors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset_archive DROP COLUMN editors;
ALTER TABLE dataset_archive RENAME COLUMN editors2 TO editors;
DROP TABLE people;

--
-- PROJECT_SOURCE TABLE
--
ALTER TABLE project_source ADD COLUMN contact2 person;
UPDATE project_source SET contact2=ROW(contact->>'givenName', contact->>'familyName', contact->>'email', contact->>'orcid')::person WHERE contact IS NOT NULL;
ALTER TABLE project_source DROP COLUMN contact;
ALTER TABLE project_source RENAME COLUMN contact2 TO contact;

ALTER TABLE project_source ADD COLUMN authors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(authors) AS a  
    FROM project_source WHERE authors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE project_source d SET authors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE project_source DROP COLUMN authors;
ALTER TABLE project_source RENAME COLUMN authors2 TO authors;
DROP TABLE people;

ALTER TABLE project_source ADD COLUMN editors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(editors) AS a  
    FROM project_source WHERE editors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE project_source d SET editors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE project_source DROP COLUMN editors;
ALTER TABLE project_source RENAME COLUMN editors2 TO editors;
DROP TABLE people;


--
-- DATASET_PATCH TABLE
--
ALTER TABLE dataset_patch ADD COLUMN contact2 person;
UPDATE dataset_patch SET contact2=ROW(contact->>'givenName', contact->>'familyName', contact->>'email', contact->>'orcid')::person WHERE contact IS NOT NULL;
ALTER TABLE dataset_patch DROP COLUMN contact;
ALTER TABLE dataset_patch RENAME COLUMN contact2 TO contact;

ALTER TABLE dataset_patch ADD COLUMN authors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(authors) AS a  
    FROM dataset_patch WHERE authors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset_patch d SET authors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset_patch DROP COLUMN authors;
ALTER TABLE dataset_patch RENAME COLUMN authors2 TO authors;
DROP TABLE people;

ALTER TABLE dataset_patch ADD COLUMN editors2 person[];
CREATE TABLE people AS (
  SELECT key, array_agg(ROW(a->>'givenName', a->>'familyName', a->>'email', a->>'orcid')::person) AS ps FROM (
    SELECT key, jsonb_array_elements(editors) AS a  
    FROM dataset_patch WHERE editors IS NOT NULL
  ) AS a GROUP BY key
);
UPDATE dataset_patch d SET editors2=p.ps FROM people p WHERE p.key=d.key;
ALTER TABLE dataset_patch DROP COLUMN editors;
ALTER TABLE dataset_patch RENAME COLUMN editors2 TO editors;
DROP TABLE people;


CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(((NEW.contact).family)::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(((NEW.authors[1]).family)::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(((NEW.editors[1]).family)::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;
```

### 2020-10-07 author & editor
```
ALTER TABLE dataset RENAME COLUMN editors TO access_control;
ALTER TABLE dataset ADD COLUMN authors JSONB;
ALTER TABLE dataset ADD COLUMN editors JSONB;
UPDATE dataset set authors = authors_and_editors WHERE authors_and_editors IS NOT NULL;
ALTER TABLE dataset DROP COLUMN authors_and_editors;

ALTER TABLE dataset_archive ADD COLUMN authors JSONB;
ALTER TABLE dataset_archive ADD COLUMN editors JSONB;
UPDATE dataset_archive set authors = authors_and_editors WHERE authors_and_editors IS NOT NULL;
ALTER TABLE dataset_archive DROP COLUMN authors_and_editors;

ALTER TABLE project_source ADD COLUMN authors JSONB;
ALTER TABLE project_source ADD COLUMN editors JSONB;
UPDATE project_source set authors = authors_and_editors WHERE authors_and_editors IS NOT NULL;
ALTER TABLE project_source DROP COLUMN authors_and_editors;

ALTER TABLE dataset_patch ADD COLUMN authors JSONB;
ALTER TABLE dataset_patch ADD COLUMN editors JSONB;
UPDATE dataset_patch set authors = authors_and_editors WHERE authors_and_editors IS NOT NULL;
ALTER TABLE dataset_patch DROP COLUMN authors_and_editors;

ALTER TABLE dataset_patch ADD COLUMN contact2 JSONB;
UPDATE dataset_patch SET contact2 = json_build_object('familyName',contact) WHERE contact IS NOT NULL;
ALTER TABLE dataset_patch DROP COLUMN contact;
ALTER TABLE dataset_patch RENAME COLUMN contact2 TO contact;


CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce((NEW.contact->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce((NEW.authors->0->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce((NEW.editors->0->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;
```

### 2020-09-27 require single names index match sine code
```
ALTER TABLE names_index DROP COLUMN code;
ALTER TABLE names_index DROP COLUMN type;
ALTER TABLE names_index DROP COLUMN notho;
ALTER TABLE names_index DROP COLUMN candidatus;

ALTER TABLE name DROP COLUMN name_index_ids; 
ALTER TABLE name ADD COLUMN name_index_id INTEGER;

ALTER TYPE MATCHTYPE ADD VALUE 'CANONICAL' before 'INSERTED';
```

### 2020-09-25 names index canonical
```
ALTER TYPE IMPORTSTATE ADD VALUE 'ARCHIVING' before 'EXPORTING';
TRUNCATE names_index;
ALTER TABLE names_index ADD COLUMN canonical_id INTEGER NOT NULL REFERENCES names_index;
```

Run the following to update all foreign keys to on update cascade 
with the `execSql --sqlfile upd-cascade.sql` command using the following sql template:

```
ALTER TABLE name_{KEY} DROP CONSTRAINT IF EXISTS name_{KEY}_publishedin_id_fk;
ALTER TABLE name_{KEY} ADD CONSTRAINT name_{KEY}_publishedin_id_fk FOREIGN KEY (published_in_id) REFERENCES reference_{KEY} (id) ON UPDATE CASCADE;

ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_name_id_fk;
ALTER TABLE name_rel_{KEY} ADD CONSTRAINT name_rel_{KEY}_name_id_fk FOREIGN KEY (name_id) REFERENCES name_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_related_name_id_fk;
ALTER TABLE name_rel_{KEY} ADD CONSTRAINT name_rel_{KEY}_related_name_id_fk FOREIGN KEY (related_name_id) REFERENCES name_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_published_in_id_fk;
ALTER TABLE name_rel_{KEY} ADD CONSTRAINT name_rel_{KEY}_published_in_id_fk FOREIGN KEY (published_in_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_name_id_fk;
ALTER TABLE type_material_{KEY} ADD CONSTRAINT type_material_{KEY}_name_id_fk FOREIGN KEY (name_id) REFERENCES name_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_reference_id_fk;
ALTER TABLE type_material_{KEY} ADD CONSTRAINT type_material_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_name_id_fk;
ALTER TABLE name_usage_{KEY} ADD CONSTRAINT name_usage_{KEY}_name_id_fk FOREIGN KEY (name_id) REFERENCES name_{KEY} (id) ON UPDATE CASCADE;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_parent_id_fk;
ALTER TABLE name_usage_{KEY} ADD CONSTRAINT name_usage_{KEY}_parent_id_fk FOREIGN KEY (parent_id) REFERENCES name_usage_{KEY} (id) ON UPDATE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE taxon_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_rel_{KEY}_taxon_id_fk;
ALTER TABLE taxon_rel_{KEY} ADD CONSTRAINT taxon_rel_{KEY}_taxon_id_fk FOREIGN KEY (taxon_id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE taxon_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_rel_{KEY}_related_taxon_id_fk;
ALTER TABLE taxon_rel_{KEY} ADD CONSTRAINT taxon_rel_{KEY}_related_taxon_id_fk FOREIGN KEY (related_taxon_id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE taxon_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_rel_{KEY}_reference_id_fk;
ALTER TABLE taxon_rel_{KEY} ADD CONSTRAINT taxon_rel_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE treatment_{KEY} DROP CONSTRAINT IF EXISTS treatment_{KEY}_id_fk;
ALTER TABLE treatment_{KEY} ADD CONSTRAINT treatment_{KEY}_id_fk FOREIGN KEY (id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_taxon_id_fk;
ALTER TABLE distribution_{KEY} ADD CONSTRAINT distribution_{KEY}_taxon_id_fk FOREIGN KEY (taxon_id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_reference_id_fk;
ALTER TABLE distribution_{KEY} ADD CONSTRAINT distribution_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_taxon_id_fk;
ALTER TABLE media_{KEY} ADD CONSTRAINT media_{KEY}_taxon_id_fk FOREIGN KEY (taxon_id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_reference_id_fk;
ALTER TABLE media_{KEY} ADD CONSTRAINT media_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_taxon_id_fk;
ALTER TABLE vernacular_name_{KEY} ADD CONSTRAINT vernacular_name_{KEY}_taxon_id_fk FOREIGN KEY (taxon_id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_reference_id_fk;
ALTER TABLE vernacular_name_{KEY} ADD CONSTRAINT vernacular_name_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE ON UPDATE CASCADE;
``` 

### 2020-09-18 dataset person
```
--
-- dataset
--

CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      --setweight(to_tsvector('simple2', coalesce((NEW.contact->'familyName')::text,'')), 'C') ||
      --setweight(to_tsvector('simple2', coalesce((NEW.authors_and_editors->0->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;


ALTER TABLE dataset ADD contact2 JSONB;
UPDATE dataset SET contact2 = json_build_object('familyName',contact) WHERE contact IS NOT NULL;
ALTER TABLE dataset DROP COLUMN contact;
ALTER TABLE dataset RENAME COLUMN contact2 TO contact;

ALTER TABLE dataset ADD authors2 JSONB;
CREATE TABLE dataset_authors AS (
    SELECT key, unnest(authors_and_editors) AS "author"  
    FROM dataset WHERE authors_and_editors IS NOT NULL AND array_length(authors_and_editors,1)>0
); 
CREATE TABLE dataset_authors2 AS (
 SELECT key, array_to_json(array_agg(json_build_object('familyName',author)))::jsonb as authors from dataset_authors
 GROUP BY key
); 
UPDATE dataset d SET authors2=a.authors FROM dataset_authors2 a WHERE a.key=d.key;
ALTER TABLE dataset DROP COLUMN authors_and_editors;
ALTER TABLE dataset RENAME COLUMN authors2 TO authors_and_editors;
DROP TABLE dataset_authors;
DROP TABLE dataset_authors2;

CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce((NEW.contact->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce((NEW.authors_and_editors->0->'familyName')::text,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;


--
-- dataset_archive
--

ALTER TABLE dataset_archive ADD contact2 JSONB;
UPDATE dataset_archive SET contact2 = json_build_object('familyName',contact) WHERE contact IS NOT NULL;
ALTER TABLE dataset_archive DROP COLUMN contact;
ALTER TABLE dataset_archive RENAME COLUMN contact2 TO contact;

ALTER TABLE dataset_archive ADD authors2 JSONB;
CREATE TABLE dataset_authors AS (
    SELECT key, unnest(authors_and_editors) AS "author"  
    FROM dataset_archive WHERE authors_and_editors IS NOT NULL AND array_length(authors_and_editors,1)>0
); 
CREATE TABLE dataset_authors2 AS (
 SELECT key, array_to_json(array_agg(json_build_object('familyName',author)))::jsonb as authors from dataset_authors
 GROUP BY key
); 
UPDATE dataset_archive d SET authors2=a.authors FROM dataset_authors2 a WHERE a.key=d.key;
ALTER TABLE dataset_archive DROP COLUMN authors_and_editors;
ALTER TABLE dataset_archive RENAME COLUMN authors2 TO authors_and_editors;
DROP TABLE dataset_authors;
DROP TABLE dataset_authors2;


--
-- project_source
--

ALTER TABLE project_source ADD contact2 JSONB;
UPDATE project_source SET contact2 = json_build_object('familyName',contact) WHERE contact IS NOT NULL;
ALTER TABLE project_source DROP COLUMN contact;
ALTER TABLE project_source RENAME COLUMN contact2 TO contact;

ALTER TABLE project_source ADD authors2 JSONB;
CREATE TABLE dataset_authors AS (
    SELECT key, unnest(authors_and_editors) AS "author"  
    FROM project_source WHERE authors_and_editors IS NOT NULL AND array_length(authors_and_editors,1)>0
); 
CREATE TABLE dataset_authors2 AS (
 SELECT key, array_to_json(array_agg(json_build_object('familyName',author)))::jsonb as authors from dataset_authors
 GROUP BY key
); 
UPDATE project_source d SET authors2=a.authors FROM dataset_authors2 a WHERE a.key=d.key;
ALTER TABLE project_source DROP COLUMN authors_and_editors;
ALTER TABLE project_source RENAME COLUMN authors2 TO authors_and_editors;
DROP TABLE dataset_authors;
DROP TABLE dataset_authors2;
```

### 2020-09-16 lifezone -> environment
```
ALTER TYPE LIFEZONE RENAME TO ENVIRONMENT;
ALTER TYPE ISSUE RENAME VALUE 'LIFEZONE_INVALID' TO 'ENVIRONMENT_INVALID';
UPDATE dataset_import SET issues_by_issue_count = delete(issues_by_issue_count, 'LIFEZONE_INVALID') || hstore('ENVIRONMENT_INVALID', issues_by_issue_count -> 'LIFEZONE_INVALID')
  WHERE issues_by_issue_count ? 'LIFEZONE_INVALID';
UPDATE sector_import SET issues_by_issue_count = delete(issues_by_issue_count, 'LIFEZONE_INVALID') || hstore('ENVIRONMENT_INVALID', issues_by_issue_count -> 'LIFEZONE_INVALID')
  WHERE issues_by_issue_count ? 'LIFEZONE_INVALID';
ALTER TABLE name_usage RENAME COLUMN lifezones TO environments;
ALTER TABLE decision RENAME COLUMN lifezones TO environments;
```

### 2020-09-02 renamed estimate types
```
ALTER TYPE ESTIMATETYPE RENAME VALUE 'DESCRIBED_SPECIES_LIVING' TO 'SPECIES_LIVING';
ALTER TYPE ESTIMATETYPE RENAME VALUE 'DESCRIBED_SPECIES_EXTINCT' TO 'SPECIES_EXTINCT';
```

### 2020-08-27 usage counter
```
CREATE TABLE usage_count (
  dataset_key int PRIMARY KEY,
  counter int
);

CREATE OR REPLACE FUNCTION count_usage_on_insert()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
    EXECUTE 'UPDATE usage_count set counter=counter+(select count(*) from inserted) where dataset_key=' || TG_ARGV[0];
    RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION count_usage_on_delete()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
  EXECUTE 'UPDATE usage_count set counter=counter-(select count(*) from deleted) where dataset_key=' || TG_ARGV[0];
  RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';
```


Then run this script against all managed datasets with the `execSql --managed --sqlfile YOUR_FILE.sql` command using the following sql template:
```
INSERT INTO usage_count (dataset_key, counter) VALUES ({KEY}, (SELECT count(*) from name_usage_{KEY}));

CREATE TRIGGER trg_name_usage_{KEY}_insert
AFTER INSERT ON name_usage_{KEY}
REFERENCING NEW TABLE AS inserted
FOR EACH STATEMENT
EXECUTE FUNCTION count_usage_on_insert({KEY});

CREATE TRIGGER trg_name_usage_{KEY}_delete
AFTER DELETE ON name_usage_{KEY}
REFERENCING OLD TABLE AS deleted
FOR EACH STATEMENT
EXECUTE FUNCTION count_usage_on_delete({KEY});
```

### 2020-08-27 ignored_usage_count metrics

```
ALTER TABLE dataset_import ADD COLUMN ignored_by_reason_count HSTORE; 
ALTER TABLE dataset_import ADD COLUMN applied_decision_count INTEGER; 

ALTER TABLE sector_import ADD COLUMN ignored_by_reason_count HSTORE; 
ALTER TABLE sector_import ADD COLUMN applied_decision_count INTEGER; 
ALTER TABLE sector_import DROP COLUMN ignored_usage_count; 
```


### 2020-08-24 nom code metrics

```
ALTER TABLE dataset_import DROP COLUMN names_by_origin_count; 
ALTER TABLE dataset_import ADD COLUMN usages_by_origin_count HSTORE; 
ALTER TABLE dataset_import ADD COLUMN names_by_code_count HSTORE; 

ALTER TABLE sector_import DROP COLUMN names_by_origin_count; 
ALTER TABLE sector_import ADD COLUMN usages_by_origin_count HSTORE; 
ALTER TABLE sector_import ADD COLUMN names_by_code_count HSTORE;

ALTER TABLE name ALTER COLUMN origin TYPE text;
ALTER TABLE name_usage ALTER COLUMN origin TYPE text;
DROP TYPE ORIGIN;
CREATE TYPE ORIGIN AS ENUM (
  'SOURCE',
  'DENORMED_CLASSIFICATION',
  'VERBATIM_PARENT',
  'VERBATIM_ACCEPTED',
  'VERBATIM_BASIONYM',
  'AUTONYM',
  'IMPLICIT_NAME',
  'MISSING_ACCEPTED',
  'BASIONYM_PLACEHOLDER',
  'EX_AUTHOR_SYNONYM',
  'USER',
  'OTHER'
);
ALTER TABLE name ALTER COLUMN origin TYPE origin using origin::origin;
ALTER TABLE name_usage ALTER COLUMN origin TYPE origin using origin::origin;
```

### 2020-08-20 track extinct and synonym counts per rank
```
ALTER TABLE dataset_import ADD COLUMN extinct_taxa_by_rank_count HSTORE; 
ALTER TABLE dataset_import ADD COLUMN synonyms_by_rank_count HSTORE; 
ALTER TABLE dataset_import RENAME COLUMN issues_count TO issues_by_issue_count;
ALTER TABLE dataset_import RENAME COLUMN verbatim_by_term_count TO verbatim_by_row_type_count;
ALTER TABLE dataset_import RENAME COLUMN verbatim_by_type_count TO verbatim_by_term_count;

ALTER TABLE sector_import ADD COLUMN extinct_taxa_by_rank_count HSTORE; 
ALTER TABLE sector_import ADD COLUMN synonyms_by_rank_count HSTORE;
ALTER TABLE sector_import RENAME COLUMN issues_count TO issues_by_issue_count;
-- missing from earlier changes
ALTER TABLE sector_import ADD COLUMN type_material_count INTEGER;
ALTER TABLE sector_import ADD COLUMN type_material_by_status_count HSTORE;
ALTER TABLE sector_import DROP COLUMN verbatim_by_type_count;
```

### 2020-08-19 track bare name counts
```
ALTER TABLE dataset_import ADD COLUMN bare_name_count INTEGER; 
ALTER TABLE sector_import ADD COLUMN bare_name_count INTEGER; 
DROP INDEX dataset_gbif_key_idx;
ALTER TABLE dataset ADD UNIQUE (gbif_key);
ALTER TABLE dataset ADD UNIQUE (alias); 
-- to list duplicate aliases
-- SELECT alias, array_agg(key) from dataset where alias is not null group by alias having count(*) > 1; 
```

### 2020-08-14 division ranks
```
ALTER TYPE RANK ADD VALUE 'SUPERDIVISION' before 'SUPERLEGION';
ALTER TYPE RANK ADD VALUE 'DIVISION' before 'SUPERLEGION';
ALTER TYPE RANK ADD VALUE 'SUBDIVISION' before 'SUPERLEGION';
ALTER TYPE RANK ADD VALUE 'INFRADIVISION' before 'SUPERLEGION';
```

### 2020-08-06 sector key compression for CoL
Turned out to be more difficult and initial statements failed, so the solution became much longer 
but is documented here. The June release 2140 now has a few bad sectors wrongly linked to data and import metrics.
The latest releases and the CoL draft itself are fine.
```
CREATE SEQUENCE sector_3NG_id_seq START 1;
ALTER TABLE sector ADD COLUMN id2 integer;
UPDATE sector SET id2=nextval('sector_3NG_id_seq'::regclass) WHERE dataset_key=3;
UPDATE sector s SET id2=s2.id2 FROM sector s2 WHERE s.dataset_key IN (2079,2081,2083,2123,2140,2165,2166) AND s2.dataset_key=3 AND s.id=s2.id;
DROP SEQUENCE sector_3ng_id_seq;

UPDATE name n SET sector_key = s.id2 FROM sector s WHERE n.sector_key=s.id AND n.dataset_key=s.dataset_key AND n.sector_key IS NOT NULL AND s.id2 IS NOT NULL; 
UPDATE name_usage n SET sector_key = s.id2 FROM sector s WHERE n.sector_key=s.id AND n.dataset_key=s.dataset_key AND n.sector_key IS NOT NULL AND s.id2 IS NOT NULL;
UPDATE reference n SET sector_key = s.id2 FROM sector s WHERE n.sector_key=s.id AND n.dataset_key=s.dataset_key AND n.sector_key IS NOT NULL AND s.id2 IS NOT NULL;
UPDATE type_material n SET sector_key = s.id2 FROM sector s WHERE n.sector_key=s.id AND n.dataset_key=s.dataset_key AND n.sector_key IS NOT NULL AND s.id2 IS NOT NULL;

ALTER TABLE sector_import ADD COLUMN id2 integer;
UPDATE sector_import i SET id2=s.id2 FROM sector s WHERE i.sector_key=s.id AND i.dataset_key=s.dataset_key;
ALTER TABLE sector_import ADD CONSTRAINT sector_import_id2_unique UNIQUE (dataset_key, id2, attempt);

ALTER TABLE sector_import ADD COLUMN id3 integer;
UPDATE sector_import SET id3=coalesce(id2,sector_key);
ALTER TABLE sector_import ALTER COLUMN id3 SET NOT NULL;
ALTER TABLE sector_import ADD CONSTRAINT sector_import_id3_unique UNIQUE (dataset_key, id3, attempt);

ALTER TABLE sector_import DROP CONSTRAINT sector_import_pkey;
ALTER TABLE sector_import DROP CONSTRAINT sector_import_dataset_key_sector_key_fkey;
UPDATE sector_import SET sector_key=id3;
ALTER TABLE sector_import DROP COLUMN id2;
ALTER TABLE sector_import DROP COLUMN id3;
ALTER TABLE sector_import ADD PRIMARY KEY(dataset_key, sector_key, attempt);


ALTER TABLE sector DROP CONSTRAINT sector_pkey;
ALTER TABLE sector ADD COLUMN id_orig integer;
UPDATE sector SET id_orig=id;
UPDATE sector SET id=coalesce(id2,id);
ALTER TABLE sector ADD PRIMARY KEY(dataset_key, id);
-- failed !!!

SELECT s1.dataset_key, s1.id, s1.id2, s1.id_orig, s2.id2 as id2_b, s2.id_orig AS id_orig_B FROM sector s1, sector s2 WHERE s1.dataset_key=s2.dataset_key AND s1.id=s2.id AND s1.id_orig!=s2.id_orig AND s1.id2 IS NULL ORDER BY s1.dataset_key, s1.id;
select max(id) from sector where dataset_key =2140;
-- 77766
select max(id_orig) from sector where dataset_key =2140;
-- 101780
select max(id2) from sector where dataset_key =2140;
-- 672
SELECT id,id2,id_orig FROM sector s1 WHERE dataset_key=2140 AND id_orig <= 672 AND EXISTS (SELECT 1 FROM sector s2 WHERE s1.dataset_key=s2.dataset_key AND s1.id=s2.id AND s1.id_orig!=s2.id_orig AND s1.id2 IS NOT NULL) order by id;

UPDATE sector SET id=1000000+id_orig WHERE dataset_key=2140 AND id2 IS NULL AND id_orig <= 672 AND id<=672;
UPDATE sector s1 SET id=1000000+id_orig  WHERE dataset_key=2140 AND id_orig <= 672 AND EXISTS (SELECT 1 FROM sector s2 WHERE s1.dataset_key=s2.dataset_key AND s1.id=s2.id AND s1.id_orig!=s2.id_orig AND s1.id2 IS NOT NULL);
ALTER TABLE sector ADD PRIMARY KEY(dataset_key, id);
ALTER TABLE sector_import ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE;
SELECT setval('sector_3_id_seq',   (SELECT MAX(id)+1 FROM sector   WHERE dataset_key=3));

ALTER TABLE sector DROP COLUMN id2;
ALTER TABLE sector DROP COLUMN id_orig;
```


#### 2020-08-05 sector truely dataset scoped 

We have 7 releases currently:
```
 2079 | Catalogue of Life - February 2020
 2081 | Catalogue of Life - February 2020 Rev2
 2083 | Catalogue of Life - March 2020
 2123 | Catalogue of Life - April 2020
 2140 | Catalogue of Life - June 2020
 2165 | Catalogue of Life - July 2020
 2166 | Catalogue of Life - August 2020
```

```
DELETE FROM "user" WHERE key=-1;
DELETE FROM "user" WHERE key=13;

UPDATE name n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.dataset_key IN (2079,2081,2083,2123,2140,2165,2166) AND n.sector_key IS NOT NULL; 
UPDATE name_usage n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.dataset_key IN (2079,2081,2083,2123,2140,2165,2166) AND n.sector_key IS NOT NULL; 
UPDATE reference n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.dataset_key IN (2079,2081,2083,2123,2140,2165,2166) AND n.sector_key IS NOT NULL; 
UPDATE type_material n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.dataset_key IN (2079,2081,2083,2123,2140,2165,2166) AND n.sector_key IS NOT NULL; 

ALTER TABLE sector_import DROP CONSTRAINT sector_import_pkey;
ALTER TABLE sector_import ADD COLUMN dataset_key INTEGER;
UPDATE sector_import i SET dataset_key=s.dataset_key, sector_key=s.copied_from_id  FROM sector s WHERE i.sector_key=s.id AND s.copied_from_id IS NOT NULL;
UPDATE sector_import i SET dataset_key=s.dataset_key FROM sector s WHERE i.sector_key=s.id AND s.copied_from_id IS NULL;
ALTER TABLE sector_import ALTER COLUMN dataset_key SET NOT NULL;
ALTER TABLE sector_import ADD PRIMARY KEY (dataset_key, sector_key, attempt);
DROP INDEX sector_import_sector_key_idx;

ALTER TABLE sector DROP CONSTRAINT sector_pkey CASCADE;
DROP SEQUENCE sector_id_seq CASCADE;
UPDATE sector SET id=copied_from_id WHERE copied_from_id IS NOT NULL;
ALTER TABLE sector DROP COLUMN copied_from_id;
ALTER TABLE sector ADD PRIMARY KEY (dataset_key, id);
DROP INDEX sector_dataset_key_subject_dataset_key_subject_id_idx;

ALTER TABLE sector_import ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE;

ALTER TABLE decision DROP CONSTRAINT decision_pkey CASCADE;
DROP SEQUENCE decision_id_seq CASCADE;
ALTER TABLE decision ADD PRIMARY KEY (dataset_key, id);
DROP INDEX decision_dataset_key_subject_dataset_key_subject_id_idx;

ALTER TABLE estimate DROP CONSTRAINT estimate_pkey CASCADE;
DROP SEQUENCE estimate_id_seq CASCADE;
ALTER TABLE estimate ADD PRIMARY KEY (dataset_key, id);

-- managed sequences
CREATE SEQUENCE sector_3_id_seq START 1;
CREATE SEQUENCE decision_3_id_seq START 1;
CREATE SEQUENCE estimate_3_id_seq START 1;
SELECT setval('sector_3_id_seq',   (SELECT MAX(id)+1 FROM sector   WHERE dataset_key=3));
SELECT setval('decision_3_id_seq', (SELECT MAX(id)+1 FROM decision WHERE dataset_key=3));
SELECT setval('estimate_3_id_seq', (SELECT MAX(id)+1 FROM estimate WHERE dataset_key=3));

-- release imports moved to mother project
CREATE SEQUENCE dataset_import_col3_seq START 1;
CREATE TABLE _release_attempts AS SELECT di.dataset_key, nextval('dataset_import_col3_seq') as attempt FROM dataset d JOIN dataset_import di ON di.dataset_key=d.key 
    WHERE d.source_key=3;
ALTER TABLE _release_attempts ADD PRIMARY KEY (dataset_key);
UPDATE dataset_import di SET attempt=r.attempt FROM _release_attempts r WHERE r.dataset_key=di.dataset_key; 
DROP TABLE _release_attempts;
DROP SEQUENCE dataset_import_col3_seq;

ALTER TABLE vernacular_name ALTER COLUMN id DROP DEFAULT;

DROP VIEW table_size;
CREATE VIEW table_size AS (
    SELECT oid, TABLE_NAME, row_estimate, pg_size_pretty(total_bytes) AS total
        , pg_size_pretty(index_bytes) AS INDEX
        , pg_size_pretty(toast_bytes) AS toast
        , pg_size_pretty(table_bytes) AS TABLE
      FROM (
      SELECT *, total_bytes-index_bytes-COALESCE(toast_bytes,0) AS table_bytes FROM (
          SELECT c.oid, relname AS TABLE_NAME
                  , c.reltuples AS row_estimate
                  , pg_total_relation_size(c.oid) AS total_bytes
                  , pg_indexes_size(c.oid) AS index_bytes
                  , pg_total_relation_size(reltoastrelid) AS toast_bytes
              FROM pg_class c
              LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE relkind = 'r' AND nspname='public'
      ) a
    ) a
);
```

#### 2020-07-10 names index intset 
```
ALTER TABLE name DROP COLUMN name_index_id; 
ALTER TABLE name ADD COLUMN name_index_ids INTEGER[];

CREATE TABLE names_index (
  id SERIAL PRIMARY KEY,
  candidatus BOOLEAN DEFAULT FALSE,
  rank RANK NOT NULL,
  notho NAMEPART,
  code NOMCODE,
  type NAMETYPE NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  scientific_name TEXT NOT NULL,
  authorship TEXT,
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  remarks TEXT
);

CREATE INDEX ON names_index (lower(scientific_name));
```

Then run this script against all datasets with the `execSql --sql` command using the following sql template
```
CREATE INDEX ON name_{KEY} USING GIN(name_index_ids);
```

#### 2020-07-07 treatment imports 
```
ALTER TABLE treatment DROP COLUMN reference_id; 
ALTER TABLE treatment ADD COLUMN verbatim_key INTEGER NOT NULL;

ALTER TYPE TREATMENTFORMAT ADD VALUE 'MARKDOWN' before 'XML';
ALTER TYPE TREATMENTFORMAT ADD VALUE 'PLAIN_TEXT' before 'MARKDOWN'; 

ALTER TYPE ISSUE ADD VALUE 'UNPARSABLE_TREATMENT' after 'CITATION_UNPARSED'; 
ALTER TYPE ISSUE ADD VALUE 'UNPARSABLE_TREAMENT_FORMAT' after 'UNPARSABLE_TREATMENT'; 
```

#### 2020-07-02 project sources 
```
ALTER TABLE dataset_archive DROP CONSTRAINT dataset_archive_key_import_attempt_dataset_key_key; 
ALTER TABLE dataset_archive DROP COLUMN dataset_key;
DELETE FROM dataset_archive WHERE import_attempt IS NULL;
ALTER TABLE dataset_archive ALTER COLUMN import_attempt set not null;

CREATE TABLE project_source (LIKE dataset_archive);
ALTER TABLE project_source
  ADD COLUMN dataset_key INTEGER REFERENCES dataset,
  ADD UNIQUE (key, dataset_key);

ALTER TABLE dataset_archive ADD UNIQUE (key, import_attempt);
```

#### 2020-06-22 scrutiny changes 

```
ALTER TABLE dataset_import 
    DROP COLUMN description_count,
    ADD COLUMN treatment_count INTEGER,
    ADD COLUMN taxon_relations_by_type_count HSTORE;

ALTER TABLE sector_import 
    DROP COLUMN description_count,
    ADD COLUMN treatment_count INTEGER,
    ADD COLUMN taxon_relations_by_type_count HSTORE;

ALTER TABLE name DROP COLUMN appended_phrase;
ALTER TABLE name ADD COLUMN nomenclatural_note TEXT;
ALTER TABLE name ADD COLUMN unparsed TEXT;

ALTER TABLE parser_config DROP COLUMN appended_phrase;
ALTER TABLE parser_config ADD COLUMN unparsed TEXT;
ALTER TABLE parser_config ADD COLUMN remarks TEXT;

ALTER TABLE name_usage RENAME COLUMN according_to TO scrutinizer;
ALTER TABLE name_usage RENAME COLUMN according_to_date TO scrutinizer_date;
ALTER TABLE name_usage ADD COLUMN name_phrase TEXT;
ALTER TABLE name_usage ADD COLUMN according_to_id TEXT;

CREATE TYPE TAXRELTYPE AS ENUM (
  'EQUALS',
  'INCLUDES',
  'INCLUDED_IN',
  'OVERLAPS',
  'EXCLUDES',
  'INTERACTS_WITH',
  'VISITS',
  'INHABITS',
  'SYMBIONT_OF',
  'ASSOCIATED_WITH',
  'EATS',
  'POLLINATES',
  'PARASITE_OF',
  'PATHOGEN_OF',
  'HOST_OF'
);

CREATE TABLE taxon_rel (
  id INTEGER NOT NULL,
  verbatim_key INTEGER,
  dataset_key INTEGER NOT NULL,
  type TAXRELTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  related_taxon_id TEXT NULL,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

DROP TABLE description;
DROP TYPE DESCRIPTIONCATEGORY;
DROP TYPE TEXTFORMAT;

CREATE TYPE TREATMENTFORMAT AS ENUM (
  'XML',
  'HTML',
  'TAX_PUB',
  'TAXON_X',
  'RDF'
);

CREATE TABLE treatment (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  format TREATMENTFORMAT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  document TEXT NOT NULL,
  reference_id TEXT
) PARTITION BY LIST (dataset_key);

ALTER TABLE parser_config ADD COLUMN published_in TEXT;
ALTER TABLE parser_config ADD COLUMN extinct BOOLEAN;


ALTER TYPE ENTITYTYPE ADD VALUE 'TAXON_RELATION' after 'NAME_USAGE';
ALTER TYPE ENTITYTYPE RENAME VALUE 'DESCRIPTION' to 'TREATMENT';

ALTER TYPE ISSUE ADD VALUE 'AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE' after 'NOMENCLATURAL_STATUS_INVALID';
ALTER TYPE ISSUE ADD VALUE 'CONFLICTING_NOMENCLATURAL_STATUS' after 'AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE';
ALTER TYPE ISSUE ADD VALUE 'AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE' after 'NAME_VARIANT';
ALTER TYPE ISSUE ADD VALUE 'NAME_CONTAINS_EXTINCT_SYMBOL' after 'IS_EXTINCT_INVALID';
ALTER TYPE ISSUE RENAME VALUE 'ACCORDING_TO_DATE_INVALID' to 'SCRUTINIZER_DATE_INVALID';
ALTER TYPE ISSUE ADD VALUE 'ACCORDING_TO_CONFLICT' after 'REFTYPE_INVALID';

DROP TYPE NAMEFIELD;
CREATE TYPE NAMEFIELD AS ENUM (
  'UNINOMIAL',
  'GENUS',
  'INFRAGENERIC_EPITHET',
  'SPECIFIC_EPITHET',
  'INFRASPECIFIC_EPITHET',
  'CULTIVAR_EPITHET',
  'CANDIDATUS',
  'NOTHO',
  'BASIONYM_AUTHORS',
  'BASIONYM_EX_AUTHORS',
  'BASIONYM_YEAR',
  'COMBINATION_AUTHORS',
  'COMBINATION_EX_AUTHORS',
  'COMBINATION_YEAR',
  'SANCTIONING_AUTHOR',
  'CODE',
  'NOM_STATUS',
  'PUBLISHED_IN',
  'PUBLISHED_IN_PAGE',
  'NOMENCLATURAL_NOTE',
  'UNPARSED',
  'REMARKS',
  'NAME_PHRASE',
  'ACCORDING_TO'
);
```

It is also required to run the `execSql --sql` command using the following sql template
in order to drop all description partitions and update existing name & name_usage partitions: 
```
CREATE TABLE treatment_{KEY} (LIKE treatment INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE treatment ATTACH PARTITION treatment_{KEY} FOR VALUES IN ( {KEY} );
ALTER TABLE treatment_{KEY} ADD PRIMARY KEY (id);
ALTER TABLE treatment_{KEY} ADD CONSTRAINT treatment_{KEY}_id_fk FOREIGN KEY (id) REFERENCES name_usage_{KEY} (id) ON DELETE CASCADE;
ALTER TABLE treatment_{KEY} ADD CONSTRAINT treatment_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE;

CREATE TABLE taxon_rel_{KEY} (LIKE taxon_rel INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE taxon_rel ATTACH PARTITION taxon_rel_{KEY} FOR VALUES IN ( {KEY} );
CREATE SEQUENCE taxon_rel_{KEY}_id_seq START 1;
ALTER TABLE taxon_rel_{KEY} ALTER COLUMN id SET DEFAULT nextval('taxon_rel_{KEY}_id_seq');

ALTER TABLE name_rel_{KEY} ADD CONSTRAINT name_rel_{KEY}_published_in_id_fk FOREIGN KEY (published_in_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE;
ALTER TABLE name_rel_{KEY} ADD CONSTRAINT name_rel_{KEY}_verbatim_key_fk FOREIGN KEY (verbatim_key) REFERENCES verbatim_{KEY} (id) ON DELETE CASCADE; 
ALTER TABLE media_{KEY} ADD CONSTRAINT media_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE;
ALTER TABLE vernacular_name_{KEY} ADD CONSTRAINT vernacular_name_{KEY}_reference_id_fk FOREIGN KEY (reference_id) REFERENCES reference_{KEY} (id) ON DELETE CASCADE;
```

#### 2020-05-25 merge import states
```
ALTER TABLE dataset_import ALTER COLUMN state TYPE text;
ALTER TABLE sector_import ALTER COLUMN state TYPE text;
DROP TYPE IMPORTSTATE;
DROP TYPE SECTORIMPORT_STATE;

CREATE TYPE IMPORTSTATE AS ENUM (
  'WAITING',
  'PREPARING',
  'DOWNLOADING',
  'PROCESSING',
  'DELETING',
  'INSERTING',
  'MATCHING',
  'INDEXING',
  'BUILDING_METRICS',
  'EXPORTING',
  'UNCHANGED',
  'FINISHED',
  'CANCELED',
  'FAILED'
);
ALTER TYPE IMPORTSTATE RENAME VALUE 'BUILDING_METRICS' to 'ANALYZING';

UPDATE sector_import SET state='INSERTING' WHERE state='COPYING';
UPDATE sector_import SET state='MATCHING' WHERE state='RELINKING';
UPDATE dataset_import SET state='FINISHED' WHERE state='RELEASED';

ALTER TABLE dataset_import ALTER COLUMN state TYPE IMPORTSTATE USING state::IMPORTSTATE;
ALTER TABLE sector_import ALTER COLUMN state TYPE IMPORTSTATE USING state::IMPORTSTATE;
```

#### 2020-05-21 duplicate job sql
```
ALTER TABLE sector ADD COLUMN copied_from_id INTEGER;
ALTER TABLE sector ADD UNIQUE (dataset_key, copied_from_id);
```

#### 2020-05-18 duplication job
```
ALTER TABLE sector_import RENAME COLUMN type TO job;
ALTER TABLE dataset_import ADD COLUMN job text;
```

#### 2020-05-13 new order ranks
```
ALTER TYPE NOMCODE ADD VALUE 'PHYTOSOCIOLOGICAL' after 'CULTIVARS';

ALTER TYPE RANK ADD VALUE 'GIGAORDER' after 'INFRACOHORT';
ALTER TYPE RANK ADD VALUE 'MIRORDER' after 'GRANDORDER';
ALTER TYPE RANK ADD VALUE 'NANORDER' after 'ORDER';
ALTER TYPE RANK ADD VALUE 'HYPOORDER' after 'NANORDER';
ALTER TYPE RANK ADD VALUE 'MINORDER' after 'HYPOORDER';
```

#### 2020-05-12 remove locked
```
ALTER TABLE dataset DROP COLUMN locked;
```
  
#### 2020-04-29 metadata archive
See https://github.com/CatalogueOfLife/backend/issues/689
```
UPDATE dataset SET settings = coalesce(settings, jsonb_build_object()) || jsonb_build_object('import frequency',  import_frequency) WHERE import_frequency IS NOT NULL;
UPDATE dataset SET settings = coalesce(settings, jsonb_build_object()) || jsonb_build_object('data access', data_access) WHERE data_access IS NOT NULL;
UPDATE dataset SET settings = coalesce(settings, jsonb_build_object()) || jsonb_build_object('data format', data_format) WHERE data_format IS NOT NULL;

ALTER TABLE dataset
  DROP COLUMN import_frequency,
  DROP COLUMN data_access,
  DROP COLUMN data_format;
ALTER TABLE dataset 
    RENAME COLUMN last_data_import_attempt TO import_attempt;

ALTER TABLE dataset_archive
  DROP COLUMN import_frequency,
  DROP COLUMN data_access,
  DROP COLUMN data_format,
  DROP COLUMN deleted,
  DROP COLUMN gbif_key,
  DROP COLUMN gbif_publisher_key,
  DROP COLUMN locked,
  DROP COLUMN private;
ALTER TABLE dataset_archive RENAME COLUMN catalogue_key TO dataset_key;
ALTER TABLE dataset_archive RENAME COLUMN last_data_import_attempt TO import_attempt;
UPDATE dataset_archive a SET import_attempt=d.import_attempt
    FROM dataset d WHERE a.import_attempt IS NULL AND d.key=a.key;
ALTER TABLE dataset_archive ADD UNIQUE (key, import_attempt, dataset_key);

ALTER TABLE sector ADD COLUMN dataset_import_attempt INTEGER;
ALTER TABLE sector RENAME COLUMN last_sync_attempt TO sync_attempt;
UPDATE sector s SET dataset_import_attempt=d.import_attempt
    FROM dataset d 
    WHERE s.sync_attempt IS NOT NULL AND d.key=s.subject_dataset_key;
```


!!! make sure all current sector imports exist in the archive !!!
```
  SELECT s.dataset_key AS project_key, s.subject_dataset_key AS key, max(s.dataset_import_attempt) AS attempt, d.import_attempt AS curr_attempt
  FROM sector s
    JOIN dataset d ON d.key=s.subject_dataset_key
  WHERE s.dataset_import_attempt IS NOT NULL
  GROUP BY s.dataset_key, s.subject_dataset_key, d.import_attempt
  ORDER BY s.dataset_key, s.subject_dataset_key
```

#### 2020-04-27 dataset patches
```
CREATE TABLE dataset_patch AS SELECT * FROM dataset LIMIT 0;
ALTER TABLE dataset_patch
  DROP COLUMN source_key,
  DROP COLUMN gbif_key,
  DROP COLUMN gbif_publisher_key,
  DROP COLUMN data_format,
  DROP COLUMN origin,
  DROP COLUMN import_frequency,
  DROP COLUMN last_data_import_attempt,
  DROP COLUMN deleted,
  DROP COLUMN locked,
  DROP COLUMN private,
  DROP COLUMN data_access,
  DROP COLUMN notes,
  DROP COLUMN settings,
  DROP COLUMN editors,
  DROP COLUMN doc,
  ADD COLUMN dataset_key INTEGER NOT NULL REFERENCES dataset;
ALTER TABLE dataset_patch ADD PRIMARY KEY (key, dataset_key);
```

#### 2020-04-17 remove cascading delete from taxon.parent_id
It is required to run the `execSql --sql` command using the following sql template
in order to update all existing name_usage partitions: 
```
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT name_usage_{KEY}_parent_id_fk, 
ADD CONSTRAINT name_usage_{KEY}_parent_id_fk FOREIGN KEY (parent_id) REFERENCES name_usage_{KEY}(id) DEFERRABLE INITIALLY DEFERRED;
```

#### 2020-04-17 move editors to dataset, not user 
```
ALTER TABLE dataset ADD COLUMN editors INT[];
UPDATE dataset SET editors = (SELECT array_agg(u.key) FROM "user" u WHERE u.datasets @> array[key] OR created_by=u.key); 
ALTER TABLE "user" DROP COLUMN datasets;
```

#### 2020-04-15 dataset scope for decision, estimate & sector 
```
ALTER TABLE decision RENAME COLUMN key TO id;
ALTER TABLE estimate RENAME COLUMN key TO id;
ALTER TABLE sector RENAME COLUMN key TO id;
```

#### 2020-04-14 remove nomcode and user role
```
UPDATE dataset SET settings = coalesce(settings, '{}'::jsonb) || jsonb_build_object('NOMENCLATURAL_CODE', code) WHERE code IS NOT NULL;
ALTER TABLE dataset DROP COLUMN code;
UPDATE dataset_archive SET settings = coalesce(settings, '{}'::jsonb) || jsonb_build_object('NOMENCLATURAL_CODE', code) WHERE code IS NOT NULL;
ALTER TABLE dataset_archive DROP COLUMN code;

ALTER TYPE COLUSER_ROLE RENAME to USER_ROLE;
ALTER TABLE coluser RENAME to "user";
ALTER sequence coluser_key_seq RENAME TO user_key_seq;
UPDATE "user" SET roles = array_remove(roles, 'USER');  
ALTER TABLE "user" ALTER COLUMN roles TYPE text[];
DROP TYPE USER_ROLE;
CREATE TYPE USER_ROLE AS ENUM (
  'EDITOR',
  'ADMIN'
);
ALTER TABLE "user" ALTER COLUMN roles TYPE USER_ROLE[] USING roles::user_role[];
```

#### 2020-04-09 user datasets
```
ALTER TABLE coluser ADD COLUMN datasets INT[];
ALTER TABLE dataset ADD COLUMN private BOOLEAN DEFAULT FALSE;
```

#### 2020-04-04 text tree
```
ALTER TYPE DATAFORMAT RENAME VALUE 'TCS' to 'TEXT_TREE';
```

#### 2020-03-31 original_subject_id
```
ALTER TABLE sector ADD COLUMN original_subject_id TEXT;
UPDATE sector SET original_subject_id = subject_id;
ALTER TABLE decision ADD COLUMN original_subject_id TEXT;
UPDATE decision SET original_subject_id = subject_id;
```

#### 2020-03-27 sector virtual minRank
```
ALTER TABLE sector ADD COLUMN placeholder_rank RANK;
```

#### 2020-03-20 dataset origin
```
ALTER TABLE dataset ADD COLUMN source_key INTEGER REFERENCES dataset;
ALTER TABLE dataset_archive ADD COLUMN source_key INTEGER REFERENCES dataset;

ALTER TABLE dataset ALTER COLUMN origin TYPE text;
ALTER TABLE dataset_archive ALTER COLUMN origin TYPE text;
ALTER TABLE dataset_import ALTER COLUMN origin TYPE text;
DROP TYPE DATASETORIGIN;
CREATE TYPE DATASETORIGIN AS ENUM (
  'EXTERNAL',
  'MANAGED',
  'RELEASED'
);
UPDATE dataset SET origin='EXTERNAL' WHERE origin='UPLOADED' AND data_access IS NOT NULL;
UPDATE dataset SET origin='MANAGED' WHERE origin='UPLOADED';
UPDATE dataset SET origin='RELEASED' WHERE origin='MANAGED' AND locked;
UPDATE dataset SET source_key=3 WHERE origin='RELEASED';
UPDATE dataset_archive SET origin='EXTERNAL' WHERE origin='UPLOADED' AND data_access IS NOT NULL;
UPDATE dataset_archive SET origin='MANAGED' WHERE origin='UPLOADED';
UPDATE dataset_archive SET origin='RELEASED' WHERE origin='MANAGED' AND locked;
UPDATE dataset_archive SET source_key=3 WHERE origin='RELEASED';
UPDATE dataset_import SET origin='EXTERNAL' WHERE origin='UPLOADED' AND download_uri IS NOT NULL;
UPDATE dataset_import SET origin='MANAGED' WHERE origin='UPLOADED';
ALTER TABLE dataset ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
ALTER TABLE dataset_archive ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
ALTER TABLE dataset_import ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
```

#### 2020-03-12 parser_config & sectors
```
CREATE TABLE parser_config (LIKE name INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE parser_config DROP COLUMN dataset_key;
ALTER TABLE parser_config DROP COLUMN sector_key;
ALTER TABLE parser_config DROP COLUMN verbatim_key;
ALTER TABLE parser_config DROP COLUMN name_index_match_type;
ALTER TABLE parser_config DROP COLUMN nom_status;
ALTER TABLE parser_config DROP COLUMN origin;
ALTER TABLE parser_config DROP COLUMN modified_by;
ALTER TABLE parser_config DROP COLUMN modified;
ALTER TABLE parser_config DROP COLUMN homotypic_name_id;
ALTER TABLE parser_config DROP COLUMN name_index_id;
ALTER TABLE parser_config DROP COLUMN published_in_id;
ALTER TABLE parser_config DROP COLUMN published_in_page;
ALTER TABLE parser_config DROP COLUMN link;
ALTER TABLE parser_config DROP COLUMN scientific_name;
ALTER TABLE parser_config DROP COLUMN scientific_name_normalized;
ALTER TABLE parser_config DROP COLUMN authorship;
ALTER TABLE parser_config DROP COLUMN authorship_normalized;
ALTER TABLE parser_config RENAME COLUMN remarks TO nomenclatural_note;
ALTER TABLE parser_config ADD COLUMN taxonomic_note TEXT;
ALTER TABLE parser_config ADD PRIMARY KEY (id);

ALTER TABLE sector RENAME COLUMN last_data_import_attempt TO last_sync_attempt;
WITH finished AS (
    SELECT sector_key, max(attempt) AS maxa FROM sector_import WHERE state='FINISHED' GROUP BY sector_key
)
UPDATE sector SET last_sync_attempt=f.maxa FROM finished f WHERE key=f.sector_key;
DROP index sector_target_id_idx;
CREATE index ON sector (dataset_key, target_id);
```

#### 2020-03-09 dataest_import 
```
ALTER TABLE dataset_import add column format DATAFORMAT;
ALTER TABLE dataset_import add column origin DATASETORIGIN;
UPDATE dataset_import i SET origin=d.origin, format=d.data_format FROM dataset d WHERE d.key=i.dataset_key;
ALTER TABLE dataset_import ALTER column origin SET NOT NULL;
```

#### 2020-02-25 import state changes 
```
ALTER TYPE IMPORTSTATE RENAME VALUE 'DECISION_MATCHING' to 'MATCHING';
ALTER TYPE IMPORTSTATE ADD VALUE 'EXPORTING' after 'BUILDING_METRICS';
ALTER TYPE IMPORTSTATE ADD VALUE 'RELEASED' after 'FINISHED';

ALTER TABLE dataset_import add column created_by INTEGER NOT NULL DEFAULT 10;
ALTER TABLE dataset_import alter column created_by DROP DEFAULT;

ALTER TABLE sector_import add column created_by INTEGER NOT NULL DEFAULT 10;
ALTER TABLE sector_import alter column created_by DROP DEFAULT;
```

#### 2020-02-24 ranks & entities for sectors 
```
ALTER TABLE sector add column ranks RANK[] DEFAULT '{}';
ALTER TABLE sector add column entities ENTITYTYPE[];
```

#### 2020-02-07 add matching state
```
ALTER TYPE IMPORTSTATE ADD VALUE 'DECISION_MATCHING' after 'INDEXING';
```

#### add type material
```
ALTER TYPE ENTITYTYPE add value 'TYPE_MATERIAL' after 'NAME_USAGE';
ALTER TYPE NAMEFIELD RENAME value 'WEBPAGE' to 'LINK';
ALTER TYPE NAMEFIELD add value 'CODE' after 'PUBLISHED_IN_PAGE';

ALTER TYPE ISSUE add value 'COUNTRY_INVALID' after 'TYPE_STATUS_INVALID'; 
ALTER TYPE ISSUE add value 'ALTITUDE_INVALID' after 'TYPE_STATUS_INVALID'; 
ALTER TYPE ISSUE add value 'LAT_LON_INVALID' after 'TYPE_STATUS_INVALID';

ALTER TABLE dataset_import add column type_material_count INTEGER;
ALTER TABLE dataset_import add column type_material_by_status_count HSTORE;

ALTER TABLE name drop column type_status;
ALTER TABLE name drop column type_material;
ALTER TABLE name drop column type_reference_id;
ALTER TABLE name rename column webpage to link;

ALTER TABLE name_rel rename column note to remarks;
ALTER TABLE name_usage rename column webpage to link;

CREATE TABLE type_material (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  name_id TEXT NOT NULL,
  citation TEXT,
  status TYPESTATUS,
  locality TEXT,
  country TEXT,
  latitude NUMERIC(8, 6) CHECK (latitude >= -90 AND latitude <= 90),
  longitude NUMERIC(9, 6) CHECK (longitude >= -180 AND longitude <= 180),
  altitude INTEGER,
  host TEXT,
  date TEXT,
  collector TEXT,
  reference_id TEXT,
  link TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);
```

Afterwards it is required to run the `AddTableCmd`` -t type_material` using the cli tools
in order to create partitions for all existing datasets. 
