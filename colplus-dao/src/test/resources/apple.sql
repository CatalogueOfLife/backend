-- test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

INSERT INTO reference(key, id, dataset_key) VALUES (1, 'ref-1', 1);
INSERT INTO reference(key, id, dataset_key) VALUES (2, 'ref-2', 2);
ALTER SEQUENCE reference_key_seq RESTART WITH 1000;

INSERT INTO name (key, dataset_key, id, homotypic_name_key, scientific_name, genus, specific_epithet, rank, origin, type, published_in_key, published_in_page) VALUES (1, 1, 'name-1', 1, 'Malus sylvestris', 'Malus', 'sylvestris', 'species'::rank, 0, 0, 1, '712');
INSERT INTO name (key, dataset_key, id, homotypic_name_key, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (2, 1, 'name-2', 2, 'Larus fuscus', 'Larus', 'fuscus', 'species'::rank, 0, 0);
INSERT INTO name (key, dataset_key, id, homotypic_name_key, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (3, 1, 'name-3', 2, 'Larus fusca', 'Larus', 'fusca', 'species'::rank, 0, 0);
INSERT INTO name (key, dataset_key, id, homotypic_name_key, scientific_name, genus, specific_epithet, rank, origin, type) VALUES (4, 1, 'name-4', 4, 'Larus erfundus', 'Larus', 'erfundus', 'species'::rank, 0, 0);
ALTER SEQUENCE name_key_seq RESTART WITH 1000;

INSERT INTO taxon (key, id, dataset_key, name_key, origin) VALUES (1, 'root-1', 1, 1, 0);
INSERT INTO taxon (key, id, dataset_key, name_key, origin) VALUES (2, 'root-2', 1, 2, 0);
ALTER SEQUENCE taxon_key_seq RESTART WITH 1000;

INSERT INTO synonym (taxon_key, name_key, dataset_key, status) VALUES (2, 3, 1, 2);
INSERT INTO synonym (taxon_key, name_key, dataset_key, status) VALUES (2, 4, 1, 2);

INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 1, 1);
INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 2, 1);
INSERT INTO taxon_reference(dataset_key, taxon_key, reference_key) VALUES (1, 2, 2);

INSERT INTO name_act (dataset_key, type, name_key, related_name_key) VALUES (1, 0, 2, 3);
ALTER SEQUENCE name_act_key_seq RESTART WITH 1000;

INSERT INTO distribution(key, dataset_key, taxon_key, area, gazetteer) VALUES (1, 1, 1, 'Berlin', 6);
INSERT INTO distribution(key, dataset_key, taxon_key, area, gazetteer) VALUES (2, 1, 1, 'Leiden', 6);
INSERT INTO distribution(key, dataset_key, taxon_key, area, gazetteer) VALUES (3, 1, 2, 'New York', 6);
ALTER SEQUENCE distribution_key_seq RESTART WITH 1000;


INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 1, 1);
INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 1, 2);
INSERT INTO distribution_reference(dataset_key,distribution_key,reference_key) VALUES (1, 2, 2);

INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (1, 1, 1, 'Apple', 'en');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (2, 1, 1, 'Apfel', 'de');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (3, 1, 1, 'Meeuw', 'nl');
ALTER SEQUENCE vernacular_name_key_seq RESTART WITH 1000;

INSERT INTO vernacular_name_reference(dataset_key,vernacular_name_key,reference_key) VALUES (1, 1, 1);
INSERT INTO vernacular_name_reference(dataset_key,vernacular_name_key,reference_key) VALUES (1, 2, 1);



