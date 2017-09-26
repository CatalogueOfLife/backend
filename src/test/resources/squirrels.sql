-- squirrels test data
INSERT INTO dataset (key, title, created) VALUES (1, 'First dataset', now());
INSERT INTO dataset (key, title, created) VALUES (2, 'Second dataset', now());
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

