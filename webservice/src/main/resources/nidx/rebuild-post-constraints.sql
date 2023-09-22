ALTER TABLE nidx.name_match ADD PRIMARY KEY (dataset_key, name_id);
ALTER TABLE nidx.name_match ADD FOREIGN KEY (index_id) REFERENCES nidx.names_index;
ALTER TABLE nidx.name_match ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE nidx.name_match ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
