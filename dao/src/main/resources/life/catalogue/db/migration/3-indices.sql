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

CREATE INDEX ON dataset_citation (dataset_key);

CREATE INDEX ON dataset_archive_citation (dataset_key);
CREATE INDEX ON dataset_archive_citation (dataset_key, attempt);

CREATE INDEX ON dataset_source_citation (dataset_key);
CREATE INDEX ON dataset_source_citation (dataset_key, release_key);

CREATE INDEX ON dataset_import (dataset_key);
CREATE INDEX ON dataset_import (started);
-- used by import scheduler:
CREATE INDEX ON dataset_import (dataset_key, attempt) WHERE finished IS NOT NULL;

CREATE INDEX ON dataset_export (created);
CREATE INDEX ON dataset_export (created_by, created);
CREATE INDEX ON dataset_export (dataset_key, attempt, format, excel, synonyms, min_rank, status);

CREATE INDEX ON sector (dataset_key);
CREATE INDEX ON sector (dataset_key, subject_dataset_key, subject_id);
CREATE INDEX ON sector (dataset_key, target_id);

CREATE INDEX ON sector_import (dataset_key, sector_key);

CREATE INDEX ON decision (dataset_key);
CREATE INDEX ON decision (subject_dataset_key);
CREATE INDEX ON decision (subject_dataset_key, subject_id);

CREATE INDEX ON names_index (canonical_id);
CREATE INDEX ON names_index (scientific_name);
CREATE INDEX ON names_index (scientific_name) WHERE id = canonical_id;

CREATE INDEX ON id_report (dataset_key);

CREATE INDEX ON verbatim (dataset_key, type);
CREATE INDEX on verbatim (dataset_key, id) WHERE array_length(issues, 1) > 0;
CREATE INDEX ON verbatim USING GIN (dataset_key, doc);
CREATE INDEX ON verbatim USING GIN (dataset_key, issues);
CREATE INDEX ON verbatim USING GIN (dataset_key, terms jsonb_ops);

CREATE INDEX ON reference (dataset_key, verbatim_key);
CREATE INDEX ON reference (dataset_key, sector_key);
CREATE INDEX ON reference USING GIN (dataset_key, doc);

CREATE INDEX ON name (dataset_key, sector_key);
CREATE INDEX ON name (dataset_key, verbatim_key);
CREATE INDEX ON name (dataset_key, published_in_id);
CREATE INDEX ON name (dataset_key, lower(scientific_name));
CREATE INDEX ON name (dataset_key, scientific_name text_pattern_ops);
CREATE INDEX ON name (dataset_key, scientific_name_normalized);

CREATE INDEX ON name_rel (dataset_key, name_id);
CREATE INDEX ON name_rel (dataset_key, related_name_id);
CREATE INDEX ON name_rel (dataset_key, sector_key);
CREATE INDEX ON name_rel (dataset_key, verbatim_key);
CREATE INDEX ON name_rel (dataset_key, reference_id);

CREATE INDEX ON type_material (dataset_key, name_id);
CREATE INDEX ON type_material (dataset_key, sector_key);
CREATE INDEX ON type_material (dataset_key, verbatim_key);
CREATE INDEX ON type_material (dataset_key, reference_id);

CREATE INDEX ON name_match (dataset_key, sector_key);
CREATE INDEX ON name_match (dataset_key, index_id);
CREATE INDEX ON name_match (index_id);

CREATE INDEX ON name_usage (dataset_key, name_id);
CREATE INDEX ON name_usage (dataset_key, parent_id);
CREATE INDEX ON name_usage (dataset_key, verbatim_key);
CREATE INDEX ON name_usage (dataset_key, sector_key);
CREATE INDEX ON name_usage (dataset_key, according_to_id);
CREATE INDEX ON name_usage (dataset_key, is_synonym(status));

CREATE INDEX ON verbatim_source USING GIN(dataset_key, issues);

CREATE INDEX ON verbatim_source_secondary (dataset_key, id);
CREATE INDEX ON verbatim_source_secondary (dataset_key, source_dataset_key);

CREATE INDEX ON taxon_concept_rel (dataset_key, taxon_id);
CREATE INDEX ON taxon_concept_rel (dataset_key, related_taxon_id);
CREATE INDEX ON taxon_concept_rel (dataset_key, sector_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, verbatim_key);
CREATE INDEX ON taxon_concept_rel (dataset_key, reference_id);

CREATE INDEX ON species_interaction (dataset_key, taxon_id);
CREATE INDEX ON species_interaction (dataset_key, related_taxon_id);
CREATE INDEX ON species_interaction (dataset_key, sector_key);
CREATE INDEX ON species_interaction (dataset_key, verbatim_key);
CREATE INDEX ON species_interaction (dataset_key, reference_id);

CREATE INDEX ON vernacular_name (dataset_key, taxon_id);
CREATE INDEX ON vernacular_name (dataset_key, sector_key);
CREATE INDEX ON vernacular_name (dataset_key, verbatim_key);
CREATE INDEX ON vernacular_name (dataset_key, reference_id);
CREATE INDEX ON vernacular_name USING GIN (dataset_key, doc);

CREATE INDEX ON distribution (dataset_key, taxon_id);
CREATE INDEX ON distribution (dataset_key, sector_key);
CREATE INDEX ON distribution (dataset_key, verbatim_key);
CREATE INDEX ON distribution (dataset_key, reference_id);

CREATE INDEX ON treatment (dataset_key, sector_key);
CREATE INDEX ON treatment (dataset_key, verbatim_key);

CREATE INDEX ON estimate (dataset_key);
CREATE INDEX ON estimate (dataset_key, target_id);
CREATE INDEX ON estimate (dataset_key, reference_id);

CREATE INDEX ON media (dataset_key, taxon_id);
CREATE INDEX ON media (dataset_key, sector_key);
CREATE INDEX ON media (dataset_key, verbatim_key);
CREATE INDEX ON media (dataset_key, reference_id);

CREATE INDEX ON name_usage_archive_match (dataset_key, index_id);
CREATE INDEX ON name_usage_archive_match (index_id);
