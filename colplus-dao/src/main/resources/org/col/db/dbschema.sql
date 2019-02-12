
-- required extensions
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

CREATE TABLE coluser (
  key serial PRIMARY KEY,
  username TEXT UNIQUE,
  firstname TEXT,
  lastname TEXT,
  email TEXT,
  orcid TEXT,
  country TEXT,
  roles int[],
  settings HSTORE,
  last_login TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  deleted TIMESTAMP WITHOUT TIME ZONE
);


CREATE TABLE dataset (
  key serial PRIMARY KEY,
  type INTEGER NOT NULL DEFAULT 4,
  title TEXT NOT NULL,
  alias TEXT,
  gbif_key UUID,
  gbif_publisher_key UUID,
  description TEXT,
  organisations TEXT[] DEFAULT '{}',
  contact TEXT,
  authors_and_editors TEXT[] DEFAULT '{}',
  license INTEGER,
  version TEXT,
  released DATE,
  citation TEXT,
  website TEXT,
  logo TEXT,
  data_format INTEGER,
  data_access TEXT,
  "group" TEXT,
  coverage INTEGER,
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  origin INTEGER NOT NULL,
  import_frequency INTEGER NOT NULL DEFAULT 7,
  code INTEGER,
  notes text,
  contributes_to INTEGER,
  last_data_import_attempt INTEGER,
  deleted TIMESTAMP WITHOUT TIME ZONE,
  doc tsvector,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
);

CREATE INDEX ON dataset USING gin(doc);

CREATE OR REPLACE FUNCTION dataset_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      setweight(to_tsvector('simple2', coalesce(NEW.title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.organisations, '|'), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(NEW.description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.contact,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(array_to_string(NEW.authors_and_editors, '|'), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(NEW.gbif_key::text,'')), 'C');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TRIGGER dataset_trigger BEFORE INSERT OR UPDATE
  ON dataset FOR EACH ROW EXECUTE PROCEDURE dataset_doc_update();


CREATE TABLE sector (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_rank rank,
  target_id TEXT,
  target_name TEXT,
  target_authorship TEXT,
  target_rank rank,
  mode INTEGER NOT NULL,
  note TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL,
  UNIQUE (dataset_key, subject_id)
);

CREATE TABLE decision (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_rank rank,
  mode INTEGER NOT NULL,
  status INTEGER,
  name JSONB,
  fossil BOOLEAN,
  recent BOOLEAN,
  lifezones INTEGER[] DEFAULT '{}',
  note TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL,
  UNIQUE (dataset_key, subject_id)
);

CREATE TABLE dataset_import (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  attempt INTEGER NOT NULL,
  state INTEGER NOT NULL,
  error TEXT,
  md5 TEXT,
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
  description_count INTEGER,
  media_count INTEGER,
  issues_count HSTORE,
  names_by_rank_count HSTORE,
  taxa_by_rank_count HSTORE,
  names_by_type_count HSTORE,
  vernaculars_by_language_count HSTORE,
  distributions_by_gazetteer_count HSTORE,
  names_by_origin_count HSTORE,
  usages_by_status_count HSTORE,
  names_by_status_count HSTORE,
  name_relations_by_type_count HSTORE,
  verbatim_by_type_count HSTORE,
  media_by_type_count HSTORE,
  PRIMARY KEY (dataset_key, attempt)
);


--
-- PARTITIONED DATA TABLES
--

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
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  csl JSONB,
  citation TEXT,
  year int,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);


CREATE TABLE name (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  homotypic_name_id TEXT NOT NULL,
  index_name_id TEXT,
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
  published_in_id TEXT,
  published_in_page TEXT,
  code INTEGER,
  nom_status INTEGER,
  origin INTEGER NOT NULL,
  type INTEGER NOT NULL,
  webpage TEXT,
  fossil BOOLEAN,
  remarks TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE name_rel (
  key serial NOT NULL,
  verbatim_key INTEGER,
  dataset_key INTEGER NOT NULL,
  type INTEGER NOT NULL,
  name_id TEXT NOT NULL,
  related_name_id TEXT NULL,
  published_in_id TEXT,
  note TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE taxon (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  parent_id TEXT,
  name_id TEXT NOT NULL,
  provisional BOOLEAN DEFAULT FALSE NOT NULL,
  origin INTEGER NOT NULL,
  according_to TEXT,
  according_to_date DATE,
  fossil BOOLEAN,
  recent BOOLEAN,
  lifezones INTEGER[] DEFAULT '{}',
  webpage TEXT,
  species_estimate INTEGER,
  species_estimate_reference_id TEXT,
  remarks TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE synonym (
  id TEXT,
  taxon_id TEXT NOT NULL,
  name_id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  status INTEGER NOT NULL,
  according_to TEXT,
  origin INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE taxon_reference (
  dataset_key INTEGER NOT NULL,
  taxon_id TEXT NOT NULL,
  reference_id TEXT NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE vernacular_name (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  name TEXT NOT NULL,
  latin TEXT,
  language CHAR(3),
  country CHAR(2),
  reference_id TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE distribution (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  area TEXT NOT NULL,
  gazetteer INTEGER NOT NULL,
  status INTEGER,
  reference_id TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE description (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  category TEXT,
  description TEXT NOT NULL,
  language CHAR(3),
  reference_id TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE media (
  key serial NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  url TEXT,
  type INTEGER,
  format TEXT,
  title TEXT,
  captured DATE,
  captured_by TEXT,
  license INTEGER,
  link TEXT,
  reference_id TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);



-- FUNCTIONS
CREATE FUNCTION plaziGbifKey() RETURNS UUID AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$
LANGUAGE SQL
IMMUTABLE;


-- INDICES for non partitioned tables
CREATE index ON dataset (gbif_key);
CREATE index ON dataset_import (started);
CREATE index ON decision (subject_id);
CREATE index ON sector (subject_id);
CREATE index ON sector (target_id);
