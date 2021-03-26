DROP SCHEMA IF EXISTS build CASCADE;
CREATE SCHEMA build;
CREATE TABLE build.name_match (LIKE public.name_match INCLUDING DEFAULTS);
CREATE TABLE build.names_index (LIKE public.names_index INCLUDING DEFAULTS);
CREATE SEQUENCE build.names_index_id_seq START 1;
ALTER TABLE build.names_index ALTER COLUMN id SET DEFAULT nextval('build.names_index_id_seq');
