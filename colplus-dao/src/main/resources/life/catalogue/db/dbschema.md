## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

#### 2020-02-25 import state changes 
```
alter type IMPORTSTATE RENAME VALUE 'DECISION_MATCHING' to 'MATCHING';
alter type IMPORTSTATE ADD VALUE 'EXPORTING' after 'BUILDING_METRICS';
alter type IMPORTSTATE ADD VALUE 'RELEASED' after 'FINISHED';

alter table dataset_import add column created_by INTEGER NOT NULL DEFAULT 10;
alter table dataset_import alter column created_by DROP DEFAULT;

alter table sector_import add column created_by INTEGER NOT NULL DEFAULT 10;
alter table sector_import alter column created_by DROP DEFAULT;
```

#### 2020-02-24 ranks & entities for sectors 
```
alter table sector add column ranks RANK[] DEFAULT '{}';
alter table sector add column entities ENTITYTYPE[] DEFAULT NULL;
```

#### 2020-02-07 add matching state
```
alter type IMPORTSTATE ADD VALUE 'DECISION_MATCHING' after 'INDEXING';
```

#### add type material
```
alter type ENTITYTYPE add value 'TYPE_MATERIAL' after 'NAME_USAGE';
alter type NAMEFIELD RENAME value 'WEBPAGE' to 'LINK';
alter type NAMEFIELD add value 'CODE' after 'PUBLISHED_IN_PAGE';

alter type ISSUE add value 'COUNTRY_INVALID' after 'TYPE_STATUS_INVALID'; 
alter type ISSUE add value 'ALTITUDE_INVALID' after 'TYPE_STATUS_INVALID'; 
alter type ISSUE add value 'LAT_LON_INVALID' after 'TYPE_STATUS_INVALID';

alter table dataset_import add column type_material_count INTEGER;
alter table dataset_import add column type_material_by_status_count HSTORE;

alter table name drop column type_status;
alter table name drop column type_material;
alter table name drop column type_reference_id;
alter table name rename column webpage to link;

alter table name_rel rename column note to remarks;
alter table name_usage rename column webpage to link;

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
