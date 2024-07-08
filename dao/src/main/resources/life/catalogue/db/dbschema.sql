
-- required extensions
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;

-- search configs
CREATE TEXT SEARCH CONFIGURATION public.dataset ( COPY = pg_catalog.english );
CREATE TEXT SEARCH CONFIGURATION public.reference ( COPY = pg_catalog.english );
CREATE TEXT SEARCH CONFIGURATION public.verbatim ( COPY = pg_catalog.simple );
CREATE TEXT SEARCH CONFIGURATION public.vernacular ( COPY = pg_catalog.simple );

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
  'PROJECT',
  'RELEASE',
  'XRELEASE'
);

CREATE TYPE DATASETTYPE AS ENUM (
  'NOMENCLATURAL',
  'TAXONOMIC',
  'PHYLOGENETIC',
  'ARTICLE',
  'LEGAL',
  'THEMATIC',
  'IDENTIFICATION',
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
  'AUTHOR',
  'NAME',
  'NAME_RELATION',
  'NAME_USAGE',
  'TAXON_CONCEPT_RELATION',
  'TAXON_PROPERTY',
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

CREATE TYPE GENDER AS ENUM (
  'MASCULINE',
  'FEMININE',
  'NEUTER'
);

CREATE TYPE IDREPORTTYPE AS ENUM (
  'DELETED',
  'RESURRECTED',
  'CREATED'
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
  'FINISHED',
  'CANCELED',
  'FAILED'
);

CREATE TYPE INFOGROUP AS ENUM (
  'AUTHORSHIP',
  'PUBLISHED_IN',
  'PARENT',
  'BASIONYM',
  'EXTINCT',
  'TEMPORAL_RANGE'
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
  'HOMOTYPIC_CONSOLIDATION_UNRESOLVED',
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
  'RELATED_NAME_MISSING',
  'DIACRITIC_CHARACTERS',
  'MULTI_WORD_MONOMIAL',
  'WRONG_MONOMIAL_CASE',
  'AUTHORSHIP_REMOVED',
  'DOI_NOT_FOUND',
  'DOI_UNRESOLVED',
  'TYPE_MATERIAL_SEX_INVALID',
  'IDENTIFIER_WITHOUT_SCOPE',
  'HOMOTYPIC_CONSOLIDATION',
  'SYNC_OUTSIDE_TARGET',
  'MULTIPLE_BASIONYMS',
  'PUBLISHED_YEAR_CONFLICT',
  'MULTILINE_RECORD',
  'NOTHO_INVALID',
  'ORIGINAL_SPELLING_INVALID',
  'UNINOMIAL_FIELD_MISPLACED',
  'INFRAGENERIC_FIELD_MISPLACED',
  'ORDINAL_INVALID',
  'GENDER_INVALID',
  'GENDER_AGREEMENT_NOT_APPLICABLE',
  'NOTHO_NOT_APPLICABLE',
  'VERNACULAR_PREFERRED',
  'DOI_INVALID',
  'RANK_NAME_SUFFIX_CONFLICT'
);

CREATE TYPE JOBSTATUS AS ENUM (
  'WAITING',
  'BLOCKED',
  'RUNNING',
  'FINISHED',
  'CANCELED',
  'FAILED'
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

CREATE TYPE MATCHTYPE AS ENUM (
  'EXACT',
  'VARIANT',
  'CANONICAL',
  'AMBIGUOUS',
  'NONE',
  'UNSUPPORTED',
  'HIGHERRANK'
);

CREATE TYPE MEDIATYPE AS ENUM (
  'IMAGE',
  'VIDEO',
  'AUDIO'
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
  'PHYTO',
  'VIRUS',
  'ZOOLOGICAL',
  'PHYLO'
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
  'SUPERDOMAIN',
  'DOMAIN',
  'SUBDOMAIN',
  'INFRADOMAIN',
  'EMPIRE',
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
  'PARVPHYLUM',
  'MICROPHYLUM',
  'NANOPHYLUM',
  'CLAUDIUS',
  'GIGACLASS',
  'MEGACLASS',
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
  'MEGACOHORT',
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
  'SUPERSECTION_ZOOLOGY',
  'SECTION_ZOOLOGY',
  'SUBSECTION_ZOOLOGY',
  'FALANX',
  'GIGAFAMILY',
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
  'SUPERGENUS',
  'GENUS',
  'SUBGENUS',
  'INFRAGENUS',
  'SUPERSECTION_BOTANY',
  'SECTION_BOTANY',
  'SUBSECTION_BOTANY',
  'SUPERSERIES',
  'SERIES',
  'SUBSERIES',
  'INFRAGENERIC_NAME',
  'SPECIES_AGGREGATE',
  'SPECIES',
  'INFRASPECIFIC_NAME',
  'GREX',
  'KLEPTON',
  'SUBSPECIES',
  'CULTIVAR_GROUP',
  'CONVARIETY',
  'INFRASUBSPECIFIC_NAME',
  'PROLES',
  'NATIO',
  'ABERRATION',
  'MORPH',
  'SUPERVARIETY',
  'VARIETY',
  'SUBVARIETY',
  'SUPERFORM',
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
  'LUSUS',
  'CULTIVAR',
  'MUTATIO',
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

-- other functions used in table/index definitions
CREATE OR REPLACE FUNCTION is_synonym(status TAXONOMICSTATUS) RETURNS BOOLEAN AS $$
  SELECT status IN ('SYNONYM','AMBIGUOUS_SYNONYM','MISAPPLIED')
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;


CREATE TABLE "user" (
  key serial PRIMARY KEY,
  last_login TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  blocked TIMESTAMP WITHOUT TIME ZONE,
  username TEXT UNIQUE,
  firstname TEXT,
  lastname TEXT,
  email TEXT,
  orcid TEXT,
  country TEXT,
  roles USER_ROLE[],
  publisher UUID[],
  settings HSTORE
);


CREATE TABLE dataset (
  key serial PRIMARY KEY,
  doi text,
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
  keyword TEXT[],
  geographic_scope TEXT,
  taxonomic_scope TEXT,
  temporal_scope TEXT,
  confidence INTEGER CHECK (confidence > 0 AND confidence <= 5),
  completeness INTEGER CHECK (completeness >= 0 AND completeness <= 100),
  license LICENSE,
  url_formatter HSTORE,
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
  last_import_attempt TIMESTAMP WITHOUT TIME ZONE,

  doc tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('dataset', f_unaccent(coalesce(alias,''))), 'A') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(key::text, ''))), 'A') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(doi, ''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(title,''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(array_str(keyword),''))), 'B') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(geographic_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(taxonomic_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(temporal_scope,''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(issn, ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(gbif_key::text,''))), 'C')  ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(identifier::text, ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent(coalesce(agent_str(contact), ''))), 'C') ||
      setweight(to_tsvector('dataset', f_unaccent( left(
        coalesce(description, '') ||
        coalesce(agent_str(publisher), '') ||
        coalesce(agent_str(creator), '') ||
        coalesce(agent_str(editor), '') ||
        coalesce(agent_str(contributor), '')
      , 1024*1024))), 'D')
  ) STORED,
  EXCLUDE (doi WITH =) WHERE (deleted IS null)
);

CREATE INDEX ON dataset (gbif_key);
CREATE INDEX ON dataset USING GIN (f_unaccent(title) gin_trgm_ops);
CREATE INDEX ON dataset USING GIN (f_unaccent(alias) gin_trgm_ops);
CREATE INDEX ON dataset USING GIN (doc);
-- used by import scheduler:
CREATE INDEX ON dataset (key)
 WHERE deleted IS NULL
 AND NOT private
 AND origin = 'EXTERNAL'
 AND settings ->> 'data access' IS NOT NULL
 AND coalesce((settings ->> 'import frequency')::int, 0) >= 0;

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
  DROP COLUMN gbif_key,
  DROP COLUMN gbif_publisher_key,
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
  max_classification_depth INTEGER,
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
  names_by_match_type_count HSTORE,
  species_interactions_by_type_count HSTORE,
  synonyms_by_rank_count HSTORE,
  taxa_by_rank_count HSTORE,
  taxa_by_scrutinizer_count HSTORE,
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

CREATE INDEX ON dataset_import (dataset_key);
CREATE INDEX ON dataset_import (started);
-- used by import scheduler:
CREATE INDEX ON dataset_import (dataset_key, attempt) WHERE finished IS NOT NULL;

CREATE TABLE dataset_export (
  key UUID PRIMARY KEY,
  -- request
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  created_by INTEGER NOT NULL REFERENCES "user",
  format DATAFORMAT NOT NULL,
  root SIMPLE_NAME,
  min_rank RANK,
  synonyms BOOLEAN NOT NULL,
  bare_names BOOLEAN NOT NULL,
  excel BOOLEAN NOT NULL,
  extended BOOLEAN NOT NULL,
  extinct BOOLEAN,
  created TIMESTAMP WITHOUT TIME ZONE NOT NULL,

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
  priority INTEGER,
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
  name_types NAMETYPE[] DEFAULT NULL,
  name_status_exclusion NOMSTATUS[] DEFAULT NULL,
  note TEXT,
  UNIQUE (dataset_key, subject_dataset_key, subject_id),
  PRIMARY KEY (dataset_key, id)
);

CREATE INDEX ON sector (dataset_key);
CREATE INDEX ON sector (dataset_key, subject_dataset_key, subject_id);
CREATE INDEX ON sector (dataset_key, target_id);

CREATE TABLE sector_import (
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER NOT NULL, -- no foreign key as we keep sector imports for deleted sectors!
  attempt INTEGER NOT NULL,
  dataset_attempt INTEGER,
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
  names_by_match_type_count HSTORE,
  species_interactions_by_type_count HSTORE,
  synonyms_by_rank_count HSTORE,
  taxa_by_rank_count HSTORE,
  taxa_by_scrutinizer_count HSTORE,
  taxon_concept_relations_by_type_count HSTORE,
  type_material_by_status_count HSTORE,
  usages_by_origin_count HSTORE,
  usages_by_status_count HSTORE,
  vernaculars_by_language_count HSTORE,
  job TEXT NOT NULL,
  warnings TEXT[],
  error TEXT,
  PRIMARY KEY (dataset_key, sector_key, attempt)
);
CREATE INDEX ON sector_import (dataset_key, sector_key);

CREATE TABLE sector_publisher (
  id UUID NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  alias TEXT,
  title TEXT,
  description TEXT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (dataset_key, id)
);
CREATE INDEX ON sector_publisher (dataset_key);


CREATE TABLE decision (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_dataset_key INTEGER NOT NULL REFERENCES dataset,
  subject_rank rank,
  subject_code NOMCODE,
  subject_status TAXONOMICSTATUS,
  mode EDITORIALDECISION_MODE NOT NULL,
  status TAXONOMICSTATUS,
  keep_original_name BOOLEAN,
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
  CHECK (dataset_key != subject_dataset_key),
  UNIQUE (dataset_key, subject_dataset_key, subject_id),
  PRIMARY KEY (dataset_key, id)
);

CREATE INDEX ON decision (dataset_key);
CREATE INDEX ON decision (subject_dataset_key);
CREATE INDEX ON decision (subject_dataset_key, subject_id);


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
CREATE INDEX ON names_index (scientific_name);
CREATE INDEX ON names_index (scientific_name) WHERE id = canonical_id;


CREATE TABLE id_report (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  type IDREPORTTYPE NOT NULL,
  PRIMARY KEY (dataset_key, id)
);
CREATE INDEX ON id_report (dataset_key);

CREATE TABLE name_usage_archive (
  id TEXT NOT NULL,
  n_id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  -- shared with name table, keep manually in sync!
  n_rank RANK NOT NULL,
  n_candidatus BOOLEAN DEFAULT FALSE,
  n_notho NAMEPART,
  n_code NOMCODE,
  n_nom_status NOMSTATUS,
  n_original_spelling BOOLEAN,
  n_gender_agreement BOOLEAN,
  n_gender GENDER,
  n_origin ORIGIN NOT NULL,
  n_type NAMETYPE NOT NULL,
  n_scientific_name TEXT NOT NULL,
  n_authorship TEXT,
  n_uninomial TEXT,
  n_genus TEXT,
  n_infrageneric_epithet TEXT,
  n_specific_epithet TEXT,
  n_infraspecific_epithet TEXT,
  n_cultivar_epithet TEXT,
  n_basionym_authors TEXT[] DEFAULT '{}',
  n_basionym_ex_authors TEXT[] DEFAULT '{}',
  n_basionym_year TEXT,
  n_combination_authors TEXT[] DEFAULT '{}',
  n_combination_ex_authors TEXT[] DEFAULT '{}',
  n_combination_year TEXT,
  n_sanctioning_author TEXT,
  n_published_in_id TEXT,
  n_published_in_page TEXT,
  n_published_in_page_link TEXT,
  n_nomenclatural_note TEXT,
  n_unparsed TEXT,
  n_identifier TEXT[],
  n_etymology TEXT,
  n_link TEXT,
  n_remarks TEXT,
  -- common with name_usage, keep in sync!
  extinct BOOLEAN,
  status TAXONOMICSTATUS NOT NULL,
  origin ORIGIN NOT NULL,
  ordinal INTEGER,
  parent_id TEXT,
  name_phrase TEXT,
  identifier TEXT[],
  link TEXT,
  remarks TEXT,
  -- archive specifics, will be dropped from partitioned name table
  according_to TEXT,
  basionym SIMPLE_NAME,
  classification SIMPLE_NAME[],
  published_in TEXT,
  first_release_key INTEGER,
  last_release_key INTEGER,

  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key) REFERENCES dataset,
  FOREIGN KEY (first_release_key) REFERENCES dataset,
  FOREIGN KEY (last_release_key) REFERENCES dataset
);


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

CREATE TABLE api_analytics(
  key bigserial NOT NULL PRIMARY KEY,
  from_datetime TIMESTAMP NOT NULL,
  to_datetime TIMESTAMP NOT NULL,
  request_count INTEGER NOT NULL,
  country_agg HSTORE,
  response_code_agg HSTORE,
  agent_agg HSTORE,
  request_pattern_agg HSTORE,
  dataset_agg HSTORE,
  other_metrics HSTORE
);

CREATE UNIQUE INDEX unique_date_range ON api_analytics(from_datetime, to_datetime);
CREATE INDEX ON api_analytics(from_datetime);
CREATE INDEX ON api_analytics(to_datetime);

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
  doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('verbatim', coalesce(terms,'{}'::jsonb), '["string", "numeric"]')) STORED,
  PRIMARY KEY (dataset_key, id)
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON verbatim (dataset_key, type);
CREATE INDEX on verbatim (dataset_key, id) WHERE array_length(issues, 1) > 0;
CREATE INDEX ON verbatim USING GIN (dataset_key, doc);
CREATE INDEX ON verbatim USING GIN (dataset_key, issues);
CREATE INDEX ON verbatim USING GIN (dataset_key, terms jsonb_ops);


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
    jsonb_to_tsvector('reference', coalesce(csl,'{}'::jsonb), '["string", "numeric"]') ||
          to_tsvector('reference', coalesce(citation,'')) ||
          to_tsvector('reference', coalesce(year::text,''))
  ) STORED,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON reference (dataset_key, verbatim_key);
CREATE INDEX ON reference (dataset_key, sector_key);
CREATE INDEX ON reference USING GIN (dataset_key, doc);


CREATE TABLE name (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  -- shared with name_usage_archive, keep in sync!
  rank RANK NOT NULL,
  candidatus BOOLEAN DEFAULT FALSE,
  notho NAMEPART,
  code NOMCODE,
  nom_status NOMSTATUS,
  original_spelling BOOLEAN,
  gender_agreement BOOLEAN,
  gender GENDER,
  origin ORIGIN NOT NULL,
  type NAMETYPE NOT NULL,
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
  published_in_id TEXT,
  published_in_page TEXT,
  published_in_page_link TEXT,
  nomenclatural_note TEXT,
  unparsed TEXT,
  identifier TEXT[],
  etymology TEXT,
  link TEXT,
  remarks TEXT,
  -- additions to name_usage_archive
  scientific_name_normalized TEXT NOT NULL,
  authorship_normalized TEXT[],
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),

  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, published_in_id) REFERENCES reference
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON name (dataset_key, sector_key);
CREATE INDEX ON name (dataset_key, verbatim_key);
CREATE INDEX ON name (dataset_key, published_in_id);
CREATE INDEX ON name (dataset_key, lower(scientific_name));
CREATE INDEX ON name (dataset_key, scientific_name text_pattern_ops);
CREATE INDEX ON name (dataset_key, scientific_name_normalized);


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
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name DEFERRABLE INITIALLY DEFERRED,
  FOREIGN KEY (dataset_key, related_name_id) REFERENCES name DEFERRABLE INITIALLY DEFERRED
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON name_rel (dataset_key, name_id);
CREATE INDEX ON name_rel (dataset_key, related_name_id);
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
  country TEXT,
  locality TEXT,
  latitude TEXT,
  longitude TEXT,
  coordinate POINT,
  altitude TEXT,
  sex SEX,
  institution_code TEXT,
  catalog_number TEXT,
  associated_sequences TEXT,
  host TEXT,
  date TEXT,
  collector TEXT,
  reference_id TEXT,
  link TEXT,
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name DEFERRABLE
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON type_material (dataset_key, name_id);
CREATE INDEX ON type_material (dataset_key, sector_key);
CREATE INDEX ON type_material (dataset_key, verbatim_key);
CREATE INDEX ON type_material (dataset_key, reference_id);


CREATE TABLE name_match (
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  index_id INTEGER REFERENCES names_index,
  name_id TEXT NOT NULL,
  type MATCHTYPE NOT NULL,
  PRIMARY KEY (dataset_key, name_id),
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name DEFERRABLE
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON name_match (dataset_key, sector_key);
CREATE INDEX ON name_match (dataset_key, index_id);
CREATE INDEX ON name_match (index_id);


CREATE TABLE name_usage (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  -- shared with name_usage_archive, keep in sync!
  extinct BOOLEAN,
  status TAXONOMICSTATUS NOT NULL,
  origin ORIGIN NOT NULL,
  ordinal INTEGER,
  parent_id TEXT,
  name_id TEXT NOT NULL,
  name_phrase TEXT,
  identifier TEXT[],
  link TEXT,
  remarks TEXT,
  -- additions to name_usage_archive
  according_to_id TEXT,
  scrutinizer TEXT,
  scrutinizer_id TEXT,
  scrutinizer_date TEXT,
  reference_ids TEXT[] DEFAULT '{}',
  temporal_range_start TEXT,
  temporal_range_end TEXT,
  environments ENVIRONMENT[] DEFAULT '{}',
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  dataset_sectors JSONB,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, according_to_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, name_id) REFERENCES name DEFERRABLE,
  FOREIGN KEY (dataset_key, parent_id) REFERENCES name_usage DEFERRABLE INITIALLY DEFERRED
) PARTITION BY HASH (dataset_key);

ALTER TABLE name_usage ADD CONSTRAINT check_parent_not_self CHECK (parent_id <> id);
CREATE INDEX ON name_usage (dataset_key, name_id);
CREATE INDEX ON name_usage (dataset_key, parent_id);
CREATE INDEX ON name_usage (dataset_key, verbatim_key);
CREATE INDEX ON name_usage (dataset_key, sector_key);
CREATE INDEX ON name_usage (dataset_key, according_to_id);
CREATE INDEX ON name_usage (dataset_key, is_synonym(status));

CREATE TABLE verbatim_source (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,
  issues ISSUE[] DEFAULT '{}',
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON verbatim_source USING GIN(dataset_key, issues);

CREATE TABLE verbatim_source_secondary (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  type INFOGROUP NOT NULL,
  source_id TEXT,
  source_dataset_key INTEGER,
  FOREIGN KEY (dataset_key, id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON verbatim_source_secondary (dataset_key, id);
CREATE INDEX ON verbatim_source_secondary (dataset_key, source_dataset_key);

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
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage,
  FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON taxon_concept_rel (dataset_key, taxon_id);
CREATE INDEX ON taxon_concept_rel (dataset_key, related_taxon_id);
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
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage,
  FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON species_interaction (dataset_key, taxon_id);
CREATE INDEX ON species_interaction (dataset_key, related_taxon_id);
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
  remarks TEXT,
  doc tsvector GENERATED ALWAYS AS (to_tsvector('vernacular', coalesce(name, '') || ' ' || coalesce(latin, ''))) STORED,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON distribution (dataset_key, taxon_id);
CREATE INDEX ON distribution (dataset_key, sector_key);
CREATE INDEX ON distribution (dataset_key, verbatim_key);
CREATE INDEX ON distribution (dataset_key, reference_id);

CREATE TABLE treatment (
  id TEXT NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  format TREATMENTFORMAT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  document TEXT NOT NULL,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON treatment (dataset_key, sector_key);
CREATE INDEX ON treatment (dataset_key, verbatim_key);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id)
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON estimate (dataset_key);
CREATE INDEX ON estimate (dataset_key, target_id);
CREATE INDEX ON estimate (dataset_key, reference_id);


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
  remarks TEXT,
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON media (dataset_key, taxon_id);
CREATE INDEX ON media (dataset_key, sector_key);
CREATE INDEX ON media (dataset_key, verbatim_key);
CREATE INDEX ON media (dataset_key, reference_id);


CREATE TABLE taxon_property (
  id INTEGER NOT NULL,
  dataset_key INTEGER NOT NULL,
  sector_key INTEGER,
  verbatim_key INTEGER,
  taxon_id TEXT NOT NULL,
  property TEXT NOT NULL,
  value TEXT NOT NULL,
  reference_id TEXT,
  page TEXT,
  ordinal INTEGER,
  remarks TEXT,
  created_by INTEGER NOT NULL,
  modified_by INTEGER NOT NULL,
  created TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  modified TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (dataset_key, id),
  FOREIGN KEY (dataset_key, verbatim_key) REFERENCES verbatim,
  FOREIGN KEY (dataset_key, sector_key) REFERENCES sector,
  FOREIGN KEY (dataset_key, reference_id) REFERENCES reference,
  FOREIGN KEY (dataset_key, taxon_id) REFERENCES name_usage
) PARTITION BY HASH (dataset_key);

CREATE INDEX ON taxon_property (dataset_key, taxon_id);
CREATE INDEX ON taxon_property (dataset_key, sector_key);
CREATE INDEX ON taxon_property (dataset_key, verbatim_key);
CREATE INDEX ON taxon_property (dataset_key, reference_id);
CREATE INDEX ON taxon_property (dataset_key, property);

--
-- SHARED TABLES REFERRING TO DATA TABLES
--
CREATE TABLE name_usage_archive_match (
  dataset_key INTEGER NOT NULL,
  index_id INTEGER REFERENCES names_index,
  usage_id TEXT NOT NULL,
  type MATCHTYPE NOT NULL,
  PRIMARY KEY (dataset_key, usage_id)
);
CREATE INDEX ON name_usage_archive_match (dataset_key, index_id);
CREATE INDEX ON name_usage_archive_match (index_id);

--
-- LOOKUPS mostly for manual queries
--
CREATE TABLE latin29 (
  id TEXT,
  idnum INTEGER,
  PRIMARY KEY (id)
);


-- FUNCTIONS
CREATE FUNCTION plaziGbifKey() RETURNS UUID AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$
LANGUAGE SQL
IMMUTABLE PARALLEL SAFE;

-- understands the status enum
CREATE OR REPLACE FUNCTION isSynonym(status TAXONOMICSTATUS) RETURNS BOOLEAN AS $$
  SELECT status IN ('SYNONYM','AMBIGUOUS_SYNONYM','MISAPPLIED')
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

CREATE AGGREGATE array_cat_agg(anycompatiblearray) (
  SFUNC=array_cat,
  STYPE=anycompatiblearray
);

CREATE OR REPLACE FUNCTION array_reverse(anyarray) RETURNS anyarray AS $$
SELECT ARRAY(
    SELECT $1[i]
    FROM generate_subscripts($1,1) AS s(i)
    ORDER BY i DESC
);
$$ LANGUAGE 'sql' STRICT IMMUTABLE PARALLEL SAFE;

-- return the simple name for the given usage id
CREATE OR REPLACE FUNCTION build_sn(v_dataset_key INTEGER, v_id TEXT) RETURNS simple_name AS $$
  SELECT (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
$$ LANGUAGE SQL;

-- return all parent names as an array
CREATE OR REPLACE FUNCTION classification(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false, v_max_depth INTEGER default 100) RETURNS TEXT[] AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, n.scientific_name, t.parent_id, 1 distance FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL
  SELECT t.id, n.scientific_name, t.parent_id, x.distance+1 FROM x, name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance < v_max_depth
  ) SELECT array_reverse(array_agg(scientific_name)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;


-- return all parent usage ids as an array
CREATE OR REPLACE FUNCTION classification_id(v_dataset_key INTEGER, v_id TEXT, v_max_depth INTEGER default 100) RETURNS TEXT[] AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, t.parent_id, 1 distance FROM name_usage t
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL
  SELECT t.id, t.parent_id, x.distance+1 FROM x, name_usage t
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance < v_max_depth
  ) SELECT array_reverse(array_agg(id)) FROM x WHERE id != v_id;
$$ LANGUAGE SQL;


-- return all parent name usages as a simple_name array
CREATE OR REPLACE FUNCTION classification_sn(v_dataset_key INTEGER, v_id TEXT, v_inc_self BOOLEAN default false, v_max_depth INTEGER default 100) RETURNS simple_name[] AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, t.parent_id, 1 distance, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL
  SELECT t.id, t.parent_id, x.distance+1, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id AND x.distance < v_max_depth
  ) SELECT array_reverse(array_agg(sn)) FROM x WHERE v_inc_self OR id != v_id;
$$ LANGUAGE SQL;


-- email domain extract function
create or replace function get_domainname(_value text)
returns text as $$
begin
_value := reverse(_value);

return nullif(reverse(substring(_value, 0, strpos(_value, '@'))), '');
end;
$$ language plpgsql
immutable returns null on null input
;

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

CREATE VIEW v_last_dataset_import AS (
 SELECT DISTINCT ON (dataset_key) dataset_key, attempt
 FROM dataset_import
 ORDER BY dataset_key, attempt
);


-- we track counts for usages and names to avoid long count() queries
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
