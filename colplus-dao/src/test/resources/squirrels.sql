-- squirrels test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

INSERT INTO name (key, id, dataset_key, scientific_name) VALUES (1, 'name-1', 1, 'Malus sylvestris');
INSERT INTO name (key, id, dataset_key, scientific_name) VALUES (2, 'name-2', 1, 'Larus fuscus');
ALTER SEQUENCE name_key_seq RESTART WITH 1000;

INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (1, 'root-1', 1, 1);
INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (2, 'root-2', 1, 2);
ALTER SEQUENCE taxon_key_seq RESTART WITH 1000;

INSERT INTO reference(key, id, dataset_key) VALUES (1, 'ref-1', 1);
INSERT INTO reference(key, id, dataset_key) VALUES (2, 'ref-2', 2);
ALTER SEQUENCE reference_key_seq RESTART WITH 1000;

INSERT INTO taxon_references(dataset_key, taxon_key, reference_key, reference_page) VALUES (1, 1, 1, '12');
INSERT INTO taxon_references(dataset_key, taxon_key, reference_key, reference_page) VALUES (1, 2, 1, '100');
INSERT INTO taxon_references(dataset_key, taxon_key, reference_key, reference_page) VALUES (1, 2, 2, '133');

INSERT INTO name_act(key, dataset_key, type, name_key, reference_key, reference_page) VALUES (1, 1, 0, 1, 1, '712');
ALTER SEQUENCE name_act_key_seq RESTART WITH 1000;

INSERT INTO distribution(key, dataset_key, taxon_key, area) VALUES (1, 1, 1, 'Berlin');
INSERT INTO distribution(key, dataset_key, taxon_key, area) VALUES (2, 1, 1, 'Leiden');
INSERT INTO distribution(key, dataset_key, taxon_key, area) VALUES (3, 1, 2, 'New York');
ALTER SEQUENCE distribution_key_seq RESTART WITH 1000;


INSERT INTO distribution_references(dataset_key,distribution_key,reference_key,reference_page) VALUES (1, 1, 1, '145');
INSERT INTO distribution_references(dataset_key,distribution_key,reference_key,reference_page) VALUES (1, 1, 2, '34');
INSERT INTO distribution_references(dataset_key,distribution_key,reference_key,reference_page) VALUES (1, 2, 2, '35');

INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (1, 1, 1, 'Apple', 'en');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (2, 1, 1, 'Apfel', 'de');
INSERT INTO vernacular_name(key,dataset_key,taxon_key,name,language) VALUES (3, 1, 1, 'Meeuw', 'nl');
ALTER SEQUENCE vernacular_name_key_seq RESTART WITH 1000;

INSERT INTO vernacular_name_references(dataset_key,vernacular_name_key,reference_key,reference_page) VALUES (1, 1, 1, '145');
INSERT INTO vernacular_name_references(dataset_key,vernacular_name_key,reference_key,reference_page) VALUES (1, 2, 1, '145');



