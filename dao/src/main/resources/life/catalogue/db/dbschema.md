## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

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

UPDATE sector_import SET state='INSERTING' WHERE state='COPYING';
UPDATE sector_import SET state='MATCHING' WHERE state='RELINKING';

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

Afterwards it is required to run the `AddTableCmd -t type_material` using the cli tools
in order to create partitions for all existing datasets. 
