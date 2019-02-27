-- bots
INSERT INTO coluser (key, username, firstname, lastname, roles, created) VALUES
    (0, 'dbinit', 'DB', 'Init', '{}', now()),
    (10, 'importer', 'Importer', 'Bot', '{}', now()),
    (11, 'matcher', 'Name', 'Matcher', '{}', now()),
    (12, 'gbifsync', 'GBIF', 'Sync', '{}', now()),
    (13, 'assembly', 'Cat', 'Assembly', '{}', now());
ALTER SEQUENCE coluser_key_seq RESTART WITH 100;

-- insert well known datasets
INSERT INTO dataset (key, type, origin, import_frequency, title, created_by, modified_by) VALUES
    (1, 1, 2,  0, 'Catalogue of Life', 0, 0),
    (2, 1, 2, -1, 'Provisional Catalogue of Life', 0, 0),
    (3, 0, 2, -1, 'Draft Catalogue of Life', 0, 0);

ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
