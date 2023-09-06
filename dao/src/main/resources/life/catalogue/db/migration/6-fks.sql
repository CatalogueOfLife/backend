CREATE TRIGGER trg_name_usage_delete AFTER DELETE ON public.name_usage REFERENCING OLD TABLE AS deleted FOR EACH STATEMENT EXECUTE FUNCTION public.track_usage_count();

CREATE TRIGGER trg_name_usage_insert AFTER INSERT ON public.name_usage REFERENCING NEW TABLE AS inserted FOR EACH STATEMENT EXECUTE FUNCTION public.track_usage_count();



ALTER TABLE public.dataset_archive
    ADD CONSTRAINT dataset_archive_key_fkey FOREIGN KEY (key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_citation
    ADD CONSTRAINT dataset_citation_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_export
    ADD CONSTRAINT dataset_export_created_by_fkey FOREIGN KEY (created_by) REFERENCES public."user"(key);

ALTER TABLE public.dataset_export
    ADD CONSTRAINT dataset_export_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_import
    ADD CONSTRAINT dataset_import_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_patch
    ADD CONSTRAINT dataset_patch_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_patch
    ADD CONSTRAINT dataset_patch_key_fkey FOREIGN KEY (key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_source_citation
    ADD CONSTRAINT dataset_source_citation_release_key_fkey FOREIGN KEY (release_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_source
    ADD CONSTRAINT dataset_source_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset
    ADD CONSTRAINT dataset_source_key_fkey FOREIGN KEY (source_key) REFERENCES public.dataset(key);

ALTER TABLE public.dataset_source
    ADD CONSTRAINT dataset_source_key_fkey1 FOREIGN KEY (key) REFERENCES public.dataset(key);

ALTER TABLE public.decision
    ADD CONSTRAINT decision_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.decision
    ADD CONSTRAINT decision_subject_dataset_key_fkey FOREIGN KEY (subject_dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.distribution
    ADD CONSTRAINT distribution_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.distribution
    ADD CONSTRAINT distribution_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.distribution
    ADD CONSTRAINT distribution_dataset_key_taxon_id_fkey FOREIGN KEY (dataset_key, taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.distribution
    ADD CONSTRAINT distribution_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.estimate
    ADD CONSTRAINT estimate_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.media
    ADD CONSTRAINT media_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.media
    ADD CONSTRAINT media_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.media
    ADD CONSTRAINT media_dataset_key_taxon_id_fkey FOREIGN KEY (dataset_key, taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.media
    ADD CONSTRAINT media_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.name
    ADD CONSTRAINT name_dataset_key_published_in_id_fkey FOREIGN KEY (dataset_key, published_in_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.name
    ADD CONSTRAINT name_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.name
    ADD CONSTRAINT name_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.name_match
    ADD CONSTRAINT name_match_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.name_match
    ADD CONSTRAINT name_match_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.name_match
    ADD CONSTRAINT name_match_index_id_fkey FOREIGN KEY (index_id) REFERENCES public.names_index(id);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_related_name_id_fkey FOREIGN KEY (dataset_key, related_name_id) REFERENCES public.name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.name_usage_archive
    ADD CONSTRAINT name_usage_archive_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.name_usage_archive
    ADD CONSTRAINT name_usage_archive_first_release_key_fkey FOREIGN KEY (first_release_key) REFERENCES public.dataset(key);

ALTER TABLE public.name_usage_archive
    ADD CONSTRAINT name_usage_archive_last_release_key_fkey FOREIGN KEY (last_release_key) REFERENCES public.dataset(key);

ALTER TABLE public.name_usage_archive_match
    ADD CONSTRAINT name_usage_archive_match_index_id_fkey FOREIGN KEY (index_id) REFERENCES public.names_index(id);

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_according_to_id_fkey FOREIGN KEY (dataset_key, according_to_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id);

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_parent_id_fkey FOREIGN KEY (dataset_key, parent_id) REFERENCES public.name_usage(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.names_index
    ADD CONSTRAINT names_index_canonical_id_fkey FOREIGN KEY (canonical_id) REFERENCES public.names_index(id);

ALTER TABLE public.reference
    ADD CONSTRAINT reference_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.reference
    ADD CONSTRAINT reference_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.sector
    ADD CONSTRAINT sector_dataset_key_fkey FOREIGN KEY (dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.sector
    ADD CONSTRAINT sector_subject_dataset_key_fkey FOREIGN KEY (subject_dataset_key) REFERENCES public.dataset(key);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_dataset_key_related_taxon_id_fkey FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_dataset_key_taxon_id_fkey FOREIGN KEY (dataset_key, taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.species_interaction
    ADD CONSTRAINT species_interaction_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_dataset_key_related_taxon_id_fkey FOREIGN KEY (dataset_key, related_taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_dataset_key_taxon_id_fkey FOREIGN KEY (dataset_key, taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.taxon_concept_rel
    ADD CONSTRAINT taxon_concept_rel_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.treatment
    ADD CONSTRAINT treatment_dataset_key_id_fkey FOREIGN KEY (dataset_key, id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.treatment
    ADD CONSTRAINT treatment_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.treatment
    ADD CONSTRAINT treatment_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.type_material
    ADD CONSTRAINT type_material_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.type_material
    ADD CONSTRAINT type_material_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.type_material
    ADD CONSTRAINT type_material_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.type_material
    ADD CONSTRAINT type_material_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

ALTER TABLE public.verbatim_source
    ADD CONSTRAINT verbatim_source_dataset_key_id_fkey FOREIGN KEY (dataset_key, id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.verbatim_source_secondary
    ADD CONSTRAINT verbatim_source_secondary_dataset_key_id_fkey FOREIGN KEY (dataset_key, id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.vernacular_name
    ADD CONSTRAINT vernacular_name_dataset_key_reference_id_fkey FOREIGN KEY (dataset_key, reference_id) REFERENCES public.reference(dataset_key, id);

ALTER TABLE public.vernacular_name
    ADD CONSTRAINT vernacular_name_dataset_key_sector_key_fkey FOREIGN KEY (dataset_key, sector_key) REFERENCES public.sector(dataset_key, id);

ALTER TABLE public.vernacular_name
    ADD CONSTRAINT vernacular_name_dataset_key_taxon_id_fkey FOREIGN KEY (dataset_key, taxon_id) REFERENCES public.name_usage(dataset_key, id);

ALTER TABLE public.vernacular_name
    ADD CONSTRAINT vernacular_name_dataset_key_verbatim_key_fkey FOREIGN KEY (dataset_key, verbatim_key) REFERENCES public.verbatim(dataset_key, id);

