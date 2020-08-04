## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

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

SELECT unnest(array[2079,2081,2083,2123,2140,2165,2166]) AS key INTO TABLE __releases;

UPDATE name n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.sector_key IS NOT NULL; 
UPDATE name_usage n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.sector_key IS NOT NULL; 
UPDATE reference n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.sector_key IS NOT NULL; 
UPDATE type_material n SET sector_key = s.copied_from_id FROM sector s WHERE n.sector_key=s.id AND n.sector_key IS NOT NULL; 

ALTER TABLE sector DROP PRIMARY KEY;
UPDATE sector SET id=copied_from_id WHERE copied_from_id IS NOT NULL;
ALTER TABLE sector DROP COLUMN copied_from_id;
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
