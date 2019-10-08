
-- required extensions
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- use unaccent by default for all simple search
CREATE TEXT SEARCH CONFIGURATION public.simple2 ( COPY = pg_catalog.simple );
ALTER TEXT SEARCH CONFIGURATION simple2 ALTER MAPPING FOR hword, hword_part, word WITH unaccent;

-- immutable unaccent function to be used in indexes
-- see https://stackoverflow.com/questions/11005036/does-postgresql-support-accent-insensitive-collations
CREATE OR REPLACE FUNCTION f_unaccent(text)
  RETURNS text AS
$func$
SELECT public.unaccent('public.unaccent', $1)  -- schema-qualify function and dictionary
$func$  LANGUAGE sql IMMUTABLE;

CREATE TYPE rank AS ENUM (
  'domain',
  'realm',
  'subrealm',
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
  'grandorder',
  'superorder',
  'order',
  'suborder',
  'infraorder',
  'parvorder',
  'megafamily',
  'grandfamily',
  'superfamily',
  'epifamily',
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

-- a simple compound type corresponding to the basics of SimpleName. Often used for building classifications as arrays
CREATE TYPE simple_name AS (id text, rank rank, name text);


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
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  origin INTEGER NOT NULL,
  import_frequency INTEGER NOT NULL DEFAULT 7,
  code INTEGER,
  notes text,
  last_data_import_attempt INTEGER,
  deleted TIMESTAMP WITHOUT TIME ZONE,
  doc tsvector,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
);

CREATE TABLE dataset_archive (LIKE dataset);
ALTER TABLE dataset_archive DROP COLUMN doc;
ALTER TABLE dataset_archive ADD COLUMN catalogue_key INTEGER NOT NULL REFERENCES dataset;

CREATE INDEX ON dataset USING gin (f_unaccent(title) gin_trgm_ops);
CREATE INDEX ON dataset USING gin (f_unaccent(alias) gin_trgm_ops);
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
  synonym_count INTEGER,
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
  verbatim_by_term_count JSONB,
  media_by_type_count HSTORE,
  PRIMARY KEY (dataset_key, attempt)
);

CREATE TABLE sector (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_rank rank,
  subject_code INTEGER,
  subject_status INTEGER,
  subject_parent TEXT,
  target_id TEXT,
  target_name TEXT,
  target_authorship TEXT,
  target_rank rank,
  target_code INTEGER,
  mode INTEGER NOT NULL,
  code INTEGER,
  note TEXT,
  last_data_import_attempt INTEGER,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL,
  UNIQUE (dataset_key, subject_dataset_key, subject_id)
);

CREATE TABLE sector_import (
  sector_key INTEGER NOT NULL REFERENCES sector,
  attempt INTEGER NOT NULL,
  type TEXT NOT NULL,
  state INTEGER NOT NULL,
  error TEXT,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  name_count INTEGER,
  taxon_count INTEGER,
  synonym_count INTEGER,
  reference_count INTEGER,
  vernacular_count INTEGER,
  distribution_count INTEGER,
  description_count INTEGER,
  media_count INTEGER,
  ignored_usage_count INTEGER,
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
  warnings TEXT[],
  PRIMARY KEY (sector_key, attempt)
);

CREATE TABLE decision (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_rank rank,
  subject_code INTEGER,
  subject_status INTEGER,
  subject_parent TEXT,
  mode INTEGER NOT NULL,
  status INTEGER,
  name JSONB,
  extinct BOOLEAN,
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  lifezones INTEGER[] DEFAULT '{}',
  note TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL,
  UNIQUE (dataset_key, subject_dataset_key, subject_id)
);

CREATE TABLE estimate (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_id TEXT,
  subject_name TEXT NOT NULL,
  subject_authorship TEXT,
  subject_rank rank,
  subject_code INTEGER,
  estimate INTEGER,
  type INTEGER NOT NULL,
  reference_id TEXT,
  note TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
);
--
-- PARTITIONED DATA TABLES
--

CREATE TABLE verbatim (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  line INTEGER,
  file TEXT,
  type TEXT,
  terms jsonb,
  issues INT[] DEFAULT '{}',
  doc tsvector
) PARTITION BY LIST (dataset_key);

CREATE OR REPLACE FUNCTION verbatim_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc := jsonb_to_tsvector('simple2', coalesce(NEW.terms,'{}'::jsonb), '["string", "numeric"]');
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TABLE reference (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  csl JSONB,
  citation TEXT,
  year int,
  doc tsvector,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE OR REPLACE FUNCTION reference_doc_update() RETURNS trigger AS $$
BEGIN
    NEW.doc :=
      jsonb_to_tsvector('simple2', coalesce(NEW.csl,'{}'::jsonb), '["string", "numeric"]') ||
      to_tsvector('simple2', coalesce(NEW.citation,'')) ||
      to_tsvector('simple2', coalesce(NEW.year::text,''));
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TABLE name (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  homotypic_name_id TEXT NOT NULL,
  name_index_id TEXT,
  scientific_name TEXT NOT NULL,
  scientific_name_normalized TEXT NOT NULL,
  authorship TEXT,
  authorship_normalized TEXT[],
  rank rank NOT NULL,
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  appended_phrase TEXT,
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


CREATE OR REPLACE FUNCTION homotypic_name_id_default() RETURNS trigger AS $$
BEGIN
    NEW.homotypic_name_id := NEW.id;
    RETURN NEW;
END
$$
LANGUAGE plpgsql;



CREATE TABLE name_rel (
  id INTEGER NOT NULL,
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

CREATE TABLE name_usage (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  parent_id TEXT,
  name_id TEXT NOT NULL,
  status INTEGER NOT NULL,
  is_synonym BOOLEAN NOT NULL,
  origin INTEGER NOT NULL,
  according_to TEXT,
  according_to_date DATE,
  reference_ids TEXT[] DEFAULT '{}',
  extinct BOOLEAN,
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  lifezones INTEGER[] DEFAULT '{}',
  webpage TEXT,
  remarks TEXT,
  dataset_sectors JSONB,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);


CREATE TABLE vernacular_name (
  id serial,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  name TEXT NOT NULL,
  latin TEXT,
  language CHAR(3),
  country CHAR(2),
  area TEXT,
  reference_id TEXT,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created_by INTEGER NOT NULL,
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified_by INTEGER NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE TABLE distribution (
  id INTEGER NOT NULL,
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
  id INTEGER NOT NULL,
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
  id INTEGER NOT NULL,
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


-- tries to gracely convert text to ints, swallowing exceptions and using null instead
CREATE OR REPLACE FUNCTION parseInt(v_value text) RETURNS INTEGER AS $$
DECLARE v_int_value INTEGER DEFAULT NULL;
BEGIN
    IF v_value IS NOT NULL THEN
        RAISE NOTICE 'Parse: "%"', v_value;
        BEGIN
            v_int_value := v_value::INTEGER;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Invalid integer value: "%".  Returning NULL.', v_value;
            BEGIN
                v_int_value := substring(v_value, 1, 4)::INTEGER;
            EXCEPTION WHEN OTHERS THEN
                RETURN NULL;
            END;
        END;
    END IF;
    RETURN v_int_value;
END;
$$ LANGUAGE plpgsql;

-- array_agg alternative that ignores null values
CREATE OR REPLACE FUNCTION fn_array_agg_nonull (
    a anyarray
    , b anyelement
) RETURNS ANYARRAY
AS $$
BEGIN
    IF b IS NOT NULL THEN
        a := array_append(a, b);
    END IF;
    RETURN a;
END;
$$ IMMUTABLE LANGUAGE 'plpgsql';

CREATE AGGREGATE array_agg_nonull(ANYELEMENT) (
    SFUNC = fn_array_agg_nonull,
    STYPE = ANYARRAY,
    INITCOND = '{}'
);

CREATE OR REPLACE FUNCTION array_reverse(anyarray) RETURNS anyarray AS $$
SELECT ARRAY(
    SELECT $1[i]
    FROM generate_subscripts($1,1) AS s(i)
    ORDER BY i DESC
);
$$ LANGUAGE 'sql' STRICT IMMUTABLE;


-- return all parent names as an array
CREATE OR REPLACE FUNCTION classification(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN) RETURNS TEXT[] AS $$
	declare seql TEXT;
	declare parents TEXT[];
BEGIN
    seql := 'WITH RECURSIVE x AS ('
        || 'SELECT t.id, n.scientific_name, t.parent_id FROM name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = $1'
        || ' UNION ALL '
        || 'SELECT t.id, n.scientific_name, t.parent_id FROM x, name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = x.parent_id'
        || ') SELECT array_agg(scientific_name) FROM x';

    IF NOT v_inc_self THEN
        seql := seql || ' WHERE id != $1';
    END IF;

    EXECUTE seql
    INTO parents
    USING v_id;
    RETURN (array_reverse(parents));
END;
$$ LANGUAGE plpgsql;


-- return all parent name usages as a simple_name array
CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN) RETURNS simple_name[] AS $$
	declare seql TEXT;
	declare parents simple_name[];
BEGIN
    seql := 'WITH RECURSIVE x AS ('
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name)::simple_name AS sn FROM name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = $1'
        || ' UNION ALL '
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name)::simple_name FROM x, name_usage_' || v_dataset_key || ' t '
        || '  JOIN name_' || v_dataset_key || ' n ON n.id=t.name_id WHERE t.id = x.parent_id'
        || ') SELECT array_agg(sn) FROM x';

    IF NOT v_inc_self THEN
        seql := seql || ' WHERE id != $1';
    END IF;

    EXECUTE seql
    INTO parents
    USING v_id;
    RETURN (array_reverse(parents));
END;
$$ LANGUAGE plpgsql;


-- INDICES for non partitioned tables
CREATE index ON dataset (gbif_key);
CREATE index ON dataset_import (started);
CREATE index ON dataset_import (dataset_key);
CREATE index ON sector_import (sector_key);
CREATE index ON sector (target_id);
CREATE index ON sector (dataset_key);
CREATE index ON sector (dataset_key, subject_dataset_key, subject_id);
CREATE index ON estimate (dataset_key);
CREATE index ON estimate (dataset_key, subject_id);
CREATE index ON decision (dataset_key);
CREATE index ON decision (dataset_key, subject_dataset_key, subject_id);
