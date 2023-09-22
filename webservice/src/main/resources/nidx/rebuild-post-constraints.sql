ALTER TABLE nidx.name_match ADD FOREIGN KEY (dataset_key, sector_key) REFERENCES sector;
ALTER TABLE nidx.name_match ADD FOREIGN KEY (dataset_key, name_id) REFERENCES name;
