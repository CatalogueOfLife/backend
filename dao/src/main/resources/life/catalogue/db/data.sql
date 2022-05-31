-- bots
INSERT INTO "user" (key, username, firstname, lastname, roles, created) VALUES
    (-1, 'tester', 'Tim', 'Test', '{EDITOR,ADMIN}', now()),
    (0, 'dbinit', 'DB', 'Init', '{}', now()),
    (10, 'importer', 'Importer', 'Bot', '{}', now()),
    (11, 'matcher', 'Name', 'Matcher', '{}', now()),
    (12, 'gbifsync', 'GBIF', 'Sync', '{}', now());
ALTER SEQUENCE user_key_seq RESTART WITH 100;

-- insert well known datasets
INSERT INTO dataset (key, type, origin, title, alias, settings, created_by, modified_by) VALUES
    (3, 'TAXONOMIC', 'PROJECT', 'Catalogue of Life', 'COL', jsonb_build_object('import frequency', -1), 0, 0);

ALTER SEQUENCE dataset_key_seq RESTART WITH 1000;

