DROP SCHEMA IF EXISTS nidx CASCADE;
CREATE SCHEMA nidx;
CREATE TABLE nidx.name_usage_archive_match (LIKE public.name_usage_archive_match INCLUDING DEFAULTS);
-- name_match is hash partitioned by dataset_key. LIKE does not copy the partitioning,
-- so we declare it explicitly here. The individual partitions are created by NamesIndexCmd.
CREATE TABLE nidx.name_match (LIKE public.name_match INCLUDING DEFAULTS) PARTITION BY HASH (dataset_key);
CREATE TABLE nidx.names_index (LIKE public.names_index INCLUDING DEFAULTS);
CREATE SEQUENCE nidx.names_index_id_seq START 1;
ALTER TABLE nidx.names_index ALTER COLUMN id SET DEFAULT nextval('nidx.names_index_id_seq');
-- unique on normalized must exist BEFORE population: assign-on-miss uses INSERT ... ON CONFLICT (normalized),
-- which requires the matching unique index on the target table during the rebuild sweep.
CREATE UNIQUE INDEX ON nidx.names_index (normalized);
