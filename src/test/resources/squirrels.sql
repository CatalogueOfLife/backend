-- nothing
INSERT INTO dataset (key, title) VALUES (1, 'First dataset');
INSERT INTO dataset (key, title) VALUES (2, 'Second dataset');

INSERT INTO name (key, id) VALUES (1, 'First name');
INSERT INTO name (key, id) VALUES (2, 'Second name');

INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (1, 'Root taxon 1', 1, 1);
INSERT INTO taxon (key, id, dataset_key, name_key) VALUES (2, 'Root taxon 2', 1, 1);
