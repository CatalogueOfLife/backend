
-- GLOBAL SEQUENCES
SELECT setval('public.dataset_key_seq', (select max(key)+1 from dataset));
SELECT setval('public.names_index_id_seq', (SELECT COALESCE(max(id),1) FROM names_index));
SELECT setval('public.user_key_seq', (SELECT COALESCE(max(key),1) FROM "user"));


-- PROJECT SEQUENCES
-- dataset keys:
-- 3,9818,9822,9847,9855,9856,9862,9873,9874,9885,9921

CREATE SEQUENCE IF NOT EXISTS sector_3_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS sector_9818_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS sector_9822_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS sector_9847_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS sector_9855_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS sector_9856_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS sector_9862_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS sector_9873_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS sector_9874_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS sector_9885_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS sector_9921_id_seq START 1;
SELECT setval('public.sector_3_id_seq', (SELECT COALESCE(max(id),1) FROM sector WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS decision_3_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS decision_9818_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS decision_9822_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS decision_9847_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS decision_9855_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS decision_9856_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS decision_9862_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS decision_9873_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS decision_9874_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS decision_9885_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS decision_9921_id_seq START 1;
SELECT setval('public.decision_3_id_seq', (SELECT COALESCE(max(id),1) FROM decision WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS verbatim_3_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS verbatim_9818_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS verbatim_9822_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS verbatim_9847_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS verbatim_9855_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS verbatim_9856_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS verbatim_9862_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS verbatim_9873_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS verbatim_9874_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS verbatim_9885_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS verbatim_9921_id_seq START 1;
SELECT setval('public.verbatim_3_id_seq', (SELECT COALESCE(max(id),1) FROM verbatim WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS name_rel_3_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS name_rel_9818_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS name_rel_9822_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS name_rel_9847_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS name_rel_9855_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS name_rel_9856_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS name_rel_9862_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS name_rel_9873_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS name_rel_9874_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS name_rel_9885_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS name_rel_9921_id_seq START 1;
SELECT setval('public.name_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM name_rel WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_3_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9818_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9822_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9847_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9855_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9856_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9862_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9873_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9874_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9885_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS taxon_concept_rel_9921_id_seq START 1;
SELECT setval('public.taxon_concept_rel_3_id_seq', (SELECT COALESCE(max(id),1) FROM taxon_concept_rel WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS species_interaction_3_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9818_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9822_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9847_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9855_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9856_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9862_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9873_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9874_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9885_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS species_interaction_9921_id_seq START 1;
SELECT setval('public.species_interaction_3_id_seq', (SELECT COALESCE(max(id),1) FROM species_interaction WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS distribution_3_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS distribution_9818_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS distribution_9822_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS distribution_9847_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS distribution_9855_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS distribution_9856_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS distribution_9862_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS distribution_9873_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS distribution_9874_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS distribution_9885_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS distribution_9921_id_seq START 1;
SELECT setval('public.distribution_3_id_seq', (SELECT COALESCE(max(id),1) FROM distribution WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS media_3_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS media_9818_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS media_9822_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS media_9847_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS media_9855_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS media_9856_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS media_9862_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS media_9873_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS media_9874_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS media_9885_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS media_9921_id_seq START 1;
SELECT setval('public.media_3_id_seq', (SELECT COALESCE(max(id),1) FROM media WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS estimate_3_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS estimate_9818_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS estimate_9822_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS estimate_9847_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS estimate_9855_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS estimate_9856_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS estimate_9862_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS estimate_9873_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS estimate_9874_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS estimate_9885_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS estimate_9921_id_seq START 1;
SELECT setval('public.estimate_3_id_seq', (SELECT COALESCE(max(id),1) FROM estimate WHERE dataset_key=9921));



CREATE SEQUENCE IF NOT EXISTS vernacular_name_3_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=3));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9818_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9818));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9822_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9822));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9847_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9847));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9855_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9855));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9856_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9856));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9862_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9862));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9873_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9873));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9874_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9874));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9885_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9885));

CREATE SEQUENCE IF NOT EXISTS vernacular_name_9921_id_seq START 1;
SELECT setval('public.vernacular_name_3_id_seq', (SELECT COALESCE(max(id),1) FROM vernacular_name WHERE dataset_key=9921));
