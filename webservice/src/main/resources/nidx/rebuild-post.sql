CREATE INDEX ON nidx.names_index (canonical_id);
CREATE INDEX ON nidx.names_index (scientific_name);
CREATE INDEX ON nidx.names_index (scientific_name) WHERE id = canonical_id;

CREATE INDEX ON nidx.name_match (dataset_key, sector_key);
CREATE INDEX ON nidx.name_match (dataset_key, index_id);
CREATE INDEX ON nidx.name_match (index_id);

ALTER TABLE nidx.names_index ADD PRIMARY KEY (id);
ALTER TABLE nidx.names_index ADD FOREIGN KEY (canonical_id) REFERENCES nidx.names_index;
