Produce like this:
\copy (SELECT id,homotypic_name_id,scientific_name,authorship,rank,uninomial,genus,infrageneric_epithet,specific_epithet,infraspecific_epithet,cultivar_epithet,strain,candidatus,notho,basionym_authors,basionym_ex_authors,basionym_year,combination_authors,combination_ex_authors,combination_year,sanctioning_author,code,nom_status,type FROM name_1000) to 'name.csv' CSV HEADER NULL ''
\copy (SELECT id,parent_id,name_id,provisional,fossil,recent,lifezones,species_estimate,species_estimate_reference_id FROM taxon_1000) to 'taxon.csv' CSV HEADER NULL ''
