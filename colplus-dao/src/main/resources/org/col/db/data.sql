-- insert well known datasets
INSERT INTO dataset (key, type, origin, import_frequency, title) VALUES (1, 1, 2, -1, 'Catalogue of Life');
INSERT INTO dataset (key, type, origin, import_frequency, title) VALUES (2, 1, 2, -1, 'Provisional Catalogue');
INSERT INTO dataset (key, type, origin, import_frequency, title) VALUES (3, 0, 2, 0, 'Draft Catalogue of Life');
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
