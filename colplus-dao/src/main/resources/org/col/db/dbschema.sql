
-- this will remove all existing tables
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

CREATE EXTENSION IF NOT EXISTS hstore;

CREATE EXTENSION IF NOT EXISTS unaccent;

-- use unaccent by default for all simple search
CREATE TEXT SEARCH CONFIGURATION public.simple2 ( COPY = pg_catalog.simple );
ALTER TEXT SEARCH CONFIGURATION simple2 ALTER MAPPING
FOR hword, hword_part, word WITH unaccent;

CREATE TYPE rank AS ENUM (
  'domain',
  'superkingdom',
  'kingdom',
  'subkingdom',
  'infrakingdom',
  'superphylum',
  'phylum',
  'subphylum',
  'infraphylum',
  'superclass',
  'class',
  'subclass',
  'infraclass',
  'parvclass',
  'superlegion',
  'legion',
  'sublegion',
  'infralegion',
  'supercohort',
  'cohort',
  'subcohort',
  'infracohort',
  'magnorder',
  'superorder',
  'grandorder',
  'order',
  'suborder',
  'infraorder',
  'parvorder',
  'superfamily',
  'family',
  'subfamily',
  'infrafamily',
  'supertribe',
  'tribe',
  'subtribe',
  'infratribe',
  'suprageneric_name',
  'genus',
  'subgenus',
  'infragenus',
  'supersection',
  'section',
  'subsection',
  'superseries',
  'series',
  'subseries',
  'infrageneric_name',
  'species_aggregate',
  'species',
  'infraspecific_name',
  'grex',
  'subspecies',
  'cultivar_group',
  'convariety',
  'infrasubspecific_name',
  'proles',
  'natio',
  'aberration',
  'morph',
  'variety',
  'subvariety',
  'form',
  'subform',
  'pathovar',
  'biovar',
  'chemovar',
  'morphovar',
  'phagovar',
  'serovar',
  'chemoform',
  'forma_specialis',
  'cultivar',
  'strain',
  'other',
  'unranked'
);

CREATE TABLE dataset (
  key serial PRIMARY KEY,
  type INTEGER,
  title TEXT NOT NULL,
  gbif_key UUID,
  gbif_publisher_key UUID,
  description TEXT,
  organisation TEXT,
  contact_person TEXT,
  authors_and_editors TEXT[],
  license INTEGER,
  version TEXT,
  release_date DATE,
  homepage TEXT,
  data_format INTEGER,
  data_access TEXT,
  import_frequency INTEGER,
  notes text,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  deleted TIMESTAMP WITHOUT TIME ZONE,
  doc tsvector
);

CREATE INDEX ON dataset USING gin(doc);

CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.organisation,'')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.contact_person,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.authors_and_editors, '|'), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER dataset_trigger BEFORE INSERT OR UPDATE
  ON dataset FOR EACH ROW EXECUTE PROCEDURE dataset_doc_update();

CREATE TABLE dataset_import (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  attempt INTEGER NOT NULL,
  state INTEGER NOT NULL,
  error TEXT,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  download TIMESTAMP WITHOUT TIME ZONE,
  verbatim_count INTEGER,
  name_count INTEGER,
  taxon_count INTEGER,
  vernacular_count INTEGER,
  distribution_count INTEGER,
  issues_count HSTORE,
  names_by_rank_count HSTORE,
  names_by_type_count HSTORE,
  vernaculars_by_language_count HSTORE,
  distributions_by_gazetteer_count HSTORE,
  names_by_origin_count HSTORE,

  PRIMARY KEY (dataset_key, attempt)
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
  basionym_key INTEGER REFERENCES name,
  scientific_name TEXT NOT NULL,
  rank rank NOT NULL,
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  notho integer,
  basionym_authors TEXT[],
  basionym_ex_authors TEXT[],
  basionym_year TEXT,
  combination_authors TEXT[],
  combination_ex_authors TEXT[],
  combination_year TEXT,
  sanctioning_author TEXT,
  code INTEGER,
  type INTEGER NOT NULL,
  status INTEGER,
  origin INTEGER NOT NULL,
  fossil BOOLEAN,
  remarks TEXT,
  issues INT[],
  doc tsvector
);

CREATE INDEX ON name USING gin(doc);

CREATE OR REPLACE FUNCTION name_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.scientific_name,'')), 'A') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.combination_authors,'')), 'C') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.combination_ex_authors,'')), 'D') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.basionym_authors,'')), 'C') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.basionym_ex_authors,'')), 'D');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER name_trigger BEFORE INSERT OR UPDATE
  ON name FOR EACH ROW EXECUTE PROCEDURE name_doc_update();


CREATE TABLE name_act (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  type INTEGER NOT NULL,
  status INTEGER,
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
  status INTEGER NOT NULL,
  origin INTEGER NOT NULL,
  according_to TEXT,
  according_to_date DATE,
  fossil BOOLEAN,
  recent BOOLEAN,
  lifezones INTEGER[],
  dataset_url TEXT,
  species_estimate INTEGER,
  species_estimate_reference_key INTEGER REFERENCES reference,
  remarks TEXT,
  issues INT[]
);

CREATE TABLE synonym (
  taxon_key INTEGER REFERENCES taxon,
  name_key INTEGER REFERENCES name,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
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

CREATE TABLE taxon_reference (
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

CREATE TABLE vernacular_name_reference (
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
  area TEXT NOT NULL,
  area_standard INTEGER NOT NULL,
  status INTEGER
);

CREATE TABLE distribution_reference (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  distribution_key INTEGER NOT NULL REFERENCES distribution,
  reference_key INTEGER NOT NULL REFERENCES reference,
  reference_page TEXT,
  PRIMARY KEY(distribution_key, reference_key)
);



-- FUNCTIONS
CREATE FUNCTION plaziGbifKey() RETURNS UUID AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$
LANGUAGE SQL
IMMUTABLE;


-- INDICES
CREATE index ON dataset (gbif_key);