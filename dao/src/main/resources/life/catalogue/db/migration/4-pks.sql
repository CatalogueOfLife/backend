
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



ALTER TABLE public.dataset
    ADD CONSTRAINT dataset_pkey PRIMARY KEY (key);

ALTER TABLE public.dataset
    ADD CONSTRAINT dataset_gbif_key_key UNIQUE (gbif_key);

ALTER TABLE public.dataset
    ADD CONSTRAINT dataset_doi_excl EXCLUDE USING btree (doi WITH =) WHERE ((deleted IS NULL));

ALTER TABLE public.dataset_archive
    ADD CONSTRAINT dataset_archive_pkey PRIMARY KEY (key, attempt);

ALTER TABLE public.dataset_export
    ADD CONSTRAINT dataset_export_pkey PRIMARY KEY (key);

ALTER TABLE public.dataset_import
    ADD CONSTRAINT dataset_import_pkey PRIMARY KEY (dataset_key, attempt);

ALTER TABLE public.dataset_patch
    ADD CONSTRAINT dataset_patch_pkey PRIMARY KEY (key, dataset_key);

ALTER TABLE public.dataset_source
    ADD CONSTRAINT dataset_source_pkey PRIMARY KEY (key, dataset_key);

ALTER TABLE public.decision
    ADD CONSTRAINT decision_dataset_key_subject_dataset_key_subject_id_key UNIQUE (dataset_key, subject_dataset_key, subject_id);

ALTER TABLE public.decision
    ADD CONSTRAINT decision_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.distribution
    ADD CONSTRAINT distribution_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.estimate
    ADD CONSTRAINT estimate_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.id_report
    ADD CONSTRAINT id_report_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.latin29
    ADD CONSTRAINT latin29_pkey PRIMARY KEY (id);

ALTER TABLE public.media
    ADD CONSTRAINT media_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.name_match
    ADD CONSTRAINT name_match_pkey PRIMARY KEY (dataset_key, name_id);

ALTER TABLE public.name
    ADD CONSTRAINT name_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.name_usage_archive_match
    ADD CONSTRAINT name_usage_archive_match_pkey PRIMARY KEY (dataset_key, usage_id);

ALTER TABLE public.name_usage_archive
    ADD CONSTRAINT name_usage_archive_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.names_index
    ADD CONSTRAINT names_index_pkey PRIMARY KEY (id);

ALTER TABLE public.parser_config
    ADD CONSTRAINT parser_config_pkey PRIMARY KEY (id);

ALTER TABLE public.reference
    ADD CONSTRAINT reference_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.sector
    ADD CONSTRAINT sector_dataset_key_subject_dataset_key_subject_id_key UNIQUE (dataset_key, subject_dataset_key, subject_id);

ALTER TABLE public.sector
    ADD CONSTRAINT sector_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.sector_import
    ADD CONSTRAINT sector_import_pkey PRIMARY KEY (dataset_key, sector_key, attempt);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.treatment
    ADD CONSTRAINT treatment_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.type_material
    ADD CONSTRAINT type_material_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.usage_count
    ADD CONSTRAINT usage_count_pkey PRIMARY KEY (dataset_key);

ALTER TABLE public."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (key);

ALTER TABLE public."user"
    ADD CONSTRAINT user_username_key UNIQUE (username);

ALTER TABLE public.verbatim
    ADD CONSTRAINT verbatim_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.verbatim_source
    ADD CONSTRAINT verbatim_source_pkey PRIMARY KEY (dataset_key, id);

ALTER TABLE public.vernacular_name
    ADD CONSTRAINT vernacular_name_pkey PRIMARY KEY (dataset_key, id);

