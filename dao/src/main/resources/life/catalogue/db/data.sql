-- bots
INSERT INTO "user" (key, username, firstname, lastname, roles, created) VALUES
    (-1, 'tester', 'Tim', 'Test', '{EDITOR,ADMIN}', now()),
    (0, 'dbinit', 'DB', 'Init', '{}', now()),
    (10, 'importer', 'Importer', 'Bot', '{}', now()),
    (11, 'matcher', 'Name', 'Matcher', '{}', now()),
    (12, 'gbifsync', 'GBIF', 'Sync', '{}', now()),
    (13, 'assembly', 'Cat', 'Assembly', '{}', now());
ALTER SEQUENCE user_key_seq RESTART WITH 100;

-- insert well known datasets
INSERT INTO dataset (key, type, origin, import_frequency, title, alias, created_by, modified_by) VALUES
    (1, 'NOMENCLATURAL', 'MANAGED', -1, 'Names Index',           'NIdx',  0, 0),
    (3, 'TAXONOMIC', 'MANAGED', -1, 'Draft Catalogue of Life',   'Draft', 0, 0);

ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;
