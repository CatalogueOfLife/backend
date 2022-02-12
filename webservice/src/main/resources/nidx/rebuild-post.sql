CREATE INDEX ON nidx.names_index (canonical_id);
CREATE INDEX ON nidx.name_match (dataset_key, sector_key);
CREATE INDEX ON nidx.name_match (dataset_key, index_id);
CREATE INDEX ON nidx.name_match (index_id);

ALTER TABLE nidx.names_index ADD PRIMARY KEY (id);
ALTER TABLE nidx.names_index ADD FOREIGN KEY (canonical_id) REFERENCES nidx.names_index;

ALTER TABLE nidx.name_match ADD PRIMARY KEY (dataset_key, name_id);
ALTER TABLE nidx.name_match ADD FOREIGN KEY (index_id) REFERENCES nidx.names_index;
