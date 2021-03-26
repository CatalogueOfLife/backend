CREATE INDEX ON build.name_match (dataset_key, sector_key);
CREATE INDEX ON build.name_match (dataset_key, index_id);
CREATE INDEX ON build.name_match (index_id);
CREATE INDEX ON build.names_index (lower(scientific_name));

ALTER TABLE build.names_index ADD PRIMARY KEY (id);
ALTER TABLE build.names_index ADD FOREIGN KEY (canonical_id) REFERENCES build.names_index;

ALTER TABLE build.name_match ADD PRIMARY KEY (dataset_key, name_id);
ALTER TABLE build.name_match ADD FOREIGN KEY (index_id) REFERENCES build.names_index;
