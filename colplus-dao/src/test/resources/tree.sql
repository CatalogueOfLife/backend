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
 (11, 1, 'n1',  'n1',  'Animalia',   'Animalia', null, null, null, 'kingdom'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n2',  'n2',  'Chordata',   'Chordata', null, null, null, 'phylum'::rank,  0, 0, 1, 1, 'r1'),
 (11, 1, 'n3',  'n3',  'Mammalia',   'Mammalia', null, null, null, 'class'::rank,   0, 0, 1, 1, 'r1'),
 (11, 1, 'n4',  'n4',  'Carnivora',  'Carnivora', null, null, null, 'order'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n5',  'n5',  'Canidae',   'Canidae',  null, null, null, 'family'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n6',  'n6',  'Felidae',   'Felidae',  null, null, null, 'family'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n10', 'n10', 'Lynx',      'Lynx',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n11', 'n11', 'Pardina',   'Pardina',  null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n12', 'n12', 'Lynx lynx',  null,     'Lynx', 'lynx', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n13', 'n13', 'Lynx rufus',  null,    'Lynx', 'rufus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 2, 'n14', 'n13', 'Felis rufus',  null,   'Felis', 'rufus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n15', 'n15', 'Lynx rufus subsp. baileyi', null, 'Lynx', 'rufus', 'baileyi', 'subspecies'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n16', 'n16', 'Lynx rufus subsp. gigas',   null, 'Lynx', 'rufus', 'gigas',   'subspecies'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n20', 'n20', 'Canis',   'Canis',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n21', 'n21', 'Alopsis', 'Alopsis',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n22', 'n22', 'Lupulus', 'Lupulus',     null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n23', 'n23', 'Canis adustus',    null,  'Canis', 'adustus',  null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n24', 'n24', 'Canis argentinus', null,  'Canis', 'argentinus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n25', 'n25', 'Canis aureus',     null,  'Canis', 'aureus',   null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n30', 'n30', 'Urocyon', 'Urocyon',   null, null, null, 'genus'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n31', 'n31', 'Urocyon citrinus', null, 'Urocyon', 'citrinus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, 1, 'n32', 'n32', 'Urocyon littoralis', null, 'Urocyon', 'littoralis', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, null, 'n33', 'n33', 'Urocyon minicephalus', null, 'Urocyon', 'minicephalus', null, 'species'::rank, 0, 0, 1, 1, 'r1'),
 (11, null, 'n34', 'n34', 'Urocyon webbi', null, 'Urocyon', 'webbi', null, 'species'::rank, 0, 0, 1, 1, 'r1');


INSERT INTO taxon (id, parent_id, dataset_key, verbatim_key, name_id, origin, created_by, modified_by) VALUES
 ('t1',  null, 11, null,  'n1', 0, 1, 1),
 ('t2',  't1', 11, null,  'n2', 0, 1, 1),
 ('t3',  't2', 11, null,  'n3', 0, 1, 1),
 ('t4',  't3', 11, null,  'n4', 0, 1, 1),
 ('t5',  't4', 11, null,  'n5', 0, 1, 1),
 ('t6',  't4', 11, null,  'n6', 0, 1, 1),
 ('t10', 't6', 11, null, 'n10', 0, 1, 1),
 ('t12', 't10',11, null, 'n12', 0, 1, 1),
 ('t13', 't10',11, null, 'n13', 0, 1, 1),
 ('t15', 't13',11, null, 'n15', 0, 1, 1),
 ('t16', 't13',11, null, 'n16', 0, 1, 1),
 ('t20', 't5', 11, null, 'n20', 0, 1, 1),
 ('t23', 't20',11, null, 'n23', 0, 1, 1),
 ('t24', 't20',11, null, 'n24', 0, 1, 1),
 ('t25', 't20',11, null, 'n25', 0, 1, 1),
 ('t30', 't5', 11, null, 'n30', 0, 1, 1),
 ('t31', 't30',11, null, 'n31', 0, 1, 1),
 ('t32', 't30',11, null, 'n32', 0, 1, 1),
 ('t33', 't30',11, null, 'n33', 0, 1, 1),
 ('t34', 't30',11, null, 'n34', 0, 1, 1);

UPDATE taxon SET
    according_to = 'M.Döring',
    according_to_date = now(),
    verbatim_key=1,
    species_estimate=10,
    webpage = 'http://myspace.com',
    remarks = 'remark me';

INSERT INTO taxon_reference(dataset_key, taxon_id, reference_id)
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

INSERT INTO synonym (id, taxon_id, dataset_key, verbatim_key, name_id, status, origin, created_by, modified_by) VALUES
 ('s11', 't10', 11, null, 'n11', 2, 0, 1, 1),
 ('s14', 't13', 11, 5, 'n14', 2, 0, 1, 1),
 ('s21', 't20', 11, null, 'n21', 2, 0, 1, 1),
 ('s22', 't20', 11, 1, 'n22', 2, 0, 1, 1);

UPDATE synonym SET
    according_to = 'M.Döring',
    verbatim_key=1;


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

