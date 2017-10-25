
-- this will remove all existing tables
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

CREATE EXTENSION IF NOT EXISTS hstore;


CREATE TYPE rank AS ENUM (
  'kingdom',
  'phylum',
  'subphylum',
  'class',
  'subclass',
  'order',
  'suborder',
  'superfamily',
  'family',
  'subfamily',
  'tribe',
  'genus',
  'subgenus',
  'species',
  'subspecies',
  'variety',
  'form',
  'clade',
  'unranked'
);

CREATE TABLE dataset (
  key serial PRIMARY KEY,
  alias TEXT,
  title TEXT NOT NULL,
  gbif_key UUID,
  description TEXT,
  group_name TEXT,
  authors_and_editors TEXT,
  organisation TEXT,
  contact_person TEXT,
  version TEXT,
  release_date DATE,
  taxonomic_coverage TEXT,
  coverage TEXT,
  completeness INTEGER,
  confidence INTEGER,
  homepage TEXT,
  data_format TEXT,
  data_access TEXT,
  notes text,
  created TIMESTAMP,
  modified TIMESTAMP,
  deleted TIMESTAMP
);

CREATE TABLE "serial" (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  aliases text[],
  bph TEXT,
  call TEXT,
  tl2 TEXT,
  oclc INTEGER,
  bhl INTEGER,
  firstYear INTEGER,
  lastYear INTEGER,
  csl JSONB,
  remarks text
);

CREATE TABLE reference (
  key serial PRIMARY KEY,
  id TEXT,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  serial_key INTEGER REFERENCES "serial",
  csl JSONB,
  year int
);

CREATE TABLE name (
  key serial PRIMARY KEY,
  id TEXT,
  dataset_key INTEGER REFERENCES dataset,
  original_name_key INTEGER REFERENCES name,
  scientific_name text NOT NULL,
  rank rank,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  notho integer,
  original_authors TEXT[],
  original_year TEXT,
  combination_authors TEXT[],
  combination_year TEXT,
  nomenclatural_code INTEGER,
  status INTEGER,
  origin INTEGER,
  type INTEGER,
  fossil BOOLEAN,
  remarks TEXT,
  issues hstore
);

CREATE TABLE name_act (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  type INTEGER NOT NULL,
  name_key INTEGER NOT NULL REFERENCES name,
  related_name_key INTEGER NULL REFERENCES name,
  description TEXT,
  reference_key int REFERENCES reference,
  reference_page TEXT
);

CREATE TABLE taxon (
  key serial PRIMARY KEY,
  id TEXT,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  parent_key INTEGER REFERENCES taxon,
  name_key INTEGER NOT NULL REFERENCES name,
  status INTEGER,
  origin INTEGER,
  rank rank,
  according_to TEXT,
  according_to_date DATE,
  fossil BOOLEAN,
  recent BOOLEAN,
  lifezones INTEGER[],
  dataset_url TEXT,
  species_estimate INTEGER,
  species_estimate_reference_key INTEGER REFERENCES reference,
  remarks TEXT,
  issues hstore
);

CREATE TABLE synonyms (
  taxon_key INTEGER REFERENCES taxon,
  name_key INTEGER REFERENCES name,
  PRIMARY KEY(taxon_key, name_key)
);

CREATE TABLE verbatim_record (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  taxon_key INTEGER REFERENCES taxon,
  name_key INTEGER REFERENCES name,
  terms jsonb,
  PRIMARY KEY(dataset_key, id)
);

CREATE TABLE taxon_references (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  taxon_key INTEGER NOT NULL REFERENCES taxon,
  reference_key INTEGER NOT NULL REFERENCES reference,
  reference_page TEXT,
  PRIMARY KEY(taxon_key, reference_key)
);

CREATE TABLE vernacular_name (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  taxon_key INTEGER NOT NULL REFERENCES taxon,
  name TEXT NOT NULL,
  language CHAR(3),
  country CHAR(2)
);

CREATE TABLE vernacular_name_references (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  vernacular_name_key INTEGER NOT NULL REFERENCES vernacular_name,
  reference_key INTEGER NOT NULL REFERENCES reference,
  reference_page TEXT,
  PRIMARY KEY(vernacular_name_key, reference_key)
);

CREATE TABLE distribution (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  taxon_key INTEGER NOT NULL REFERENCES taxon,
  area TEXT,
  area_standard INTEGER,
  status INTEGER,
  reference_key INTEGER REFERENCES reference,
  reference_page TEXT
);

CREATE TABLE distribution_references (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  distribution_key INTEGER NOT NULL REFERENCES distribution,
  reference_key INTEGER NOT NULL REFERENCES reference,
  reference_page TEXT,
  PRIMARY KEY(distribution_key, reference_key)
);

