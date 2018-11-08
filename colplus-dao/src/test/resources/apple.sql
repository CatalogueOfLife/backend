-- test data
INSERT INTO dataset (key, origin, title, import_frequency, created) VALUES (11, 1, 'First dataset',  -1, '2017-03-24');
INSERT INTO dataset (key, origin, title, import_frequency, created) VALUES (12, 1, 'Second dataset', -1, now());

INSERT INTO verbatim(key, dataset_key, issues, type) VALUES (1, 11, '{1,2,3,4}', 'acef:AcceptedSpecies');
INSERT INTO verbatim(key, dataset_key, issues, type) VALUES (2, 11, '{10}', 'acef:AcceptedSpecies');
INSERT INTO verbatim(key, dataset_key, issues, type) VALUES (3, 11, '{2,13}', 'acef:Synonyms');
INSERT INTO verbatim(key, dataset_key, issues, type) VALUES (4, 11, '{}', 'acef:Synonyms');
INSERT INTO verbatim(key, dataset_key, issues, type) VALUES (5, 11, null, 'acef:AcceptedSpecies');
ALTER SEQUENCE verbatim_key_seq RESTART WITH 100;

INSERT INTO reference(id, dataset_key) VALUES ('ref-1', 11);
INSERT INTO reference(id, dataset_key) VALUES ('ref-1b', 11);
INSERT INTO reference(id, dataset_key) VALUES ('ref-2', 12);

INSERT INTO name (dataset_key, id, homotypic_name_id, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (11, 'http://services.snsb.info/DTNtaxonlists/rest/v0.1/names/DiversityTaxonNames_Insecta/5009538/', 'name-4', 'Apia apis', 'Apia', 'apis', 'species'::rank, 0, 0);
INSERT INTO name (dataset_key, id, homotypic_name_id, scientific_name, genus, specific_epithet, rank, origin, type, published_in_id, published_in_page) VALUES (11, 'name-1', 'name-1', 'Malus sylvestris', 'Malus', 'sylvestris', 'species'::rank, 0, 0, 'ref-1', '712');
INSERT INTO name (dataset_key, id, homotypic_name_id, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (11, 'name-2', 'name-2', 'Larus fuscus', 'Larus', 'fuscus', 'species'::rank, 0, 0);
INSERT INTO name (dataset_key, id, homotypic_name_id, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (11, 'name-3', 'name-2', 'Larus fusca', 'Larus', 'fusca', 'species'::rank, 0, 0);
INSERT INTO name (dataset_key, id, homotypic_name_id, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (11, 'name-4', 'name-4', 'Larus erfundus', 'Larus', 'erfundus', 'species'::rank, 0, 0);

INSERT INTO taxon (id, dataset_key, name_id, origin) VALUES ('root-1', 11, 'name-1', 0);
INSERT INTO taxon (id, dataset_key, name_id, origin) VALUES ('root-2', 11, 'name-2', 0);

INSERT INTO synonym (taxon_id, name_id, dataset_key, status) VALUES ('root-2', 'name-3', 11, 2);
INSERT INTO synonym (taxon_id, name_id, dataset_key, status) VALUES ('root-2', 'name-4', 11, 2);

INSERT INTO taxon_reference(dataset_key, taxon_id, reference_id) VALUES (11, 'root-1', 'ref-1');
INSERT INTO taxon_reference(dataset_key, taxon_id, reference_id) VALUES (11, 'root-2', 'ref-1');
INSERT INTO taxon_reference(dataset_key, taxon_id, reference_id) VALUES (11, 'root-2', 'ref-1b');

INSERT INTO name_rel (key, dataset_key, type, name_id, related_name_id) VALUES (1, 11, 0, 'name-2', 'name-3');
ALTER SEQUENCE name_rel_key_seq RESTART WITH 1000;

INSERT INTO distribution(key, dataset_key, taxon_id, area, gazetteer, reference_id) VALUES (1, 11, 'root-1', 'Berlin', 6, 'ref-1');
INSERT INTO distribution(key, dataset_key, taxon_id, area, gazetteer, reference_id) VALUES (2, 11, 'root-1', 'Leiden', 6, 'ref-1b');
INSERT INTO distribution(key, dataset_key, taxon_id, area, gazetteer, reference_id) VALUES (3, 11, 'root-2', 'New York', 6, 'ref-1b');
ALTER SEQUENCE distribution_key_seq RESTART WITH 1000;

INSERT INTO vernacular_name(key,dataset_key,taxon_id,name,language, reference_id) VALUES (1, 11, 'root-1', 'Apple', 'en', 'ref-1');
INSERT INTO vernacular_name(key,dataset_key,taxon_id,name,language, reference_id) VALUES (2, 11, 'root-1', 'Apfel', 'de', 'ref-1');
INSERT INTO vernacular_name(key,dataset_key,taxon_id,name,language, reference_id) VALUES (3, 11, 'root-1', 'Meeuw', 'nl', null);
ALTER SEQUENCE vernacular_name_key_seq RESTART WITH 1000;



