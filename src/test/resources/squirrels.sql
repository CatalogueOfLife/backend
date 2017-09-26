-- squirrels test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

INSERT INTO name (key, id) VALUES (1, 'First name');
INSERT INTO name (key, id) VALUES (2, 'Second name');

INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (1, 'Root taxon 1', 1, 1);
INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (2, 'Root taxon 2', 1, 1);
