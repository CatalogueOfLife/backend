DROP SCHEMA IF EXISTS nidx CASCADE;
CREATE SCHEMA nidx;
CREATE TABLE nidx.name_match (LIKE public.name_match INCLUDING DEFAULTS);
CREATE TABLE nidx.names_index (LIKE public.names_index INCLUDING DEFAULTS);
CREATE SEQUENCE nidx.names_index_id_seq START 1;
ALTER TABLE nidx.names_index ALTER COLUMN id SET DEFAULT nextval('nidx.names_index_id_seq');
