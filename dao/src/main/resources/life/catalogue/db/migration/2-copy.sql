INSERT INTO public."user" (key,last_login,created,blocked,username,firstname,lastname,email,orcid,country,roles,settings) 
 SELECT key,last_login,created,blocked,username,firstname,lastname,email,orcid,country,roles,settings FROM old."user";

INSERT INTO public.dataset (key,doi,source_key,attempt,private,type,origin,gbif_key,gbif_publisher_key,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,settings,acl_editor,acl_reviewer,created_by,modified_by,created,modified,deleted)
 SELECT key,doi,source_key,attempt,private,type,origin,gbif_key,gbif_publisher_key,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,settings,acl_editor,acl_reviewer,created_by,modified_by,created,modified,deleted FROM old.dataset;

INSERT INTO public.names_index (id,canonical_id,rank,created,modified,scientific_name,authorship,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,remarks)
 SELECT id,canonical_id,rank,created,modified,scientific_name,authorship,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,remarks FROM old.names_index;

INSERT INTO public.parser_config (id,candidatus,extinct,rank,notho,code,type,created_by,created,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,published_in,nomenclatural_note,taxonomic_note,unparsed,remarks)
 SELECT id,candidatus,extinct,rank,notho,code,type,created_by,created,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,published_in,nomenclatural_note,taxonomic_note,unparsed,remarks FROM old.parser_config;

INSERT INTO public.id_report (id,dataset_key,type)
 SELECT id,dataset_key,type FROM old.id_report;

INSERT INTO public.latin29 (id,idnum)
 SELECT id,idnum FROM old.latin29;

INSERT INTO public.dataset_archive (key,doi,source_key,attempt,type,origin,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified)
 SELECT key,doi,source_key,attempt,type,origin,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified FROM old.dataset_archive;

INSERT INTO public.dataset_archive_citation (dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note,attempt)
 SELECT dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note,attempt FROM old.dataset_archive_citation;

INSERT INTO public.dataset_citation (dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note)
 SELECT dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note FROM old.dataset_citation;

INSERT INTO public.dataset_export (key,dataset_key,format,excel,root,synonyms,min_rank,created_by,created,modified_by,modified,attempt,started,finished,deleted,classification,status,error,truncated,md5,size,synonym_count,taxon_count,taxa_by_rank_count)
 SELECT key,dataset_key,format,excel,root,synonyms,min_rank,created_by,created,modified_by,modified,attempt,started,finished,deleted,classification,status,error,truncated,md5,size,synonym_count,taxon_count,taxa_by_rank_count FROM old.dataset_export;

INSERT INTO public.dataset_import (dataset_key,attempt,state,origin,format,started,finished,download,created_by,verbatim_count,applied_decision_count,bare_name_count,distribution_count,estimate_count,media_count,name_count,reference_count,synonym_count,taxon_count,treatment_count,type_material_count,vernacular_count,distributions_by_gazetteer_count,extinct_taxa_by_rank_count,ignored_by_reason_count,issues_by_issue_count,media_by_type_count,name_relations_by_type_count,names_by_code_count,names_by_rank_count,names_by_status_count,names_by_type_count,species_interactions_by_type_count,synonyms_by_rank_count,taxa_by_rank_count,taxon_concept_relations_by_type_count,type_material_by_status_count,usages_by_origin_count,usages_by_status_count,vernaculars_by_language_count,verbatim_by_row_type_count,verbatim_by_term_count,job,error,md5,download_uri)
 SELECT dataset_key,attempt,state,origin,format,started,finished,download,created_by,verbatim_count,applied_decision_count,bare_name_count,distribution_count,estimate_count,media_count,name_count,reference_count,synonym_count,taxon_count,treatment_count,type_material_count,vernacular_count,distributions_by_gazetteer_count,extinct_taxa_by_rank_count,ignored_by_reason_count,issues_by_issue_count,media_by_type_count,name_relations_by_type_count,names_by_code_count,names_by_rank_count,names_by_status_count,names_by_type_count,species_interactions_by_type_count,synonyms_by_rank_count,taxa_by_rank_count,taxon_concept_relations_by_type_count,type_material_by_status_count,usages_by_origin_count,usages_by_status_count,vernaculars_by_language_count,verbatim_by_row_type_count,verbatim_by_term_count,job,error,md5,download_uri FROM old.dataset_import;

INSERT INTO public.dataset_patch (key,doi,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified,dataset_key)
 SELECT key,doi,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified,dataset_key FROM old.dataset_patch;

INSERT INTO public.dataset_source (key,doi,source_key,attempt,type,origin,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified,dataset_key)
 SELECT key,doi,source_key,attempt,type,origin,identifier,title,alias,description,issued,version,issn,contact,creator,editor,publisher,contributor,keyword,geographic_scope,taxonomic_scope,temporal_scope,confidence,completeness,license,url,logo,notes,created_by,modified_by,created,modified,dataset_key FROM old.dataset_source;

INSERT INTO public.dataset_source_citation (dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note,release_key)
 SELECT dataset_key,id,type,doi,author,editor,title,container_author,container_title,issued,accessed,collection_editor,collection_title,volume,issue,edition,page,publisher,publisher_place,version,isbn,issn,url,note,release_key FROM old.dataset_source_citation;

INSERT INTO public.name_usage_archive (id,n_id,dataset_key,n_rank,n_candidatus,n_notho,n_code,n_nom_status,n_origin,n_type,n_scientific_name,n_authorship,n_uninomial,n_genus,n_infrageneric_epithet,n_specific_epithet,n_infraspecific_epithet,n_cultivar_epithet,n_basionym_authors,n_basionym_ex_authors,n_basionym_year,n_combination_authors,n_combination_ex_authors,n_combination_year,n_sanctioning_author,n_published_in_id,n_published_in_page,n_published_in_page_link,n_nomenclatural_note,n_unparsed,n_identifier,n_link,n_remarks,extinct,status,origin,parent_id,name_phrase,identifier,link,remarks,according_to,basionym,classification,published_in,first_release_key,last_release_key)
 SELECT id,n_id,dataset_key,n_rank,n_candidatus,n_notho,n_code,n_nom_status,n_origin,n_type,n_scientific_name,n_authorship,n_uninomial,n_genus,n_infrageneric_epithet,n_specific_epithet,n_infraspecific_epithet,n_cultivar_epithet,n_basionym_authors,n_basionym_ex_authors,n_basionym_year,n_combination_authors,n_combination_ex_authors,n_combination_year,n_sanctioning_author,n_published_in_id,n_published_in_page,n_published_in_page_link,n_nomenclatural_note,n_unparsed,n_identifier,n_link,n_remarks,extinct,status,origin,parent_id,name_phrase,identifier,link,remarks,according_to,basionym,classification,published_in,first_release_key,last_release_key FROM old.name_usage_archive;

INSERT INTO public.name_usage_archive_match (dataset_key,index_id,usage_id,type)
 SELECT dataset_key,index_id,usage_id,type FROM old.name_usage_archive_match;

INSERT INTO public.sector (id,dataset_key,subject_dataset_key,subject_rank,subject_code,subject_status,target_rank,target_code,mode,code,sync_attempt,dataset_attempt,priority,created_by,modified_by,created,modified,original_subject_id,subject_id,subject_name,subject_authorship,subject_parent,target_id,target_name,target_authorship,placeholder_rank,ranks,entities,name_types,name_status_exclusion,note)
 SELECT id,dataset_key,subject_dataset_key,subject_rank,subject_code,subject_status,target_rank,target_code,mode,code,sync_attempt,dataset_attempt,priority,created_by,modified_by,created,modified,original_subject_id,subject_id,subject_name,subject_authorship,subject_parent,target_id,target_name,target_authorship,placeholder_rank,ranks,entities,name_types,name_status_exclusion,note FROM old.sector;

INSERT INTO public.decision (id,dataset_key,subject_dataset_key,subject_rank,subject_code,subject_status,mode,status,extinct,environments,created_by,modified_by,created,modified,original_subject_id,subject_id,subject_name,subject_authorship,subject_parent,temporal_range_start,temporal_range_end,name,note)
 SELECT id,dataset_key,subject_dataset_key,subject_rank,subject_code,subject_status,mode,status,extinct,environments,created_by,modified_by,created,modified,original_subject_id,subject_id,subject_name,subject_authorship,subject_parent,temporal_range_start,temporal_range_end,name,note FROM old.decision;

INSERT INTO public.sector_import (dataset_key,sector_key,attempt,dataset_attempt,started,finished,created_by,state,applied_decision_count,bare_name_count,distribution_count,estimate_count,media_count,name_count,reference_count,synonym_count,taxon_count,treatment_count,type_material_count,vernacular_count,distributions_by_gazetteer_count,extinct_taxa_by_rank_count,ignored_by_reason_count,issues_by_issue_count,media_by_type_count,name_relations_by_type_count,names_by_code_count,names_by_rank_count,names_by_status_count,names_by_type_count,species_interactions_by_type_count,synonyms_by_rank_count,taxa_by_rank_count,taxon_concept_relations_by_type_count,type_material_by_status_count,usages_by_origin_count,usages_by_status_count,vernaculars_by_language_count,job,warnings,error)
 SELECT dataset_key,sector_key,attempt,dataset_attempt,started,finished,created_by,state,applied_decision_count,bare_name_count,distribution_count,estimate_count,media_count,name_count,reference_count,synonym_count,taxon_count,treatment_count,type_material_count,vernacular_count,distributions_by_gazetteer_count,extinct_taxa_by_rank_count,ignored_by_reason_count,issues_by_issue_count,media_by_type_count,name_relations_by_type_count,names_by_code_count,names_by_rank_count,names_by_status_count,names_by_type_count,species_interactions_by_type_count,synonyms_by_rank_count,taxa_by_rank_count,taxon_concept_relations_by_type_count,type_material_by_status_count,usages_by_origin_count,usages_by_status_count,vernaculars_by_language_count,job,warnings,error FROM old.sector_import;

INSERT INTO public.usage_count (dataset_key,counter)
 SELECT dataset_key,counter FROM old.usage_count;

INSERT INTO public.verbatim (id,dataset_key,line,file,type,terms,issues)
 SELECT id,dataset_key,line,file,type,terms,issues FROM old.verbatim;

INSERT INTO public.verbatim_source (id,dataset_key,source_id,source_dataset_key,issues)
 SELECT id,dataset_key,source_id,source_dataset_key,issues FROM old.verbatim_source;

INSERT INTO public.verbatim_source_secondary (id,dataset_key,type,source_id,source_dataset_key)
 SELECT id,dataset_key,type,source_id,source_dataset_key FROM old.verbatim_source_secondary;

INSERT INTO public.reference (id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation)
 SELECT id,dataset_key,sector_key,verbatim_key,year,created_by,modified_by,created,modified,csl,citation FROM old.reference;


INSERT INTO public.name (id,dataset_key,sector_key,verbatim_key,rank,candidatus,notho,code,nom_status,origin,type,scientific_name,authorship,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,published_in_id,published_in_page,published_in_page_link,nomenclatural_note,unparsed,identifier,link,remarks,scientific_name_normalized,authorship_normalized,created_by,modified_by,created,modified)
 SELECT id,dataset_key,sector_key,verbatim_key,rank,candidatus,notho,code,nom_status,origin,type,scientific_name,authorship,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,published_in_id,published_in_page,published_in_page_link,nomenclatural_note,unparsed,identifier,link,remarks,scientific_name_normalized,authorship_normalized,created_by,modified_by,created,modified FROM old.name;

INSERT INTO public.name_rel (id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,name_id,related_name_id,reference_id,remarks)
 SELECT id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,name_id,related_name_id,reference_id,remarks FROM old.name_rel;

INSERT INTO public.name_match (dataset_key,sector_key,index_id,name_id,type)
 SELECT dataset_key,sector_key,index_id,name_id,type FROM old.name_match;

INSERT INTO public.name_usage (id,dataset_key,sector_key,verbatim_key,extinct,status,origin,parent_id,name_id,name_phrase,identifier,link,remarks,according_to_id,scrutinizer,scrutinizer_date,reference_ids,temporal_range_start,temporal_range_end,environments,created_by,modified_by,created,modified,dataset_sectors)
 SELECT id,dataset_key,sector_key,verbatim_key,extinct,status,origin,parent_id,name_id,name_phrase,identifier,link,remarks,according_to_id,scrutinizer,scrutinizer_date,reference_ids,temporal_range_start,temporal_range_end,environments,created_by,modified_by,created,modified,dataset_sectors FROM old.name_usage;

INSERT INTO public.type_material (id,dataset_key,sector_key,verbatim_key,created_by,modified_by,created,modified,name_id,citation,status,country,locality,latitude,longitude,coordinate,altitude,sex,institution_code,catalog_number,associated_sequences,host,date,collector,reference_id,link,remarks)
 SELECT id,dataset_key,sector_key,verbatim_key,created_by,modified_by,created,modified,name_id,citation,status,country,locality,latitude,longitude,coordinate,altitude,sex,institution_code,catalog_number,associated_sequences,host,date,collector,reference_id,link,remarks FROM old.type_material;

INSERT INTO public.taxon_concept_rel (id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,taxon_id,related_taxon_id,reference_id,remarks)
 SELECT id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,taxon_id,related_taxon_id,reference_id,remarks FROM old.taxon_concept_rel;

INSERT INTO public.species_interaction (id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,taxon_id,related_taxon_id,related_taxon_scientific_name,reference_id,remarks)
 SELECT id,dataset_key,sector_key,verbatim_key,type,created_by,modified_by,created,modified,taxon_id,related_taxon_id,related_taxon_scientific_name,reference_id,remarks FROM old.species_interaction;

INSERT INTO public.distribution (id,dataset_key,sector_key,verbatim_key,gazetteer,status,created_by,modified_by,created,modified,taxon_id,area,reference_id)
 SELECT id,dataset_key,sector_key,verbatim_key,gazetteer,status,created_by,modified_by,created,modified,taxon_id,area,reference_id FROM old.distribution;

INSERT INTO public.media (id,dataset_key,sector_key,verbatim_key,type,captured,license,created_by,modified_by,created,modified,taxon_id,url,format,title,captured_by,link,reference_id)
 SELECT id,dataset_key,sector_key,verbatim_key,type,captured,license,created_by,modified_by,created,modified,taxon_id,url,format,title,captured_by,link,reference_id FROM old.media;

INSERT INTO public.estimate (id,dataset_key,verbatim_key,target_rank,target_code,estimate,type,created_by,modified_by,created,modified,target_id,target_name,target_authorship,reference_id,note)
 SELECT id,dataset_key,verbatim_key,target_rank,target_code,estimate,type,created_by,modified_by,created,modified,target_id,target_name,target_authorship,reference_id,note FROM old.estimate;

INSERT INTO public.treatment (id,dataset_key,sector_key,verbatim_key,format,created_by,modified_by,created,modified,document)
 SELECT id,dataset_key,sector_key,verbatim_key,format,created_by,modified_by,created,modified,document FROM old.treatment;

INSERT INTO public.vernacular_name (id,dataset_key,sector_key,verbatim_key,created_by,modified_by,created,modified,language,country,taxon_id,name,latin,area,sex,reference_id)
 SELECT id,dataset_key,sector_key,verbatim_key,created_by,modified_by,created,modified,language,country,taxon_id,name,latin,area,sex,reference_id FROM old.vernacular_name;

