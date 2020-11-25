
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
$$
SELECT public.unaccent('public.unaccent', $1)  -- schema-qualify function and dictionary
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;


-- all enum types produces via PgSetupRuleTest.pgEnumSql()
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
  'TEXT_TREE',
  'COLDP',
  'PROXY'
);

CREATE TYPE DATASETORIGIN AS ENUM (
  'EXTERNAL',
  'MANAGED',
  'RELEASED'
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
  'TAXON_CONCEPT_RELATION',
  'TYPE_MATERIAL',
  'TREATMENT',
  'DISTRIBUTION',
  'MEDIA',
  'VERNACULAR',
  'REFERENCE',
  'ESTIMATE',
  'SPECIES_INTERACTION'
);

CREATE TYPE ENVIRONMENT AS ENUM (
  'BRACKISH',
  'FRESHWATER',
  'MARINE',
  'TERRESTRIAL'
);

CREATE TYPE ESTIMATETYPE AS ENUM (
  'SPECIES_LIVING',
  'SPECIES_EXTINCT',
  'ESTIMATED_SPECIES'
);

CREATE TYPE GAZETTEER AS ENUM (
  'TDWG',
  'ISO',
  'FAO',
  'LONGHURST',
  'TEOW',
  'IHO',
  'MRGID',
  'TEXT'
);

CREATE TYPE IMPORTSTATE AS ENUM (
  'WAITING',
  'PREPARING',
  'DOWNLOADING',
  'PROCESSING',
  'DELETING',
  'INSERTING',
  'MATCHING',
  'INDEXING',
  'ANALYZING',
  'ARCHIVING',
  'EXPORTING',
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
  'PREVIOUS_LINE_SKIPPED',
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
  'AUTHORSHIP_CONTAINS_NOMENCLATURAL_NOTE',
  'CONFLICTING_NOMENCLATURAL_STATUS',
  'NOMENCLATURAL_CODE_INVALID',
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
  'AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE',
  'TYPE_STATUS_INVALID',
  'LAT_LON_INVALID',
  'ALTITUDE_INVALID',
  'COUNTRY_INVALID',
  'TAXON_VARIANT',
  'TAXON_ID_INVALID',
  'NAME_ID_INVALID',
  'PARENT_ID_INVALID',
  'ACCEPTED_ID_INVALID',
  'ACCEPTED_NAME_MISSING',
  'TAXONOMIC_STATUS_INVALID',
  'PROVISIONAL_STATUS_INVALID',
  'ENVIRONMENT_INVALID',
  'IS_EXTINCT_INVALID',
  'NAME_CONTAINS_EXTINCT_SYMBOL',
  'GEOTIME_INVALID',
  'SCRUTINIZER_DATE_INVALID',
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
  'ACCORDING_TO_CONFLICT',
  'VERNACULAR_NAME_INVALID',
  'VERNACULAR_LANGUAGE_INVALID',
  'VERNACULAR_SEX_INVALID',
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
  'CITATION_UNPARSED',
  'UNPARSABLE_TREATMENT',
  'UNPARSABLE_TREAMENT_FORMAT',
  'ESTIMATE_INVALID',
  'ESTIMATE_TYPE_INVALID'
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

CREATE TYPE MATCHINGMODE AS ENUM (
  'STRICT',
  'FUZZY'
);

CREATE TYPE MATCHTYPE AS ENUM (
  'EXACT',
  'VARIANT',
  'CANONICAL',
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
  'CANDIDATUS',
  'NOTHO',
  'BASIONYM_AUTHORS',
  'BASIONYM_EX_AUTHORS',
  'BASIONYM_YEAR',
  'COMBINATION_AUTHORS',
  'COMBINATION_EX_AUTHORS',
  'COMBINATION_YEAR',
  'SANCTIONING_AUTHOR',
  'CODE',
  'NOM_STATUS',
  'PUBLISHED_IN',
  'PUBLISHED_IN_PAGE',
  'NOMENCLATURAL_NOTE',
  'UNPARSED',
  'REMARKS',
  'NAME_PHRASE',
  'ACCORDING_TO'
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
  'PHYTOSOCIOLOGICAL',
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
  'SUPERDIVISION',
  'DIVISION',
  'SUBDIVISION',
  'INFRADIVISION',
  'SUPERLEGION',
  'LEGION',
  'SUBLEGION',
  'INFRALEGION',
  'SUPERCOHORT',
  'COHORT',
  'SUBCOHORT',
  'INFRACOHORT',
  'GIGAORDER',
  'MAGNORDER',
  'GRANDORDER',
  'MIRORDER',
  'SUPERORDER',
  'ORDER',
  'NANORDER',
  'HYPOORDER',
  'MINORDER',
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

CREATE TYPE SECTOR_MODE AS ENUM (
  'ATTACH',
  'UNION',
  'MERGE'
);

CREATE TYPE SEX AS ENUM (
  'FEMALE',
  'MALE',
  'HERMAPHRODITE'
);

CREATE TYPE SPECIESINTERACTIONTYPE AS ENUM (
  'RELATED_TO',
  'CO_OCCURS_WITH',
  'INTERACTS_WITH',
  'ADJACENT_TO',
  'SYMBIONT_OF',
  'EATS',
  'EATEN_BY',
  'KILLS',
  'KILLED_BY',
  'PREYS_UPON',
  'PREYED_UPON_BY',
  'HOST_OF',
  'HAS_HOST',
  'PARASITE_OF',
  'HAS_PARASITE',
  'PATHOGEN_OF',
  'HAS_PATHOGEN',
  'VECTOR_OF',
  'HAS_VECTOR',
  'ENDOPARASITE_OF',
  'HAS_ENDOPARASITE',
  'ECTOPARASITE_OF',
  'HAS_ECTOPARASITE',
  'HYPERPARASITE_OF',
  'HAS_HYPERPARASITE',
  'KLEPTOPARASITE_OF',
  'HAS_KLEPTOPARASITE',
  'PARASITOID_OF',
  'HAS_PARASITOID',
  'HYPERPARASITOID_OF',
  'HAS_HYPERPARASITOID',
  'VISITS',
  'VISITED_BY',
  'VISITS_FLOWERS_OF',
  'FLOWERS_VISITED_BY',
  'POLLINATES',
  'POLLINATED_BY',
  'LAYS_EGGS_ON',
  'HAS_EGGS_LAYED_ON_BY',
  'EPIPHYTE_OF',
  'HAS_EPIPHYTE',
  'COMMENSALIST_OF',
  'MUTUALIST_OF'
);

CREATE TYPE TAXONCONCEPTRELTYPE AS ENUM (
  'EQUALS',
  'INCLUDES',
  'INCLUDED_IN',
  'OVERLAPS',
  'EXCLUDES'
);

CREATE TYPE TAXONOMICSTATUS AS ENUM (
  'ACCEPTED',
  'PROVISIONALLY_ACCEPTED',
  'SYNONYM',
  'AMBIGUOUS_SYNONYM',
  'MISAPPLIED',
  'BARE_NAME'
);

CREATE TYPE TREATMENTFORMAT AS ENUM (
  'PLAIN_TEXT',
  'MARKDOWN',
  'XML',
  'HTML',
  'TAX_PUB',
  'TAXON_X',
  'RDF'
);

CREATE TYPE TYPESTATUS AS ENUM (
  'EPITYPE',
  'ERGATOTYPE',
  'EX_TYPE',
  'HAPANTOTYPE',
  'HOLOTYPE',
  'ICONOTYPE',
  'LECTOTYPE',
  'NEOTYPE',
  'ORIGINAL_MATERIAL',
  'PARATYPE',
  'PATHOTYPE',
  'SYNTYPE',
  'TOPOTYPE',
  'ISOTYPE',
  'ISOEPITYPE',
  'ISOLECTOTYPE',
  'ISONEOTYPE',
  'ISOPARATYPE',
  'ISOSYNTYPE',
  'PARALECTOTYPE',
  'PARANEOTYPE',
  'ALLOLECTOTYPE',
  'ALLONEOTYPE',
  'ALLOTYPE',
  'PLASTOHOLOTYPE',
  'PLASTOISOTYPE',
  'PLASTOLECTOTYPE',
  'PLASTONEOTYPE',
  'PLASTOPARATYPE',
  'PLASTOSYNTYPE',
  'PLASTOTYPE',
  'OTHER'
);

CREATE TYPE USER_ROLE AS ENUM (
  'EDITOR',
  'ADMIN'
);


-- a simple compound type corresponding to the basics of SimpleName. Often used for building classifications as arrays
CREATE TYPE simple_name AS (id text, rank rank, name text);

-- Person type for dataset to avoid extra tables
CREATE TYPE person AS (given text, family text, email text, orcid text);

-- Organisation type for dataset to avoid extra tables
CREATE TYPE organisation AS (name text, department text, city text, state text, country CHAR(2));

-- immutable person casts to text function to be used in indexes
CREATE OR REPLACE FUNCTION person_str(person) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION person_str(person[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION organisation_str(organisation[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION array_str(text[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

-- CUSTOM CASTS
CREATE OR REPLACE FUNCTION text2person(text) RETURNS person AS
$$
SELECT ROW(null, $1, null, null)::person
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION text2organisation(text) RETURNS organisation AS
$$
SELECT ROW($1, null, null, null, null)::organisation
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS person) WITH FUNCTION text2person;
CREATE CAST (text AS organisation) WITH FUNCTION text2organisation;


CREATE TABLE "user" (
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
  roles USER_ROLE[],
  settings HSTORE
);


CREATE TABLE dataset (
  key serial PRIMARY KEY,
  source_key INTEGER REFERENCES dataset,
  import_attempt INTEGER,
  type DATASETTYPE NOT NULL DEFAULT 'OTHER',
  origin DATASETORIGIN NOT NULL,
  private BOOLEAN DEFAULT FALSE,
  gbif_key UUID UNIQUE,
  gbif_publisher_key UUID,

  title TEXT NOT NULL,
  alias TEXT UNIQUE,
  description TEXT,
  organisations organisation[] DEFAULT '{}',
  contact person,
  authors person[],
  editors person[],
  license LICENSE,
  version TEXT,
  released DATE,
  citation TEXT,
  geographic_scope TEXT,
  website TEXT,
  logo TEXT,
  "group" TEXT,
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  notes text,

  settings JSONB,
  access_control INT[],

  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  deleted TIMESTAMP WITHOUT TIME ZONE,
  doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(organisation_str(organisations), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(contact), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(authors), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(person_str(editors), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
  ) STORED
);

CREATE INDEX ON dataset USING gin (f_unaccent(title) gin_trgm_ops);
CREATE INDEX ON dataset USING gin (f_unaccent(alias) gin_trgm_ops);
CREATE INDEX ON dataset USING gin(doc);


CREATE TABLE dataset_archive (LIKE dataset);
ALTER TABLE dataset_archive
  DROP COLUMN deleted,
  DROP COLUMN doc,
  DROP COLUMN access_control,
  DROP COLUMN gbif_key,
  DROP COLUMN gbif_publisher_key,
  DROP COLUMN private,
  DROP COLUMN settings;


CREATE TABLE project_source (LIKE dataset_archive);
ALTER TABLE project_source
  ADD COLUMN dataset_key INTEGER REFERENCES dataset;
ALTER TABLE project_source ADD UNIQUE (key, dataset_key);

ALTER TABLE dataset_archive ALTER COLUMN import_attempt set not null;
ALTER TABLE dataset_archive ADD UNIQUE (key, import_attempt);

CREATE TABLE dataset_patch AS SELECT * FROM dataset_archive LIMIT 0;
ALTER TABLE dataset_patch
  DROP COLUMN source_key,
  DROP COLUMN import_attempt,
  DROP COLUMN notes,
  DROP COLUMN origin,
  ADD COLUMN dataset_key INTEGER NOT NULL REFERENCES dataset;
ALTER TABLE dataset_patch ADD PRIMARY KEY (key, dataset_key);


CREATE TABLE dataset_import (
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  attempt INTEGER NOT NULL,
  state IMPORTSTATE NOT NULL,
  origin DATASETORIGIN NOT NULL,
  format DATAFORMAT,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  download TIMESTAMP WITHOUT TIME ZONE,
  created_by INTEGER NOT NULL,
  verbatim_count INTEGER,
  -- shared
  applied_decision_count INTEGER,
  bare_name_count INTEGER,
  distribution_count INTEGER,
  estimate_count INTEGER,
  media_count INTEGER,
  name_count INTEGER,
  reference_count INTEGER,
  synonym_count INTEGER,
  taxon_count INTEGER,
  treatment_count INTEGER,
  type_material_count INTEGER,
  vernacular_count INTEGER,
  distributions_by_gazetteer_count HSTORE,
  extinct_taxa_by_rank_count HSTORE,
  ignored_by_reason_count HSTORE,
  issues_by_issue_count HSTORE,
  media_by_type_count HSTORE,
  name_relations_by_type_count HSTORE,
  names_by_code_count HSTORE,
  names_by_rank_count HSTORE,
  names_by_status_count HSTORE,
  names_by_type_count HSTORE,
  species_interactions_by_type_count HSTORE,
  synonyms_by_rank_count HSTORE,
  taxa_by_rank_count HSTORE,
  taxon_concept_relations_by_type_count HSTORE,
  type_material_by_status_count HSTORE,
  usages_by_origin_count HSTORE,
  usages_by_status_count HSTORE,
  vernaculars_by_language_count HSTORE,
  -- extra
  verbatim_by_row_type_count JSONB,
  verbatim_by_term_count HSTORE,
  job TEXT NOT NULL,
  error TEXT,
  md5 TEXT,
  download_uri TEXT,
  PRIMARY KEY (dataset_key, attempt)
);

CREATE TABLE sector (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_rank RANK,
  subject_code NOMCODE,
  subject_status TAXONOMICSTATUS,
  target_rank RANK,
  target_code NOMCODE,
  mode SECTOR_MODE NOT NULL,
  code NOMCODE,
  sync_attempt INTEGER,
  dataset_import_attempt INTEGER,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  original_subject_id TEXT,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_parent TEXT,
  target_id TEXT,
  target_name TEXT,
  target_authorship TEXT,
  placeholder_rank RANK,
  ranks RANK[] DEFAULT '{}',
  entities ENTITYTYPE[] DEFAULT NULL,
  note TEXT,
  UNIQUE (dataset_key, subject_dataset_key, subject_id),
  PRIMARY KEY (dataset_key, id)
);

CREATE TABLE sector_import (
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER NOT NULL,
  attempt INTEGER NOT NULL,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  created_by INTEGER NOT NULL,
  state IMPORTSTATE NOT NULL,
  -- shared
  applied_decision_count INTEGER,
  bare_name_count INTEGER,
  distribution_count INTEGER,
  estimate_count INTEGER,
  media_count INTEGER,
  name_count INTEGER,
  reference_count INTEGER,
  synonym_count INTEGER,
  taxon_count INTEGER,
  treatment_count INTEGER,
  type_material_count INTEGER,
  vernacular_count INTEGER,
  distributions_by_gazetteer_count HSTORE,
  extinct_taxa_by_rank_count HSTORE,
  ignored_by_reason_count HSTORE,
  issues_by_issue_count HSTORE,
  media_by_type_count HSTORE,
  name_relations_by_type_count HSTORE,
  names_by_code_count HSTORE,
  names_by_rank_count HSTORE,
  names_by_status_count HSTORE,
  names_by_type_count HSTORE,
  species_interactions_by_type_count HSTORE,
  synonyms_by_rank_count HSTORE,
  taxa_by_rank_count HSTORE,
  taxon_concept_relations_by_type_count HSTORE,
  type_material_by_status_count HSTORE,
  usages_by_origin_count HSTORE,
  usages_by_status_count HSTORE,
  vernaculars_by_language_count HSTORE,
  job TEXT NOT NULL,
  warnings TEXT[],
  error TEXT,
  PRIMARY KEY (dataset_key, sector_key, attempt),
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE
);

CREATE TABLE decision (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_rank rank,
  subject_code NOMCODE,
  subject_status TAXONOMICSTATUS,
  mode EDITORIALDECISION_MODE NOT NULL,
  status TAXONOMICSTATUS,
  extinct BOOLEAN,
  environments ENVIRONMENT[] DEFAULT '{}',
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  original_subject_id TEXT,
  subject_id TEXT,
  subject_name TEXT,
  subject_authorship TEXT,
  subject_parent TEXT,
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  name JSONB,
  note TEXT,
  UNIQUE (dataset_key, subject_dataset_key, subject_id),
  PRIMARY KEY (dataset_key, id)
);

CREATE TABLE estimate (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  verbatim_key INTEGER,
  target_rank RANK,
  target_code NOMCODE,
  estimate INTEGER,
  type ESTIMATETYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  target_id TEXT,
  target_name TEXT,
  target_authorship TEXT,
  reference_id TEXT,
  note TEXT,
  PRIMARY KEY (dataset_key, id)
);
CREATE INDEX ON estimate (dataset_key, target_id);
CREATE INDEX ON estimate (dataset_key, reference_id);

CREATE TABLE names_index (
  id SERIAL PRIMARY KEY,
  canonical_id INTEGER NOT NULL REFERENCES names_index,
  rank RANK NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  scientific_name TEXT NOT NULL,
  authorship TEXT,
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  remarks TEXT
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
  doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('simple2', coalesce(terms,'{}'::jsonb), '["string", "numeric"]')) STORED
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim USING GIN(issues);
CREATE INDEX ON verbatim (type);
CREATE INDEX ON verbatim USING GIN (terms jsonb_path_ops);


CREATE TABLE verbatim_source (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,
  issues ISSUE[] DEFAULT '{}'
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim_source USING GIN(issues);


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
  doc tsvector GENERATED ALWAYS AS (
    jsonb_to_tsvector('simple2', coalesce(csl,'{}'::jsonb), '["string", "numeric"]') ||
          to_tsvector('simple2', coalesce(citation,'')) ||
          to_tsvector('simple2', coalesce(year::text,''))
  ) STORED
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON reference (verbatim_key);
CREATE INDEX ON reference (sector_key);

CREATE TABLE name (
  id TEXT NOT NULL,
  candidatus BOOLEAN DEFAULT FALSE,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  rank RANK NOT NULL,
  notho NAMEPART,
  code NOMCODE,
  nom_status NOMSTATUS,
  origin ORIGIN NOT NULL,
  type NAMETYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  homotypic_name_id TEXT NOT NULL,
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
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  published_in_id TEXT,
  published_in_page TEXT,
  link TEXT,
  nomenclatural_note TEXT,
  unparsed TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name (sector_key);
CREATE INDEX ON name (verbatim_key);
CREATE INDEX ON name (homotypic_name_id);
CREATE INDEX ON name (published_in_id);
CREATE INDEX ON name (lower(scientific_name));
CREATE INDEX ON name (scientific_name_normalized);

CREATE OR REPLACE FUNCTION homotypic_name_id_default() RETURNS trigger AS $$
BEGIN
    NEW.homotypic_name_id := NEW.id;
    RETURN NEW;
END
$$
LANGUAGE plpgsql;

CREATE TABLE name_match (
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  type MATCHTYPE,
  index_id INTEGER NOT NULL REFERENCES names_index,
  name_id TEXT NOT NULL,
  PRIMARY KEY (dataset_key, name_id)
);
CREATE INDEX ON name_match (dataset_key, sector_key);
CREATE INDEX ON name_match (dataset_key, index_id);
CREATE INDEX ON name_match (index_id);

CREATE TABLE name_rel (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  type NOMRELTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  name_id TEXT NOT NULL,
  related_name_id TEXT NULL,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name_rel (name_id, type);
CREATE INDEX ON name_rel (sector_key);
CREATE INDEX ON name_rel (verbatim_key);
CREATE INDEX ON name_rel (reference_id);

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

CREATE INDEX ON type_material (name_id);
CREATE INDEX ON type_material (sector_key);
CREATE INDEX ON type_material (verbatim_key);
CREATE INDEX ON type_material (reference_id);

CREATE TABLE name_usage (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  is_synonym BOOLEAN NOT NULL,
  extinct BOOLEAN,
  status TAXONOMICSTATUS NOT NULL,
  origin ORIGIN NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  parent_id TEXT,
  name_id TEXT NOT NULL,
  name_phrase TEXT,
  according_to_id TEXT,
  scrutinizer TEXT,
  scrutinizer_date TEXT,
  reference_ids TEXT[] DEFAULT '{}',
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  environments ENVIRONMENT[] DEFAULT '{}',
  link TEXT,
  remarks TEXT,
  dataset_sectors JSONB
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name_usage (name_id);
CREATE INDEX ON name_usage (parent_id);
CREATE INDEX ON name_usage (verbatim_key);
CREATE INDEX ON name_usage (sector_key);
CREATE INDEX ON name_usage (according_to_id);

CREATE TABLE taxon_concept_rel (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  type TAXONCONCEPTRELTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  related_taxon_id TEXT NOT NULL,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON taxon_concept_rel (taxon_id, type);
CREATE INDEX ON taxon_concept_rel (sector_key);
CREATE INDEX ON taxon_concept_rel (verbatim_key);
CREATE INDEX ON taxon_concept_rel (reference_id);

CREATE TABLE species_interaction (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  type SPECIESINTERACTIONTYPE NOT NULL,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  taxon_id TEXT NOT NULL,
  related_taxon_id TEXT,
  related_taxon_scientific_name TEXT,
  reference_id TEXT,
  remarks TEXT
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON species_interaction (taxon_id, type);
CREATE INDEX ON species_interaction (sector_key);
CREATE INDEX ON species_interaction (verbatim_key);
CREATE INDEX ON species_interaction (reference_id);

CREATE TABLE vernacular_name (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
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
  sex SEX,
  reference_id TEXT,
  doc tsvector GENERATED ALWAYS AS (to_tsvector('simple2', coalesce(name, '') || ' ' || coalesce(latin, ''))) STORED
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON vernacular_name (taxon_id);
CREATE INDEX ON vernacular_name (sector_key);
CREATE INDEX ON vernacular_name (verbatim_key);
CREATE INDEX ON vernacular_name (reference_id);

CREATE TABLE distribution (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
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

CREATE INDEX ON distribution (taxon_id);
CREATE INDEX ON distribution (sector_key);
CREATE INDEX ON distribution (verbatim_key);
CREATE INDEX ON distribution (reference_id);

CREATE TABLE treatment (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER NOT NULL,
  format TREATMENTFORMAT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  document TEXT NOT NULL
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON treatment (sector_key);
CREATE INDEX ON treatment (verbatim_key);

CREATE TABLE media (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
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

CREATE INDEX ON media (taxon_id);
CREATE INDEX ON media (sector_key);
CREATE INDEX ON media (verbatim_key);
CREATE INDEX ON media (reference_id);



CREATE TABLE parser_config (
  id TEXT PRIMARY KEY,
  candidatus BOOLEAN DEFAULT FALSE,
  extinct BOOLEAN DEFAULT FALSE,
  rank RANK NOT NULL,
  notho NAMEPART,
  code NOMCODE,
  type NAMETYPE NOT NULL,
  created_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  uninomial TEXT,
  genus TEXT,
  infrageneric_epithet TEXT,
  specific_epithet TEXT,
  infraspecific_epithet TEXT,
  cultivar_epithet TEXT,
  basionym_authors TEXT[] DEFAULT '{}',
  basionym_ex_authors TEXT[] DEFAULT '{}',
  basionym_year TEXT,
  combination_authors TEXT[] DEFAULT '{}',
  combination_ex_authors TEXT[] DEFAULT '{}',
  combination_year TEXT,
  sanctioning_author TEXT,
  published_in TEXT,
  nomenclatural_note TEXT,
  taxonomic_note TEXT,
  unparsed TEXT,
  remarks TEXT
);

-- FUNCTIONS
CREATE FUNCTION plaziGbifKey() RETURNS UUID AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;

-- botanical subgeneric ranks that are placed above genus level in zoology
CREATE FUNCTION ambiguousRanks() RETURNS rank[] AS $$
  SELECT ARRAY['SUPERSECTION','SECTION','SUBSECTION','SUPERSERIES','SERIES','SUBSERIES','OTHER','UNRANKED']::rank[]
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;

-- replaces whitespace including tabs, carriage returns and new lines with a single space
CREATE FUNCTION repl_ws(x text) RETURNS TEXT AS $$
  SELECT regexp_replace(x, '\s', ' ', 'g' )
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;

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


CREATE OR REPLACE FUNCTION array_distinct(anyarray)
RETURNS anyarray AS $$
  SELECT ARRAY(SELECT DISTINCT unnest($1))
$$ LANGUAGE sql;


-- array_agg alternative that ignores null values
CREATE OR REPLACE FUNCTION array_agg_nonull (
    a anyarray
    , b anynonarray
) RETURNS ANYARRAY
AS $$
BEGIN
    IF b IS NOT NULL THEN
        a := array_append(a, b);
    END IF;
    RETURN a;
END;
$$ IMMUTABLE PARALLEL SAFE LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION array_agg_nonull (
    a anyarray
    , b anyarray
) RETURNS ANYARRAY
AS $$
BEGIN
    IF b IS NOT NULL THEN
        a := array_cat(a, b);
    END IF;
    RETURN a;
END;
$$ IMMUTABLE PARALLEL SAFE LANGUAGE 'plpgsql';

CREATE AGGREGATE array_agg_nonull(ANYNONARRAY) (
    SFUNC = array_agg_nonull,
    STYPE = ANYARRAY,
    INITCOND = '{}'
);

CREATE AGGREGATE array_agg_nonull(ANYARRAY) (
    SFUNC = array_agg_nonull,
    STYPE = ANYARRAY,
    INITCOND = '{}'
);


CREATE OR REPLACE FUNCTION array_reverse(anyarray) RETURNS anyarray AS $$
SELECT ARRAY(
    SELECT $1[i]
    FROM generate_subscripts($1,1) AS s(i)
    ORDER BY i DESC
);
$$ LANGUAGE 'sql' STRICT IMMUTABLE PARALLEL SAFE;


-- return all parent names as an array
CREATE OR REPLACE FUNCTION classification(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS TEXT[] AS $$
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
CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false) RETURNS simple_name[] AS $$
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
CREATE index ON dataset_import (dataset_key);
CREATE index ON dataset_import (started);
CREATE index ON decision (dataset_key);
CREATE index ON estimate (dataset_key);
CREATE index ON estimate (dataset_key, target_id);
CREATE INDEX ON names_index (lower(scientific_name));
CREATE index ON sector (dataset_key);
CREATE index ON sector (dataset_key, subject_dataset_key, subject_id);
CREATE index ON sector (dataset_key, target_id);


-- useful views
CREATE VIEW table_size AS (
    SELECT oid, TABLE_NAME, row_estimate, pg_size_pretty(total_bytes) AS total
        , pg_size_pretty(index_bytes) AS INDEX
        , pg_size_pretty(toast_bytes) AS toast
        , pg_size_pretty(table_bytes) AS TABLE
      FROM (
      SELECT *, total_bytes-index_bytes-COALESCE(toast_bytes,0) AS table_bytes FROM (
          SELECT c.oid, relname AS TABLE_NAME
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


CREATE VIEW v_name_usage AS (
  SELECT u.dataset_key, u.id, n.id AS nid, u.parent_id, u.status, n.rank, n.scientific_name, n.authorship
  FROM name_usage u JOIN name n ON n.id=u.name_id AND u.dataset_key=n.dataset_key
);



CREATE TABLE usage_count (
  dataset_key int PRIMARY KEY,
  counter int
);

CREATE OR REPLACE FUNCTION count_usage_on_insert()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
    EXECUTE 'UPDATE usage_count set counter=counter+(select count(*) from inserted) where dataset_key=' || TG_ARGV[0];
    RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';

CREATE OR REPLACE FUNCTION count_usage_on_delete()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
  EXECUTE 'UPDATE usage_count set counter=counter-(select count(*) from deleted) where dataset_key=' || TG_ARGV[0];
  RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';
