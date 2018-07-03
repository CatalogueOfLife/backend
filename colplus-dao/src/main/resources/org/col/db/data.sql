-- insert well known datasets
INSERT INTO dataset (key, title) VALUES (1, 'Catalogue of Life');
INSERT INTO dataset (key, title) VALUES (2, 'Provisional Catalogue');
ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
