
-- required extensions
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

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
  'PROXY',
  'NEWICK',
  'DOT'
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
  'UPDATE_RECURSIVE',
  'IGNORE'
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
  'SELF_REFERENCED_RELATION',
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
  'PARENT_SPECIES_MISSING',
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
  'ESTIMATE_TYPE_INVALID',
  'INVISIBLE_CHARACTERS',
  'HOMOGLYPH_CHARACTERS',
  'RELATED_NAME_MISSING'
);

CREATE TYPE JOBSTATUS AS ENUM (
  'WAITING',
  'BLOCKED',
  'RUNNING',
  'FINISHED',
  'CANCELED',
  'FAILED'
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
  'CC_BY_SA',
  'CC_BY_NC',
  'CC_BY_ND',
  'CC_BY_NC_SA',
  'CC_BY_NC_ND',
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
  'SUBTERCLASS',
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
  'PLESIOTYPE',
  'HOMOEOTYPE',
  'OTHER'
);

CREATE TYPE USER_ROLE AS ENUM (
  'REVIEWER',
  'EDITOR',
  'ADMIN'
);


-- a simple compound type corresponding to the basics of SimpleName. Often used for building classifications as arrays
CREATE TYPE simple_name AS (id text, rank rank, name text, authorship text);

-- Agent type for dataset to avoid extra tables. 12 fields (11 commas)
CREATE TYPE agent AS (orcid text, given text, family text,
  rorid text, organisation text, department text, city text, state text, country CHAR(2),
  email text, url text, note text
);

-- CSLName type for citations and references to avoid extra tables
CREATE TYPE cslname AS (given text, family text, particle text);

-- immutable agent casts to text function to be used in indexes
CREATE OR REPLACE FUNCTION agent_str(agent) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION agent_str(agent[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION cslname_str(cslname) RETURNS text AS
$$
SELECT $1::text
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION cslname_str(cslname[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE OR REPLACE FUNCTION array_str(text[]) RETURNS text AS
$$
SELECT array_to_string($1, ' ')
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

-- CUSTOM CASTS
CREATE OR REPLACE FUNCTION text2agent(text) RETURNS agent AS
$$
SELECT ROW(null, null, $1, null, null, null, null, null, null, null, null, null)::agent
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS agent) WITH FUNCTION text2agent;

CREATE OR REPLACE FUNCTION text2cslname(text) RETURNS cslname AS
$$
SELECT ROW(null, $1, null)::cslname
$$  LANGUAGE sql IMMUTABLE PARALLEL SAFE;

CREATE CAST (text AS cslname) WITH FUNCTION text2cslname;



CREATE TABLE "user" (
  key serial PRIMARY KEY,
  last_login TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  blocked TIMESTAMP WITHOUT TIME ZONE,
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
  doi text UNIQUE,
  source_key INTEGER REFERENCES dataset,
  attempt INTEGER,
  private BOOLEAN DEFAULT FALSE,
  type DATASETTYPE NOT NULL DEFAULT 'OTHER',
  origin DATASETORIGIN NOT NULL,
  gbif_key UUID UNIQUE,
  gbif_publisher_key UUID,

  identifier HSTORE,
  title TEXT NOT NULL,
  alias TEXT,
  description TEXT,
  issued TEXT,
  version TEXT,
  issn TEXT,
  contact agent,
  creator agent[],
  editor agent[],
  publisher agent,
  contributor agent[],
  geographic_scope TEXT,
  taxonomic_scope TEXT,
  temporal_scope TEXT,
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  license LICENSE,
  url TEXT,
  logo TEXT,
  notes TEXT,

  settings JSONB,
  acl_editor INT[],
  acl_reviewer INT[],

  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  deleted TIMESTAMP WITHOUT TIME ZONE,

  doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('simple2', coalesce(alias,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(title,'')), 'A') ||
      setweight(to_tsvector('simple2', coalesce(issn, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(identifier::text, '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(creator), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(version, '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(description,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(geographic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(taxonomic_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(temporal_scope,'')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contact), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(editor), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(publisher), '')), 'C') ||
      setweight(to_tsvector('simple2', coalesce(agent_str(contributor), '')), 'B') ||
      setweight(to_tsvector('simple2', coalesce(gbif_key::text,'')), 'C')
  ) STORED
);

CREATE INDEX ON dataset USING GIN (f_unaccent(title) gin_trgm_ops);
CREATE INDEX ON dataset USING GIN (f_unaccent(alias) gin_trgm_ops);
CREATE INDEX ON dataset USING GIN (doc);

CREATE TABLE dataset_citation (
  dataset_key INTEGER REFERENCES dataset,
  id TEXT,
  type TEXT,
  doi TEXT,
  author cslname[],
  editor cslname[],
  title TEXT,
  container_author cslname[],
  container_title TEXT,
  issued TEXT,
  accessed TEXT,
  collection_editor cslname[],
  collection_title TEXT,
  volume TEXT,
  issue TEXT,
  edition TEXT,
  page TEXT,
  publisher TEXT,
  publisher_place TEXT,
  version TEXT,
  isbn TEXT,
  issn TEXT,
  url TEXT,
  note TEXT
);
CREATE INDEX ON dataset_citation (dataset_key);

CREATE TABLE dataset_archive (LIKE dataset);
ALTER TABLE dataset_archive
  DROP COLUMN deleted,
  DROP COLUMN doc,
  DROP COLUMN acl_editor,
  DROP COLUMN acl_reviewer,
  DROP COLUMN gbif_key,
  DROP COLUMN gbif_publisher_key,
  DROP COLUMN private,
  DROP COLUMN settings;
ALTER TABLE dataset_archive ADD FOREIGN KEY (key) REFERENCES dataset;

CREATE TABLE dataset_archive_citation (LIKE dataset_citation INCLUDING INDEXES);
ALTER TABLE dataset_archive_citation
  ADD COLUMN attempt INTEGER NOT NULL;
CREATE INDEX ON dataset_archive_citation (dataset_key, attempt);

CREATE TABLE dataset_source (LIKE dataset_archive INCLUDING INDEXES);
ALTER TABLE dataset_source
  ADD COLUMN dataset_key INTEGER REFERENCES dataset;
ALTER TABLE dataset_source ADD PRIMARY KEY (key, dataset_key);
ALTER TABLE dataset_source ADD FOREIGN KEY (key) REFERENCES dataset;

CREATE TABLE dataset_source_citation (LIKE dataset_citation INCLUDING INDEXES);
ALTER TABLE dataset_source_citation
  ADD COLUMN release_key INTEGER REFERENCES dataset;
CREATE INDEX ON dataset_source_citation (dataset_key, release_key);

-- finally we also assign the primary key of the archive which is different from dataset_source - hence only here
ALTER TABLE dataset_archive ALTER COLUMN attempt SET NOT NULL;
ALTER TABLE dataset_archive ADD PRIMARY KEY (key, attempt);

-- patches must allow nulls everywhere!
CREATE TABLE dataset_patch (LIKE dataset_source INCLUDING INDEXES);
ALTER TABLE dataset_patch
  DROP COLUMN source_key,
  DROP COLUMN attempt,
  DROP COLUMN type,
  DROP COLUMN origin;
ALTER TABLE dataset_patch
  ALTER COLUMN title DROP NOT NULL;
ALTER TABLE dataset_patch ADD FOREIGN KEY (key) REFERENCES dataset;
ALTER TABLE dataset_patch ADD FOREIGN KEY (dataset_key) REFERENCES dataset;


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

CREATE TABLE dataset_export (
  key UUID PRIMARY KEY,
  -- request
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  format DATAFORMAT NOT NULL,
  excel BOOLEAN NOT NULL,
  root SIMPLE_NAME,
  synonyms BOOLEAN NOT NULL,
  min_rank RANK,
  created_by INTEGER NOT NULL REFERENCES "user",
  created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  modified_by INTEGER,
  modified TIMESTAMP WITHOUT TIME ZONE,
  -- results
  attempt INTEGER,
  started TIMESTAMP WITHOUT TIME ZONE,
  finished TIMESTAMP WITHOUT TIME ZONE,
  deleted TIMESTAMP WITHOUT TIME ZONE,
  classification SIMPLE_NAME[],
  status JOBSTATUS NOT NULL,
  error TEXT,
  truncated TEXT[],
  md5 TEXT,
  size INTEGER,
  synonym_count INTEGER,
  taxon_count INTEGER,
  taxa_by_rank_count HSTORE
);

CREATE INDEX ON dataset_export (created);
CREATE INDEX ON dataset_export (created_by, created);
CREATE INDEX ON dataset_export (dataset_key, attempt, format, excel, synonyms, min_rank, status);

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
  dataset_attempt INTEGER,
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
CREATE INDEX ON names_index (canonical_id);


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
  doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('simple2', coalesce(terms,'{}'::jsonb), '["string", "numeric"]')) STORED,
  PRIMARY KEY (dataset_key, id)
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim (dataset_key, type);
CREATE INDEX ON verbatim USING GIN (dataset_key, doc);
CREATE INDEX ON verbatim USING GIN (dataset_key, issues);
CREATE INDEX ON verbatim USING GIN (dataset_key, terms jsonb_path_ops);

CREATE TABLE verbatim_source (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,
  issues ISSUE[] DEFAULT '{}'
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON verbatim_source USING GIN(dataset_key, issues);


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
  ) STORED,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON reference (dataset_key, verbatim_key);
CREATE INDEX ON reference (dataset_key, sector_key);
CREATE INDEX ON reference USING GIN (dataset_key, doc);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, published_in_id) REFERENCES reference
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name (dataset_key, sector_key);
CREATE INDEX ON name (dataset_key, verbatim_key);
CREATE INDEX ON name (dataset_key, published_in_id);
CREATE INDEX ON name (dataset_key, lower(scientific_name));
CREATE INDEX ON name (dataset_key, scientific_name_normalized);



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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, related_name_id) REFERENCES name ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name_rel (dataset_key, name_id, type);
CREATE INDEX ON name_rel (dataset_key, sector_key);
CREATE INDEX ON name_rel (dataset_key, verbatim_key);
CREATE INDEX ON name_rel (dataset_key, reference_id);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON type_material (dataset_key, name_id);
CREATE INDEX ON type_material (dataset_key, sector_key);
CREATE INDEX ON type_material (dataset_key, verbatim_key);
CREATE INDEX ON type_material (dataset_key, reference_id);


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
  dataset_sectors JSONB,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, parent_id) REFERENCES name_usage DEFERRABLE INITIALLY DEFERRED
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON name_usage (dataset_key, name_id);
CREATE INDEX ON name_usage (dataset_key, parent_id);
CREATE INDEX ON name_usage (dataset_key, verbatim_key);
CREATE INDEX ON name_usage (dataset_key, sector_key);
CREATE INDEX ON name_usage (dataset_key, according_to_id);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON taxon_concept_rel (dataset_key, taxon_id, type);
CREATE INDEX ON taxon_concept_rel (dataset_key, sector_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, verbatim_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, reference_id);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON species_interaction (dataset_key, taxon_id, type);
CREATE INDEX ON species_interaction (dataset_key, sector_key);
CREATE INDEX ON species_interaction (dataset_key, verbatim_key);
CREATE INDEX ON species_interaction (dataset_key, reference_id);


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
  doc tsvector GENERATED ALWAYS AS (to_tsvector('simple2', coalesce(name, '') || ' ' || coalesce(latin, ''))) STORED,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON vernacular_name (dataset_key, taxon_id);
CREATE INDEX ON vernacular_name (dataset_key, sector_key);
CREATE INDEX ON vernacular_name (dataset_key, verbatim_key);
CREATE INDEX ON vernacular_name (dataset_key, reference_id);
CREATE INDEX ON vernacular_name USING GIN (dataset_key, doc);


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
  reference_id TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON distribution (dataset_key, taxon_id);
CREATE INDEX ON distribution (dataset_key, sector_key);
CREATE INDEX ON distribution (dataset_key, verbatim_key);
CREATE INDEX ON distribution (dataset_key, reference_id);

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
  document TEXT NOT NULL,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON treatment (dataset_key, sector_key);
CREATE INDEX ON treatment (dataset_key, verbatim_key);


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
  reference_id TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference ON DELETE CASCADE,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage ON DELETE CASCADE
) PARTITION BY LIST (dataset_key);

CREATE INDEX ON media (dataset_key, taxon_id);
CREATE INDEX ON media (dataset_key, sector_key);
CREATE INDEX ON media (dataset_key, verbatim_key);
CREATE INDEX ON media (dataset_key, reference_id);


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

-- escapes the 3 special characters in LIKE arguments using the default backslash ESCAPE character
CREATE OR REPLACE FUNCTION escape_like(text) RETURNS text AS $$
SELECT replace(replace(replace($1
         , '\', '\\')  -- must come 1st
         , '%', '\%')
         , '_', '\_');
$$
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE;

-- replaces whitespace including tabs, carriage returns and new lines with a single space
CREATE OR REPLACE FUNCTION repl_ws(x text) RETURNS TEXT AS $$
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
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t '
        || '  JOIN name n ON n.dataset_key=$1 AND n.id=t.name_id WHERE t.dataset_key=$1 AND t.id = $2'
        || ' UNION ALL '
        || 'SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t '
        || '  JOIN name n ON n.dataset_key=$1 AND n.id=t.name_id WHERE t.dataset_key=$1 AND t.id = x.parent_id'
        || ') SELECT array_agg(sn) FROM x';

    IF NOT v_inc_self THEN
        seql := seql || ' WHERE id != $1';
    END IF;

    EXECUTE seql
    INTO parents
    USING v_dataset_key, v_id;
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

CREATE OR REPLACE FUNCTION track_usage_count()
RETURNS TRIGGER AS
$$
  DECLARE
  BEGIN
      -- making use of the special variable TG_OP to work out the operation.
      -- we assume we never mix records from several datasets in an insert or delete statement !!!
      IF (TG_OP = 'DELETE') THEN
        EXECUTE 'UPDATE usage_count set counter=counter+(select count(*) from deleted) where dataset_key=(SELECT dataset_key FROM deleted LIMIT 1)';
      ELSIF (TG_OP = 'INSERT') THEN
        EXECUTE 'UPDATE usage_count set counter=counter-(select count(*) from inserted) where dataset_key=(SELECT dataset_key FROM inserted LIMIT 1)';
      END IF;

    RETURN NULL;
  END;
$$
LANGUAGE 'plpgsql';
