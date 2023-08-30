
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;


CREATE FUNCTION public.text2agent(text) RETURNS public.agent
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT ROW(null, null, $1, null, null, null, null, null, null, null, null, null)::agent
$_$;

ALTER FUNCTION public.text2agent(text) OWNER TO col;

CREATE CAST (text AS public.agent) WITH FUNCTION public.text2agent(text);

CREATE FUNCTION public.text2cslname(text) RETURNS public.cslname
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT ROW(null, $1, null)::cslname
$_$;

ALTER FUNCTION public.text2cslname(text) OWNER TO col;

CREATE CAST (text AS public.cslname) WITH FUNCTION public.text2cslname(text);

CREATE FUNCTION public.agent_str(public.agent[]) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT array_to_string($1, ' ')
$_$;

ALTER FUNCTION public.agent_str(public.agent[]) OWNER TO col;

CREATE FUNCTION public.agent_str(public.agent) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT $1::text
$_$;

ALTER FUNCTION public.agent_str(public.agent) OWNER TO col;

CREATE FUNCTION public.ambiguousranks() RETURNS public.rank[]
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT ARRAY['SUPERSECTION','SECTION','SUBSECTION','SUPERSERIES','SERIES','SUBSERIES','OTHER','UNRANKED']::rank[]
$$;

ALTER FUNCTION public.ambiguousranks() OWNER TO col;

CREATE FUNCTION public.array_agg_nonull(a anyarray, b anyarray) RETURNS anyarray
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
    AS $$
BEGIN
    IF b IS NOT NULL THEN
        a := array_cat(a, b);
    END IF;
    RETURN a;
END;
$$;

ALTER FUNCTION public.array_agg_nonull(a anyarray, b anyarray) OWNER TO col;

CREATE FUNCTION public.array_agg_nonull(a anyarray, b anynonarray) RETURNS anyarray
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
    AS $$
BEGIN
    IF b IS NOT NULL THEN
        a := array_append(a, b);
    END IF;
    RETURN a;
END;
$$;

ALTER FUNCTION public.array_agg_nonull(a anyarray, b anynonarray) OWNER TO col;

CREATE FUNCTION public.array_distinct(anyarray) RETURNS anyarray
    LANGUAGE sql
    AS $_$
  SELECT ARRAY(SELECT DISTINCT unnest($1))
$_$;

ALTER FUNCTION public.array_distinct(anyarray) OWNER TO col;

CREATE FUNCTION public.array_reverse(anyarray) RETURNS anyarray
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
SELECT ARRAY(
    SELECT $1[i]
    FROM generate_subscripts($1,1) AS s(i)
    ORDER BY i DESC
);
$_$;

ALTER FUNCTION public.array_reverse(anyarray) OWNER TO col;

CREATE FUNCTION public.array_str(text[]) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT array_to_string($1, ' ')
$_$;

ALTER FUNCTION public.array_str(text[]) OWNER TO col;

CREATE FUNCTION public.build_sn(v_dataset_key integer, v_id text) RETURNS public.simple_name
    LANGUAGE sql
    AS $$
  SELECT (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
$$;

ALTER FUNCTION public.build_sn(v_dataset_key integer, v_id text) OWNER TO col;

CREATE FUNCTION public.classification(v_dataset_key integer, v_id text, v_inc_self boolean DEFAULT false) RETURNS text[]
    LANGUAGE sql
    AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, n.scientific_name, t.parent_id FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL
  SELECT t.id, n.scientific_name, t.parent_id FROM x, name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id
  ) SELECT array_reverse(array_agg(scientific_name)) FROM x WHERE v_inc_self OR id != v_id;
$$;

ALTER FUNCTION public.classification(v_dataset_key integer, v_id text, v_inc_self boolean) OWNER TO col;

CREATE FUNCTION public.classification_sn(v_dataset_key integer, v_id text, v_inc_self boolean DEFAULT false) RETURNS public.simple_name[]
    LANGUAGE sql
    AS $$
  WITH RECURSIVE x AS (
  SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name AS sn FROM name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = v_id
   UNION ALL
  SELECT t.id, t.parent_id, (t.id,n.rank,n.scientific_name,n.authorship)::simple_name FROM x, name_usage t
    JOIN name n ON n.dataset_key=v_dataset_key AND n.id=t.name_id
    WHERE t.dataset_key=v_dataset_key AND t.id = x.parent_id
  ) SELECT array_reverse(array_agg(sn)) FROM x WHERE v_inc_self OR id != v_id;
$$;

ALTER FUNCTION public.classification_sn(v_dataset_key integer, v_id text, v_inc_self boolean) OWNER TO col;

CREATE FUNCTION public.cslname_str(public.cslname[]) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT array_to_string($1, ' ')
$_$;

ALTER FUNCTION public.cslname_str(public.cslname[]) OWNER TO col;

CREATE FUNCTION public.cslname_str(public.cslname) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT $1::text
$_$;

ALTER FUNCTION public.cslname_str(public.cslname) OWNER TO col;

CREATE FUNCTION public.escape_like(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $_$
SELECT replace(replace(replace($1
         , '\', '\\')  -- must come 1st
         , '%', '\%')
         , '_', '\_');
$_$;

ALTER FUNCTION public.escape_like(text) OWNER TO col;

CREATE FUNCTION public.f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $_$
SELECT public.unaccent('public.unaccent', $1)  -- schema-qualify function and dictionary
$_$;

ALTER FUNCTION public.f_unaccent(text) OWNER TO col;

CREATE FUNCTION public.is_synonym(status public.taxonomicstatus) RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT status IN ('SYNONYM','AMBIGUOUS_SYNONYM','MISAPPLIED')
$$;

ALTER FUNCTION public.is_synonym(status public.taxonomicstatus) OWNER TO col;

CREATE FUNCTION public.issynonym(status public.taxonomicstatus) RETURNS boolean
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT status IN ('SYNONYM','AMBIGUOUS_SYNONYM','MISAPPLIED')
$$;

ALTER FUNCTION public.issynonym(status public.taxonomicstatus) OWNER TO col;

CREATE FUNCTION public.parseint(v_value text) RETURNS integer
    LANGUAGE plpgsql
    AS $$
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
$$;

ALTER FUNCTION public.parseint(v_value text) OWNER TO col;

CREATE FUNCTION public.plazigbifkey() RETURNS uuid
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT '7ce8aef0-9e92-11dc-8738-b8a03c50a862'::uuid
$$;

ALTER FUNCTION public.plazigbifkey() OWNER TO col;

CREATE FUNCTION public.repl_ws(x text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS $$
  SELECT regexp_replace(x, '\s', ' ', 'g' )
$$;

ALTER FUNCTION public.repl_ws(x text) OWNER TO col;

CREATE FUNCTION public.track_usage_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
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
$$;

ALTER FUNCTION public.track_usage_count() OWNER TO col;

CREATE AGGREGATE public.array_agg_nonull(anyarray) (
    SFUNC = public.array_agg_nonull,
    STYPE = anyarray,
    INITCOND = '{}'
);

ALTER AGGREGATE public.array_agg_nonull(anyarray) OWNER TO col;

CREATE AGGREGATE public.array_agg_nonull(anynonarray) (
    SFUNC = public.array_agg_nonull,
    STYPE = anyarray,
    INITCOND = '{}'
);

ALTER AGGREGATE public.array_agg_nonull(anynonarray) OWNER TO col;

CREATE AGGREGATE public.array_cat_agg(anycompatiblearray) (
    SFUNC = array_cat,
    STYPE = anycompatiblearray
);

ALTER AGGREGATE public.array_cat_agg(anycompatiblearray) OWNER TO col;

CREATE TEXT SEARCH CONFIGURATION public.dataset (
    PARSER = pg_catalog."default" );

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR asciiword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR word WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR numword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR email WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR url WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR host WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR sfloat WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR version WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR hword_numpart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR hword_part WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR hword_asciipart WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR numhword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR asciihword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR hword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR url_path WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR file WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR "float" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR "int" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset
    ADD MAPPING FOR uint WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.dataset OWNER TO col;

CREATE TEXT SEARCH CONFIGURATION public.reference (
    PARSER = pg_catalog."default" );

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR asciiword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR word WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR numword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR email WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR url WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR host WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR sfloat WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR version WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR hword_numpart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR hword_part WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR hword_asciipart WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR numhword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR asciihword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR hword WITH english_stem;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR url_path WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR file WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR "float" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR "int" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference
    ADD MAPPING FOR uint WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.reference OWNER TO col;

CREATE TEXT SEARCH CONFIGURATION public.verbatim (
    PARSER = pg_catalog."default" );

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR asciiword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR word WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR numword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR email WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR url WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR host WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR sfloat WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR version WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR hword_numpart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR hword_part WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR hword_asciipart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR numhword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR asciihword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR hword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR url_path WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR file WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR "float" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR "int" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim
    ADD MAPPING FOR uint WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.verbatim OWNER TO col;

CREATE TEXT SEARCH CONFIGURATION public.vernacular (
    PARSER = pg_catalog."default" );

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR asciiword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR word WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR numword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR email WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR url WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR host WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR sfloat WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR version WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR hword_numpart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR hword_part WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR hword_asciipart WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR numhword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR asciihword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR hword WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR url_path WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR file WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR "float" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR "int" WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular
    ADD MAPPING FOR uint WITH simple;

ALTER TEXT SEARCH CONFIGURATION public.vernacular OWNER TO col;

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE public.dataset (
    key integer NOT NULL,
    doi text,
    source_key integer,
    attempt integer,
    private boolean DEFAULT false,
    type public.datasettype DEFAULT 'OTHER'::public.datasettype NOT NULL,
    origin public.datasetorigin NOT NULL,
    gbif_key uuid,
    gbif_publisher_key uuid,
    identifier public.hstore,
    title text NOT NULL,
    alias text,
    description text,
    issued text,
    version text,
    issn text,
    contact public.agent,
    creator public.agent[],
    editor public.agent[],
    publisher public.agent,
    contributor public.agent[],
    keyword text[],
    geographic_scope text,
    taxonomic_scope text,
    temporal_scope text,
    confidence integer,
    completeness integer,
    license public.license,
    url text,
    logo text,
    notes text,
    settings jsonb,
    acl_editor integer[],
    acl_reviewer integer[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    deleted timestamp without time zone,
    doc tsvector GENERATED ALWAYS AS (((((((((((((((((setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(alias, ''::text))), 'A'::"char") || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE((key)::text, ''::text))), 'A'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(doi, ''::text))), 'B'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(title, ''::text))), 'B'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.array_str(keyword), ''::text))), 'B'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(geographic_scope, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(taxonomic_scope, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(temporal_scope, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(issn, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE((gbif_key)::text, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE((identifier)::text, ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.agent_str(contact), ''::text))), 'C'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.agent_str(creator), ''::text))), 'D'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.agent_str(publisher), ''::text))), 'D'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.agent_str(editor), ''::text))), 'D'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(public.agent_str(contributor), ''::text))), 'D'::"char")) || setweight(to_tsvector('public.dataset'::regconfig, public.f_unaccent(COALESCE(description, ''::text))), 'D'::"char"))) STORED,
    CONSTRAINT dataset_completeness_check CHECK (((completeness >= 0) AND (completeness <= 100))),
    CONSTRAINT dataset_confidence_check CHECK (((confidence > 0) AND (confidence <= 5)))
);

ALTER TABLE public.dataset OWNER TO col;

CREATE TABLE public.dataset_archive (
    key integer NOT NULL,
    doi text,
    source_key integer,
    attempt integer NOT NULL,
    type public.datasettype NOT NULL,
    origin public.datasetorigin NOT NULL,
    identifier public.hstore,
    title text NOT NULL,
    alias text,
    description text,
    issued text,
    version text,
    issn text,
    contact public.agent,
    creator public.agent[],
    editor public.agent[],
    publisher public.agent,
    contributor public.agent[],
    keyword text[],
    geographic_scope text,
    taxonomic_scope text,
    temporal_scope text,
    confidence integer,
    completeness integer,
    license public.license,
    url text,
    logo text,
    notes text,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone,
    modified timestamp without time zone
);

ALTER TABLE public.dataset_archive OWNER TO col;

CREATE TABLE public.dataset_archive_citation (
    dataset_key integer,
    id text,
    type text,
    doi text,
    author public.cslname[],
    editor public.cslname[],
    title text,
    container_author public.cslname[],
    container_title text,
    issued text,
    accessed text,
    collection_editor public.cslname[],
    collection_title text,
    volume text,
    issue text,
    edition text,
    page text,
    publisher text,
    publisher_place text,
    version text,
    isbn text,
    issn text,
    url text,
    note text,
    attempt integer NOT NULL
);

ALTER TABLE public.dataset_archive_citation OWNER TO col;

CREATE TABLE public.dataset_citation (
    dataset_key integer,
    id text,
    type text,
    doi text,
    author public.cslname[],
    editor public.cslname[],
    title text,
    container_author public.cslname[],
    container_title text,
    issued text,
    accessed text,
    collection_editor public.cslname[],
    collection_title text,
    volume text,
    issue text,
    edition text,
    page text,
    publisher text,
    publisher_place text,
    version text,
    isbn text,
    issn text,
    url text,
    note text
);

ALTER TABLE public.dataset_citation OWNER TO col;

CREATE TABLE public.dataset_export (
    key uuid NOT NULL,
    dataset_key integer NOT NULL,
    format public.dataformat NOT NULL,
    excel boolean NOT NULL,
    root public.simple_name,
    synonyms boolean NOT NULL,
    min_rank public.rank,
    created_by integer NOT NULL,
    created timestamp without time zone NOT NULL,
    modified_by integer,
    modified timestamp without time zone,
    attempt integer,
    started timestamp without time zone,
    finished timestamp without time zone,
    deleted timestamp without time zone,
    classification public.simple_name[],
    status public.jobstatus NOT NULL,
    error text,
    truncated text[],
    md5 text,
    size integer,
    synonym_count integer,
    taxon_count integer,
    taxa_by_rank_count public.hstore
);

ALTER TABLE public.dataset_export OWNER TO col;

CREATE TABLE public.dataset_import (
    dataset_key integer NOT NULL,
    attempt integer NOT NULL,
    state public.importstate NOT NULL,
    origin public.datasetorigin NOT NULL,
    format public.dataformat,
    started timestamp without time zone,
    finished timestamp without time zone,
    download timestamp without time zone,
    created_by integer NOT NULL,
    verbatim_count integer,
    applied_decision_count integer,
    bare_name_count integer,
    distribution_count integer,
    estimate_count integer,
    media_count integer,
    name_count integer,
    reference_count integer,
    synonym_count integer,
    taxon_count integer,
    treatment_count integer,
    type_material_count integer,
    vernacular_count integer,
    distributions_by_gazetteer_count public.hstore,
    extinct_taxa_by_rank_count public.hstore,
    ignored_by_reason_count public.hstore,
    issues_by_issue_count public.hstore,
    media_by_type_count public.hstore,
    name_relations_by_type_count public.hstore,
    names_by_code_count public.hstore,
    names_by_rank_count public.hstore,
    names_by_status_count public.hstore,
    names_by_type_count public.hstore,
    species_interactions_by_type_count public.hstore,
    synonyms_by_rank_count public.hstore,
    taxa_by_rank_count public.hstore,
    taxon_concept_relations_by_type_count public.hstore,
    type_material_by_status_count public.hstore,
    usages_by_origin_count public.hstore,
    usages_by_status_count public.hstore,
    vernaculars_by_language_count public.hstore,
    verbatim_by_row_type_count jsonb,
    verbatim_by_term_count public.hstore,
    job text NOT NULL,
    error text,
    md5 text,
    download_uri text
);

ALTER TABLE public.dataset_import OWNER TO col;

CREATE SEQUENCE public.dataset_key_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.dataset_key_seq OWNER TO col;

ALTER SEQUENCE public.dataset_key_seq OWNED BY public.dataset.key;

CREATE TABLE public.dataset_patch (
    key integer NOT NULL,
    doi text,
    identifier public.hstore,
    title text,
    alias text,
    description text,
    issued text,
    version text,
    issn text,
    contact public.agent,
    creator public.agent[],
    editor public.agent[],
    publisher public.agent,
    contributor public.agent[],
    keyword text[],
    geographic_scope text,
    taxonomic_scope text,
    temporal_scope text,
    confidence integer,
    completeness integer,
    license public.license,
    url text,
    logo text,
    notes text,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone,
    modified timestamp without time zone,
    dataset_key integer NOT NULL
);

ALTER TABLE public.dataset_patch OWNER TO col;

CREATE TABLE public.dataset_source (
    key integer NOT NULL,
    doi text,
    source_key integer,
    attempt integer,
    type public.datasettype NOT NULL,
    origin public.datasetorigin NOT NULL,
    identifier public.hstore,
    title text NOT NULL,
    alias text,
    description text,
    issued text,
    version text,
    issn text,
    contact public.agent,
    creator public.agent[],
    editor public.agent[],
    publisher public.agent,
    contributor public.agent[],
    keyword text[],
    geographic_scope text,
    taxonomic_scope text,
    temporal_scope text,
    confidence integer,
    completeness integer,
    license public.license,
    url text,
    logo text,
    notes text,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone,
    modified timestamp without time zone,
    dataset_key integer NOT NULL
);

ALTER TABLE public.dataset_source OWNER TO col;

CREATE TABLE public.dataset_source_citation (
    dataset_key integer,
    id text,
    type text,
    doi text,
    author public.cslname[],
    editor public.cslname[],
    title text,
    container_author public.cslname[],
    container_title text,
    issued text,
    accessed text,
    collection_editor public.cslname[],
    collection_title text,
    volume text,
    issue text,
    edition text,
    page text,
    publisher text,
    publisher_place text,
    version text,
    isbn text,
    issn text,
    url text,
    note text,
    release_key integer
);

ALTER TABLE public.dataset_source_citation OWNER TO col;

CREATE TABLE public.decision (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    subject_dataset_key integer NOT NULL,
    subject_rank public.rank,
    subject_code public.nomcode,
    subject_status public.taxonomicstatus,
    mode public.editorialdecision_mode NOT NULL,
    status public.taxonomicstatus,
    extinct boolean,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    original_subject_id text,
    subject_id text,
    subject_name text,
    subject_authorship text,
    subject_parent text,
    temporal_range_start text,
    temporal_range_end text,
    name jsonb,
    note text,
    CONSTRAINT decision_check CHECK ((dataset_key <> subject_dataset_key))
);

ALTER TABLE public.decision OWNER TO col;

CREATE TABLE public.distribution (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.distribution OWNER TO col;

CREATE TABLE public.distribution_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod0 OWNER TO col;

CREATE TABLE public.distribution_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod1 OWNER TO col;

CREATE TABLE public.distribution_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod10 OWNER TO col;

CREATE TABLE public.distribution_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod11 OWNER TO col;

CREATE TABLE public.distribution_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod12 OWNER TO col;

CREATE TABLE public.distribution_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod13 OWNER TO col;

CREATE TABLE public.distribution_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod14 OWNER TO col;

CREATE TABLE public.distribution_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod15 OWNER TO col;

CREATE TABLE public.distribution_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod16 OWNER TO col;

CREATE TABLE public.distribution_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod17 OWNER TO col;

CREATE TABLE public.distribution_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod18 OWNER TO col;

CREATE TABLE public.distribution_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod19 OWNER TO col;

CREATE TABLE public.distribution_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod2 OWNER TO col;

CREATE TABLE public.distribution_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod20 OWNER TO col;

CREATE TABLE public.distribution_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod21 OWNER TO col;

CREATE TABLE public.distribution_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod22 OWNER TO col;

CREATE TABLE public.distribution_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod23 OWNER TO col;

CREATE TABLE public.distribution_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod3 OWNER TO col;

CREATE TABLE public.distribution_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod4 OWNER TO col;

CREATE TABLE public.distribution_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod5 OWNER TO col;

CREATE TABLE public.distribution_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod6 OWNER TO col;

CREATE TABLE public.distribution_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod7 OWNER TO col;

CREATE TABLE public.distribution_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod8 OWNER TO col;

CREATE TABLE public.distribution_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    gazetteer public.gazetteer NOT NULL,
    status public.distributionstatus,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    area text NOT NULL,
    reference_id text
);

ALTER TABLE public.distribution_mod9 OWNER TO col;

CREATE TABLE public.estimate (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.estimate OWNER TO col;

CREATE TABLE public.estimate_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod0 OWNER TO col;

CREATE TABLE public.estimate_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod1 OWNER TO col;

CREATE TABLE public.estimate_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod10 OWNER TO col;

CREATE TABLE public.estimate_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod11 OWNER TO col;

CREATE TABLE public.estimate_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod12 OWNER TO col;

CREATE TABLE public.estimate_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod13 OWNER TO col;

CREATE TABLE public.estimate_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod14 OWNER TO col;

CREATE TABLE public.estimate_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod15 OWNER TO col;

CREATE TABLE public.estimate_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod16 OWNER TO col;

CREATE TABLE public.estimate_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod17 OWNER TO col;

CREATE TABLE public.estimate_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod18 OWNER TO col;

CREATE TABLE public.estimate_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod19 OWNER TO col;

CREATE TABLE public.estimate_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod2 OWNER TO col;

CREATE TABLE public.estimate_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod20 OWNER TO col;

CREATE TABLE public.estimate_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod21 OWNER TO col;

CREATE TABLE public.estimate_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod22 OWNER TO col;

CREATE TABLE public.estimate_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod23 OWNER TO col;

CREATE TABLE public.estimate_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod3 OWNER TO col;

CREATE TABLE public.estimate_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod4 OWNER TO col;

CREATE TABLE public.estimate_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod5 OWNER TO col;

CREATE TABLE public.estimate_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod6 OWNER TO col;

CREATE TABLE public.estimate_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod7 OWNER TO col;

CREATE TABLE public.estimate_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod8 OWNER TO col;

CREATE TABLE public.estimate_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    verbatim_key integer,
    target_rank public.rank,
    target_code public.nomcode,
    estimate integer,
    type public.estimatetype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    target_id text,
    target_name text,
    target_authorship text,
    reference_id text,
    note text
);

ALTER TABLE public.estimate_mod9 OWNER TO col;

CREATE TABLE public.id_report (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    type public.idreporttype NOT NULL
);

ALTER TABLE public.id_report OWNER TO col;

CREATE TABLE public.latin29 (
    id text NOT NULL,
    idnum integer
);

ALTER TABLE public.latin29 OWNER TO col;

CREATE TABLE public.media (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.media OWNER TO col;

CREATE TABLE public.media_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod0 OWNER TO col;

CREATE TABLE public.media_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod1 OWNER TO col;

CREATE TABLE public.media_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod10 OWNER TO col;

CREATE TABLE public.media_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod11 OWNER TO col;

CREATE TABLE public.media_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod12 OWNER TO col;

CREATE TABLE public.media_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod13 OWNER TO col;

CREATE TABLE public.media_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod14 OWNER TO col;

CREATE TABLE public.media_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod15 OWNER TO col;

CREATE TABLE public.media_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod16 OWNER TO col;

CREATE TABLE public.media_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod17 OWNER TO col;

CREATE TABLE public.media_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod18 OWNER TO col;

CREATE TABLE public.media_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod19 OWNER TO col;

CREATE TABLE public.media_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod2 OWNER TO col;

CREATE TABLE public.media_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod20 OWNER TO col;

CREATE TABLE public.media_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod21 OWNER TO col;

CREATE TABLE public.media_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod22 OWNER TO col;

CREATE TABLE public.media_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod23 OWNER TO col;

CREATE TABLE public.media_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod3 OWNER TO col;

CREATE TABLE public.media_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod4 OWNER TO col;

CREATE TABLE public.media_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod5 OWNER TO col;

CREATE TABLE public.media_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod6 OWNER TO col;

CREATE TABLE public.media_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod7 OWNER TO col;

CREATE TABLE public.media_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod8 OWNER TO col;

CREATE TABLE public.media_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.mediatype,
    captured date,
    license public.license,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    url text,
    format text,
    title text,
    captured_by text,
    link text,
    reference_id text
);

ALTER TABLE public.media_mod9 OWNER TO col;

CREATE TABLE public.name (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.name OWNER TO col;

CREATE TABLE public.name_match (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.name_match OWNER TO col;

CREATE TABLE public.name_match_mod0 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod0 OWNER TO col;

CREATE TABLE public.name_match_mod1 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod1 OWNER TO col;

CREATE TABLE public.name_match_mod10 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod10 OWNER TO col;

CREATE TABLE public.name_match_mod11 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod11 OWNER TO col;

CREATE TABLE public.name_match_mod12 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod12 OWNER TO col;

CREATE TABLE public.name_match_mod13 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod13 OWNER TO col;

CREATE TABLE public.name_match_mod14 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod14 OWNER TO col;

CREATE TABLE public.name_match_mod15 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod15 OWNER TO col;

CREATE TABLE public.name_match_mod16 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod16 OWNER TO col;

CREATE TABLE public.name_match_mod17 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod17 OWNER TO col;

CREATE TABLE public.name_match_mod18 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod18 OWNER TO col;

CREATE TABLE public.name_match_mod19 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod19 OWNER TO col;

CREATE TABLE public.name_match_mod2 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod2 OWNER TO col;

CREATE TABLE public.name_match_mod20 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod20 OWNER TO col;

CREATE TABLE public.name_match_mod21 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod21 OWNER TO col;

CREATE TABLE public.name_match_mod22 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod22 OWNER TO col;

CREATE TABLE public.name_match_mod23 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod23 OWNER TO col;

CREATE TABLE public.name_match_mod3 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod3 OWNER TO col;

CREATE TABLE public.name_match_mod4 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod4 OWNER TO col;

CREATE TABLE public.name_match_mod5 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod5 OWNER TO col;

CREATE TABLE public.name_match_mod6 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod6 OWNER TO col;

CREATE TABLE public.name_match_mod7 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod7 OWNER TO col;

CREATE TABLE public.name_match_mod8 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod8 OWNER TO col;

CREATE TABLE public.name_match_mod9 (
    dataset_key integer NOT NULL,
    sector_key integer,
    index_id integer NOT NULL,
    name_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_match_mod9 OWNER TO col;

CREATE TABLE public.name_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod0 OWNER TO col;

CREATE TABLE public.name_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod1 OWNER TO col;

CREATE TABLE public.name_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod10 OWNER TO col;

CREATE TABLE public.name_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod11 OWNER TO col;

CREATE TABLE public.name_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod12 OWNER TO col;

CREATE TABLE public.name_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod13 OWNER TO col;

CREATE TABLE public.name_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod14 OWNER TO col;

CREATE TABLE public.name_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod15 OWNER TO col;

CREATE TABLE public.name_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod16 OWNER TO col;

CREATE TABLE public.name_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod17 OWNER TO col;

CREATE TABLE public.name_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod18 OWNER TO col;

CREATE TABLE public.name_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod19 OWNER TO col;

CREATE TABLE public.name_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod2 OWNER TO col;

CREATE TABLE public.name_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod20 OWNER TO col;

CREATE TABLE public.name_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod21 OWNER TO col;

CREATE TABLE public.name_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod22 OWNER TO col;

CREATE TABLE public.name_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod23 OWNER TO col;

CREATE TABLE public.name_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod3 OWNER TO col;

CREATE TABLE public.name_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod4 OWNER TO col;

CREATE TABLE public.name_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod5 OWNER TO col;

CREATE TABLE public.name_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod6 OWNER TO col;

CREATE TABLE public.name_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod7 OWNER TO col;

CREATE TABLE public.name_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod8 OWNER TO col;

CREATE TABLE public.name_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    rank public.rank NOT NULL,
    candidatus boolean DEFAULT false,
    notho public.namepart,
    code public.nomcode,
    nom_status public.nomstatus,
    origin public.origin NOT NULL,
    type public.nametype NOT NULL,
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in_id text,
    published_in_page text,
    published_in_page_link text,
    nomenclatural_note text,
    unparsed text,
    identifier text[],
    link text,
    remarks text,
    scientific_name_normalized text NOT NULL,
    authorship_normalized text[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now()
);

ALTER TABLE public.name_mod9 OWNER TO col;

CREATE TABLE public.name_rel (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.name_rel OWNER TO col;

CREATE TABLE public.name_rel_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod0 OWNER TO col;

CREATE TABLE public.name_rel_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod1 OWNER TO col;

CREATE TABLE public.name_rel_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod10 OWNER TO col;

CREATE TABLE public.name_rel_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod11 OWNER TO col;

CREATE TABLE public.name_rel_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod12 OWNER TO col;

CREATE TABLE public.name_rel_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod13 OWNER TO col;

CREATE TABLE public.name_rel_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod14 OWNER TO col;

CREATE TABLE public.name_rel_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod15 OWNER TO col;

CREATE TABLE public.name_rel_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod16 OWNER TO col;

CREATE TABLE public.name_rel_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod17 OWNER TO col;

CREATE TABLE public.name_rel_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod18 OWNER TO col;

CREATE TABLE public.name_rel_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod19 OWNER TO col;

CREATE TABLE public.name_rel_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod2 OWNER TO col;

CREATE TABLE public.name_rel_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod20 OWNER TO col;

CREATE TABLE public.name_rel_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod21 OWNER TO col;

CREATE TABLE public.name_rel_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod22 OWNER TO col;

CREATE TABLE public.name_rel_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod23 OWNER TO col;

CREATE TABLE public.name_rel_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod3 OWNER TO col;

CREATE TABLE public.name_rel_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod4 OWNER TO col;

CREATE TABLE public.name_rel_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod5 OWNER TO col;

CREATE TABLE public.name_rel_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod6 OWNER TO col;

CREATE TABLE public.name_rel_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod7 OWNER TO col;

CREATE TABLE public.name_rel_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod8 OWNER TO col;

CREATE TABLE public.name_rel_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.nomreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    related_name_id text,
    reference_id text,
    remarks text
);

ALTER TABLE public.name_rel_mod9 OWNER TO col;

CREATE TABLE public.name_usage (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.name_usage OWNER TO col;

CREATE TABLE public.name_usage_archive (
    id text NOT NULL,
    n_id text NOT NULL,
    dataset_key integer NOT NULL,
    n_rank public.rank NOT NULL,
    n_candidatus boolean DEFAULT false,
    n_notho public.namepart,
    n_code public.nomcode,
    n_nom_status public.nomstatus,
    n_origin public.origin NOT NULL,
    n_type public.nametype NOT NULL,
    n_scientific_name text NOT NULL,
    n_authorship text,
    n_uninomial text,
    n_genus text,
    n_infrageneric_epithet text,
    n_specific_epithet text,
    n_infraspecific_epithet text,
    n_cultivar_epithet text,
    n_basionym_authors text[] DEFAULT '{}'::text[],
    n_basionym_ex_authors text[] DEFAULT '{}'::text[],
    n_basionym_year text,
    n_combination_authors text[] DEFAULT '{}'::text[],
    n_combination_ex_authors text[] DEFAULT '{}'::text[],
    n_combination_year text,
    n_sanctioning_author text,
    n_published_in_id text,
    n_published_in_page text,
    n_published_in_page_link text,
    n_nomenclatural_note text,
    n_unparsed text,
    n_identifier text[],
    n_link text,
    n_remarks text,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to text,
    basionym public.simple_name,
    classification public.simple_name[],
    published_in text,
    first_release_key integer,
    last_release_key integer
);

ALTER TABLE public.name_usage_archive OWNER TO col;

CREATE TABLE public.name_usage_archive_match (
    dataset_key integer NOT NULL,
    index_id integer NOT NULL,
    usage_id text NOT NULL,
    type public.matchtype
);

ALTER TABLE public.name_usage_archive_match OWNER TO col;

CREATE TABLE public.name_usage_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod0 OWNER TO col;

CREATE TABLE public.name_usage_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod1 OWNER TO col;

CREATE TABLE public.name_usage_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod10 OWNER TO col;

CREATE TABLE public.name_usage_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod11 OWNER TO col;

CREATE TABLE public.name_usage_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod12 OWNER TO col;

CREATE TABLE public.name_usage_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod13 OWNER TO col;

CREATE TABLE public.name_usage_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod14 OWNER TO col;

CREATE TABLE public.name_usage_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod15 OWNER TO col;

CREATE TABLE public.name_usage_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod16 OWNER TO col;

CREATE TABLE public.name_usage_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod17 OWNER TO col;

CREATE TABLE public.name_usage_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod18 OWNER TO col;

CREATE TABLE public.name_usage_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod19 OWNER TO col;

CREATE TABLE public.name_usage_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod2 OWNER TO col;

CREATE TABLE public.name_usage_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod20 OWNER TO col;

CREATE TABLE public.name_usage_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod21 OWNER TO col;

CREATE TABLE public.name_usage_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod22 OWNER TO col;

CREATE TABLE public.name_usage_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod23 OWNER TO col;

CREATE TABLE public.name_usage_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod3 OWNER TO col;

CREATE TABLE public.name_usage_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod4 OWNER TO col;

CREATE TABLE public.name_usage_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod5 OWNER TO col;

CREATE TABLE public.name_usage_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod6 OWNER TO col;

CREATE TABLE public.name_usage_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod7 OWNER TO col;

CREATE TABLE public.name_usage_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod8 OWNER TO col;

CREATE TABLE public.name_usage_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    extinct boolean,
    status public.taxonomicstatus NOT NULL,
    origin public.origin NOT NULL,
    parent_id text,
    name_id text NOT NULL,
    name_phrase text,
    identifier text[],
    link text,
    remarks text,
    according_to_id text,
    scrutinizer text,
    scrutinizer_date text,
    reference_ids text[] DEFAULT '{}'::text[],
    temporal_range_start text,
    temporal_range_end text,
    environments public.environment[] DEFAULT '{}'::public.environment[],
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    dataset_sectors jsonb
);

ALTER TABLE public.name_usage_mod9 OWNER TO col;

CREATE TABLE public.names_index (
    id integer NOT NULL,
    canonical_id integer NOT NULL,
    rank public.rank NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    scientific_name text NOT NULL,
    authorship text,
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    remarks text
);

ALTER TABLE public.names_index OWNER TO col;

CREATE SEQUENCE public.names_index_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.names_index_id_seq OWNER TO col;

ALTER SEQUENCE public.names_index_id_seq OWNED BY public.names_index.id;

CREATE TABLE public.parser_config (
    id text NOT NULL,
    candidatus boolean DEFAULT false,
    extinct boolean DEFAULT false,
    rank public.rank NOT NULL,
    notho public.namepart,
    code public.nomcode,
    type public.nametype NOT NULL,
    created_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    uninomial text,
    genus text,
    infrageneric_epithet text,
    specific_epithet text,
    infraspecific_epithet text,
    cultivar_epithet text,
    basionym_authors text[] DEFAULT '{}'::text[],
    basionym_ex_authors text[] DEFAULT '{}'::text[],
    basionym_year text,
    combination_authors text[] DEFAULT '{}'::text[],
    combination_ex_authors text[] DEFAULT '{}'::text[],
    combination_year text,
    sanctioning_author text,
    published_in text,
    nomenclatural_note text,
    taxonomic_note text,
    unparsed text,
    remarks text
);

ALTER TABLE public.parser_config OWNER TO col;

CREATE TABLE public.reference (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.reference OWNER TO col;

CREATE TABLE public.reference_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod0 OWNER TO col;

CREATE TABLE public.reference_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod1 OWNER TO col;

CREATE TABLE public.reference_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod10 OWNER TO col;

CREATE TABLE public.reference_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod11 OWNER TO col;

CREATE TABLE public.reference_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod12 OWNER TO col;

CREATE TABLE public.reference_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod13 OWNER TO col;

CREATE TABLE public.reference_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod14 OWNER TO col;

CREATE TABLE public.reference_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod15 OWNER TO col;

CREATE TABLE public.reference_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod16 OWNER TO col;

CREATE TABLE public.reference_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod17 OWNER TO col;

CREATE TABLE public.reference_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod18 OWNER TO col;

CREATE TABLE public.reference_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod19 OWNER TO col;

CREATE TABLE public.reference_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod2 OWNER TO col;

CREATE TABLE public.reference_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod20 OWNER TO col;

CREATE TABLE public.reference_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod21 OWNER TO col;

CREATE TABLE public.reference_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod22 OWNER TO col;

CREATE TABLE public.reference_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod23 OWNER TO col;

CREATE TABLE public.reference_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod3 OWNER TO col;

CREATE TABLE public.reference_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod4 OWNER TO col;

CREATE TABLE public.reference_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod5 OWNER TO col;

CREATE TABLE public.reference_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod6 OWNER TO col;

CREATE TABLE public.reference_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod7 OWNER TO col;

CREATE TABLE public.reference_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod8 OWNER TO col;

CREATE TABLE public.reference_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    year integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    csl jsonb,
    citation text,
    doc tsvector GENERATED ALWAYS AS (((jsonb_to_tsvector('public.reference'::regconfig, COALESCE(csl, '{}'::jsonb), '["string", "numeric"]'::jsonb) || to_tsvector('public.reference'::regconfig, COALESCE(citation, ''::text))) || to_tsvector('public.reference'::regconfig, COALESCE((year)::text, ''::text)))) STORED
);

ALTER TABLE public.reference_mod9 OWNER TO col;

CREATE TABLE public.sector (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    subject_dataset_key integer NOT NULL,
    subject_rank public.rank,
    subject_code public.nomcode,
    subject_status public.taxonomicstatus,
    target_rank public.rank,
    target_code public.nomcode,
    mode public.sector_mode NOT NULL,
    code public.nomcode,
    sync_attempt integer,
    dataset_attempt integer,
    priority integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    original_subject_id text,
    subject_id text,
    subject_name text,
    subject_authorship text,
    subject_parent text,
    target_id text,
    target_name text,
    target_authorship text,
    placeholder_rank public.rank,
    ranks public.rank[] DEFAULT '{}'::public.rank[],
    entities public.entitytype[],
    name_types public.nametype[],
    name_status_exclusion public.nomstatus[],
    note text
);

ALTER TABLE public.sector OWNER TO col;

CREATE TABLE public.sector_import (
    dataset_key integer NOT NULL,
    sector_key integer NOT NULL,
    attempt integer NOT NULL,
    dataset_attempt integer,
    started timestamp without time zone,
    finished timestamp without time zone,
    created_by integer NOT NULL,
    state public.importstate NOT NULL,
    applied_decision_count integer,
    bare_name_count integer,
    distribution_count integer,
    estimate_count integer,
    media_count integer,
    name_count integer,
    reference_count integer,
    synonym_count integer,
    taxon_count integer,
    treatment_count integer,
    type_material_count integer,
    vernacular_count integer,
    distributions_by_gazetteer_count public.hstore,
    extinct_taxa_by_rank_count public.hstore,
    ignored_by_reason_count public.hstore,
    issues_by_issue_count public.hstore,
    media_by_type_count public.hstore,
    name_relations_by_type_count public.hstore,
    names_by_code_count public.hstore,
    names_by_rank_count public.hstore,
    names_by_status_count public.hstore,
    names_by_type_count public.hstore,
    species_interactions_by_type_count public.hstore,
    synonyms_by_rank_count public.hstore,
    taxa_by_rank_count public.hstore,
    taxon_concept_relations_by_type_count public.hstore,
    type_material_by_status_count public.hstore,
    usages_by_origin_count public.hstore,
    usages_by_status_count public.hstore,
    vernaculars_by_language_count public.hstore,
    job text NOT NULL,
    warnings text[],
    error text
);

ALTER TABLE public.sector_import OWNER TO col;

CREATE TABLE public.species_interaction (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.species_interaction OWNER TO col;

CREATE TABLE public.species_interaction_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod0 OWNER TO col;

CREATE TABLE public.species_interaction_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod1 OWNER TO col;

CREATE TABLE public.species_interaction_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod10 OWNER TO col;

CREATE TABLE public.species_interaction_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod11 OWNER TO col;

CREATE TABLE public.species_interaction_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod12 OWNER TO col;

CREATE TABLE public.species_interaction_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod13 OWNER TO col;

CREATE TABLE public.species_interaction_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod14 OWNER TO col;

CREATE TABLE public.species_interaction_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod15 OWNER TO col;

CREATE TABLE public.species_interaction_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod16 OWNER TO col;

CREATE TABLE public.species_interaction_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod17 OWNER TO col;

CREATE TABLE public.species_interaction_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod18 OWNER TO col;

CREATE TABLE public.species_interaction_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod19 OWNER TO col;

CREATE TABLE public.species_interaction_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod2 OWNER TO col;

CREATE TABLE public.species_interaction_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod20 OWNER TO col;

CREATE TABLE public.species_interaction_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod21 OWNER TO col;

CREATE TABLE public.species_interaction_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod22 OWNER TO col;

CREATE TABLE public.species_interaction_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod23 OWNER TO col;

CREATE TABLE public.species_interaction_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod3 OWNER TO col;

CREATE TABLE public.species_interaction_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod4 OWNER TO col;

CREATE TABLE public.species_interaction_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod5 OWNER TO col;

CREATE TABLE public.species_interaction_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod6 OWNER TO col;

CREATE TABLE public.species_interaction_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod7 OWNER TO col;

CREATE TABLE public.species_interaction_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod8 OWNER TO col;

CREATE TABLE public.species_interaction_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.speciesinteractiontype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text,
    related_taxon_scientific_name text,
    reference_id text,
    remarks text
);

ALTER TABLE public.species_interaction_mod9 OWNER TO col;

CREATE VIEW public.table_size AS
 SELECT a.oid,
    a.table_name,
    a.row_estimate,
    pg_size_pretty(a.total_bytes) AS total,
    pg_size_pretty(a.index_bytes) AS index,
    pg_size_pretty(a.toast_bytes) AS toast,
    pg_size_pretty(a.table_bytes) AS "table"
   FROM ( SELECT a_1.oid,
            a_1.table_name,
            a_1.row_estimate,
            a_1.total_bytes,
            a_1.index_bytes,
            a_1.toast_bytes,
            ((a_1.total_bytes - a_1.index_bytes) - COALESCE(a_1.toast_bytes, (0)::bigint)) AS table_bytes
           FROM ( SELECT c.oid,
                    c.relname AS table_name,
                    c.reltuples AS row_estimate,
                    pg_total_relation_size((c.oid)::regclass) AS total_bytes,
                    pg_indexes_size((c.oid)::regclass) AS index_bytes,
                    pg_total_relation_size((c.reltoastrelid)::regclass) AS toast_bytes
                   FROM (pg_class c
                     LEFT JOIN pg_namespace n ON ((n.oid = c.relnamespace)))
                  WHERE ((c.relkind = 'r'::"char") AND (n.nspname = 'public'::name))) a_1) a;

ALTER TABLE public.table_size OWNER TO col;

CREATE TABLE public.taxon_concept_rel (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.taxon_concept_rel OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod0 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod1 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod10 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod11 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod12 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod13 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod14 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod15 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod16 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod17 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod18 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod19 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod2 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod20 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod21 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod22 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod23 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod3 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod4 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod5 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod6 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod7 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod8 OWNER TO col;

CREATE TABLE public.taxon_concept_rel_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    type public.taxonconceptreltype NOT NULL,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    taxon_id text NOT NULL,
    related_taxon_id text NOT NULL,
    reference_id text,
    remarks text
);

ALTER TABLE public.taxon_concept_rel_mod9 OWNER TO col;

CREATE TABLE public.treatment (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.treatment OWNER TO col;

CREATE TABLE public.treatment_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod0 OWNER TO col;

CREATE TABLE public.treatment_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod1 OWNER TO col;

CREATE TABLE public.treatment_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod10 OWNER TO col;

CREATE TABLE public.treatment_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod11 OWNER TO col;

CREATE TABLE public.treatment_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod12 OWNER TO col;

CREATE TABLE public.treatment_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod13 OWNER TO col;

CREATE TABLE public.treatment_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod14 OWNER TO col;

CREATE TABLE public.treatment_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod15 OWNER TO col;

CREATE TABLE public.treatment_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod16 OWNER TO col;

CREATE TABLE public.treatment_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod17 OWNER TO col;

CREATE TABLE public.treatment_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod18 OWNER TO col;

CREATE TABLE public.treatment_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod19 OWNER TO col;

CREATE TABLE public.treatment_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod2 OWNER TO col;

CREATE TABLE public.treatment_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod20 OWNER TO col;

CREATE TABLE public.treatment_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod21 OWNER TO col;

CREATE TABLE public.treatment_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod22 OWNER TO col;

CREATE TABLE public.treatment_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod23 OWNER TO col;

CREATE TABLE public.treatment_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod3 OWNER TO col;

CREATE TABLE public.treatment_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod4 OWNER TO col;

CREATE TABLE public.treatment_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod5 OWNER TO col;

CREATE TABLE public.treatment_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod6 OWNER TO col;

CREATE TABLE public.treatment_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod7 OWNER TO col;

CREATE TABLE public.treatment_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod8 OWNER TO col;

CREATE TABLE public.treatment_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    format public.treatmentformat,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    document text NOT NULL
);

ALTER TABLE public.treatment_mod9 OWNER TO col;

CREATE TABLE public.type_material (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.type_material OWNER TO col;

CREATE TABLE public.type_material_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod0 OWNER TO col;

CREATE TABLE public.type_material_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod1 OWNER TO col;

CREATE TABLE public.type_material_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod10 OWNER TO col;

CREATE TABLE public.type_material_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod11 OWNER TO col;

CREATE TABLE public.type_material_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod12 OWNER TO col;

CREATE TABLE public.type_material_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod13 OWNER TO col;

CREATE TABLE public.type_material_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod14 OWNER TO col;

CREATE TABLE public.type_material_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod15 OWNER TO col;

CREATE TABLE public.type_material_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod16 OWNER TO col;

CREATE TABLE public.type_material_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod17 OWNER TO col;

CREATE TABLE public.type_material_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod18 OWNER TO col;

CREATE TABLE public.type_material_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod19 OWNER TO col;

CREATE TABLE public.type_material_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod2 OWNER TO col;

CREATE TABLE public.type_material_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod20 OWNER TO col;

CREATE TABLE public.type_material_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod21 OWNER TO col;

CREATE TABLE public.type_material_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod22 OWNER TO col;

CREATE TABLE public.type_material_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod23 OWNER TO col;

CREATE TABLE public.type_material_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod3 OWNER TO col;

CREATE TABLE public.type_material_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod4 OWNER TO col;

CREATE TABLE public.type_material_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod5 OWNER TO col;

CREATE TABLE public.type_material_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod6 OWNER TO col;

CREATE TABLE public.type_material_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod7 OWNER TO col;

CREATE TABLE public.type_material_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod8 OWNER TO col;

CREATE TABLE public.type_material_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    name_id text NOT NULL,
    citation text,
    status public.typestatus,
    country text,
    locality text,
    latitude text,
    longitude text,
    coordinate point,
    altitude text,
    sex public.sex,
    institution_code text,
    catalog_number text,
    associated_sequences text,
    host text,
    date text,
    collector text,
    reference_id text,
    link text,
    remarks text
);

ALTER TABLE public.type_material_mod9 OWNER TO col;

CREATE TABLE public.usage_count (
    dataset_key integer NOT NULL,
    counter integer
);

ALTER TABLE public.usage_count OWNER TO col;

CREATE TABLE public."user" (
    key integer NOT NULL,
    last_login timestamp without time zone DEFAULT now(),
    created timestamp without time zone DEFAULT now(),
    blocked timestamp without time zone,
    username text,
    firstname text,
    lastname text,
    email text,
    orcid text,
    country text,
    roles public.user_role[],
    settings public.hstore
);

ALTER TABLE public."user" OWNER TO col;

CREATE SEQUENCE public.user_key_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.user_key_seq OWNER TO col;

ALTER SEQUENCE public.user_key_seq OWNED BY public."user".key;

CREATE VIEW public.v_name_usage AS
 SELECT u.dataset_key,
    u.id,
    n.id AS nid,
    u.parent_id,
    u.status,
    n.rank,
    n.scientific_name,
    n.authorship
   FROM (public.name_usage u
     JOIN public.name n ON (((n.id = u.name_id) AND (u.dataset_key = n.dataset_key))));

ALTER TABLE public.v_name_usage OWNER TO col;

CREATE TABLE public.verbatim (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.verbatim OWNER TO col;

CREATE TABLE public.verbatim_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod0 OWNER TO col;

CREATE TABLE public.verbatim_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod1 OWNER TO col;

CREATE TABLE public.verbatim_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod10 OWNER TO col;

CREATE TABLE public.verbatim_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod11 OWNER TO col;

CREATE TABLE public.verbatim_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod12 OWNER TO col;

CREATE TABLE public.verbatim_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod13 OWNER TO col;

CREATE TABLE public.verbatim_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod14 OWNER TO col;

CREATE TABLE public.verbatim_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod15 OWNER TO col;

CREATE TABLE public.verbatim_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod16 OWNER TO col;

CREATE TABLE public.verbatim_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod17 OWNER TO col;

CREATE TABLE public.verbatim_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod18 OWNER TO col;

CREATE TABLE public.verbatim_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod19 OWNER TO col;

CREATE TABLE public.verbatim_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod2 OWNER TO col;

CREATE TABLE public.verbatim_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod20 OWNER TO col;

CREATE TABLE public.verbatim_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod21 OWNER TO col;

CREATE TABLE public.verbatim_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod22 OWNER TO col;

CREATE TABLE public.verbatim_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod23 OWNER TO col;

CREATE TABLE public.verbatim_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod3 OWNER TO col;

CREATE TABLE public.verbatim_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod4 OWNER TO col;

CREATE TABLE public.verbatim_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod5 OWNER TO col;

CREATE TABLE public.verbatim_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod6 OWNER TO col;

CREATE TABLE public.verbatim_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod7 OWNER TO col;

CREATE TABLE public.verbatim_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod8 OWNER TO col;

CREATE TABLE public.verbatim_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    line integer,
    file text,
    type text,
    terms jsonb,
    issues public.issue[] DEFAULT '{}'::public.issue[],
    doc tsvector GENERATED ALWAYS AS (jsonb_to_tsvector('public.verbatim'::regconfig, COALESCE(terms, '{}'::jsonb), '["string", "numeric"]'::jsonb)) STORED
);

ALTER TABLE public.verbatim_mod9 OWNER TO col;

CREATE TABLE public.verbatim_source (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.verbatim_source OWNER TO col;

CREATE TABLE public.verbatim_source_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod0 OWNER TO col;

CREATE TABLE public.verbatim_source_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod1 OWNER TO col;

CREATE TABLE public.verbatim_source_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod10 OWNER TO col;

CREATE TABLE public.verbatim_source_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod11 OWNER TO col;

CREATE TABLE public.verbatim_source_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod12 OWNER TO col;

CREATE TABLE public.verbatim_source_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod13 OWNER TO col;

CREATE TABLE public.verbatim_source_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod14 OWNER TO col;

CREATE TABLE public.verbatim_source_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod15 OWNER TO col;

CREATE TABLE public.verbatim_source_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod16 OWNER TO col;

CREATE TABLE public.verbatim_source_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod17 OWNER TO col;

CREATE TABLE public.verbatim_source_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod18 OWNER TO col;

CREATE TABLE public.verbatim_source_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod19 OWNER TO col;

CREATE TABLE public.verbatim_source_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod2 OWNER TO col;

CREATE TABLE public.verbatim_source_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod20 OWNER TO col;

CREATE TABLE public.verbatim_source_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod21 OWNER TO col;

CREATE TABLE public.verbatim_source_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod22 OWNER TO col;

CREATE TABLE public.verbatim_source_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod23 OWNER TO col;

CREATE TABLE public.verbatim_source_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod3 OWNER TO col;

CREATE TABLE public.verbatim_source_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod4 OWNER TO col;

CREATE TABLE public.verbatim_source_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod5 OWNER TO col;

CREATE TABLE public.verbatim_source_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod6 OWNER TO col;

CREATE TABLE public.verbatim_source_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod7 OWNER TO col;

CREATE TABLE public.verbatim_source_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod8 OWNER TO col;

CREATE TABLE public.verbatim_source_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    source_id text,
    source_dataset_key integer,
    issues public.issue[] DEFAULT '{}'::public.issue[]
);

ALTER TABLE public.verbatim_source_mod9 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.verbatim_source_secondary OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod0 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod0 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod1 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod1 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod10 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod10 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod11 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod11 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod12 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod12 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod13 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod13 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod14 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod14 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod15 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod15 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod16 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod16 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod17 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod17 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod18 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod18 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod19 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod19 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod2 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod2 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod20 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod20 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod21 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod21 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod22 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod22 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod23 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod23 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod3 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod3 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod4 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod4 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod5 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod5 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod6 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod6 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod7 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod7 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod8 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod8 OWNER TO col;

CREATE TABLE public.verbatim_source_secondary_mod9 (
    id text NOT NULL,
    dataset_key integer NOT NULL,
    type public.infogroup NOT NULL,
    source_id text,
    source_dataset_key integer
);

ALTER TABLE public.verbatim_source_secondary_mod9 OWNER TO col;

CREATE TABLE public.vernacular_name (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
)
PARTITION BY HASH (dataset_key);

ALTER TABLE public.vernacular_name OWNER TO col;

CREATE TABLE public.vernacular_name_mod0 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod0 OWNER TO col;

CREATE TABLE public.vernacular_name_mod1 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod1 OWNER TO col;

CREATE TABLE public.vernacular_name_mod10 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod10 OWNER TO col;

CREATE TABLE public.vernacular_name_mod11 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod11 OWNER TO col;

CREATE TABLE public.vernacular_name_mod12 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod12 OWNER TO col;

CREATE TABLE public.vernacular_name_mod13 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod13 OWNER TO col;

CREATE TABLE public.vernacular_name_mod14 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod14 OWNER TO col;

CREATE TABLE public.vernacular_name_mod15 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod15 OWNER TO col;

CREATE TABLE public.vernacular_name_mod16 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod16 OWNER TO col;

CREATE TABLE public.vernacular_name_mod17 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod17 OWNER TO col;

CREATE TABLE public.vernacular_name_mod18 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod18 OWNER TO col;

CREATE TABLE public.vernacular_name_mod19 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod19 OWNER TO col;

CREATE TABLE public.vernacular_name_mod2 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod2 OWNER TO col;

CREATE TABLE public.vernacular_name_mod20 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod20 OWNER TO col;

CREATE TABLE public.vernacular_name_mod21 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod21 OWNER TO col;

CREATE TABLE public.vernacular_name_mod22 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod22 OWNER TO col;

CREATE TABLE public.vernacular_name_mod23 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod23 OWNER TO col;

CREATE TABLE public.vernacular_name_mod3 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod3 OWNER TO col;

CREATE TABLE public.vernacular_name_mod4 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod4 OWNER TO col;

CREATE TABLE public.vernacular_name_mod5 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod5 OWNER TO col;

CREATE TABLE public.vernacular_name_mod6 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod6 OWNER TO col;

CREATE TABLE public.vernacular_name_mod7 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod7 OWNER TO col;

CREATE TABLE public.vernacular_name_mod8 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod8 OWNER TO col;

CREATE TABLE public.vernacular_name_mod9 (
    id integer NOT NULL,
    dataset_key integer NOT NULL,
    sector_key integer,
    verbatim_key integer,
    created_by integer NOT NULL,
    modified_by integer NOT NULL,
    created timestamp without time zone DEFAULT now(),
    modified timestamp without time zone DEFAULT now(),
    language character(3),
    country character(2),
    taxon_id text NOT NULL,
    name text NOT NULL,
    latin text,
    area text,
    sex public.sex,
    reference_id text,
    doc tsvector GENERATED ALWAYS AS (to_tsvector('public.vernacular'::regconfig, ((COALESCE(name, ''::text) || ' '::text) || COALESCE(latin, ''::text)))) STORED
);

ALTER TABLE public.vernacular_name_mod9 OWNER TO col;

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.distribution ATTACH PARTITION public.distribution_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.estimate ATTACH PARTITION public.estimate_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.media ATTACH PARTITION public.media_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.name_match ATTACH PARTITION public.name_match_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.name ATTACH PARTITION public.name_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.name_rel ATTACH PARTITION public.name_rel_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.name_usage ATTACH PARTITION public.name_usage_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.reference ATTACH PARTITION public.reference_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.species_interaction ATTACH PARTITION public.species_interaction_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.taxon_concept_rel ATTACH PARTITION public.taxon_concept_rel_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.treatment ATTACH PARTITION public.treatment_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.type_material ATTACH PARTITION public.type_material_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.verbatim ATTACH PARTITION public.verbatim_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.verbatim_source ATTACH PARTITION public.verbatim_source_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.verbatim_source_secondary ATTACH PARTITION public.verbatim_source_secondary_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod0 FOR VALUES WITH (modulus 24, remainder 0);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod1 FOR VALUES WITH (modulus 24, remainder 1);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod10 FOR VALUES WITH (modulus 24, remainder 10);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod11 FOR VALUES WITH (modulus 24, remainder 11);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod12 FOR VALUES WITH (modulus 24, remainder 12);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod13 FOR VALUES WITH (modulus 24, remainder 13);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod14 FOR VALUES WITH (modulus 24, remainder 14);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod15 FOR VALUES WITH (modulus 24, remainder 15);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod16 FOR VALUES WITH (modulus 24, remainder 16);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod17 FOR VALUES WITH (modulus 24, remainder 17);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod18 FOR VALUES WITH (modulus 24, remainder 18);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod19 FOR VALUES WITH (modulus 24, remainder 19);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod2 FOR VALUES WITH (modulus 24, remainder 2);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod20 FOR VALUES WITH (modulus 24, remainder 20);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod21 FOR VALUES WITH (modulus 24, remainder 21);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod22 FOR VALUES WITH (modulus 24, remainder 22);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod23 FOR VALUES WITH (modulus 24, remainder 23);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod3 FOR VALUES WITH (modulus 24, remainder 3);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod4 FOR VALUES WITH (modulus 24, remainder 4);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod5 FOR VALUES WITH (modulus 24, remainder 5);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod6 FOR VALUES WITH (modulus 24, remainder 6);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod7 FOR VALUES WITH (modulus 24, remainder 7);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod8 FOR VALUES WITH (modulus 24, remainder 8);

ALTER TABLE ONLY public.vernacular_name ATTACH PARTITION public.vernacular_name_mod9 FOR VALUES WITH (modulus 24, remainder 9);

ALTER TABLE ONLY public.dataset ALTER COLUMN key SET DEFAULT nextval('public.dataset_key_seq'::regclass);

ALTER TABLE ONLY public.names_index ALTER COLUMN id SET DEFAULT nextval('public.names_index_id_seq'::regclass);

ALTER TABLE ONLY public."user" ALTER COLUMN key SET DEFAULT nextval('public.user_key_seq'::regclass);
