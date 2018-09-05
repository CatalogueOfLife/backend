
-- this will remove all existing tables
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

CREATE EXTENSION IF NOT EXISTS hstore;

CREATE EXTENSION IF NOT EXISTS unaccent;

-- use unaccent by default for all simple search
CREATE TEXT SEARCH CONFIGURATION public.simple2 ( COPY = pg_catalog.simple );
ALTER TEXT SEARCH CONFIGURATION simple2 ALTER MAPPING FOR hword, hword_part, word WITH unaccent;

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
  authors_and_editors TEXT[] DEFAULT '{}',
  license INTEGER,
  version TEXT,
  release_date DATE,
  homepage TEXT,
  data_format INTEGER,
  data_access TEXT,
  import_frequency INTEGER NOT NULL DEFAULT 7,
  code INTEGER,
  notes text,
  trusted BOOLEAN DEFAULT FALSE,
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
  download_uri TEXT,
  download TIMESTAMP WITHOUT TIME ZONE,
  verbatim_count INTEGER,
  name_count INTEGER,
  taxon_count INTEGER,
  reference_count INTEGER,
  vernacular_count INTEGER,
  distribution_count INTEGER,
  issues_count HSTORE,
  names_by_rank_count HSTORE,
  names_by_type_count HSTORE,
  vernaculars_by_language_count HSTORE,
  distributions_by_gazetteer_count HSTORE,
  names_by_origin_count HSTORE,
  usages_by_status_count HSTORE,
  names_by_status_count HSTORE,
  name_relations_by_type_count HSTORE,
  verbatim_by_type_count HSTORE,
  PRIMARY KEY (dataset_key, attempt)
);

CREATE TABLE verbatim (
  key serial,
  dataset_key INTEGER NOT NULL,
  line INTEGER,
  file TEXT,
  type TEXT,
  terms jsonb,
  issues INT[] DEFAULT '{}'
) PARTITION BY LIST (dataset_key);

CREATE TABLE reference (
  key serial NOT NULL,
  id TEXT,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  csl JSONB,
  citation TEXT,
  year int
) PARTITION BY LIST (dataset_key);

CREATE SEQUENCE name_key_seq;

CREATE TABLE name (
  key INTEGER DEFAULT nextval('name_key_seq') PRIMARY KEY,
  id TEXT,
  dataset_key INTEGER REFERENCES dataset,
  verbatim_key INTEGER,
  homotypic_name_key INTEGER REFERENCES name DEFAULT currval('name_key_seq'::regclass) NOT NULL ,
  index_name_key INTEGER REFERENCES name,
  scientific_name TEXT NOT NULL,
  rank rank NOT NULL,
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  strain TEXT,
  candidatus BOOLEAN DEFAULT FALSE,
  notho integer,
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  published_in_key int,
  published_in_page TEXT,
  code INTEGER,
  nom_status INTEGER,
  origin INTEGER NOT NULL,
  type INTEGER NOT NULL,
  source_url TEXT,
  fossil BOOLEAN,
  remarks TEXT,
  issues INT[] DEFAULT '{}',
  doc tsvector
);

CREATE INDEX ON name USING gin(doc);

CREATE OR REPLACE FUNCTION name_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.scientific_name, '')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(NEW.remarks, '')), 'D') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.combination_authors, '')), 'B') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.combination_ex_authors, '')), 'D') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.basionym_authors, '')), 'B') ||
      setweight(to_tsvector('simple2', array_to_string(NEW.basionym_ex_authors, '')), 'D');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER name_trigger BEFORE INSERT OR UPDATE
  ON name FOR EACH ROW EXECUTE PROCEDURE name_doc_update();

CREATE TABLE name_rel (
  key serial NOT NULL,
  verbatim_key INTEGER,
  dataset_key INTEGER NOT NULL,
  type INTEGER NOT NULL,
  name_key INTEGER NOT NULL,
  related_name_key INTEGER NULL,
  published_in_key int,
  note TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE taxon (
  key serial NOT NULL,
  id TEXT,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  parent_key INTEGER,
  name_key INTEGER NOT NULL,
  doubtful BOOLEAN DEFAULT FALSE NOT NULL,
  origin INTEGER NOT NULL,
  according_to TEXT,
  according_to_date DATE,
  fossil BOOLEAN,
  recent BOOLEAN,
  lifezones INTEGER[] DEFAULT '{}',
  dataset_url TEXT,
  species_estimate INTEGER,
  species_estimate_reference_key INTEGER,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE synonym (
  taxon_key INTEGER,
  name_key INTEGER,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  status INTEGER NOT NULL,
  according_to TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE taxon_reference (
  dataset_key INTEGER NOT NULL,
  taxon_key INTEGER NOT NULL,
  reference_key INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE vernacular_name (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_key INTEGER NOT NULL,
  name TEXT NOT NULL,
  latin TEXT,
  language CHAR(3),
  country CHAR(2)
) PARTITION BY LIST (dataset_key);

CREATE TABLE vernacular_name_reference (
  dataset_key INTEGER NOT NULL,
  vernacular_name_key INTEGER NOT NULL,
  reference_key INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE distribution (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_key INTEGER NOT NULL,
  area TEXT NOT NULL,
  gazetteer INTEGER NOT NULL,
  status INTEGER
) PARTITION BY LIST (dataset_key);

CREATE TABLE distribution_reference (
  dataset_key INTEGER NOT NULL,
  distribution_key INTEGER NOT NULL,
  reference_key INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);



-- FUNCTIONS
CREATE FUNCTION plaziGbifKey() RETURNS UUID AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$
LANGUAGE SQL
IMMUTABLE;


-- INDICES
CREATE index ON dataset (gbif_key);

CREATE index ON dataset_import (dataset_key, finished);
CREATE index ON dataset_import (started);

CREATE UNIQUE index ON name (id, dataset_key);
CREATE index ON name (rank);
CREATE index ON name (nom_status);
CREATE index ON name (type);
CREATE index ON name (homotypic_name_key);
CREATE index ON name (index_name_key);
CREATE index ON name (published_in_key);
CREATE index ON name (verbatim_key);

