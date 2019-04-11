-- test data
INSERT INTO coluser (key, username, firstname, lastname, email, roles) VALUES
    (91, 'admin',  'Stan', 'Sterling', 'stan@mailinator.com', '{2}'),
    (92, 'editor', 'Yuri', 'Roskov', 'yuri@mailinator.com', '{0,1}'),
    (93, 'user',   'Frank', 'Müller', 'frank@mailinator.com', '{0}');
ALTER SEQUENCE coluser_key_seq RESTART WITH 100;

INSERT INTO dataset (key, origin, title, import_frequency, created_by, modified_by, created) VALUES (11, 1, 'Tree dataset',  -1, 1, 1, '2017-03-24');

INSERT INTO verbatim(key, dataset_key, issues, type) VALUES
    (1, 11, '{}', 'acef:AcceptedSpecies'),
    (2, 11, '{13,14}', 'acef:AcceptedSpecies');
ALTER SEQUENCE verbatim_key_seq RESTART WITH 100;

INSERT INTO reference(dataset_key, id, citation, csl, created_by, modified_by)
VALUES
    (11, 'r1',  'Full R1 citation', '{}', 1, 1),
    (11, 'r2',  'Full R2 citation', '{}', 1, 1);

INSERT INTO name (dataset_key, verbatim_key, id, homotypic_name_id, scientific_name, uninomial, genus, specific_epithet, infraspecific_epithet, rank, origin, type, created_by, modified_by, published_in_id)
VALUES
 (11, 1, 'n1',  null,  'Animalia',   'Animalia', null, null, null, 'kingdom'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n2',  null,  'Chordata',   'Chordata', null, null, null, 'phylum'::rank,  0, 0, 1, 1, 'r1'),
 (11, 1, 'n3',  null,  'Mammalia',   'Mammalia', null, null, null, 'class'::rank,   0, 0, 1, 1, 'r1'),
 (11, 1, 'n4',  null,  'Carnivora',  'Carnivora', null, null, null, 'order'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n5',  null,  'Canidae',   'Canidae',  null, null, null, 'family'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n6',  null,  'Felidae',   'Felidae',  null, null, null, 'family'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n10', null, 'Lynx',      'Lynx',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n11', null, 'Pardina',   'Pardina',  null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n12', null, 'Lynx lynx',  null,     'Lynx', 'lynx', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n13', null, 'Lynx rufus',  null,    'Lynx', 'rufus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 2, 'n14', 'n13', 'Felis rufus',  null,   'Felis', 'rufus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n15', null, 'Lynx rufus subsp. baileyi', null, 'Lynx', 'rufus', 'baileyi', 'subspecies'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n16', null, 'Lynx rufus subsp. gigas',   null, 'Lynx', 'rufus', 'gigas',   'subspecies'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n20', null, 'Canis',   'Canis',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n21', null, 'Alopsis', 'Alopsis',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n22', null, 'Lupulus', 'Lupulus',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n23', null, 'Canis adustus',    null,  'Canis', 'adustus',  null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n24', null, 'Canis argentinus', null,  'Canis', 'argentinus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n25', null, 'Canis aureus',     null,  'Canis', 'aureus',   null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n30', null, 'Urocyon', 'Urocyon',   null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n31', null, 'Urocyon citrinus', null, 'Urocyon', 'citrinus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n32', null, 'Urocyon littoralis', null, 'Urocyon', 'littoralis', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, null, 'n33', null, 'Urocyon minicephalus', null, 'Urocyon', 'minicephalus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, null, 'n34', null, 'Urocyon webbi', null, 'Urocyon', 'webbi', null, 'species'::rank, 0, 0, 1, 1, 'r1');


INSERT INTO name_usage (id, parent_id, dataset_key, verbatim_key, name_id, origin, status, is_synonym, created_by, modified_by) VALUES
 ('t1',  null, 11, null,  'n1', 0, 0, false, 1, 1),
 ('t2',  't1', 11, null,  'n2', 0, 0, false, 1, 1),
 ('t3',  't2', 11, null,  'n3', 0, 0, false, 1, 1),
 ('t4',  't3', 11, null,  'n4', 0, 0, false, 1, 1),
 ('t5',  't4', 11, null,  'n5', 0, 0, false, 1, 1),
 ('t6',  't4', 11, null,  'n6', 0, 0, false, 1, 1),
 ('t10', 't6', 11, null, 'n10', 0, 0, false, 1, 1),
 ('t12', 't10',11, null, 'n12', 0, 0, false, 1, 1),
 ('t13', 't10',11, null, 'n13', 0, 0, false, 1, 1),
 ('t15', 't13',11, null, 'n15', 0, 0, false, 1, 1),
 ('t16', 't13',11, null, 'n16', 0, 0, false, 1, 1),
 ('t20', 't5', 11, null, 'n20', 0, 0, false, 1, 1),
 ('t23', 't20',11, null, 'n23', 0, 0, false, 1, 1),
 ('t24', 't20',11, null, 'n24', 0, 0, false, 1, 1),
 ('t25', 't20',11, null, 'n25', 0, 0, false, 1, 1),
 ('t30', 't5', 11, null, 'n30', 0, 0, false, 1, 1),
 ('t31', 't30',11, null, 'n31', 0, 0, false, 1, 1),
 ('t32', 't30',11, null, 'n32', 0, 0, false, 1, 1),
 ('t33', 't30',11, null, 'n33', 0, 0, false, 1, 1),
 ('t34', 't30',11, null, 'n34', 0, 0, false, 1, 1);

UPDATE name_usage SET
    according_to = 'M.Döring',
    according_to_date = now(),
    verbatim_key=1,
    species_estimate=10,
    webpage = 'http://myspace.com',
    remarks = 'remark me';

INSERT INTO usage_reference(dataset_key, taxon_id, reference_id)
VALUES
 (11, 't1', 'r1'),
 (11, 't2', 'r1'),
 (11, 't3', 'r1'),
 (11, 't10', 'r1'),
 (11, 't10', 'r2'),
 (11, 't12', 'r1'),
 (11, 't13', 'r1'),
 (11, 't20', 'r1'),
 (11, 't20', 'r2'),
 (11, 't23', 'r1');

INSERT INTO name_usage (id, parent_id, dataset_key, verbatim_key, name_id, origin, status, is_synonym, created_by, modified_by) VALUES
 ('s11', 't10', 11, null, 'n11', 0, 2, true, 1, 1),
 ('s14', 't13', 11, 5,    'n14', 0, 2, true, 1, 1),
 ('s21', 't20', 11, null, 'n21', 0, 2, true, 1, 1),
 ('s22', 't20', 11, 1,    'n22', 0, 2, true, 1, 1);

UPDATE name_usage SET
    according_to = 'M.Döring',
    verbatim_key=1
    WHERE is_synonym;


INSERT INTO distribution (dataset_key, taxon_id, area, gazetteer, reference_id, created_by, modified_by)
VALUES
(11, 't10', 'DE', 1, 'r1' , 1, 1),
(11, 't10', 'NL', 1, 'r1' , 1, 1),
(11, 't10', 'UK', 1, 'r1' , 1, 1),
(11, 't10', 'DK', 1, 'r1' , 1, 1),
(11, 't10', 'ES', 1, 'r1' , 1, 1),
(11, 't20', 'Germany', 6, 'r2' , 1, 1),
(11, 't20', 'Österreich', 6, 'r2' , 1, 1);

INSERT INTO vernacular_name(dataset_key,taxon_id,name,language, reference_id, created_by, modified_by)
VALUES
(11, 't10', 'Apple', 'eng', 'r1', 1, 1),
(11, 't10', 'Apfel', 'deu', 'r1', 1, 1),
(11, 't10', 'Meeuw', 'nld', null, 1, 1);

