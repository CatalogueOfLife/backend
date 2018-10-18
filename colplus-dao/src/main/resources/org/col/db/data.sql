-- insert well known datasets
INSERT INTO dataset (key, origin, import_frequency, title) VALUES (1, 2, -1, 'Catalogue of Life');
INSERT INTO dataset (key, origin, import_frequency, title) VALUES (2, 2, -1, 'Provisional Catalogue');
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
