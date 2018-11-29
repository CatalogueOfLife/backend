-- insert well known datasets
INSERT INTO dataset (key, type, origin, import_frequency, title) VALUES
    (1, 1, 2,  0, 'Catalogue of Life'),
    (2, 1, 2, -1, 'Provisional Catalogue of Life'),
    (3, 0, 2, -1, 'Draft Catalogue of Life');

UPDATE dataset SET (data_format, data_access)
                 = (0, 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/higher-classification.dwca.zip')
                WHERE key=1;

ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
