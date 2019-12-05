
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


-- all enum types produces via PgSetupRuleTest.pgEnumSql()
CREATE TYPE AREASTANDARD AS ENUM (
  'TDWG',
  'ISO',
  'FAO',
  'FAO_FISHING',
  'LONGHURST',
  'TEOW',
  'IHO',
  'TEXT'
);

CREATE TYPE COLUSER_ROLE AS ENUM (
  'USER',
  'EDITOR',
  'ADMIN'
);

CREATE TYPE CONTINENT AS ENUM (
  'AFRICA',
  'ANTARCTICA',
  'ASIA',
  'OCEANIA',
  'EUROPE',
  'NORTH_AMERICA',
  'SOUTH_AMERICA'
);

CREATE TYPE DATAFORMAT AS ENUM (
  'DWCA',
  'ACEF',
  'TCS',
  'COLDP',
  'PROXY'
);

CREATE TYPE DATASETORIGIN AS ENUM (
  'EXTERNAL',
  'UPLOADED',
  'MANAGED'
);

CREATE TYPE DATASETTYPE AS ENUM (
  'NOMENCLATURAL',
  'TAXONOMIC',
  'ARTICLE',
  'PERSONAL',
  'OTU',
  'THEMATIC',
  'OTHER'
);

CREATE TYPE DESCRIPTIONCATEGORY AS ENUM (
  'BIOLOGY',
  'CONSERVATION',
  'HABITAT',
  'USE',
  'DISTRIBUTION',
  'DESCRIPTION',
  'ETYMOLOGY',
  'TREATMENT',
  'MISCELLANEOUS',
  'NOMENCLATURE',
  'STRATIGRAPHY',
  'TAXONOMY',
  'TYPIFICATION',
  'OTHER'
);

CREATE TYPE DISTRIBUTIONSTATUS AS ENUM (
  'NATIVE',
  'DOMESTICATED',
  'ALIEN',
  'UNCERTAIN'
);

CREATE TYPE EDITORIALDECISION_MODE AS ENUM (
  'BLOCK',
  'REVIEWED',
  'UPDATE',
  'UPDATE_RECURSIVE'
);

CREATE TYPE ENTITYTYPE AS ENUM (
  'ANY',
  'NAME',
  'NAME_RELATION',
  'NAME_USAGE',
  'DESCRIPTION',
  'DISTRIBUTION',
  'MEDIA',
  'VERNACULAR',
  'REFERENCE'
);

CREATE TYPE ESTIMATETYPE AS ENUM (
  'DESCRIBED_SPECIES_LIVING',
  'DESCRIBED_SPECIES_EXTINCT',
  'ESTIMATED_SPECIES'
);

CREATE TYPE GAZETTEER AS ENUM (
  'TDWG',
  'ISO',
  'FAO',
  'LONGHURST',
  'TEOW',
  'IHO',
  'TEXT'
);

CREATE TYPE IMPORTSTATE AS ENUM (
  'WAITING',
  'DOWNLOADING',
  'PROCESSING',
  'INSERTING',
  'INDEXING',
  'BUILDING_METRICS',
  'UNCHANGED',
  'FINISHED',
  'CANCELED',
  'FAILED'
);

CREATE TYPE ISSUE AS ENUM (
  'NOT_INTERPRETED',
  'ESCAPED_CHARACTERS',
  'REFERENCE_ID_INVALID',
  'ID_NOT_UNIQUE',
  'URL_INVALID',
  'PARTIAL_DATE',
  'UNPARSABLE_NAME',
  'PARTIALLY_PARSABLE_NAME',
  'UNPARSABLE_AUTHORSHIP',
  'DOUBTFUL_NAME',
  'INCONSISTENT_AUTHORSHIP',
  'INCONSISTENT_NAME',
  'PARSED_NAME_DIFFERS',
  'UNUSUAL_NAME_CHARACTERS',
  'MULTI_WORD_EPITHET',
  'UPPERCASE_EPITHET',
  'CONTAINS_REFERENCE',
  'NULL_EPITHET',
  'BLACKLISTED_EPITHET',
  'SUBSPECIES_ASSIGNED',
  'LC_MONOMIAL',
  'INDETERMINED',
  'HIGHER_RANK_BINOMIAL',
  'QUESTION_MARKS_REMOVED',
  'REPL_ENCLOSING_QUOTE',
  'MISSING_GENUS',
  'NOMENCLATURAL_STATUS_INVALID',
  'NOMENCLATURAL_CODE_INVALID',
  'TYPE_STATUS_INVALID',
  'BASIONYM_AUTHOR_MISMATCH',
  'BASIONYM_DERIVED',
  'CONFLICTING_BASIONYM_COMBINATION',
  'CHAINED_BASIONYM',
  'NAME_NOT_UNIQUE',
  'NAME_MATCH_INSERTED',
  'NAME_MATCH_VARIANT',
  'NAME_MATCH_AMBIGUOUS',
  'NAME_MATCH_NONE',
  'POTENTIAL_CHRESONYM',
  'PUBLISHED_BEFORE_GENUS',
  'BASIONYM_ID_INVALID',
  'RANK_INVALID',
  'UNMATCHED_NAME_BRACKETS',
  'TRUNCATED_NAME',
  'DUPLICATE_NAME',
  'NAME_VARIANT',
  'TAXON_VARIANT',
  'TAXON_ID_INVALID',
  'NAME_ID_INVALID',
  'PARENT_ID_INVALID',
  'ACCEPTED_ID_INVALID',
  'ACCEPTED_NAME_MISSING',
  'TAXONOMIC_STATUS_INVALID',
  'PROVISIONAL_STATUS_INVALID',
  'LIFEZONE_INVALID',
  'IS_EXTINCT_INVALID',
  'GEOTIME_INVALID',
  'ACCORDING_TO_DATE_INVALID',
  'CHAINED_SYNONYM',
  'PARENT_CYCLE',
  'SYNONYM_PARENT',
  'CLASSIFICATION_RANK_ORDER_INVALID',
  'CLASSIFICATION_NOT_APPLIED',
  'PARENT_NAME_MISMATCH',
  'DERIVED_TAXONOMIC_STATUS',
  'TAXONOMIC_STATUS_DOUBTFUL',
  'SYNONYM_DATA_MOVED',
  'SYNONYM_DATA_REMOVED',
  'REFTYPE_INVALID',
  'VERNACULAR_NAME_INVALID',
  'VERNACULAR_LANGUAGE_INVALID',
  'VERNACULAR_COUNTRY_INVALID',
  'VERNACULAR_NAME_TRANSLITERATED',
  'DISTRIBUTION_INVALID',
  'DISTRIBUTION_AREA_INVALID',
  'DISTRIBUTION_STATUS_INVALID',
  'DISTRIBUTION_GAZETEER_INVALID',
  'MEDIA_CREATED_DATE_INVALID',
  'UNPARSABLE_YEAR',
  'UNLIKELY_YEAR',
  'MULTIPLE_PUBLISHED_IN_REFERENCES',
  'UNPARSABLE_REFERENCE',
  'UNPARSABLE_REFERENCE_TYPE',
  'UNMATCHED_REFERENCE_BRACKETS',
  'CITATION_CONTAINER_TITLE_UNPARSED',
  'CITATION_DETAILS_UNPARSED',
  'CITATION_AUTHORS_UNPARSED',
  'CITATION_UNPARSED'
);

CREATE TYPE KINGDOM AS ENUM (
  'INCERTAE_SEDIS',
  'ANIMALIA',
  'ARCHAEA',
  'BACTERIA',
  'CHROMISTA',
  'FUNGI',
  'PLANTAE',
  'PROTOZOA',
  'VIRUSES'
);

CREATE TYPE LICENSE AS ENUM (
  'CC0',
  'CC_BY',
  'CC_BY_NC',
  'UNSPECIFIED',
  'OTHER'
);

CREATE TYPE LIFEZONE AS ENUM (
  'BRACKISH',
  'FRESHWATER',
  'MARINE',
  'TERRESTRIAL'
);

CREATE TYPE MATCHINGMODE AS ENUM (
  'STRICT',
  'FUZZY'
);

CREATE TYPE MATCHTYPE AS ENUM (
  'EXACT',
  'VARIANT',
  'INSERTED',
  'AMBIGUOUS',
  'NONE'
);

CREATE TYPE MEDIATYPE AS ENUM (
  'IMAGE',
  'VIDEO',
  'AUDIO'
);

CREATE TYPE NAMECATEGORY AS ENUM (
  'UNINOMIAL',
  'BINOMIAL',
  'TRINOMIAL'
);

CREATE TYPE NAMEFIELD AS ENUM (
  'UNINOMIAL',
  'GENUS',
  'INFRAGENERIC_EPITHET',
  'SPECIFIC_EPITHET',
  'INFRASPECIFIC_EPITHET',
  'CULTIVAR_EPITHET',
  'APPENDED_PHRASE',
  'CANDIDATUS',
  'NOTHO',
  'BASIONYM_AUTHORS',
  'BASIONYM_EX_AUTHORS',
  'BASIONYM_YEAR',
  'COMBINATION_AUTHORS',
  'COMBINATION_EX_AUTHORS',
  'COMBINATION_YEAR',
  'SANCTIONING_AUTHOR',
  'NOM_STATUS',
  'PUBLISHED_IN_ID',
  'PUBLISHED_IN_PAGE',
  'WEBPAGE',
  'REMARKS'
);

CREATE TYPE NAMEPART AS ENUM (
  'GENERIC',
  'INFRAGENERIC',
  'SPECIFIC',
  'INFRASPECIFIC'
);

CREATE TYPE NAMETYPE AS ENUM (
  'SCIENTIFIC',
  'VIRUS',
  'HYBRID_FORMULA',
  'INFORMAL',
  'OTU',
  'PLACEHOLDER',
  'NO_NAME'
);

CREATE TYPE NOMCODE AS ENUM (
  'BACTERIAL',
  'BOTANICAL',
  'CULTIVARS',
  'VIRUS',
  'ZOOLOGICAL'
);

CREATE TYPE NOMRELTYPE AS ENUM (
  'SPELLING_CORRECTION',
  'BASIONYM',
  'BASED_ON',
  'REPLACEMENT_NAME',
  'CONSERVED',
  'LATER_HOMONYM',
  'SUPERFLUOUS',
  'HOMOTYPIC',
  'TYPE'
);

CREATE TYPE NOMSTATUS AS ENUM (
  'ESTABLISHED',
  'NOT_ESTABLISHED',
  'ACCEPTABLE',
  'UNACCEPTABLE',
  'CONSERVED',
  'REJECTED',
  'DOUBTFUL',
  'MANUSCRIPT',
  'CHRESONYM'
);

CREATE TYPE ORIGIN AS ENUM (
  'SOURCE',
  'DENORMED_CLASSIFICATION',
  'VERBATIM_PARENT',
  'VERBATIM_ACCEPTED',
  'VERBATIM_BASIONYM',
  'AUTONYM',
  'IMPLICIT_NAME',
  'MISSING_ACCEPTED',
  'BASIONYM_PLACEHOLDER',
  'EX_AUTHOR_SYNONYM',
  'NAME_MATCHING',
  'USER',
  'OTHER'
);

CREATE TYPE RANK AS ENUM (
  'DOMAIN',
  'REALM',
  'SUBREALM',
  'SUPERKINGDOM',
  'KINGDOM',
  'SUBKINGDOM',
  'INFRAKINGDOM',
  'SUPERPHYLUM',
  'PHYLUM',
  'SUBPHYLUM',
  'INFRAPHYLUM',
  'SUPERCLASS',
  'CLASS',
  'SUBCLASS',
  'INFRACLASS',
  'PARVCLASS',
  'SUPERLEGION',
  'LEGION',
  'SUBLEGION',
  'INFRALEGION',
  'SUPERCOHORT',
  'COHORT',
  'SUBCOHORT',
  'INFRACOHORT',
  'MAGNORDER',
  'GRANDORDER',
  'SUPERORDER',
  'ORDER',
  'SUBORDER',
  'INFRAORDER',
  'PARVORDER',
  'MEGAFAMILY',
  'GRANDFAMILY',
  'SUPERFAMILY',
  'EPIFAMILY',
  'FAMILY',
  'SUBFAMILY',
  'INFRAFAMILY',
  'SUPERTRIBE',
  'TRIBE',
  'SUBTRIBE',
  'INFRATRIBE',
  'SUPRAGENERIC_NAME',
  'GENUS',
  'SUBGENUS',
  'INFRAGENUS',
  'SUPERSECTION',
  'SECTION',
  'SUBSECTION',
  'SUPERSERIES',
  'SERIES',
  'SUBSERIES',
  'INFRAGENERIC_NAME',
  'SPECIES_AGGREGATE',
  'SPECIES',
  'INFRASPECIFIC_NAME',
  'GREX',
  'SUBSPECIES',
  'CULTIVAR_GROUP',
  'CONVARIETY',
  'INFRASUBSPECIFIC_NAME',
  'PROLES',
  'NATIO',
  'ABERRATION',
  'MORPH',
  'VARIETY',
  'SUBVARIETY',
  'FORM',
  'SUBFORM',
  'PATHOVAR',
  'BIOVAR',
  'CHEMOVAR',
  'MORPHOVAR',
  'PHAGOVAR',
  'SEROVAR',
  'CHEMOFORM',
  'FORMA_SPECIALIS',
  'CULTIVAR',
  'STRAIN',
  'OTHER',
  'UNRANKED'
);

CREATE TYPE SECTORIMPORT_STATE AS ENUM (
  'WAITING',
  'PREPARING',
  'COPYING',
  'DELETING',
  'RELINKING',
  'INDEXING',
  'FINISHED',
  'CANCELED',
  'FAILED'
);

CREATE TYPE SECTOR_MODE AS ENUM (
  'ATTACH',
  'UNION',
  'MERGE'
);

CREATE TYPE TAXONOMICSTATUS AS ENUM (
  'ACCEPTED',
  'PROVISIONALLY_ACCEPTED',
  'SYNONYM',
  'AMBIGUOUS_SYNONYM',
  'MISAPPLIED'
);

CREATE TYPE TEXTFORMAT AS ENUM (
  'HTML',
  'MARKDOWN',
  'PLAIN_TEXT'
);

CREATE TYPE TYPESTATUS AS ENUM (
  'ALLOLECTOTYPE',
  'ALLONEOTYPE',
  'ALLOTYPE',
  'COTYPE',
  'EPITYPE',
  'ERGATOTYPE',
  'EXEPITYPE',
  'EXHOLOTYPE',
  'EXISOTYPE',
  'EXLECTOTYPE',
  'EXNEOTYPE',
  'EXPARATYPE',
  'EXSYNTYPE',
  'EXTYPE',
  'HAPANTOTYPE',
  'HOLOTYPE',
  'ICONOTYPE',
  'ISOLECTOTYPE',
  'ISONEOTYPE',
  'ISOSYNTYPE',
  'ISOTYPE',
  'LECTOTYPE',
  'NEOTYPE',
  'PARALECTOTYPE',
  'PARANEOTYPE',
  'PARATYPE',
  'PLASTOHOLOTYPE',
  'PLASTOISOTYPE',
  'PLASTOLECTOTYPE',
  'PLASTONEOTYPE',
  'PLASTOPARATYPE',
  'PLASTOSYNTYPE',
  'PLASTOTYPE',
  'SYNTYPE',
  'TOPOTYPE',
  'OTHER'
);


-- a simple compound type corresponding to the basics of SimpleName. Often used for building classifications as arrays
CREATE TYPE simple_name AS (id text, rank rank, name text);


CREATE TABLE coluser (
  key serial PRIMARY KEY,
  last_login TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  deleted TIMESTAMP WITHOUT TIME ZONE,
  username TEXT UNIQUE,
  firstname TEXT,
  lastname TEXT,
  email TEXT,
  orcid TEXT,
  country TEXT,
  roles COLUSER_ROLE[],
  settings HSTORE
);


CREATE TABLE dataset (
  key serial PRIMARY KEY,
  type DATASETTYPE NOT NULL DEFAULT 'OTHER',
  gbif_key UUID,
  gbif_publisher_key UUID,
  license LICENSE,
  released DATE,
  data_format DATAFORMAT,
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  origin DATASETORIGIN NOT NULL,
  import_frequency INTEGER NOT NULL DEFAULT 7,
  code NOMCODE,
  last_data_import_attempt INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  deleted TIMESTAMP WITHOUT TIME ZONE,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  locked BOOLEAN DEFAULT FALSE,
  title TEXT NOT NULL,
  alias TEXT,
  description TEXT,
  organisations TEXT[] DEFAULT '{}',
  contact TEXT,
  authors_and_editors TEXT[] DEFAULT '{}',
  version TEXT,
  citation TEXT,
  geographic_scope TEXT,
  website TEXT,
  logo TEXT,
  data_access TEXT,
  "group" TEXT,
  notes text,
  doc tsvector
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
  state IMPORTSTATE NOT NULL,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
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
  error TEXT,
  md5 TEXT,
  download_uri TEXT,
  PRIMARY KEY (dataset_key, attempt)
);

CREATE TABLE sector (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_rank RANK,
  subject_code NOMCODE,
  subject_status TAXONOMICSTATUS,
  target_rank RANK,
  target_code NOMCODE,
  mode SECTOR_MODE NOT NULL,
  code NOMCODE,
  last_data_import_attempt INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_parent TEXT,
  target_id TEXT,
  target_name TEXT,
  target_authorship TEXT,
  note TEXT,
  UNIQUE (dataset_key, subject_dataset_key, subject_id)
);

CREATE TABLE sector_import (
  sector_key INTEGER NOT NULL REFERENCES sector,
  attempt INTEGER NOT NULL,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  state SECTORIMPORT_STATE NOT NULL,
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
  type TEXT NOT NULL,
  warnings TEXT[],
  error TEXT,
  PRIMARY KEY (sector_key, attempt)
);

CREATE TABLE decision (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_rank rank,
  subject_code NOMCODE,
  subject_status TAXONOMICSTATUS,
  mode EDITORIALDECISION_MODE NOT NULL,
  status TAXONOMICSTATUS,
  extinct BOOLEAN,
  lifezones LIFEZONE[] DEFAULT '{}',
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_parent TEXT,
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  name JSONB,
  note TEXT,
  UNIQUE (dataset_key, subject_dataset_key, subject_id)
);

CREATE TABLE estimate (
  key serial PRIMARY KEY,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  target_rank RANK,
  target_code NOMCODE,
  estimate INTEGER,
  type ESTIMATETYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  target_id TEXT,
  target_name TEXT NOT NULL,
  target_authorship TEXT,
  reference_id TEXT,
  note TEXT
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
  issues ISSUE[] DEFAULT '{}',
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
  year INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  csl JSONB,
  citation TEXT,
  doc tsvector
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
  candidatus BOOLEAN DEFAULT FALSE,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  name_index_match_type MATCHTYPE,
  rank RANK NOT NULL,
  notho NAMEPART,
  code NOMCODE,
  nom_status NOMSTATUS,
  type_status TYPESTATUS,
  origin ORIGIN NOT NULL,
  type NAMETYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  homotypic_name_id TEXT NOT NULL,
  name_index_id TEXT,
  scientific_name TEXT NOT NULL,
  scientific_name_normalized TEXT NOT NULL,
  authorship TEXT,
  authorship_normalized TEXT[],
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  appended_phrase TEXT,
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  published_in_id TEXT,
  published_in_page TEXT,
  type_material TEXT,
  type_reference_id TEXT,
  webpage TEXT,
  remarks TEXT
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
  type NOMRELTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  name_id TEXT NOT NULL,
  related_name_id TEXT NULL,
  published_in_id TEXT,
  note TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE name_usage (
  id TEXT NOT NULL,
  is_synonym BOOLEAN NOT NULL,
  extinct BOOLEAN,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  status TAXONOMICSTATUS NOT NULL,
  origin ORIGIN NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  parent_id TEXT,
  name_id TEXT NOT NULL,
  according_to TEXT,
  according_to_date TEXT,
  reference_ids TEXT[] DEFAULT '{}',
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  lifezones LIFEZONE[] DEFAULT '{}',
  webpage TEXT,
  remarks TEXT,
  dataset_sectors JSONB
) PARTITION BY LIST (dataset_key);


CREATE TABLE vernacular_name (
  id serial,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  language CHAR(3),
  country CHAR(2),
  taxon_id TEXT NOT NULL,
  name TEXT NOT NULL,
  latin TEXT,
  area TEXT,
  reference_id TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE distribution (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  gazetteer GAZETTEER NOT NULL,
  status DISTRIBUTIONSTATUS,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  area TEXT NOT NULL,
  reference_id TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE description (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  format TEXTFORMAT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  language CHAR(3),
  taxon_id TEXT NOT NULL,
  category TEXT,
  description TEXT NOT NULL,
  reference_id TEXT
) PARTITION BY LIST (dataset_key);

CREATE TABLE media (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  verbatim_key INTEGER,
  type MEDIATYPE,
  captured DATE,
  license LICENSE,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  url TEXT,
  format TEXT,
  title TEXT,
  captured_by TEXT,
  link TEXT,
  reference_id TEXT
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
CREATE index ON estimate (dataset_key, target_id);
CREATE index ON decision (dataset_key);
CREATE index ON decision (dataset_key, subject_dataset_key, subject_id);


-- useful views
CREATE VIEW table_size AS (
    SELECT *, pg_size_pretty(total_bytes) AS total
        , pg_size_pretty(index_bytes) AS INDEX
        , pg_size_pretty(toast_bytes) AS toast
        , pg_size_pretty(table_bytes) AS TABLE
      FROM (
      SELECT *, total_bytes-index_bytes-COALESCE(toast_bytes,0) AS table_bytes FROM (
          SELECT c.oid,nspname AS table_schema, relname AS TABLE_NAME
                  , c.reltuples AS row_estimate
                  , pg_total_relation_size(c.oid) AS total_bytes
                  , pg_indexes_size(c.oid) AS index_bytes
                  , pg_total_relation_size(reltoastrelid) AS toast_bytes
              FROM pg_class c
              LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE relkind = 'r' AND nspname='public'
      ) a
    ) a
);
