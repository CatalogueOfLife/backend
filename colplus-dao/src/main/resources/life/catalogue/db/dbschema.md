## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

#### 2020-03-12 parser_config 
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
