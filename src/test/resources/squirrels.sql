-- squirrels test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

INSERT INTO name (key, id, dataset_key, scientific_name) VALUES (1, 'name-1', 1, 'Malus Sylvestris');
INSERT INTO name (key, id, dataset_key, scientific_name) VALUES (2, 'name-2', 1, 'Larus fuscus');
ALTER SEQUENCE name_key_seq RESTART WITH 1000;

INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (1, 'root-1', 1, 1);
INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (2, 'root-2', 1, 2);
ALTER SEQUENCE taxon_key_seq RESTART WITH 1000;
