--
-- foreign key constraints are slowing down deletions so much, that it takes several hours to remove a single COL release
-- we trust our application to not cause data problems and remove the constraints - except the name_rel one which had orphaned records before
ALTER TABLE distribution DROP CONSTRAINT distribution_dataset_key_reference_id_fkey;
ALTER TABLE distribution DROP CONSTRAINT distribution_dataset_key_sector_key_fkey;
ALTER TABLE distribution DROP CONSTRAINT distribution_dataset_key_taxon_id_fkey;
ALTER TABLE distribution DROP CONSTRAINT distribution_dataset_key_verbatim_key_fkey;

ALTER TABLE media DROP CONSTRAINT media_dataset_key_reference_id_fkey;
ALTER TABLE media DROP CONSTRAINT media_dataset_key_sector_key_fkey;
ALTER TABLE media DROP CONSTRAINT media_dataset_key_taxon_id_fkey;
ALTER TABLE media DROP CONSTRAINT media_dataset_key_verbatim_key_fkey;

ALTER TABLE name DROP CONSTRAINT name_dataset_key_published_in_id_fkey;
ALTER TABLE name DROP CONSTRAINT name_dataset_key_sector_key_fkey;
ALTER TABLE name DROP CONSTRAINT name_dataset_key_verbatim_key_fkey;

ALTER TABLE name_match DROP CONSTRAINT name_match_dataset_key_name_id_fkey;
ALTER TABLE name_match DROP CONSTRAINT name_match_dataset_key_sector_key_fkey;
ALTER TABLE name_match DROP CONSTRAINT name_match_index_id_fkey;

-- ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_name_id_fkey;
-- ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_reference_id_fkey;
-- ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_related_name_id_fkey;
ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_sector_key_fkey;
ALTER TABLE name_rel DROP CONSTRAINT name_rel_dataset_key_verbatim_key_fkey;

ALTER TABLE name_usage_archive DROP CONSTRAINT name_usage_archive_dataset_key_fkey;
ALTER TABLE name_usage_archive DROP CONSTRAINT name_usage_archive_first_release_key_fkey;
ALTER TABLE name_usage_archive DROP CONSTRAINT name_usage_archive_last_release_key_fkey;

ALTER TABLE name_usage_archive_match DROP CONSTRAINT name_usage_archive_match_index_id_fkey;

ALTER TABLE name_usage DROP CONSTRAINT name_usage_dataset_key_according_to_id_fkey;
ALTER TABLE name_usage DROP CONSTRAINT name_usage_dataset_key_name_id_fkey;
ALTER TABLE name_usage DROP CONSTRAINT name_usage_dataset_key_parent_id_fkey;
ALTER TABLE name_usage DROP CONSTRAINT name_usage_dataset_key_sector_key_fkey;
ALTER TABLE name_usage DROP CONSTRAINT name_usage_dataset_key_verbatim_key_fkey;

ALTER TABLE taxon_concept_rel DROP CONSTRAINT taxon_concept_rel_dataset_key_sector_key_fkey;
ALTER TABLE reference DROP CONSTRAINT reference_dataset_key_sector_key_fkey;
ALTER TABLE reference DROP CONSTRAINT reference_dataset_key_verbatim_key_fkey;

ALTER TABLE species_interaction DROP CONSTRAINT species_interaction_dataset_key_reference_id_fkey;
ALTER TABLE species_interaction DROP CONSTRAINT species_interaction_dataset_key_related_taxon_id_fkey;
ALTER TABLE species_interaction DROP CONSTRAINT species_interaction_dataset_key_sector_key_fkey;
ALTER TABLE species_interaction DROP CONSTRAINT species_interaction_dataset_key_taxon_id_fkey;
ALTER TABLE species_interaction DROP CONSTRAINT species_interaction_dataset_key_verbatim_key_fkey;

ALTER TABLE taxon_concept_rel DROP CONSTRAINT taxon_concept_rel_dataset_key_reference_id_fkey;
ALTER TABLE taxon_concept_rel DROP CONSTRAINT taxon_concept_rel_dataset_key_related_taxon_id_fkey;
ALTER TABLE taxon_concept_rel DROP CONSTRAINT taxon_concept_rel_dataset_key_taxon_id_fkey;
ALTER TABLE taxon_concept_rel DROP CONSTRAINT taxon_concept_rel_dataset_key_verbatim_key_fkey;

ALTER TABLE treatment DROP CONSTRAINT treatment_dataset_key_id_fkey;
ALTER TABLE treatment DROP CONSTRAINT treatment_dataset_key_sector_key_fkey;
ALTER TABLE treatment DROP CONSTRAINT treatment_dataset_key_verbatim_key_fkey;

ALTER TABLE type_material DROP CONSTRAINT type_material_dataset_key_name_id_fkey;
ALTER TABLE type_material DROP CONSTRAINT type_material_dataset_key_reference_id_fkey;
ALTER TABLE type_material DROP CONSTRAINT type_material_dataset_key_sector_key_fkey;
ALTER TABLE type_material DROP CONSTRAINT type_material_dataset_key_verbatim_key_fkey;

ALTER TABLE verbatim_source DROP CONSTRAINT verbatim_source_dataset_key_id_fkey;
ALTER TABLE verbatim_source_secondary DROP CONSTRAINT verbatim_source_secondary_dataset_key_id_fkey;

ALTER TABLE vernacular_name DROP CONSTRAINT vernacular_name_dataset_key_reference_id_fkey;
ALTER TABLE vernacular_name DROP CONSTRAINT vernacular_name_dataset_key_sector_key_fkey;
ALTER TABLE vernacular_name DROP CONSTRAINT vernacular_name_dataset_key_taxon_id_fkey;
ALTER TABLE vernacular_name DROP CONSTRAINT vernacular_name_dataset_key_verbatim_key_fkey;
