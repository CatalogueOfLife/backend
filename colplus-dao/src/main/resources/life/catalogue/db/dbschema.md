## DB Schema Changes
are to be applied manually to prod.
Dev can usually be wiped and started from scratch.

We maintain here a log list of DDL statements executed on prod 
so we a) know the current state and b) can reproduce the same migration.

We could have used Liquibase, but we would not have trusted the automatic updates anyways
and done it manually. So we can as well log changes here.

### PROD changes

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
  modified_byInitD INTEGER NOT NULL,
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
