



## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

### 2023-10-20 add taxon property table
```sql
CREATE TABLE taxon_property (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  property TEXT NOT NULL,
  value TEXT NOT NULL,
  reference_id TEXT,
  page TEXT,
  ordinal INTEGER,
  remarks TEXT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON taxon_property (dataset_key, taxon_id);
CREATE INDEX ON taxon_property (dataset_key, sector_key);
CREATE INDEX ON taxon_property (dataset_key, verbatim_key);
CREATE INDEX ON taxon_property (dataset_key, reference_id);
CREATE INDEX ON taxon_property (dataset_key, property);

ALTER TYPE ENTITYTYPE ADD VALUE 'TAXON_PROPERTY' AFTER 'TAXON_CONCEPT_RELATION';
```

In the existing dbs we then need to create the actual partition tables.
We can use the partition command for that, i.e. to create 24 partitions::
> ./partition.sh taxon_property 24

Finally run the `updSequence --all true` command to create project sequences for the new table.
Make sure you use the latest jar already with support for the TaxonProperty table.
> ./sequence-update.sh --all true

### 2023-10-20 add ordinal ordering, gender, remarks and more interpreting issues
```sql
ALTER TABLE name ADD COLUMN gender GENDER;
ALTER TABLE name ADD COLUMN gender_agreement BOOLEAN;
ALTER TABLE name_usage ADD COLUMN ordinal INTEGER;

ALTER TABLE name_usage_archive ADD COLUMN n_gender GENDER;
ALTER TABLE name_usage_archive ADD COLUMN n_gender_agreement BOOLEAN;
ALTER TABLE name_usage_archive ADD COLUMN ordinal INTEGER;

ALTER TABLE media ADD COLUMN remarks TEXT;
ALTER TABLE distribution ADD COLUMN remarks TEXT;
ALTER TABLE vernacular_name ADD COLUMN remarks TEXT;
ALTER TABLE estimate RENAME COLUMN note TO remarks;

ALTER TYPE ISSUE ADD VALUE 'NOTHO_INVALID';
ALTER TYPE ISSUE ADD VALUE 'ORIGINAL_SPELLING_INVALID';
ALTER TYPE ISSUE ADD VALUE 'UNINOMIAL_FIELD_MISPLACED';
ALTER TYPE ISSUE ADD VALUE 'INFRAGENERIC_FIELD_MISPLACED';
ALTER TYPE ISSUE ADD VALUE 'ORDINAL_INVALID';
ALTER TYPE ISSUE ADD VALUE 'GENDER_INVALID';
ALTER TYPE ISSUE ADD VALUE 'GENDER_AGREEMENT_NOT_APPLICABLE';
ALTER TYPE ISSUE ADD VALUE 'NOTHO_NOT_APPLICABLE';
ALTER TYPE ISSUE ADD VALUE 'VERNACULAR_PREFERRED';
```

### 2023-10-18 add original spelling flag
```sql
ALTER TABLE name ADD COLUMN original_spelling BOOLEAN;
ALTER TABLE name_usage_archive ADD COLUMN n_original_spelling BOOLEAN;
```

### 2023-10-13 add api analytics table
```sql
CREATE TABLE api_analytics(
  key bigserial NOT NULL PRIMARY KEY,
  from_datetime TIMESTAMP NOT NULL,
  to_datetime TIMESTAMP NOT NULL,
  request_count INTEGER NOT NULL,
  country_agg HSTORE,
  response_code_agg HSTORE,
  agent_agg HSTORE,
  request_pattern_agg HSTORE,
  dataset_agg HSTORE,
  other_metrics HSTORE
);

CREATE UNIQUE INDEX unique_date_range ON api_analytics(from_datetime, to_datetime);
CREATE INDEX api_analytics_from_idx ON api_analytics(from_datetime);
CREATE INDEX api_analytics_to_idx ON api_analytics(to_datetime);
```

### 2023-09-26 provide classification functions a maximum depth to search for to avoid loops 
```sql
-- return all parent names as an array
CREATE OR REPLACE FUNCTION classification(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false, v_max_depth INTEGER default 100) RETURNS TEXT[] AS $$
WITH RECURSIVE x AS (
  SELECT t.id, n.scientific_name, t.parent_id, 1 distance FROM name_usage t
  JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
  WHERE t.dataset_key=v_dataset_key AND t.id = v_id
UNION ALL
  SELECT t.id, n.scientific_name, t.parent_id, x.distance+1 FROM x, name_usage t
  JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
  WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance <= v_max_depth
) SELECT array_reverse(array_agg(scientific_name)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;


-- return all parent usage ids as an array
CREATE OR REPLACE FUNCTION classification_id(v_dataset_key INTEGER, v_id TEXT, v_max_depth INTEGER default 100) RETURNS TEXT[] AS $$
WITH RECURSIVE x AS (
SELECT t.id, t.parent_id, 1 distance FROM name_usage t
WHERE t.dataset_key=v_dataset_key AND t.id = v_id
UNION ALL
SELECT t.id, t.parent_id, x.distance+1 FROM x, name_usage t
WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance <= v_max_depth
) SELECT array_reverse(array_agg(id)) FROM x WHERE id != v_id;
$$ LANGUAGE SQL;


-- return all parent name usages as a simple_name array
CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false, v_max_depth INTEGER default 100) RETURNS simple_name[] AS $$
WITH RECURSIVE x AS (
SELECT t.id, t.parent_id, 1 distance, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t
JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
WHERE t.dataset_key=v_dataset_key AND t.id = v_id
UNION ALL
SELECT t.id, t.parent_id, x.distance+1, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t
JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance <= v_max_depth
) SELECT array_reverse(array_agg(sn)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;
```


### 2023-09-26 prevent parents to be the usage itself
```sql
ALTER TABLE name_usage ADD CONSTRAINT check_parent_not_self CHECK (parent_id <> id);
```

For manual cycle detection use this per dataset:
```sql
CREATE TABLE _loops AS  
WITH RECURSIVE parents(id, depth) AS (
    SELECT u.id, 1
    FROM name_usage u
    WHERE u.dataset_key=268159
  UNION ALL
    SELECT c.id, p.depth + 1
    FROM name_usage c, parents p
    WHERE p.id = c.parent_id AND c.dataset_key=268159
) CYCLE id SET is_cycle USING path 
SELECT id FROM parents WHERE is_cycle;


INSERT into name (id,dataset_key,rank,origin,type,scientific_name, scientific_name_normalized, created_by, modified_by) 
 VALUES ('loop',268159,'UNRANKED','USER','INFORMAL', 'Loop holder', 'loopholder', 102, 102);

INSERT into name_usage (id,name_id,dataset_key,origin,status,created_by, modified_by) 
 VALUES ('loop','loop',268159,'USER','ACCEPTED', 102,102);

UPDATE name_usage u set parent_id='loop' FROM _loops l WHERE u.dataset_key=268159 and u.id=l.id; 


INSERT into name (id,dataset_key,rank,origin,type,scientific_name, scientific_name_normalized, created_by, modified_by) 
 VALUES ('missingParent',268557,'UNRANKED','USER','INFORMAL', 'missing parent', 'missing parent', 102, 102);

INSERT into name_usage (id,name_id,dataset_key,origin,status,created_by, modified_by) 
 VALUES ('missingParent','missingParent',268557,'USER','ACCEPTED', 102,102);

UPDATE name_usage set parent_id='missingParent' WHERE dataset_key=268557 and id IN (); 

```

### 2023-09-21 changed user roles
```sql
UPDATE "user" SET roles = '{}';
-- we only keep markus as the admin
UPDATE "user" SET roles = array['ADMIN'::user_role] WHERE key = 101;
-- we make thomas, yuri, geoff, olaf, donald, camila, diana & tim  global editors
UPDATE "user" SET roles = array['EDITOR'::user_role] WHERE key in (100,102,103,155,117,728,643,115);
-- we make walter,daveN & leenv global reviwers
UPDATE "user" SET roles = array['REVIEWER'::user_role] WHERE key in (737,318,130);
```

### 2023-09-15 dataset import indices to support more filters
```sql
CREATE INDEX ON dataset_import (format);
CREATE INDEX ON dataset_import (lower(job));
CREATE INDEX ON dataset_import (created_by);
```

### 2023-09-15 add user publisher rights
```sql
ALTER TABLE "user" ADD COLUMN publisher UUID[];
```

### 2023-09-15 remove unused enums
```sql
DROP TYPE KINGDOM; 
DROP TYPE MATCHINGMODE; 
DROP TYPE NAMECATEGORY; 
DROP TYPE NAMEFIELD; 

CREATE TYPE GENDER AS ENUM (
  'MASCULINE',
  'FEMININE',
  'NEUTER'
);
```

### 2023-09-06 mark name constraints deferred to allow deletion & existance of orphans
```sql
ALTER TABLE name_match DROP CONSTRAINT name_match_dataset_key_name_id_fkey; 
ALTER TABLE name_match ADD CONSTRAINT name_match_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_name_id_fkey;
ALTER TABLE name_rel ADD CONSTRAINT name_rel_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_related_name_id_fkey; 
ALTER TABLE name_rel ADD CONSTRAINT name_rel_dataset_key_related_name_id_fkey FOREIGN KEY (dataset_key, related_name_id) REFERENCES name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE type_material DROP CONSTRAINT type_material_dataset_key_name_id_fkey; 
ALTER TABLE type_material ADD CONSTRAINT type_material_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;
```

### 2023-09-01 email domain extract function

```sql
create or replace function get_domainname(_value text)
returns text
as $$
begin
_value := reverse(_value);

return nullif(reverse(substring(_value, 0, strpos(_value, '@'))), '');
end;
$$ language plpgsql
immutable returns null on null input
;
```
### 2023-08-21 migrate partitioning
This is a serious change, removing the list partitioning and replacing it with just the hash one and 24 fixed partitions.
First dump the current db:
> nohup pg_dump -Fc -Z 7 -U postgres -f clb-prod.dump clb &

Then restore it into the empty public schema of a new database;
> CREATE DATABASE clb OWNER col;
nohup pg_restore -j 12 -U postgres -d clb ~/clb-prod.dump &

Then alter its schema to be "old", but reuse its enum types in the public schema (for simpler copying later on witout casts):
> ALTER SCHEMA public RENAME TO old;
 CREATE SCHEMA public;
 ALTER EXTENSION btree_gin SET SCHEMA public;
 ALTER EXTENSION hstore SET SCHEMA public;
 ALTER EXTENSION pg_trgm SET SCHEMA public;
 ALTER EXTENSION unaccent SET SCHEMA public;

>ALTER TYPE old.agent SET SCHEMA public;
ALTER TYPE old.continent SET SCHEMA public;
ALTER TYPE old.cslname SET SCHEMA public;
ALTER TYPE old.dataformat SET SCHEMA public;
ALTER TYPE old.datasetorigin SET SCHEMA public;
ALTER TYPE old.datasettype SET SCHEMA public;
ALTER TYPE old.distributionstatus SET SCHEMA public;
ALTER TYPE old.editorialdecision_mode SET SCHEMA public;
ALTER TYPE old.entitytype SET SCHEMA public;
ALTER TYPE old.environment SET SCHEMA public;
ALTER TYPE old.estimatetype SET SCHEMA public;
ALTER TYPE old.gazetteer SET SCHEMA public;
ALTER TYPE old.idreporttype SET SCHEMA public;
ALTER TYPE old.importstate SET SCHEMA public;
ALTER TYPE old.infogroup SET SCHEMA public;
ALTER TYPE old.issue SET SCHEMA public;
ALTER TYPE old.jobstatus SET SCHEMA public;
ALTER TYPE old.kingdom SET SCHEMA public;
ALTER TYPE old.license SET SCHEMA public;
ALTER TYPE old.matchingmode SET SCHEMA public;
ALTER TYPE old.matchtype SET SCHEMA public;
ALTER TYPE old.mediatype SET SCHEMA public;
ALTER TYPE old.namecategory SET SCHEMA public;
ALTER TYPE old.namefield SET SCHEMA public;
ALTER TYPE old.namepart SET SCHEMA public;
ALTER TYPE old.nametype SET SCHEMA public;
ALTER TYPE old.nomcode SET SCHEMA public;
ALTER TYPE old.nomreltype SET SCHEMA public;
ALTER TYPE old.nomstatus SET SCHEMA public;
ALTER TYPE old.origin SET SCHEMA public;
ALTER TYPE old.rank SET SCHEMA public;
ALTER TYPE old.sector_mode SET SCHEMA public;
ALTER TYPE old.sex SET SCHEMA public;
ALTER TYPE old.simple_name SET SCHEMA public;
ALTER TYPE old.speciesinteractiontype SET SCHEMA public;
ALTER TYPE old.taxgroup SET SCHEMA public;
ALTER TYPE old.taxonconceptreltype SET SCHEMA public;
ALTER TYPE old.taxonomicstatus SET SCHEMA public;
ALTER TYPE old.treatmentformat SET SCHEMA public;
ALTER TYPE old.typestatus SET SCHEMA public;
ALTER TYPE old.user_role SET SCHEMA public;

Then run the sql scripts in the given order found in the migration subfolder.
This takes a long time to complete.

At the end, drop the old schema with:
>DROP SCHEMA old;

Finally run the new `updSequence --all true` command to create/update required project sequences.


### 2023-08-15 add new issue
```
ALTER TYPE ISSUE ADD VALUE 'MULTILINE_RECORD';
```

### 2023-07-20 add new issue
```
ALTER TYPE ISSUE ADD VALUE 'PUBLISHED_YEAR_CONFLICT';
```

### 2023-06-28 add new match type
```
ALTER TYPE MATCHTYPE ADD VALUE 'UNSUPPORTED';
```

### 2023-06-08 improve import scheduler sql for large import table 
```
-- used by import scheduler:
CREATE INDEX ON dataset (key)
  WHERE deleted IS NULL
  AND NOT private  
  AND origin = 'EXTERNAL'
  AND settings ->> 'data access' IS NOT NULL
  AND coalesce((settings ->> 'import frequency')::int, 0) >= 0;
  
CREATE INDEX ON dataset_import (dataset_key, attempt) WHERE finished IS NOT NULL;  
```

### 2023-06-02 unique doi only for non deleted datasets
```
ALTER TABLE dataset ADD CONSTRAINT dataset_doi_unique EXCLUDE (doi WITH =) WHERE (deleted IS null);
ALTER TABLE dataset DROP CONSTRAINT dataset_doi_key;
```

### 2023-05-23 new dataset type 
```
ALTER TYPE DATASETTYPE ADD VALUE 'IDENTIFICATION' BEFORE 'OTHER';
```

### 2023-03-24 replace is_synonym column with function and index
```
CREATE OR REPLACE FUNCTION is_synonym(status TAXONOMICSTATUS) RETURNS BOOLEAN AS $$
  SELECT status IN ('SYNONYM','AMBIGUOUS_SYNONYM','MISAPPLIED')
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;

CREATE INDEX ON name_usage (dataset_key, is_synonym(status));
ALTER TABLE name_usage DROP COLUMN is_synonym;
ALTER TABLE name_usage_archive DROP COLUMN is_synonym;
```

### 2023-03-16 new merge sector issues
```
ALTER TYPE ISSUE RENAME VALUE 'HOMOTYPIC_MULTI_ACCEPTED' TO 'HOMOTYPIC_CONSOLIDATION';
ALTER TYPE ISSUE RENAME VALUE 'CONFLICTING_BASIONYM_COMBINATION' TO 'HOMOTYPIC_CONSOLIDATION_UNRESOLVED';

ALTER TYPE ISSUE ADD VALUE 'SYNC_OUTSIDE_TARGET';
ALTER TYPE ISSUE ADD VALUE 'MULTIPLE_BASIONYMS';
```

### 2023-03-15 better postgres full text search configs
```
CREATE TEXT SEARCH CONFIGURATION public.dataset ( COPY = pg_catalog.english );
CREATE TEXT SEARCH CONFIGURATION public.reference ( COPY = pg_catalog.english );
CREATE TEXT SEARCH CONFIGURATION public.verbatim ( COPY = pg_catalog.simple );
CREATE TEXT SEARCH CONFIGURATION public.vernacular ( COPY = pg_catalog.simple );

ALTER TABLE dataset ADD COLUMN doc2 tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('dataset', f_unaccent(coalesce(alias,''))), 'A') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(key::text, ''))), 'A') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(doi, ''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(title,''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(array_str(keyword),''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(geographic_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(taxonomic_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(temporal_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(issn, ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(gbif_key::text,''))), 'C')  ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(identifier::text, ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(contact), ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(creator), ''))), 'D') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(publisher), ''))), 'D') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(editor), ''))), 'D') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(contributor), ''))), 'D') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(description,''))), 'D')
  ) STORED;
ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset RENAME COLUMN doc2 TO doc;
  
ALTER TABLE verbatim ADD COLUMN doc2 tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('verbatim', coalesce(terms,'{}'::jsonb), '["string", "numeric"]')) STORED;
ALTER TABLE verbatim DROP COLUMN doc;
ALTER TABLE verbatim RENAME COLUMN doc2 TO doc;

ALTER TABLE reference ADD COLUMN doc2 tsvector GENERATED ALWAYS AS (
    jsonb_to_tsvector('reference', coalesce(csl,'{}'::jsonb), '["string", "numeric"]') ||
          to_tsvector('reference', coalesce(citation,'')) ||
          to_tsvector('reference', coalesce(year::text,''))
  ) STORED;
ALTER TABLE reference DROP COLUMN doc;
ALTER TABLE reference RENAME COLUMN doc2 TO doc;

ALTER TABLE vernacular_name ADD COLUMN doc2 tsvector GENERATED ALWAYS AS (to_tsvector('vernacular', coalesce(name, '') || ' ' || coalesce(latin, ''))) STORED;
ALTER TABLE vernacular_name DROP COLUMN doc;
ALTER TABLE vernacular_name RENAME COLUMN doc2 TO doc;
```

### 2023-03-06 new release bot user
```
ALTER TYPE ISSUE ADD VALUE 'HOMOTYPIC_MULTI_ACCEPTED';

INSERT INTO "user" (key, username, firstname, lastname, roles, created) VALUES
 (13, 'releaser', 'Release', 'Bot', '{}', now()),
 (14, 'homotypic_grouper', 'Homotypic', 'Grouper', '{}', now());
```

### 2023-03-02 more sector configs
```
ALTER TABLE sector ADD COLUMN name_types NAMETYPE[] DEFAULT NULL;
ALTER TABLE sector ADD COLUMN name_status_exclusion NOMSTATUS[] DEFAULT NULL;
```

### 2023-03-01 decision indices
```
CREATE INDEX ON decision (subject_dataset_key);
CREATE INDEX ON decision (subject_dataset_key, subject_id);
```

### 2023-02-20 PROLES FIX
```
ALTER TYPE RANK RENAME VALUE 'PROLE' TO 'PROLES';
```

### 2023-01-24 verbatim index
change verbatim terms index to support has key queries which we use in mappers.
The jsonb_ops index is larger and slower for value queries, but it is the only one that supports has key ? queries
```
CREATE INDEX ON verbatim USING GIN (dataset_key, terms jsonb_ops);
DROP INDEX verbatim_dataset_key_terms_idx;
```

### 2023-01-24 new ranks
```
ALTER TYPE RANK ADD VALUE 'SUPERDOMAIN' BEFORE 'DOMAIN';
ALTER TYPE RANK ADD VALUE 'FALANX' BEFORE 'MEGAFAMILY';
ALTER TYPE RANK ADD VALUE 'SUPERGENUS' BEFORE 'GENUS';
ALTER TYPE RANK ADD VALUE 'KLEPTON' BEFORE 'SUBSPECIES';
ALTER TYPE RANK ADD VALUE 'SUPERVARIETY' BEFORE 'VARIETY';
ALTER TYPE RANK ADD VALUE 'SUPERFORM' BEFORE 'FORM';
ALTER TYPE RANK ADD VALUE 'LUSUS' BEFORE 'CULTIVAR';
ALTER TYPE RANK ADD VALUE 'MUTATIO' BEFORE 'STRAIN';

ALTER TYPE RANK RENAME VALUE 'PROLES' TO 'PROLE';
```

### 2023-01-06 Add array concatenation aggregate function
```
CREATE AGGREGATE array_cat_agg(anycompatiblearray) (
  SFUNC=array_cat,
  STYPE=anycompatiblearray
);

CREATE INDEX on verbatim(dataset_key, id) WHERE array_length(issues, 1) > 0;
```

### 2023-01-04 Improved classification functions using SQL only
```
-- return all parent names as an array
CREATE OR REPLACE FUNCTION classification(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS TEXT[] AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, n.scientific_name, t.parent_id FROM name_usage t 
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id 
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL 
  SELECT t.id, n.scientific_name, t.parent_id FROM x, name_usage t 
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id 
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id
  ) SELECT array_reverse(array_agg(scientific_name)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;


-- return all parent name usages as a simple_name array
CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS simple_name[] AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t 
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id 
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL 
  SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t 
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id 
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id
  ) SELECT array_reverse(array_agg(sn)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;
```

### 2022-12-14 coldwc term changes
```
UPDATE verbatim SET type = 'col:NameRelation' WHERE type = 'coldwc:NameRelation';
  
UPDATE verbatim SET terms = terms - 'coldwc:relatedNameUsageID' || jsonb_build_object('col:relatedNameID', terms->'coldwc:relatedNameUsageID') WHERE terms ? 'coldwc:relatedNameUsageID';
UPDATE verbatim SET terms = terms - 'coldwc:relationType' || jsonb_build_object('dc:type', terms->'coldwc:relationType') WHERE terms ? 'coldwc:relationType'; 
UPDATE verbatim SET terms = terms - 'coldwc:relationPublishedIn' || jsonb_build_object('dc:bibliographicCitation', terms->'coldwc:relationPublishedIn') WHERE terms ? 'coldwc:relationPublishedIn'; 
UPDATE verbatim SET terms = terms - 'coldwc:relationPublishedInID' || jsonb_build_object('col:referenceID', terms->'coldwc:relationPublishedInID') WHERE terms ? 'coldwc:relationPublishedInID'; 
UPDATE verbatim SET terms = terms - 'coldwc:relationRemarks' || jsonb_build_object('col:remarks', terms->'coldwc:relationRemarks') WHERE terms ? 'coldwc:relationRemarks';
 
UPDATE verbatim SET terms = terms - 'coldwc:superkingdom' || jsonb_build_object('col:superkingdom', terms->'coldwc:superkingdom') WHERE terms ? 'coldwc:superkingdom'; 
UPDATE verbatim SET terms = terms - 'coldwc:subkingdom' || jsonb_build_object('col:subkingdom', terms->'coldwc:subkingdom') WHERE terms ? 'coldwc:subkingdom'; 
UPDATE verbatim SET terms = terms - 'coldwc:superphylum' || jsonb_build_object('col:superphylum', terms->'coldwc:superphylum') WHERE terms ? 'coldwc:superphylum'; 
UPDATE verbatim SET terms = terms - 'coldwc:subphylum' || jsonb_build_object('col:subphylum', terms->'coldwc:subphylum') WHERE terms ? 'coldwc:subphylum'; 
UPDATE verbatim SET terms = terms - 'coldwc:superclass' || jsonb_build_object('col:superclass', terms->'coldwc:superclass') WHERE terms ? 'coldwc:superclass'; 
UPDATE verbatim SET terms = terms - 'coldwc:subclass' || jsonb_build_object('col:subclass', terms->'coldwc:subclass') WHERE terms ? 'coldwc:subclass'; 
UPDATE verbatim SET terms = terms - 'coldwc:superorder' || jsonb_build_object('col:superorder', terms->'coldwc:superorder') WHERE terms ? 'coldwc:superorder'; 
UPDATE verbatim SET terms = terms - 'coldwc:suborder' || jsonb_build_object('col:suborder', terms->'coldwc:suborder') WHERE terms ? 'coldwc:suborder'; 
UPDATE verbatim SET terms = terms - 'coldwc:superfamily' || jsonb_build_object('col:superfamily', terms->'coldwc:superfamily') WHERE terms ? 'coldwc:superfamily'; 
UPDATE verbatim SET terms = terms - 'coldwc:tribe' || jsonb_build_object('col:tribe', terms->'coldwc:tribe') WHERE terms ? 'coldwc:tribe'; 
```

### 2022-10-20 dataset keywords
```
ALTER TABLE dataset ADD COLUMN keyword TEXT[];
ALTER TABLE dataset_archive ADD COLUMN keyword TEXT[];
ALTER TABLE dataset_source ADD COLUMN keyword TEXT[];
ALTER TABLE dataset_patch ADD COLUMN keyword TEXT[];

ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', f_unaccent(coalesce(alias,''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(doi, ''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(key::text, ''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(title,''))), 'B') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(array_str(keyword),''))), 'B') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(issn, ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(gbif_key::text,''))), 'C')  ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(identifier::text, ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(creator), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(publisher), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(contact), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(editor), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(contributor), ''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(geographic_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(taxonomic_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(temporal_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(description,''))), 'D')
  ) STORED;
```

### 2022-09-17 verbatim_source_secondary
Add verbatim_source_secondary to all projects and releases!

```
CREATE TYPE INFOGROUP AS ENUM (
  'NAME',
  'AUTHORSHIP',
  'PUBLISHED_IN',
  'BASIONYM',
  'STATUS',
  'PARENT',
  'EXTINCT',
  'DOI',
  'LINK'
);

CREATE TABLE verbatim_source_secondary (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  type INFOGROUP NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,
  FOREIGN KEY (dataset_key, id) REFERENCES name_usage
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim_source_secondary (dataset_key, id);
CREATE INDEX ON verbatim_source_secondary (dataset_key, source_dataset_key);
```

Now you can add partition tables for all projects and releases by executing the following template with the CLI:
```
./exec-sql.sh sql/create_vss.sql --origin PROJECT
./exec-sql.sh sql/create_vss.sql --origin RELEASE
./exec-sql.sh sql/create_vss.sql --origin XRELEASE

CREATE TABLE verbatim_source_secondary_{KEY} (LIKE verbatim_source_secondary INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
ALTER TABLE verbatim_source_secondary ATTACH PARTITION verbatim_source_secondary_{KEY} FOR VALUES IN ( {KEY} );
```

### 2022-09-17 extended catalogue
```
ALTER TABLE sector ADD COLUMN priority INTEGER;

ALTER TABLE dataset ALTER COLUMN origin TYPE text;
ALTER TABLE dataset_archive ALTER COLUMN origin TYPE text;
ALTER TABLE dataset_source ALTER COLUMN origin TYPE text;
ALTER TABLE dataset_import ALTER COLUMN origin TYPE text;
UPDATE dataset SET origin = 'PROJECT' WHERE origin = 'MANAGED';
UPDATE dataset SET origin = 'RELEASE' WHERE origin = 'RELEASED';
UPDATE dataset_archive SET origin = 'PROJECT' WHERE origin = 'MANAGED';
UPDATE dataset_archive SET origin = 'RELEASE' WHERE origin = 'RELEASED';
UPDATE dataset_source SET origin = 'PROJECT' WHERE origin = 'MANAGED';
UPDATE dataset_source SET origin = 'RELEASE' WHERE origin = 'RELEASED';
UPDATE dataset_import SET origin = 'PROJECT' WHERE origin = 'MANAGED';
UPDATE dataset_import SET origin = 'RELEASE' WHERE origin = 'RELEASED';
DROP TYPE DATASETORIGIN;
CREATE TYPE DATASETORIGIN AS ENUM (
  'EXTERNAL',
  'PROJECT',
  'RELEASE',
  'XRELEASE'
);
ALTER TABLE dataset ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
ALTER TABLE dataset_archive ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
ALTER TABLE dataset_source ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
ALTER TABLE dataset_import ALTER COLUMN origin TYPE DATASETORIGIN USING origin::DATASETORIGIN;
```

### 2022-09-16 alternative identifiers
```
ALTER TYPE ISSUE ADD VALUE 'IDENTIFIER_WITHOUT_SCOPE';

ALTER TABLE name ADD COLUMN identifier TEXT[];
ALTER TABLE name_usage ADD COLUMN identifier TEXT[];
ALTER TABLE name_usage_archive 
  ADD COLUMN n_identifier TEXT[],
  ADD COLUMN n_link TEXT,
  ADD COLUMN identifier TEXT[];
```

### 2022-09-07 drop verbatim key constraint for treatments
```
ALTER TABLE treatment ALTER verbatim_key DROP NOT NULL;
```

### 2022-08-25 fine tune dataset search ranking, keep plazi articles lower
```
ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', f_unaccent(coalesce(alias,''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(doi, ''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(key::text, ''))), 'A') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(title,''))), 'B') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(issn, ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(gbif_key::text,''))), 'C')  ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(identifier::text, ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(creator), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(publisher), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(contact), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(editor), ''))), 'C') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(agent_str(contributor), ''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(geographic_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(taxonomic_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(temporal_scope,''))), 'D') ||
      setweight(to_tsvector('simple2', f_unaccent(coalesce(description,''))), 'D')
  ) STORED;
```

### 2022-05-17 extend TypeMaterial
```
ALTER TYPE ISSUE ADD VALUE 'TYPE_MATERIAL_SEX_INVALID';

ALTER TABLE type_material ADD COLUMN sex SEX;
ALTER TABLE type_material ADD COLUMN institution_code TEXT;
ALTER TABLE type_material ADD COLUMN catalog_number TEXT;
ALTER TABLE type_material ADD COLUMN associated_sequences TEXT;
ALTER TABLE type_material ADD COLUMN coordinate POINT;
UPDATE type_material SET coordinate = POINT(longitude, latitude) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
ALTER TABLE type_material DROP CONSTRAINT type_material_latitude_check;
ALTER TABLE type_material DROP CONSTRAINT type_material_longitude_check;
ALTER TABLE type_material ALTER COLUMN altitude type TEXT;
ALTER TABLE type_material ALTER COLUMN latitude type TEXT;
ALTER TABLE type_material ALTER COLUMN longitude type TEXT;
```

### 2022-05-10 add DOI issues & publishedPageLink
```
ALTER TYPE ISSUE ADD VALUE 'DOI_NOT_FOUND';
ALTER TYPE ISSUE ADD VALUE 'DOI_UNRESOLVED';

ALTER TABLE name ADD COLUMN published_in_page_link TEXT;
ALTER TABLE name_usage_archive ADD COLUMN n_published_in_page_link TEXT;
```

### 2022-04-27 fix reference partition & data integrity
Somehow the reference and vernacular name default partition contains the latest project data since March 21st 
while there is an unattached reference_3 table with most of the old data.

Merging data and attaching to the main table:
```
--
-- REFERENCE
--

-- make sure we only have the project in the default partitions
SELECT DISTINCT dataset_key FROM reference WHERE dataset_key IN (3,2056,2082,2142,2149,2153,2161,2242,2274,2296,2303,2315,2328,2332,2344,2349,2351,2366,2368,2369,2370);

-- move old unused data out of the way, keep it just in case
ALTER TABLE reference_3 RENAME TO reference_3_old;

-- migrate project data out of shared partitions
CREATE TABLE reference_3 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
INSERT INTO reference_3 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference WHERE dataset_key=3;
INSERT INTO reference_3 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_3_old;
ALTER TABLE reference_3 ADD CONSTRAINT reference_3_dataset_key_check CHECK (dataset_key = 3);
ALTER TABLE reference ATTACH PARTITION reference_3 FOR VALUES IN ( 3 );
DELETE FROM reference WHERE dataset_key=3;

-- do similar for all releases
ALTER TABLE reference_2056 RENAME TO reference_2056_old;
ALTER TABLE reference_2082 RENAME TO reference_2082_old;
ALTER TABLE reference_2142 RENAME TO reference_2142_old;
ALTER TABLE reference_2149 RENAME TO reference_2149_old;
ALTER TABLE reference_2153 RENAME TO reference_2153_old;
ALTER TABLE reference_2161 RENAME TO reference_2161_old;
ALTER TABLE reference_2242 RENAME TO reference_2242_old;
ALTER TABLE reference_2274 RENAME TO reference_2274_old;
ALTER TABLE reference_2296 RENAME TO reference_2296_old;
ALTER TABLE reference_2303 RENAME TO reference_2303_old;
ALTER TABLE reference_2315 RENAME TO reference_2315_old;
ALTER TABLE reference_2328 RENAME TO reference_2328_old;
ALTER TABLE reference_2332 RENAME TO reference_2332_old;
ALTER TABLE reference_2344 RENAME TO reference_2344_old;
ALTER TABLE reference_2349 RENAME TO reference_2349_old;
ALTER TABLE reference_2351 RENAME TO reference_2351_old;
ALTER TABLE reference_2366 RENAME TO reference_2366_old;
ALTER TABLE reference_2368 RENAME TO reference_2368_old;
ALTER TABLE reference_2369 RENAME TO reference_2369_old;
ALTER TABLE reference_2370 RENAME TO reference_2370_old;

CREATE TABLE reference_2056 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2082 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2142 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2149 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2153 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2161 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2242 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2274 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2296 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2303 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2315 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2328 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2332 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2344 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2349 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2351 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2366 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2368 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2369 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE reference_2370 (LIKE reference INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);

INSERT INTO reference_2056 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2056_old;
INSERT INTO reference_2082 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2082_old;
INSERT INTO reference_2142 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2142_old;
INSERT INTO reference_2149 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2149_old;
INSERT INTO reference_2153 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2153_old;
INSERT INTO reference_2161 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2161_old;
INSERT INTO reference_2242 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2242_old;
INSERT INTO reference_2274 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2274_old;
INSERT INTO reference_2296 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2296_old;
INSERT INTO reference_2303 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2303_old;
INSERT INTO reference_2315 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2315_old;
INSERT INTO reference_2328 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2328_old;
INSERT INTO reference_2332 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2332_old;
INSERT INTO reference_2344 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2344_old;
INSERT INTO reference_2349 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2349_old;
INSERT INTO reference_2351 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2351_old;
INSERT INTO reference_2366 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2366_old;
INSERT INTO reference_2368 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2368_old;
INSERT INTO reference_2369 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2369_old;
INSERT INTO reference_2370 (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation) SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM reference_2370_old;

ALTER TABLE reference ATTACH PARTITION reference_2056 FOR VALUES IN (2056);
ALTER TABLE reference ATTACH PARTITION reference_2082 FOR VALUES IN (2082);
ALTER TABLE reference ATTACH PARTITION reference_2142 FOR VALUES IN (2142);
ALTER TABLE reference ATTACH PARTITION reference_2149 FOR VALUES IN (2149);
ALTER TABLE reference ATTACH PARTITION reference_2153 FOR VALUES IN (2153);
ALTER TABLE reference ATTACH PARTITION reference_2161 FOR VALUES IN (2161);
ALTER TABLE reference ATTACH PARTITION reference_2242 FOR VALUES IN (2242);
ALTER TABLE reference ATTACH PARTITION reference_2274 FOR VALUES IN (2274);
ALTER TABLE reference ATTACH PARTITION reference_2296 FOR VALUES IN (2296);
ALTER TABLE reference ATTACH PARTITION reference_2303 FOR VALUES IN (2303);
ALTER TABLE reference ATTACH PARTITION reference_2315 FOR VALUES IN (2315);
ALTER TABLE reference ATTACH PARTITION reference_2328 FOR VALUES IN (2328);
ALTER TABLE reference ATTACH PARTITION reference_2332 FOR VALUES IN (2332);
ALTER TABLE reference ATTACH PARTITION reference_2344 FOR VALUES IN (2344);
ALTER TABLE reference ATTACH PARTITION reference_2349 FOR VALUES IN (2349);
ALTER TABLE reference ATTACH PARTITION reference_2351 FOR VALUES IN (2351);
ALTER TABLE reference ATTACH PARTITION reference_2366 FOR VALUES IN (2366);
ALTER TABLE reference ATTACH PARTITION reference_2368 FOR VALUES IN (2368);
ALTER TABLE reference ATTACH PARTITION reference_2369 FOR VALUES IN (2369);
ALTER TABLE reference ATTACH PARTITION reference_2370 FOR VALUES IN (2370);

DROP TABLE reference_2056_old;
DROP TABLE reference_2082_old;
DROP TABLE reference_2142_old;
DROP TABLE reference_2149_old;
DROP TABLE reference_2153_old;
DROP TABLE reference_2161_old;
DROP TABLE reference_2242_old;
DROP TABLE reference_2274_old;
DROP TABLE reference_2296_old;
DROP TABLE reference_2303_old;
DROP TABLE reference_2315_old;
DROP TABLE reference_2328_old;
DROP TABLE reference_2332_old;
DROP TABLE reference_2344_old;
DROP TABLE reference_2349_old;
DROP TABLE reference_2351_old;
DROP TABLE reference_2366_old;
DROP TABLE reference_2368_old;
DROP TABLE reference_2369_old;
DROP TABLE reference_2370_old;

--
-- VERBATIM
-- the doc field is generated and the indices not derived from the parent tables.
-- So we better create new tables to be safe and copy data to them before attaching
--
SELECT DISTINCT dataset_key FROM verbatim WHERE dataset_key IN (3,2056,2082,2142,2149,2153,2161,2242,2274,2296,2303,2315,2328,2332,2344,2349,2351,2366,2368,2369,2370);
-- nothing is in there, so we just attach the old tables!

ALTER TABLE verbatim_3 RENAME TO verbatim_3_old;
ALTER TABLE verbatim_2056 RENAME TO verbatim_2056_old;
ALTER TABLE verbatim_2082 RENAME TO verbatim_2082_old;
ALTER TABLE verbatim_2142 RENAME TO verbatim_2142_old;
ALTER TABLE verbatim_2149 RENAME TO verbatim_2149_old;
ALTER TABLE verbatim_2153 RENAME TO verbatim_2153_old;
ALTER TABLE verbatim_2161 RENAME TO verbatim_2161_old;
ALTER TABLE verbatim_2242 RENAME TO verbatim_2242_old;
ALTER TABLE verbatim_2274 RENAME TO verbatim_2274_old;
ALTER TABLE verbatim_2296 RENAME TO verbatim_2296_old;
ALTER TABLE verbatim_2303 RENAME TO verbatim_2303_old;
ALTER TABLE verbatim_2315 RENAME TO verbatim_2315_old;
ALTER TABLE verbatim_2328 RENAME TO verbatim_2328_old;
ALTER TABLE verbatim_2332 RENAME TO verbatim_2332_old;
ALTER TABLE verbatim_2344 RENAME TO verbatim_2344_old;
ALTER TABLE verbatim_2349 RENAME TO verbatim_2349_old;
ALTER TABLE verbatim_2351 RENAME TO verbatim_2351_old;
ALTER TABLE verbatim_2366 RENAME TO verbatim_2366_old;
ALTER TABLE verbatim_2368 RENAME TO verbatim_2368_old;
ALTER TABLE verbatim_2369 RENAME TO verbatim_2369_old;
ALTER TABLE verbatim_2370 RENAME TO verbatim_2370_old;

CREATE TABLE verbatim_3 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2056 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2082 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2142 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2149 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2153 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2161 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2242 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2274 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2296 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2303 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2315 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2328 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2332 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2344 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2349 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2351 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2366 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2368 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2369 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);
CREATE TABLE verbatim_2370 (LIKE verbatim INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING GENERATED);

INSERT INTO verbatim_3 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_3_old;
INSERT INTO verbatim_2056 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2056_old;
INSERT INTO verbatim_2082 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2082_old;
INSERT INTO verbatim_2142 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2142_old;
INSERT INTO verbatim_2149 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2149_old;
INSERT INTO verbatim_2153 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2153_old;
INSERT INTO verbatim_2161 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2161_old;
INSERT INTO verbatim_2242 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2242_old;
INSERT INTO verbatim_2274 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2274_old;
INSERT INTO verbatim_2296 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2296_old;
INSERT INTO verbatim_2303 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2303_old;
INSERT INTO verbatim_2315 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2315_old;
INSERT INTO verbatim_2328 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2328_old;
INSERT INTO verbatim_2332 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2332_old;
INSERT INTO verbatim_2344 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2344_old;
INSERT INTO verbatim_2349 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2349_old;
INSERT INTO verbatim_2351 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2351_old;
INSERT INTO verbatim_2366 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2366_old;
INSERT INTO verbatim_2368 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2368_old;
INSERT INTO verbatim_2369 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2369_old;
INSERT INTO verbatim_2370 (id,dataset_key,line,file,type,terms,issues) SELECT id,dataset_key,line,file,type,terms,issues FROM verbatim_2370_old;

-- for speedy attaching
ALTER TABLE verbatim_default ADD CONSTRAINT verbatim_default_dataset_key_not_known CHECK (dataset_key!=3 AND dataset_key!=2056 AND dataset_key!=2082 AND dataset_key!=2142 AND dataset_key!=2149 AND dataset_key!=2153 AND dataset_key!=2161 AND dataset_key!=2242 AND dataset_key!=2274 AND dataset_key!=2296 AND dataset_key!=2303 AND dataset_key!=2315 AND dataset_key!=2328 AND dataset_key!=2332 AND dataset_key!=2344 AND dataset_key!=2349 AND dataset_key!=2351 AND dataset_key!=2366 AND dataset_key!=2368 AND dataset_key!=2369 AND dataset_key!=2370);

ALTER TABLE verbatim ATTACH PARTITION verbatim_3 FOR VALUES IN (3);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2056 FOR VALUES IN (2056);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2082 FOR VALUES IN (2082);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2142 FOR VALUES IN (2142);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2149 FOR VALUES IN (2149);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2153 FOR VALUES IN (2153);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2161 FOR VALUES IN (2161);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2242 FOR VALUES IN (2242);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2274 FOR VALUES IN (2274);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2296 FOR VALUES IN (2296);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2303 FOR VALUES IN (2303);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2315 FOR VALUES IN (2315);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2328 FOR VALUES IN (2328);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2332 FOR VALUES IN (2332);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2344 FOR VALUES IN (2344);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2349 FOR VALUES IN (2349);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2351 FOR VALUES IN (2351);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2366 FOR VALUES IN (2366);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2368 FOR VALUES IN (2368);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2369 FOR VALUES IN (2369);
ALTER TABLE verbatim ATTACH PARTITION verbatim_2370 FOR VALUES IN (2370);

DROP TABLE verbatim_3_old;
DROP TABLE verbatim_2056_old;
DROP TABLE verbatim_2082_old;
DROP TABLE verbatim_2142_old;
DROP TABLE verbatim_2149_old;
DROP TABLE verbatim_2153_old;
DROP TABLE verbatim_2161_old;
DROP TABLE verbatim_2242_old;
DROP TABLE verbatim_2274_old;
DROP TABLE verbatim_2296_old;
DROP TABLE verbatim_2303_old;
DROP TABLE verbatim_2315_old;
DROP TABLE verbatim_2328_old;
DROP TABLE verbatim_2332_old;
DROP TABLE verbatim_2344_old;
DROP TABLE verbatim_2349_old;
DROP TABLE verbatim_2351_old;
DROP TABLE verbatim_2366_old;
DROP TABLE verbatim_2368_old;
DROP TABLE verbatim_2369_old;
DROP TABLE verbatim_2370_old;

--
-- DATA INTEGRITY ISSUES
-- sector_key is missing in some old project tables - update based on the usage or remove

-- DISTRIBUTION
UPDATE distribution_3 d SET sector_key=u.sector_key FROM name_usage_3 u WHERE u.id=d.taxon_id AND d.sector_key IS NULL;
UPDATE distribution x SET sector_key=u.sector_key FROM dataset d, name_usage u 
    WHERE x.dataset_key=d.key AND x.dataset_key=u.dataset_key AND d.origin='RELEASED' AND d.source_key=3 AND u.id=x.taxon_id AND x.sector_key IS NULL;
DELETE FROM distribution_3 WHERE sector_key IS NULL;
DELETE FROM distribution x USING dataset d, name_usage u 
    WHERE x.dataset_key=d.key AND x.dataset_key=u.dataset_key AND d.origin='RELEASED' AND d.source_key=3 AND u.id=x.taxon_id AND x.sector_key IS NULL;
DELETE FROM distribution x WHERE NOT EXISTS (SELECT TRUE FROM name_usage u WHERE u.dataset_key=x.dataset_key AND u.id=x.taxon_id);

-- VERNACULAR NAME
UPDATE vernacular_name_3 v SET sector_key=u.sector_key FROM name_usage_3 u WHERE u.id=v.taxon_id AND v.sector_key IS NULL;
UPDATE vernacular_name x SET sector_key=u.sector_key FROM dataset d, name_usage u 
    WHERE x.dataset_key=d.key AND x.dataset_key=u.dataset_key AND d.origin='RELEASED' AND d.source_key=3 AND u.id=x.taxon_id AND x.sector_key IS NULL;
DELETE FROM vernacular_name_3 WHERE sector_key IS NULL;
DELETE FROM vernacular_name x USING dataset d, name_usage u 
    WHERE x.dataset_key=d.key AND x.dataset_key=u.dataset_key AND d.origin='RELEASED' AND d.source_key=3 AND u.id=x.taxon_id AND x.sector_key IS NULL;

-- NAME REL
DELETE FROM name_rel x WHERE NOT EXISTS (SELECT TRUE FROM name n WHERE n.dataset_key=x.dataset_key AND n.id=x.name_id);
DELETE FROM name_rel x WHERE NOT EXISTS (SELECT TRUE FROM name n WHERE n.dataset_key=x.dataset_key AND n.id=x.related_name_id);

-- VERBATIM SOURCE (projects only)
DELETE FROM verbatim_source x WHERE NOT EXISTS (SELECT TRUE FROM name_usage u WHERE u.dataset_key=x.dataset_key AND u.id=x.id);

-- References had data integrity problems because the table was not attached for some weeks. Remove unused refs:
CREATE TABLE md_ref_fks AS
    SELECT dataset_key,published_in_id AS id FROM name WHERE published_in_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM name_rel WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM type_material WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,unnest(reference_ids) FROM name_usage WHERE reference_ids IS NOT NULL
      UNION
    SELECT dataset_key,according_to_id FROM name_usage WHERE according_to_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM taxon_concept_rel WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM species_interaction WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM vernacular_name WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM media WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM distribution WHERE reference_id IS NOT NULL
      UNION
    SELECT dataset_key,reference_id FROM estimate WHERE reference_id IS NOT NULL;
CREATE UNIQUE INDEX ON md_ref_fks (dataset_key,id);
SELECT dataset_key, count(*) from reference r join dataset d on d.key=r.dataset_key where d.source_key=3 AND NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE r.dataset_key=rfk.dataset_key AND r.id=rfk.id) GROUP BY 1; 

delete from reference_2242 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2242 AND r.id=rfk.id); 
delete from reference_2296 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2296 AND r.id=rfk.id); 
delete from reference_2303 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2303 AND r.id=rfk.id); 
delete from reference_2315 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2315 AND r.id=rfk.id); 
delete from reference_2328 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2328 AND r.id=rfk.id); 
delete from reference_2332 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2332 AND r.id=rfk.id); 
delete from reference_2344 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2344 AND r.id=rfk.id); 
delete from reference_2349 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2349 AND r.id=rfk.id); 
delete from reference_2351 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2351 AND r.id=rfk.id); 
delete from reference_2366 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2366 AND r.id=rfk.id); 
delete from reference_2368 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=2368 AND r.id=rfk.id); 
delete from reference_9804 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=9804 AND r.id=rfk.id); 
delete from reference_9812 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=9812 AND r.id=rfk.id); 
delete from reference_9817 r where NOT EXISTS (SELECT TRUE FROM md_ref_fks rfk WHERE rfk.dataset_key=9817 AND r.id=rfk.id); 

DROP TABLE md_ref_fks;


--
-- FOREIGN KEYS
ALTER TABLE name_match ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
-- establish missing foreign key constraints (takes long time) which we should have created when the partitioning went live
--
ALTER TABLE verbatim_source ADD FOREIGN KEY (dataset_key, id) REFERENCES name_usage;
ALTER TABLE reference ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE reference ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE name ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name ADD FOREIGN KEY (dataset_key, published_in_id) REFERENCES reference;
ALTER TABLE name ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, related_name_id) REFERENCES name;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, according_to_id) REFERENCES reference;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, parent_id) REFERENCES name_usage DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE treatment ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE treatment ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE treatment ADD FOREIGN KEY (dataset_key, id) REFERENCES name_usage;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;

-- INDEX UPDATE
DROP INDEX name_rel_dataset_key_name_id_type_idx;
CREATE INDEX ON name_rel (dataset_key, name_id);
CREATE INDEX ON name_rel (dataset_key, related_name_id);
DROP INDEX taxon_concept_rel_dataset_key_taxon_id_type_idx;
CREATE INDEX ON taxon_concept_rel (dataset_key, taxon_id);
CREATE INDEX ON taxon_concept_rel (dataset_key, related_taxon_id);
DROP INDEX species_interaction_dataset_key_taxon_id_type_idx;
CREATE INDEX ON species_interaction (dataset_key, taxon_id);
CREATE INDEX ON species_interaction (dataset_key, related_taxon_id);
```

### 2022-03-30 changed dataset types
```
ALTER TABLE dataset ALTER COLUMN type SET DEFAULT null;
ALTER TABLE dataset ALTER COLUMN type TYPE text;
ALTER TABLE dataset_archive ALTER COLUMN type TYPE text;
ALTER TABLE dataset_source ALTER COLUMN type TYPE text;
DROP TYPE DATASETTYPE;
CREATE TYPE DATASETTYPE AS ENUM (
  'NOMENCLATURAL',
  'TAXONOMIC',
  'PHYLOGENETIC',
  'ARTICLE',
  'LEGAL',
  'THEMATIC',
  'OTHER'
);
ALTER TABLE dataset ALTER COLUMN type TYPE DATASETTYPE USING type::DATASETTYPE;
ALTER TABLE dataset_archive ALTER COLUMN type TYPE DATASETTYPE USING type::DATASETTYPE;
ALTER TABLE dataset_source ALTER COLUMN type TYPE DATASETTYPE USING type::DATASETTYPE;
ALTER TABLE dataset ALTER COLUMN type SET DEFAULT 'OTHER'::DATASETTYPE;
```

### 2022-03-28 new issue
```
ALTER TYPE ISSUE ADD VALUE 'AUTHORSHIP_REMOVED';
```

### 2022-03-25 missing names index indices for pattern search
```
CREATE INDEX ON names_index (scientific_name);
CREATE INDEX ON names_index (scientific_name) WHERE id = canonical_id;
```

### 2022-03-11 dataset attempt stored with sector import 
```
ALTER TABLE sector_import ADD COLUMN dataset_attempt INTEGER;

-- update dataset_attempt in existing imports
CREATE TABLE s_source_attempts as SELECT dataset_key as source_key, attempt, finished, state 
FROM dataset_import di 
WHERE job='ImportJob' and state ='FINISHED';
ALTER TABLE s_source_attempts ADD PRIMARY KEY (source_key, attempt);

CREATE TABLE s_sector AS SELECT DISTINCT p.key as project_key, subject_dataset_key AS source_key, s.id AS sector_key 
FROM sector s JOIN dataset d on d.key=s.dataset_key LEFT JOIN dataset p on p.key=coalesce(d.source_key,d.key) ORDER BY 1,2,3;
ALTER TABLE s_sector ADD PRIMARY KEY (project_key, sector_key);

CREATE TABLE s_sync AS SELECT si.dataset_key,si.sector_key,si.attempt,si.state,si.finished, s.source_key, max(a.attempt) as dataset_attempt
FROM sector_import si 
 LEFT JOIN s_sector s ON s.sector_key=si.sector_key AND s.project_key=si.dataset_key
 LEFT JOIN s_source_attempts a ON s.source_key=a.source_key AND a.finished < si.started 
WHERE job='SectorSync'
GROUP BY 1,2,3,4,5,6;
ALTER TABLE s_sync ADD PRIMARY KEY (dataset_key, sector_key, attempt);

UPDATE sector_import i SET dataset_attempt=s.dataset_attempt 
FROM s_sync s
WHERE s.dataset_key=i.dataset_key AND s.sector_key=i.sector_key AND s.attempt=i.attempt;

-- check syncs without dataset_attempt
SELECT dataset_key, sector_key, attempt, job, state, started from sector_import WHERE dataset_attempt IS NULL ORDER BY 1,2,3;

DROP TABLE s_sync;
DROP TABLE s_sector;
DROP TABLE s_source_attempts;
```

### 2022-03-09 new higher ranks
```
ALTER TYPE RANK ADD VALUE 'PARVPHYLUM' AFTER 'INFRAPHYLUM';
ALTER TYPE RANK ADD VALUE 'MICROPHYLUM' AFTER 'PARVPHYLUM';
ALTER TYPE RANK ADD VALUE 'NANOPHYLUM' AFTER 'MICROPHYLUM';
ALTER TYPE RANK ADD VALUE 'GIGACLASS' AFTER 'NANOPHYLUM';
ALTER TYPE RANK ADD VALUE 'MEGACLASS' AFTER 'GIGACLASS';
ALTER TYPE RANK ADD VALUE 'MEGACOHORT' BEFORE 'SUPERCOHORT';
```

### 2022-03-07 dataset keys based on origin
The dataset key based partitioning differs between external datasets that live on the default partition
and managed & released datasets that live on their own, dedicated partition.
When a new non external dataset is created, new tables need to be created and finally attached to the main tables.
For this to happen quick the default partition needs to have a check constraint that excludes those expected new keys.
```
-- find out the current maximum dataset key to replace $MAX below:
SELECT max(key) FROM dataset;
-- gives 9812 ON PROD 22th March 2022. With config minExternalDatasetKey: 20000 this then becomes: 
ALTER TABLE verbatim_default ADD CONSTRAINT verbatim_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE reference_default ADD CONSTRAINT reference_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE name_default ADD CONSTRAINT name_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE name_rel_default ADD CONSTRAINT name_rel_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE type_material_default ADD CONSTRAINT type_material_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE name_usage_default ADD CONSTRAINT name_usage_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE taxon_concept_rel_default ADD CONSTRAINT taxon_concept_rel_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE species_interaction_default ADD CONSTRAINT species_interaction_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE distribution_default ADD CONSTRAINT distribution_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE media_default ADD CONSTRAINT media_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE treatment_default ADD CONSTRAINT treatment_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
ALTER TABLE vernacular_name_default ADD CONSTRAINT vernacular_name_default_dataset_key_no_project_check CHECK (dataset_key < 9812 OR dataset_key >= 20000);
```

### 2022-02-22 new issues
```
ALTER TYPE ISSUE ADD VALUE 'MULTI_WORD_MONOMIAL';
ALTER TYPE ISSUE ADD VALUE 'WRONG_MONOMIAL_CASE';
```

### 2022-02-22 names archive
```
CREATE TYPE IDREPORTTYPE AS ENUM (
  'DELETED',
  'RESURRECTED',
  'CREATED'
);

ALTER TABLE dataset DROP COLUMN doc;
ALTER TABLE dataset ADD COLUMN doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(doi, '')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(key::text, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(issn, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(identifier::text, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(creator), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(publisher), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(version, '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(geographic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(taxonomic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(temporal_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contact), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(editor), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contributor), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
  ) STORED;


CREATE TABLE id_report (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  type IDREPORTTYPE NOT NULL,
  PRIMARY KEY (dataset_key, id)
);
CREATE INDEX ON id_report (dataset_key);

CREATE TABLE name_usage_archive (
  id TEXT NOT NULL,
  n_id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  -- shared with name table, keep manually in sync!
  n_rank RANK NOT NULL,
  n_candidatus BOOLEAN DEFAULT FALSE,
  n_notho NAMEPART,
  n_code NOMCODE,
  n_nom_status NOMSTATUS,
  n_origin ORIGIN NOT NULL,
  n_type NAMETYPE NOT NULL,
  n_scientific_name TEXT NOT NULL,
  n_authorship TEXT,
  n_uninomial TEXT,
  n_genus TEXT,
  n_infrageneric_epithet TEXT,
  n_specific_epithet TEXT,
  n_infraspecific_epithet TEXT,
  n_cultivar_epithet TEXT,
  n_basionym_authors TEXT[] DEFAULT '{}',
  n_basionym_ex_authors TEXT[] DEFAULT '{}',
  n_basionym_year TEXT,
  n_combination_authors TEXT[] DEFAULT '{}',
  n_combination_ex_authors TEXT[] DEFAULT '{}',
  n_combination_year TEXT,
  n_sanctioning_author TEXT,
  n_published_in_id TEXT,
  n_published_in_page TEXT,
  n_nomenclatural_note TEXT,
  n_unparsed TEXT,
  n_remarks TEXT,
  -- common with name_usage, keep in sync!
  extinct BOOLEAN,
  status TAXONOMICSTATUS NOT NULL,
  origin ORIGIN NOT NULL,
  parent_id TEXT,
  name_phrase TEXT,
  link TEXT,
  remarks TEXT,
  -- archive specifics, will be dropped from partitioned name table
  according_to TEXT,
  basionym SIMPLE_NAME,
  classification SIMPLE_NAME[],
  published_in TEXT,
  first_release_key INTEGER,
  last_release_key INTEGER,

  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key) REFERENCES dataset,
  FOREIGN KEY (first_release_key) REFERENCES dataset,
  FOREIGN KEY (last_release_key) REFERENCES dataset
);

CREATE TABLE name_usage_archive_match (
  dataset_key INTEGER NOT NULL,
  index_id INTEGER NOT NULL REFERENCES names_index,
  usage_id TEXT NOT NULL,
  type MATCHTYPE,
  PRIMARY KEY (dataset_key, usage_id)
);
CREATE INDEX ON name_usage_archive_match (dataset_key, index_id);
CREATE INDEX ON name_usage_archive_match (index_id);

-- insert id mapping file generated by IdConverterTest.writeMappingFile !!!
CREATE TABLE latin29 (
  id TEXT,
  idnum INTEGER,
  PRIMARY KEY (id)
);
\copy latin29 (idnum,id) from 'latin29.txt' 

---
-- populate id reports
---
CREATE SCHEMA idr;

-- track releases in sequential order
CREATE TABLE idr.releases (
	key int PRIMARY KEY,
	first boolean,
	seq serial,
	attempt int,
	project_key int
);
INSERT INTO idr.releases (project_key,key,attempt,first)
SELECT p.key AS project_key, r.key AS release_key, r.attempt, (
  SELECT r.key = min(key) FROM dataset 
  WHERE origin='RELEASED' AND deleted IS NULL AND NOT private AND source_key=p.key
)  
FROM dataset p JOIN dataset r ON r.source_key=p.key 
WHERE p.origin='PROJECT' AND r.origin='RELEASED' AND r.deleted IS NULL AND NOT r.private
ORDER BY p.key, r.created;

-- copy ids into a faster temp table
CREATE TABLE idr.ids (
  dataset_key INTEGER,
  id TEXT,
  idnum INTEGER,
  PRIMARY KEY (dataset_key, id)
);
INSERT INTO idr.ids (dataset_key,id) 
SELECT u.dataset_key, u.id
FROM idr.releases d JOIN name_usage u ON d.key=u.dataset_key;

CREATE INDEX ON idr.ids (id);
CREATE INDEX ON idr.ids (dataset_key);

-- insert all first releases
INSERT INTO id_report (type,dataset_key,id) 
SELECT 'CREATED', i.dataset_key, l.idnum
FROM idr.releases r 
  JOIN idr.ids i ON i.dataset_key=r.key 
  JOIN latin29 l ON l.id=i.id  
WHERE r.first;

-- now each event type for all subsequent releases
-- DELETED
INSERT INTO id_report (type,dataset_key,id) 
SELECT 'DELETED', r2.key, l.idnum
FROM idr.releases r1 
  JOIN idr.releases r2 ON r1.project_key=r2.project_key AND r2.seq=r1.seq+1    
  JOIN idr.ids i1 ON i1.dataset_key=r1.key 
  JOIN latin29 l ON l.id=i1.id   
WHERE NOT EXISTS (
  SELECT NULL FROM idr.ids i2
  WHERE i2.id = i1.id AND i2.dataset_key=r2.key
);

-- RESURRECTED
INSERT INTO id_report (type,dataset_key,id) 
SELECT 'RESURRECTED', i.dataset_key, l.idnum
FROM idr.releases r
  JOIN idr.releases rp ON rp.project_key=r.project_key AND rp.seq=r.seq-1    
  JOIN idr.ids i ON i.dataset_key=r.key 
  JOIN latin29 l ON l.id=i.id   
  LEFT JOIN idr.ids ip ON ip.dataset_key=rp.key AND ip.id=i.id 
WHERE NOT r.first AND ip.id IS NULL AND EXISTS (
  SELECT NULL 
  FROM idr.ids prev
    JOIN idr.releases prev_r ON prev_r.key=prev.dataset_key    
  WHERE prev.id=i.id AND prev_r.project_key=r.project_key AND prev_r.seq<r.seq
  LIMIT 1
) ;

-- CREATED
INSERT INTO id_report (type,dataset_key,id) 
SELECT 'CREATED', i.dataset_key, map.idnum
FROM idr.releases r
  JOIN idr.ids i ON i.dataset_key=r.key 
  JOIN latin29 map ON map.id=i.id    
WHERE NOT r.first AND NOT EXISTS (
  SELECT NULL 
  FROM idr.ids prev
    JOIN idr.releases prev_r ON prev_r.key=prev.dataset_key    
  WHERE prev.id=i.id AND prev_r.project_key=r.project_key AND prev_r.seq<r.seq
  LIMIT 1
);
```

With id reports in place you can now run the Migration tool.
Finally the temp schema can be dropped:

```
DROP SCHEMA idr;
```

### 2022-02-09 add DIACRITIC_CHARACTERS issue
```
ALTER TYPE ISSUE ADD VALUE 'DIACRITIC_CHARACTERS';
```

### 2022-02-09 add regex search to name index
```
DROP INDEX names_index_lower_idx;
DROP INDEX estimate_dataset_key_target_id_idx1;
CREATE INDEX ON name (dataset_key, scientific_name text_pattern_ops);
```

### 2022-01-17 dataset partitioning without cascading deletes
```
-- general table changes first
ALTER TABLE name DROP COLUMN homotypic_name_id CASCADE;
DROP FUNCTION homotypic_name_id_default CASCADE;

CREATE EXTENSION IF NOT EXISTS btree_gin;

DROP INDEX verbatim_doc_idx;
DROP INDEX verbatim_issues_idx;
DROP INDEX verbatim_terms_idx;
DROP INDEX verbatim_type_idx;
DROP INDEX verbatim_source_issues_idx;
DROP INDEX reference_doc_idx;
DROP INDEX reference_sector_key_idx;
DROP INDEX reference_verbatim_key_idx;
DROP INDEX name_lower_idx;
DROP INDEX name_published_in_id_idx;
DROP INDEX name_scientific_name_normalized_idx;
DROP INDEX name_sector_key_idx;
DROP INDEX name_verbatim_key_idx;
DROP INDEX name_rel_name_id_idx;
DROP INDEX name_rel_reference_id_idx;
DROP INDEX name_rel_published_in_id_idx;
DROP INDEX name_rel_related_name_id_idx;
DROP INDEX name_rel_sector_key_idx;
DROP INDEX name_rel_verbatim_key_idx;
DROP INDEX type_material_name_id_idx;
DROP INDEX type_material_reference_id_idx;
DROP INDEX type_material_sector_key_idx;
DROP INDEX type_material_verbatim_key_idx;
DROP INDEX name_usage_according_to_id_idx;
DROP INDEX name_usage_name_id_idx;
DROP INDEX name_usage_parent_id_idx;
DROP INDEX name_usage_sector_key_idx;
DROP INDEX name_usage_verbatim_key_idx;
DROP INDEX taxon_concept_rel_reference_id_idx;
DROP INDEX taxon_concept_rel_sector_key_idx;
DROP INDEX taxon_concept_rel_taxon_id_type_idx;
DROP INDEX taxon_concept_rel_verbatim_key_idx;
DROP INDEX species_interaction_reference_id_idx;
DROP INDEX species_interaction_sector_key_idx;
DROP INDEX species_interaction_taxon_id_type_idx;
DROP INDEX species_interaction_verbatim_key_idx;
DROP INDEX vernacular_name_doc_idx;
DROP INDEX vernacular_name_reference_id_idx;
DROP INDEX vernacular_name_sector_key_idx;
DROP INDEX vernacular_name_taxon_id_idx;
DROP INDEX vernacular_name_verbatim_key_idx;
DROP INDEX distribution_reference_id_idx;
DROP INDEX distribution_sector_key_idx;
DROP INDEX distribution_taxon_id_idx;
DROP INDEX distribution_verbatim_key_idx;
DROP INDEX treatment_sector_key_idx;
DROP INDEX treatment_verbatim_key_idx;
DROP INDEX media_reference_id_idx;
DROP INDEX media_sector_key_idx;
DROP INDEX media_taxon_id_idx;
DROP INDEX media_verbatim_key_idx;

CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS simple_name[] AS $$
	declare seql TEXT;
	declare parents simple_name[];
BEGIN
    seql := 'WITH RECURSIVE x AS ('
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t '
        || '  JOIN name n ON n.dataset_key=$1 AND n.id=t.name_id WHERE t.dataset_key=$1 AND t.id = $2'
        || ' UNION ALL '
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t '
        || '  JOIN name n ON n.dataset_key=$1 AND n.id=t.name_id WHERE t.dataset_key=$1 AND t.id = x.parent_id'
        || ') SELECT array_agg(sn) FROM x';

    IF NOT v_inc_self THEN
        seql := seql || ' WHERE id != $1';
    END IF;

    EXECUTE seql
    INTO parents
    USING v_dataset_key, v_id;
    RETURN (array_reverse(parents));
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION track_usage_count()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
      -- making use of the special variable TG_OP to work out the operation.
      -- we assume we never mix records from several datasets in an insert or delete statement !!!
      IF (TG_OP = 'DELETE') THEN
        EXECUTE 'UPDATE usage_count set counter=counter+(select count(*) from deleted) where dataset_key=(SELECT dataset_key FROM deleted LIMIT 1)';
      ELSIF (TG_OP = 'INSERT') THEN
        EXECUTE 'UPDATE usage_count set counter=counter-(select count(*) from inserted) where dataset_key=(SELECT dataset_key FROM inserted LIMIT 1)';
      END IF;

    RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';
```

execute the following to clean project tables for managed and released datasets each:
```
./exec-sql.sh sql/partitioning1.sql --origin PROJECT
./exec-sql.sh sql/partitioning1.sql --origin RELEASED

ALTER TABLE verbatim_source_{KEY} DROP CONSTRAINT IF EXISTS verbatim_source_{KEY}_id_fkey;
ALTER TABLE verbatim_source_{KEY} DROP CONSTRAINT IF EXISTS verbatim_source_{KEY}_pkey;
```


Then for all partition suffices execute the following to remove previous triggers, indices, primary and foreign keys:
```
./exec-sql.sh sql/partitioning2.sql

--
-- TRIGGER
--
DROP TRIGGER IF EXISTS name_trigger_{KEY} ON name_{KEY};

DROP TRIGGER IF EXISTS trg_name_usage_{KEY}_insert ON name_usage_{KEY};
CREATE TRIGGER trg_name_usage_{KEY}_insert AFTER INSERT ON name_usage_{KEY}
REFERENCING NEW TABLE AS inserted
FOR EACH STATEMENT EXECUTE FUNCTION track_usage_count();

DROP TRIGGER IF EXISTS trg_name_usage_{KEY}_delete ON name_usage_{KEY};
CREATE TRIGGER trg_name_usage_{KEY}_delete AFTER DELETE ON name_usage_{KEY}
REFERENCING OLD TABLE AS deleted
FOR EACH STATEMENT EXECUTE FUNCTION track_usage_count();

--
-- FOREIGN KEYS
--
ALTER TABLE reference_{KEY} DROP CONSTRAINT IF EXISTS reference_{KEY}_verbatim_key_fkey;
ALTER TABLE name_{KEY} DROP CONSTRAINT IF EXISTS name_{KEY}_verbatim_key_fkey;
ALTER TABLE name_{KEY} DROP CONSTRAINT IF EXISTS name_{KEY}_published_in_id_fkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_verbatim_key_fkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_reference_id_fkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_published_in_id_fkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_name_id_fkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_related_name_id_fkey;
ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_verbatim_key_fkey;
ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_reference_id_fkey;
ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_name_id_fkey;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_verbatim_key_fkey;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_according_to_id_fkey;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_parent_id_fkey;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_name_id_fkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_verbatim_key_fkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_reference_id_fkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_taxon_id_fkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_related_taxon_id_fkey;
ALTER TABLE species_interaction_{KEY} DROP CONSTRAINT IF EXISTS species_interaction_{KEY}_verbatim_key_fkey;
ALTER TABLE species_interaction_{KEY} DROP CONSTRAINT IF EXISTS species_interaction_{KEY}_reference_id_fkey;
ALTER TABLE species_interaction_{KEY} DROP CONSTRAINT IF EXISTS species_interaction_{KEY}_taxon_id_fkey;
ALTER TABLE species_interaction_{KEY} DROP CONSTRAINT IF EXISTS species_interaction_{KEY}_related_taxon_id_fkey;
ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_verbatim_key_fkey;
ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_reference_id_fkey;
ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_taxon_id_fkey;
ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_verbatim_key_fkey;
ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_reference_id_fkey;
ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_taxon_id_fkey;
ALTER TABLE treatment_{KEY} DROP CONSTRAINT IF EXISTS treatment_{KEY}_verbatim_key_fkey;
ALTER TABLE treatment_{KEY} DROP CONSTRAINT IF EXISTS treatment_{KEY}_id_fkey;
ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_verbatim_key_fkey;
ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_reference_id_fkey;
ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_taxon_id_fkey;

--
-- PRIMARY KEYS
--
ALTER TABLE verbatim_{KEY} DROP CONSTRAINT IF EXISTS verbatim_{KEY}_pkey;
ALTER TABLE reference_{KEY} DROP CONSTRAINT IF EXISTS reference_{KEY}_pkey;
ALTER TABLE name_{KEY} DROP CONSTRAINT IF EXISTS name_{KEY}_pkey;
ALTER TABLE name_rel_{KEY} DROP CONSTRAINT IF EXISTS name_rel_{KEY}_pkey;
ALTER TABLE type_material_{KEY} DROP CONSTRAINT IF EXISTS type_material_{KEY}_pkey;
ALTER TABLE name_usage_{KEY} DROP CONSTRAINT IF EXISTS name_usage_{KEY}_pkey;
ALTER TABLE distribution_{KEY} DROP CONSTRAINT IF EXISTS distribution_{KEY}_pkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_pkey;
ALTER TABLE species_interaction_{KEY} DROP CONSTRAINT IF EXISTS species_interaction_{KEY}_pkey;
ALTER TABLE taxon_concept_rel_{KEY} DROP CONSTRAINT IF EXISTS taxon_concept_rel_{KEY}_pkey;
ALTER TABLE vernacular_name_{KEY} DROP CONSTRAINT IF EXISTS vernacular_name_{KEY}_pkey;
ALTER TABLE media_{KEY} DROP CONSTRAINT IF EXISTS media_{KEY}_pkey;
ALTER TABLE treatment_{KEY} DROP CONSTRAINT IF EXISTS treatment_{KEY}_pkey;
```

Now we can create default subpartitions with a configurable number of shards using the repartition command.
Note that the postgres user that runs this must have (temporary) SUPERUSER rights to change the session_replication_role environment setting!
```
ALTER USER col WITH SUPERUSER;
./repartition.sh {PORT} --num 12
```

Finally Run:
```
DROP FUNCTION count_usage_on_insert;

--
-- PRIMARY KEYS
--
ALTER TABLE verbatim ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE reference ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE name ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE name_rel ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE type_material ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE name_usage ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE distribution ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE taxon_concept_rel ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE species_interaction ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE vernacular_name ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE media ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE treatment ADD PRIMARY KEY (dataset_key, id);
ALTER TABLE verbatim_source ADD PRIMARY KEY (dataset_key, id);

--
-- FOREIGN KEYS
--
ALTER TABLE reference ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name ADD FOREIGN KEY (dataset_key, published_in_id) REFERENCES reference;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE name_rel ADD FOREIGN KEY (dataset_key, related_name_id) REFERENCES name;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE type_material ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, according_to_id) REFERENCES reference;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, parent_id) REFERENCES name_usage DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE name_usage ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE taxon_concept_rel ADD FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE species_interaction ADD FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE distribution ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE media ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE treatment ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE treatment ADD FOREIGN KEY (dataset_key, id) REFERENCES name_usage;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, reference_id) REFERENCES reference;
ALTER TABLE vernacular_name ADD FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage;
ALTER TABLE verbatim_source ADD FOREIGN KEY (dataset_key, id) REFERENCES name_usage;

--
-- INDICES
--
CREATE INDEX ON verbatim (dataset_key, type);
CREATE INDEX ON verbatim USING GIN (dataset_key, doc);
CREATE INDEX ON verbatim USING GIN (dataset_key, issues);
CREATE INDEX ON verbatim USING GIN (dataset_key, terms jsonb_path_ops);
CREATE INDEX ON verbatim_source USING GIN(dataset_key, issues);
CREATE INDEX ON reference (dataset_key, verbatim_key);
CREATE INDEX ON reference (dataset_key, sector_key);
CREATE INDEX ON reference USING GIN (dataset_key, doc);
CREATE INDEX ON name (dataset_key, sector_key);
CREATE INDEX ON name (dataset_key, verbatim_key);
CREATE INDEX ON name (dataset_key, published_in_id);
CREATE INDEX ON name (dataset_key, lower(scientific_name));
CREATE INDEX ON name (dataset_key, scientific_name_normalized);
CREATE INDEX ON name_rel (dataset_key, sector_key);
CREATE INDEX ON name_rel (dataset_key, verbatim_key);
CREATE INDEX ON name_rel (dataset_key, reference_id);
CREATE INDEX ON name_rel (dataset_key, name_id);
CREATE INDEX ON name_rel (dataset_key, related_name_id);
CREATE INDEX ON type_material (dataset_key, name_id);
CREATE INDEX ON type_material (dataset_key, sector_key);
CREATE INDEX ON type_material (dataset_key, verbatim_key);
CREATE INDEX ON type_material (dataset_key, reference_id);
CREATE INDEX ON name_usage (dataset_key, name_id);
CREATE INDEX ON name_usage (dataset_key, parent_id);
CREATE INDEX ON name_usage (dataset_key, verbatim_key);
CREATE INDEX ON name_usage (dataset_key, sector_key);
CREATE INDEX ON name_usage (dataset_key, according_to_id);
CREATE INDEX ON taxon_concept_rel (dataset_key, taxon_id, type);
CREATE INDEX ON taxon_concept_rel (dataset_key, sector_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, verbatim_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, reference_id);
CREATE INDEX ON species_interaction (dataset_key, taxon_id, type);
CREATE INDEX ON species_interaction (dataset_key, sector_key);
CREATE INDEX ON species_interaction (dataset_key, verbatim_key);
CREATE INDEX ON species_interaction (dataset_key, reference_id);
CREATE INDEX ON vernacular_name (dataset_key, taxon_id);
CREATE INDEX ON vernacular_name (dataset_key, sector_key);
CREATE INDEX ON vernacular_name (dataset_key, verbatim_key);
CREATE INDEX ON vernacular_name (dataset_key, reference_id);
CREATE INDEX ON vernacular_name USING GIN (dataset_key, doc);
CREATE INDEX ON distribution (dataset_key, taxon_id);
CREATE INDEX ON distribution (dataset_key, sector_key);
CREATE INDEX ON distribution (dataset_key, verbatim_key);
CREATE INDEX ON distribution (dataset_key, reference_id);
CREATE INDEX ON treatment (dataset_key, sector_key);
CREATE INDEX ON treatment (dataset_key, verbatim_key);
CREATE INDEX ON media (dataset_key, taxon_id);
CREATE INDEX ON media (dataset_key, sector_key);
CREATE INDEX ON media (dataset_key, verbatim_key);
CREATE INDEX ON media (dataset_key, reference_id);

ALTER USER col WITH NOSUPERUSER;
```

### 2022-01-14 blocking user
```
ALTER TABLE "user" ADD COLUMN blocked TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE "user" DROP COLUMN deleted;
```

### 2022-01-13 reviewer role
```
ALTER TABLE dataset RENAME COLUMN access_control TO acl_editor;
ALTER TABLE dataset ADD COLUMN acl_reviewer INT[];
ALTER TYPE USER_ROLE ADD VALUE 'REVIEWER' BEFORE 'EDITOR';
```

### 2021-12-15 new issue
```
ALTER TYPE ISSUE ADD VALUE 'RELATED_NAME_MISSING';
```

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

and for all PROJECT data partitions `./exec-sql {YOURFILE} --managed true`
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
  'PROJECT',
  'RELEASED'
);
UPDATE dataset SET origin='EXTERNAL' WHERE origin='UPLOADED' AND data_access IS NOT NULL;
UPDATE dataset SET origin='PROJECT' WHERE origin='UPLOADED';
UPDATE dataset SET origin='RELEASED' WHERE origin='PROJECT' AND locked;
UPDATE dataset SET source_key=3 WHERE origin='RELEASED';
UPDATE dataset_archive SET origin='EXTERNAL' WHERE origin='UPLOADED' AND data_access IS NOT NULL;
UPDATE dataset_archive SET origin='PROJECT' WHERE origin='UPLOADED';
UPDATE dataset_archive SET origin='RELEASED' WHERE origin='PROJECT' AND locked;
UPDATE dataset_archive SET source_key=3 WHERE origin='RELEASED';
UPDATE dataset_import SET origin='EXTERNAL' WHERE origin='UPLOADED' AND download_uri IS NOT NULL;
UPDATE dataset_import SET origin='PROJECT' WHERE origin='UPLOADED';
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
