
-- this will remove all existing tables
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

CREATE EXTENSION IF NOT EXISTS hstore;

CREATE TABLE typetest (
  key serial PRIMARY KEY,
  data hstore,
  json jsonb,
  uuid uuid
);

CREATE TABLE reference (
  key serial PRIMARY KEY,
  title text,
  author text,
  year int,
  link text,
  identifier text
);

CREATE TABLE name (
  key serial PRIMARY KEY,
  scientific_name text NOT NULL,
  canonical_name text,
  authorship text,
  monomial text,
  epithet text,
  infra_epithet text,
  rank int,
  notho int,
  parsed boolean,
  published_in_key int REFERENCES reference,
  published_in_year int
);


CREATE TABLE serial (
  key serial PRIMARY KEY,
  title text
);
