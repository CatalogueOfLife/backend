drop schema public cascade;
create schema public;

create table databases (
  record_id int PRIMARY KEY,
  database_name_displayed text,
  database_name text,
  database_full_name text,
  web_site text,
  organization text,
  contact_person text,
  taxa text,
  taxonomic_coverage text,
  abstract text,
  version text,
  release_date text,
  speciescount int,
  speciesest int,
  authors_editors text,
  accepted_species_names int,
  accepted_infraspecies_names int,
  species_synonyms int,
  infraspecies_synonyms int,
  common_names int,
  total_names int,
  is_new int,
  coverage int,
  completeness int,
  confidence int
);

create table common_names (
  record_id int,
  name_code text,
  common_name text,
  transliteration text,
  language text,
  country text,
  area text,
  reference_id int,
  database_id int,
  is_infraspecies int,
  reference_code text
);

create table distribution (
  record_id int,
  name_code text,
  distribution text,
  standardinuse text,
  distributionstatus text,
  database_id int
);

create table estimates (
  name_code text,
  kingdom text,
  name text,
  rank text,
  estimate int,
  source text,
  inserted text,
  updated text
);

create table families (
  record_id int PRIMARY KEY,
  hierarchy_code text,
  kingdom text,
  phylum text,
  class text,
  "order" text,
  family text,
  superfamily text,
  database_id int,
  family_code text,
  is_accepted_name int
);

create table lifezone (
  record_id int,
  name_code text,
  lifezone text,
  database_id int
);

create table "references" (
  record_id int PRIMARY KEY,
  author text,
  year text,
  title text,
  source text,
  database_id int,
  reference_code text
);

create table scientific_name_references (
  record_id int,
  name_code text,
  reference_type text,
  reference_id int,
  reference_code text,
  database_id int
);

create table scientific_names (
  record_id int UNIQUE,
  name_code text,
  web_site text,
  genus text,
  subgenus text,
  species text,
  infraspecies_parent_name_code text,
  infraspecies text,
  infraspecies_marker text,
  author text,
  accepted_name_code text,
  comment text,
  scrutiny_date text,
  sp2000_status_id int,
  database_id int,
  specialist_id int,
  family_id int,
  specialist_code text,
  family_code text,
  is_accepted_name int,
  gsdtaxonguid text,
  gsdnameguid text,
  is_extinct int,
  has_preholocene int,
  has_modern int
);

create table specialists (
  record_id int PRIMARY KEY,
  specialist_name text,
  specialist_code text,
  database_id int
);



\copy databases from 'databases.csv' WITH CSV HEADER NULL '\N'
\copy common_names from 'common_names.csv' WITH CSV HEADER NULL '\N'
\copy distribution from 'distribution.csv' WITH CSV HEADER NULL '\N'
\copy estimates from 'estimates.csv' WITH CSV HEADER NULL '\N'
\copy families from 'families.csv' WITH CSV HEADER NULL '\N'
\copy lifezone from 'lifezone.csv' WITH CSV HEADER NULL '\N'
\copy "references" from 'references.csv' WITH CSV HEADER NULL '\N'
\copy scientific_name_references from 'scientific_name_references.csv' WITH CSV HEADER NULL '\N'
\copy scientific_names from 'scientific_names.csv' WITH CSV HEADER NULL '\N'
\copy specialists from 'specialists.csv' WITH CSV HEADER NULL '\N'



CREATE INDEX ON scientific_names (genus, species);
CREATE INDEX ON scientific_names (name_code);
CREATE INDEX ON scientific_names (accepted_name_code);
CREATE INDEX ON scientific_names (database_id);

CREATE INDEX ON common_names (database_id);
CREATE INDEX ON common_names (name_code);
