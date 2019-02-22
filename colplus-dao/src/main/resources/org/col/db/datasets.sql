
--------------------------
-- COL GSD DATASETS
--   dumped from assembly_global into colplus-repo
--------------------------

-- origin:  0=EXTERNAL, 1=UPLOADED, 2=MANAGED
INSERT INTO dataset (key, origin, type, contributes_to, title, import_frequency, created_by, modified_by, data_format, data_access) 
VALUES ('1000', 0, 1, 0, 'CoL Management Classification', 1, 0, 0, 0, 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/higher-classification.dwca.zip');

INSERT INTO dataset (key, origin, type, contributes_to, title, import_frequency, created_by, modified_by, data_format, data_access) 
SELECT x.id+1000, 0, 1, 0, 'GSD ' || x.id, 1, 0, 0, 1, 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/' || x.id || '.tar.gz'
FROM (SELECT unnest(array[
10,
100,
101,
103,
104,
105,
106,
107,
108,
109,
11,
110,
112,
113,
115,
118,
119,
12,
120,
121,
122,
123,
124,
125,
126,
127,
128,
129,
130,
131,
132,
133,
134,
138,
139,
14,
142,
143,
144,
146,
148,
149,
15,
150,
152,
153,
154,
157,
158,
161,
162,
164,
166,
167,
168,
169,
17,
170,
171,
172,
173,
174,
175,
176,
177,
178,
179,
18,
180,
181,
182,
183,
184,
185,
186,
188,
189,
19,
190,
191,
192,
193,
194,
195,
196,
197,
198,
199,
20,
200,
201,
21,
22,
23,
24,
25,
26,
28,
29,
30,
31,
32,
33,
34,
36,
37,
38,
39,
40,
42,
44,
45,
46,
47,
48,
49,
5,
50,
500,
501,
502,
51,
52,
53,
54,
57,
58,
59,
6,
61,
62,
63,
65,
66,
67,
68,
69,
7,
70,
73,
75,
76,
78,
79,
8,
80,
81,
82,
85,
86,
87,
88,
89,
9,
90,
91,
92,
93,
94,
95,
96,
97,
98,
99
]) AS id) AS x;


-- regional ACEF dataset: NZIB, COL-CHINA, ITIS-regional
UPDATE dataset SET type=2 WHERE key IN (
	1121, 1075, 1017
);

-- other ACEF dataset: common names
UPDATE dataset SET type=4 WHERE key IN (
	1007
);

UPDATE dataset SET code=1 WHERE key IN (
	1015,1025,1036,1038,1040,1045,1048,1066,1097,1098
);
UPDATE dataset SET code=3 WHERE key IN (
	1014
);
UPDATE dataset SET code=4 WHERE key IN (
	1005,1006,1008,1009,1010,1011,1018,1020,1021,1022,1023,1026,1029,1030,1031,1032,1034,1037,1039,1042,1044,
	1046,1047,1049,1050,1051,1052,1054,1057,1058,1059,1061,1062,1063,1065,1067,1068,1069,1070,1076,1078,
	1080,1081,1082,1085,1086,1087,1088,1089,1090,1091,1092,1093,1094,1095,1096,1099,1100,1103,1104,1105,1106,
	1107,1108,1109,1110,1112,1118,1119,1120,1122,1130,1133,1134
);

-- removed, old sources which we mark as deleted
INSERT INTO dataset (key, origin, title, created_by, modified_by, deleted) VALUES 
	('1016', 0, 'IOPI-GPC', 0, 0, now()),
	('1041', 0, 'Systematic Myriapod Database', 0, 0, now()),
	('1043', 0, 'lecypages', 0, 0, now()),
	('1056', 0, 'lhd', 0, 0, now()),
	('1060', 0, 'worms_proseriata-kalyptorhynchia', 0, 0, now()),
	('1064', 0, 'solanaceae_source', 0, 0, now()),
	('1117', 0, 'chenobase', 0, 0, now()),
	('1135', 0, 'fada_turbellaria', 0, 0, now()),
	('1159', 0, 'fada_copepoda', 0, 0, now()),
	('1165', 0, 'faeu_turbellaria', 0, 0, now());


-- reserve keys below 2000 for existing GSDs
ALTER SEQUENCE dataset_key_seq RESTART WITH 2000;





--------------------------
-- GBIF
--   for the provisional catalogue
--------------------------

-- all datasets from https://github.com/gbif/checklistbank/blob/master/checklistbank-nub/nub-sources.tsv
-- excluding CoL, the GBIF patches and entire organisation or installations which we add below as lists of datasets
-- nom codes: 0=BACTERIAL, 1=BOTANICAL, 2=CULTIVARS, 3=VIRUS, 4=ZOOLOGICAL

INSERT INTO dataset (gbif_key, created_by, modified_by, origin, code, data_access, title) VALUES
    ('00e791be-36ae-40ee-8165-0b2cb0b8c84f', 12, 12, 0, null, 'https://github.com/mdoering/famous-organism/archive/master.zip', 'Species named after famous people'),
    ('046bbc50-cae2-47ff-aa43-729fbf53f7c5', 12, 12, 0, 1,    'http://rs.gbif.org/datasets/protected/ipni.zip', 'International Plant Names Index'),
    ('0938172b-2086-439c-a1dd-c21cb0109ed5', 12, 12, 0, null, 'http://www.irmng.org/export/IRMNG_genera_DwCA.zip', 'The Interim Register of Marine and Nonmarine Genera'),
    ('0e61f8fe-7d25-4f81-ada7-d970bbb2c6d6', 12, 12, 0, null, 'http://ipt.gbif.fr/archive.do?r=taxref-test', 'TAXREF'),
    ('1c1f2cfc-8370-414f-9202-9f00ccf51413', 12, 12, 0, 1,    'http://rs.gbif.org/datasets/protected/euro_med.zip', 'Euro+Med PlantBase data sample'),
    ('1ec61203-14fa-4fbd-8ee5-a4a80257b45a', 12, 12, 0, null, 'http://ipt.taibif.tw/archive.do?r=taibnet_com_all', 'The National Checklist of Taiwan'),
    ('2d59e5db-57ad-41ff-97d6-11f5fb264527', 12, 12, 0, null, 'http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip', 'World Register of Marine Species'),
    ('3f8a1297-3259-4700-91fc-acc4170b27ce', 12, 12, 0, 1,    'http://data.canadensys.net/ipt/archive.do?r=vascan', 'Database of Vascular Plants of Canada (VASCAN)'),
    ('47f16512-bf31-410f-b272-d151c996b2f6', 12, 12, 0, 4,    'http://rs.gbif.org/datasets/clements.zip', 'The Clements Checklist'),
    ('4dd32523-a3a3-43b7-84df-4cda02f15cf7', 12, 12, 0, null, 'http://api.biodiversitydata.nl/v2/taxon/dwca/getDataSet/nsr', 'Checklist Dutch Species Register - Nederlands Soortenregister'),
    ('52a423d2-0486-4e77-bcee-6350d708d6ff', 12, 12, 0, 0,    'http://rs.gbif.org/datasets/dsmz.zip', 'Prokaryotic Nomenclature Up-to-date'),
    ('5c7bf05c-2890-48e8-9b65-a6060cb75d6d', 12, 12, 0, 4,    'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_megophryidae_bufonidae', 'Catalogue of the type specimens of Bufonidae and Megophryidae (Amphibia: Anura) from research collections of the Zoological Institute,'),
    ('65c9103f-2fbf-414b-9b0b-e47ca96c5df2', 12, 12, 0, 4,    'http://ipt.biodiversity.be/archive.do?r=afromoths', 'Afromoths, online database of Afrotropical moth species (Lepidoptera)'),
    ('66dd0960-2d7d-46ee-a491-87b9adcfe7b1', 12, 12, 0, 1,    'http://rs.gbif.org/datasets/grin_archive.zip', 'GRIN Taxonomy'),
    ('672aca30-f1b5-43d3-8a2b-c1606125fa1b', 12, 12, 0, 4,    'http://rs.gbif.org/datasets/msw3.zip', 'Mammal Species of the World'),
    ('6cfd67d6-4f9b-400b-8549-1933ac27936f', 12, 12, 0, null, 'http://api.gbif.org/v1/occurrence/download/request/dwca-type-specimen-checklist.zip', 'GBIF Type Specimen Names'),
    ('7a9bccd4-32fc-420e-a73b-352b92267571', 12, 12, 0, 4,    'http://data.canadensys.net/ipt/archive.do?r=coleoptera-ca-ak', 'Checklist of Beetles (Coleoptera) of Canada and Alaska. Second Edition.'),
    ('7ea21580-4f06-469d-995b-3f713fdcc37c', 12, 12, 0, 1,    'https://github.com/gbif/algae/archive/master.zip', 'GBIF Algae Classification'),
    ('80b4b440-eaca-4860-aadf-d0dfdd3e856e', 12, 12, 0, 4,    'https://github.com/gbif/iczn-lists/archive/master.zip', 'Official Lists and Indexes of Names in Zoology'),
    ('8d431c96-9e2f-4249-8b0a-d875e3273908', 12, 12, 0, 4,    'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_cosmopterigidae', 'Catalogue of the type specimens of Cosmopterigidae (Lepidoptera: Gelechioidea) from research collections of the Zoological Institute, R'),
    ('8dc469b3-8e61-4f6f-b9db-c70dbbc8858c', 12, 12, 0, null, 'https://raw.githubusercontent.com/mdoering/ion-taxonomic-hierarchy/master/classification.tsv', 'ION Taxonomic Hierarchy'),
    ('90d9e8a6-0ce1-472d-b682-3451095dbc5a', 12, 12, 0, 4,    'http://rs.gbif.org/datasets/protected/fauna_europaea.zip', 'Fauna Europaea'),
    ('96dfd141-7bca-4f82-9325-4420d24e0793', 12, 12, 0, 4,    'http://plazi.cs.umb.edu/GgServer/dwca/49CC45D6B497E6D97BDDF3C0D38289E2.zip', 'Spinnengids'),
    ('9ca92552-f23a-41a8-a140-01abaa31c931', 12, 12, 0, null, 'http://rs.gbif.org/datasets/itis.zip', 'Integrated Taxonomic Information System (ITIS)'),
    ('a43ec6d8-7b8a-4868-ad74-56b824c75698', 12, 12, 0, null, 'http://ipt.gbif.pt/ipt/archive.do?r=uac_checklist_madeira', 'A list of the terrestrial fungi, flora and fauna of Madeira and Selvagens archipelagos'),
    ('a6c6cead-b5ce-4a4e-8cf5-1542ba708dec', 12, 12, 0, null, 'https://data.gbif.no/ipt/archive.do?r=artsnavn', 'Artsnavnebasen'),
    ('aacd816d-662c-49d2-ad1a-97e66e2a2908', 12, 12, 0, 1,    'http://ipt.jbrj.gov.br/jbrj/archive.do?r=lista_especies_flora_brasil', 'Brazilian Flora 2020 project - Projeto Flora do Brasil 2020'),
    ('b267ac9b-6516-458e-bea7-7643842187f7', 12, 12, 0, 4,    'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_polycestinae', 'Catalogue of the type specimens of Polycestinae (Coleoptera: Buprestidae) from research collections of the Zoological Institute, Russia'),
    ('bd25fbf7-278f-41d6-bc17-9f08f2632f70', 12, 12, 0, 4,    'http://ipt.biodiversity.be/archive.do?r=mrac_fruitfly_checklist', 'True Fruit Flies (Diptera, Tephritidae) of the Afrotropical Region'),
    ('bf3db7c9-5e5d-4fd0-bd5b-94539eaf9598', 12, 12, 0, 1,    'http://rs.gbif.org/datasets/index_fungorum.zip', 'Index Fungorum'),
    ('c33ce2f2-c3cc-43a5-a380-fe4526d63650', 12, 12, 0, null, 'http://rs.gbif.org/datasets/pbdb.zip', 'The Paleobiology Database'),
    ('c696e5ee-9088-4d11-bdae-ab88daffab78', 12, 12, 0, 4,    'http://rs.gbif.org/datasets/ioc.zip', 'IOC World Bird List, v8.1'),
    ('c8227bb4-4143-443f-8cb2-51f9576aff14', 12, 12, 0, 4,    'http://zoobank.org:8080/ipt/archive.do?r=zoobank', 'ZooBank'),
    ('d8fb1600-d636-4b35-aa0d-d4f292c1b424', 12, 12, 0, 4,    'http://rs.gbif.org/datasets/protected/fauna_europaea-lepidoptera.zip', 'Fauna Europaea - Lepidoptera'),
    ('d9a4eedb-e985-4456-ad46-3df8472e00e8', 12, 12, 0, 1,    'https://zenodo.org/record/1194673/files/dwca.zip', 'The Plant List with literature'),
    ('da38f103-4410-43d1-b716-ea6b1b92bbac', 12, 12, 0, 4,    'http://ipt.saiab.ac.za/archive.do?r=catalogueofafrotropicalbees', 'Catalogue of Afrotropical Bees'),
    ('de8934f4-a136-481c-a87a-b0b202b80a31', 12, 12, 0, null, 'http://www.gbif.se/ipt/archive.do?r=test', 'Dyntaxa. Svensk taxonomisk databas'),
    ('ded724e7-3fde-49c5-bfa3-03b4045c4c5f', 12, 12, 0, 1,    'http://wp5.e-taxonomy.eu/download/data/dwca/cichorieae.zip', 'International Cichorieae Network (ICN): Cichorieae Portal'),
    ('e01b0cbb-a10a-420c-b5f3-a3b20cc266ad', 12, 12, 0, 3,    'http://rs.gbif.org/datasets/ictv.zip', 'ICTV Master Species List'),
    ('e1c9e885-9d8c-45b5-9f7d-b710ac2b303b', 12, 12, 0, null, 'http://ipt.taibif.tw/archive.do?r=taibnet_endemic', 'Endemic species in Taiwan'),
    ('e402255a-aed1-4701-9b96-14368e1b5d6b', 12, 12, 0, 4,    'http://ctap.inhs.uiuc.edu/dmitriev/DwCArchive.zip', '3i - Typhlocybinae Database'),
    ('e768b669-5f12-42b3-9bc7-ede76e4436fa', 12, 12, 0, 4,    'http://plazi.cs.umb.edu/GgServer/dwca/61134126326DC5BE0901E529D48F9481.zip', 'Carabodes cephalotes'),
    ('f43069fe-38c1-43e3-8293-37583dcf5547', 12, 12, 0, 1,    'https://svampe.databasen.org/dwc/DMS_Fun_taxa.zip', 'Danish Mycological Society - Checklist of Fungi'),
    ('56c83fd9-533b-4b77-a67a-cf521816866e', 12, 12, 0, 4,    'http://ipt.pensoft.net/archive.do?r=tenebrionidae_north_america', 'Catalogue of Tenebrionidae (Coleoptera) of North America');

UPDATE dataset SET
    data_format = 0,
    contributes_to = 1,
    import_frequency = 7
WHERE gbif_key IS NOT NULL;




--------------------------
-- NEW DATASETS
--   since late 2018 managed in their own github repos
--------------------------

-- for enums we use the int ordinal, i.e. array index starting with 0:
-- origin:  http://api.col.plus/vocab/datasetorigin
--          0=EXTERNAL, 1=UPLOADED, 2=MANAGED
-- type:  http://api.col.plus/vocab/datasettype
--          0=nomenclatural, 1=global, 2=regional, 3=personal, 4=other
-- contributes_to:  http://api.col.plus/vocab/catalogue
--          0=COL, 1=PCAT
-- code:  http://api.col.plus/vocab/nomCode
--          0=bacterial, 1=botanical, 2=cultivars, 3=virus, 4=zoological
-- data_format:  http://api.col.plus/vocab/dataformat
--          0=dwca, 1=acef, 2=tcs, 3=coldp

-- use keys from range 1000-1500 for existing GSD IDs+1000
-- or for entirely new datasets in the range of 1600-1699
INSERT INTO dataset (key, origin, type, contributes_to, code, title, import_frequency, created_by, modified_by, data_format, data_access) VALUES
('1027', 0, 1, 0,    4, 'Scarabs',           1, 0, 0, 1, 'https://github.com/Sp2000/data-scarabs/archive/master.zip'),
('1055', 0, 1, 0,    4, 'Neuropterida',      1, 0, 0, 1, 'https://github.com/Sp2000/data-neuropterida/archive/master.zip'),
('1074', 0, 1, 0,    1, 'ELPT',              1, 0, 0, 1, 'https://github.com/Sp2000/data-elpt/archive/master.zip'),
('1140', 0, 0, 0,    1, 'WorldFerns',        1, 0, 0, 1, 'https://github.com/Sp2000/data-world-ferns/archive/master.zip'),
('1141', 0, 0, 0,    1, 'WorldPlants',       1, 0, 0, 0, 'https://github.com/Sp2000/data-world-plants/archive/master.zip'),
('1202', 0, 1, 0,    4, 'WoRMS Amphipoda',   1, 0, 0, 1, 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/202.tar.gz'),
('1203', 0, 1, 0,    4, 'ThripsWiki',        1, 0, 0, 1, 'https://github.com/Sp2000/data-thrips/archive/master.zip'),
('1204', 0, 1, 0,    4, 'StaphBase',         1, 0, 0, 3, 'https://github.com/Sp2000/data-staphbase/archive/master.zip'),
('1600', 0, 4, null, 1, 'ColDP Example',     7, 0, 0, 3, 'https://github.com/Sp2000/coldp/archive/master.zip'),
('1601', 0, 0, 1,    1, 'MycoBank',          7, 0, 0, 0, 'https://github.com/mdoering/mycobank/raw/master/mycobank.zip'),
('1602', 0, 4, null, 4, 'Testing Data ACEF', 7, 0, 0, 1, 'https://github.com/Sp2000/data-testing/archive/master.zip'),
('1603', 0, 4, null, 4, 'Testing Data ColDP',7, 0, 0, 3, 'https://github.com/Sp2000/data-testing/archive/master.zip'),
('1604', 0, 1, 0,    4, 'StaphBase ACEF',    1, 0, 0, 1, 'https://github.com/Sp2000/data-staphbase/archive/master.zip'),
('1163', 0, 1, 0,    1, 'Cycads',            1, 0, 0, 3, 'https://github.com/gdower/data-cycads/archive/master.zip');




--------------------------
-- UNIT TESTS
--   from https://github.com/Sp2000/data-unit-tests
--------------------------

-- for enums we use the int ordinal, i.e. array index starting with 0:
-- origin:  http://api.col.plus/vocab/datasetorigin
--          0=EXTERNAL, 1=UPLOADED, 2=MANAGED
-- type:  http://api.col.plus/vocab/datasettype
--          0=nomenclatural, 1=global, 2=regional, 3=personal, 4=other
-- contributes_to:  http://api.col.plus/vocab/catalogue
--          0=COL, 1=PCAT
-- code:  http://api.col.plus/vocab/nomCode
--          0=bacterial, 1=botanical, 2=cultivars, 3=virus, 4=zoological
-- data_format:  http://api.col.plus/vocab/dataformat
--          0=dwca, 1=acef, 2=tcs, 3=coldp

-- use keys from range 1000-1500 for existing GSD IDs+1000
-- or for entirely new datasets in the range of 1600-1699
-- or for unit tests use 1700-1799
INSERT INTO dataset (key, origin, type, contributes_to, code, title, import_frequency, created_by, modified_by, data_format, data_access, website, description) VALUES
('1701', 0, 4, null, 4, 'Unit Test: C1  (GSD Duplicates: ACC-ACC species diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C1.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-ACC species (different authors)'),
('1702', 0, 4, null, 4, 'Unit Test: C2  (GSD Duplicates: ACC-ACC species same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C2.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-ACC species (same authors)'),
('1703', 0, 4, null, 4, 'Unit Test: C3  (GSD Duplicates: ACC-ACC infraspecies diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C3.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-ACC infraspecies and infraspecies marker (different authors)'),
('1704', 0, 4, null, 4, 'Unit Test: C4  (GSD Duplicates: ACC-ACC infraspecies same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C4.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-ACC infraspecies and infraspecies marker (same authors)'),
('1705', 0, 4, null, 1, 'Unit Test: C5  (GSD Duplicates: ACC-SYN species diff parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C5.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN species (different parent, different authors)'),
('1706', 0, 4, null, 1, 'Unit Test: C6  (GSD Duplicates: ACC-SYN species diff parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C6.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN species (different parent, same authors)'),
('1707', 0, 4, null, 1, 'Unit Test: C7  (GSD Duplicates: ACC-SYN species same parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C7.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN species (same parent, same authors)'),
('1708', 0, 4, null, 4, 'Unit Test: C8  (GSD Duplicates: ACC-SYN infraspecies diff parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C8.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN infraspecies (different parent, different authors)'),
('1709', 0, 4, null, 1, 'Unit Test: C9  (GSD Duplicates: ACC-SYN infraspecies diff parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C9.zip',  'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN infraspecies (different parent, same authors)'),
('1710', 0, 4, null, 1, 'Unit Test: C10 (GSD Duplicates: ACC-SYN infraspecies same parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C10.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: ACC-SYN infraspecies (same parent, same authors)'),
('1711', 0, 4, null, 1, 'Unit Test: C11 (GSD Duplicates: SYN-SYN species diff parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C11.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN species (different parent, different authors)'),
('1712', 0, 4, null, 1, 'Unit Test: C12 (GSD Duplicates: SYN-SYN species diff parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C12.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN species (different parent, same authors)'),
('1713', 0, 4, null, 1, 'Unit Test: C13 (GSD Duplicates: SYN-SYN species same parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C13.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN species (same parent, different authors)'),
('1714', 0, 4, null, 1, 'Unit Test: C14 (GSD Duplicates: SYN-SYN species same parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C14.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN species (same parent, same authors)'),
('1715', 0, 4, null, 1, 'Unit Test: C15 (GSD Duplicates: SYN-SYN infraspecies diff parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C15.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN infraspecies (different parent, different authors)'),
('1716', 0, 4, null, 1, 'Unit Test: C16 (GSD Duplicates: SYN-SYN infraspecies diff parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C16.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN infraspecies (different parent, same authors)'),
('1717', 0, 4, null, 1, 'Unit Test: C17 (GSD Duplicates: SYN-SYN infraspecies same parent, diff authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C17.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN infraspecies (same parent, different authors)'),
('1718', 0, 4, null, 1, 'Unit Test: C18 (GSD Duplicates: SYN-SYN infraspecies same parent, same authors)',     1, 0, 0, 3, 'https://github.com/Sp2000/data-unit-tests/raw/master/C18.zip', 'https://github.com/Sp2000/colplus-backend/issues/195', 'GSD duplicates: SYN-SYN infraspecies (same parent, same authors)'),
('1719', 0, 4, null, 1, 'Unit Test: A99 (Data validation: References)',                                        1, 0, 0, 1, 'https://github.com/Sp2000/data-unit-tests/raw/master/A99.zip', 'https://github.com/Sp2000/colplus-backend/issues/193', 'Unit Test: A99 (Data validation: References)');
