
--------------------------
-- COL GSD DATASETS
--   dumped from assembly_global into colplus-repo
--   commented out ids are ignored from ACEF dumps because we have a superior coldp archive already
--------------------------

-- origin:  0=EXTERNAL, 1=UPLOADED, 2=MANAGED
INSERT INTO dataset (key, origin, type, title, import_frequency, created_by, modified_by, data_format, data_access)
SELECT x.id+1000, 'EXTERNAL', 'TAXONOMIC', 'GSD ' || x.id, 1, 0, 0, 'ACEF', 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/' || x.id || '.tar.gz'
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
140,
141,
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
163,
164,
166,
167,
168,
169,
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
202,
203,
204,
21,
22,
23,
24,
25,
26,
27,
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
502,
51,
52,
53,
54,
55,
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
70,
73,
74,
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

-- code:  http://api.col.plus/vocab/nomCode
--          0=bacterial, 1=botanical, 2=cultivars, 3=virus, 4=zoological

UPDATE dataset SET code=1 WHERE key IN (
	1015,1025,1036,1038,1040,1041,1045,1048,1066,1074,1097,1098,1163
);
UPDATE dataset SET code=3 WHERE key IN (
	1014
);
UPDATE dataset SET code=4 WHERE key IN (
	1005,1006,1008,1009,1010,1011,1018,1020,1021,1022,1023,1026,1027,1029,1030,1031,1032,1034,1037,1039,1042,1044,
	1046,1047,1049,1050,1051,1052,1054,1055,1057,1058,1059,1061,1062,1063,1065,1067,1068,1069,1070,1076,1078,
	1080,1081,1082,1085,1086,1087,1088,1089,1090,1091,1092,1093,1094,1095,1096,1099,1100,1103,1104,1105,1106,
	1107,1108,1109,1110,1112,1118,1119,1120,1122,1130,1133,1134,1202,1203,1204
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


-- dataset titles, will be overwritten by actual titles in the archives
UPDATE dataset SET alias='CCW', title='Catalogue of Craneflies of the World' WHERE key=1005;
UPDATE dataset SET alias='CIPA', title='Computer Aided Identification of Phlebotomine sandflies of Americas' WHERE key=1006;
UPDATE dataset SET alias='ReptileDB', title='The Reptile Database' WHERE key=1008;
UPDATE dataset SET alias='ETI WBD (Euphausiacea)', title='World Biodiversity Database (Euphausiacea)' WHERE key=1009;
UPDATE dataset SET alias='FishBase', title='FishBase' WHERE key=1010;
UPDATE dataset SET alias='FLOW', title='Fulgoromorpha Lists On the WEB' WHERE key=1011;
UPDATE dataset SET alias='Glomeromycota', title='Phylogeny and taxonomy of Glomeromycota (arbuscular mycorrhizal (AM) and related fungi)' WHERE key=1012;
UPDATE dataset SET alias='ICTV_MSL', title='International Committee on Taxonomy of Viruses / Master Species List' WHERE key=1014;
UPDATE dataset SET alias='ILDIS', title='ILDIS World Database of Legumes' WHERE key=1015;
UPDATE dataset SET alias='ITIS Regional', title='The Integrated Taxonomic Information System' WHERE key=1017;
UPDATE dataset SET alias='LepIndex', title='LepIndex: The Global Lepidoptera Names Index' WHERE key=1018;
UPDATE dataset SET alias='MOST', title='Moss TROPICOS Database' WHERE key=1019;
UPDATE dataset SET alias='Odonata', title='Global Species Database of Odonata' WHERE key=1020;
UPDATE dataset SET alias='SF Orthoptera', title='Orthoptera Species File' WHERE key=1021;
UPDATE dataset SET alias='Parhost', title='Parhost World Database of Fleas' WHERE key=1022;
UPDATE dataset SET alias='Phyllachorales', title='Phyllachorales' WHERE key=1023;
UPDATE dataset SET alias='WCSP', title='World Checklist of Selected Plant Families' WHERE key=1024;
UPDATE dataset SET alias='Rhytismatales', title='Rhytismatales' WHERE key=1025;
UPDATE dataset SET alias='ScaleNet', title='Systematic Database of the Scale Insects of the World' WHERE key=1026;
UPDATE dataset SET alias='Scarabs', title='World Scarabaeidae Database' WHERE key=1027;
UPDATE dataset SET alias='Species Fungorum', title='Species Fungorum' WHERE key=1028;
UPDATE dataset SET alias='WSC', title='World Spider Catalog' WHERE key=1029;
UPDATE dataset SET alias='TicksBase', title='TicksBase' WHERE key=1030;
UPDATE dataset SET alias='Tineidae NHM', title='Tineidae NHM: Global taxonomic database of Tineidae (Lepidoptera)' WHERE key=1031;
UPDATE dataset SET alias='TITAN', title='Cerambycidae database' WHERE key=1032;
UPDATE dataset SET alias='Trichomycetes', title='Trichomycetes ‚Äì Fungi Associated with Arthropods' WHERE key=1033;
UPDATE dataset SET alias='UCD', title='Universal Chalcidoidea Database' WHERE key=1034;
UPDATE dataset SET alias='Xylariaceae', title='Home of the Xylariaceae' WHERE key=1036;
UPDATE dataset SET alias='ZOBODAT Vespoidea', title='Zoological-Botanical Database (Vespoidea)' WHERE key=1037;
UPDATE dataset SET alias='Zygomycetes', title='Zygomycetes' WHERE key=1038;
UPDATE dataset SET alias='WTaxa', title='Electronic Catalogue of Weevil names (Curculionoidea) ' WHERE key=1039;
UPDATE dataset SET alias='AnnonBase', title='Annonaceae GSD' WHERE key=1040;
UPDATE dataset SET alias='ChiloBase', title='A World Catalogue of Centipedes (Chilopoda) for the Web' WHERE key=1042;
UPDATE dataset SET alias='WoRMS Porifera', title='World Porifera database' WHERE key=1044;
UPDATE dataset SET alias='Conifer Database', title='Conifer Database' WHERE key=1045;
UPDATE dataset SET alias='GloBIS (GART)', title='Global Butterfly Information System' WHERE key=1046;
UPDATE dataset SET alias='FADA Rotifera', title='Annotated checklist of the rotifers (Phylum Rotifera)' WHERE key=1047;
UPDATE dataset SET alias='RJB Geranium', title='Geranium Taxonomic Information System' WHERE key=1048;
UPDATE dataset SET alias='Global Gracillariidae', title='Global Taxonomic Database of Gracillariidae' WHERE key=1049;
UPDATE dataset SET alias='SF Phasmida', title='Phasmida Species File' WHERE key=1050;
UPDATE dataset SET alias='SF Cockroach', title='Cockroach Species File' WHERE key=1051;
UPDATE dataset SET alias='COOL', title='Cercopoidea Organised On Line' WHERE key=1052;
UPDATE dataset SET alias='Nomen.eumycetozoa.com', title='An online nomenclatural information system of Eumycetozoa' WHERE key=1053;
UPDATE dataset SET alias='Psyllist', title='Psylloidea database' WHERE key=1054;
UPDATE dataset SET alias='LDL Neuropterida', title='LDL Neuropterida Species of the World' WHERE key=1055;
UPDATE dataset SET alias='Brachiopoda Database', title='Brachiopoda Database' WHERE key=1057;
UPDATE dataset SET alias='WoRMS Cumacea', title='World Cumacea Database' WHERE key=1058;
UPDATE dataset SET alias='WoRMS Ophiuroidea', title='World Ophiuroidea database' WHERE key=1059;
UPDATE dataset SET alias='SF Aphid', title='Aphid Species File' WHERE key=1061;
UPDATE dataset SET alias='SF Mantodea', title='Mantodea Species File' WHERE key=1062;
UPDATE dataset SET alias='OlogamasidBase', title='Mites GSDs: OlogamasidBase' WHERE key=1063;
UPDATE dataset SET alias='SF Plecoptera', title='Plecoptera Species File' WHERE key=1065;
UPDATE dataset SET alias='Droseraceae Database', title='Droseraceae Database' WHERE key=1066;
UPDATE dataset SET alias='ITIS Bees', title='ITIS World Bee Checklist' WHERE key=1067;
UPDATE dataset SET alias='Taxapad Ichneumonoidea', title='Taxapad Ichneumonoidea' WHERE key=1068;
UPDATE dataset SET alias='RhodacaridBase', title='Mites GSDs: RhodacaridBase' WHERE key=1069;
UPDATE dataset SET alias='PhytoseiidBase', title='Mites GSDs: PhytoseiidBase' WHERE key=1070;
UPDATE dataset SET alias='Brassicaceae', title='Brassicaceae species checklist and database' WHERE key=1073;
UPDATE dataset SET alias='ELPT', title='Early Land Plants Today ' WHERE key=1074;
UPDATE dataset SET alias='NZIB', title='New Zealand Inventory of Biodiversity' WHERE key=1075;
UPDATE dataset SET alias='MBB', title='Moss Bug Base' WHERE key=1076;
UPDATE dataset SET alias='TenuipalpidBase', title='Mites GSDs: TenuipalpidBase' WHERE key=1078;
UPDATE dataset SET alias='LIAS', title='A Global Information System for Lichenized and Non-Lichenized Ascomycetes' WHERE key=1079;
UPDATE dataset SET alias='BdelloideaBase', title='Bdellid & Cunaxid Databases' WHERE key=1080;
UPDATE dataset SET alias='WoRMS Bryozoa', title='World List of Bryozoa' WHERE key=1081;
UPDATE dataset SET alias='SpmWeb', title='Spider Mites Web' WHERE key=1082;
UPDATE dataset SET alias='WoRMS Nemertea', title='World Nemertea Database' WHERE key=1085;
UPDATE dataset SET alias='WoRMS Bochusacea', title='World List of Bochusacea' WHERE key=1086;
UPDATE dataset SET alias='WoRMS Brachypoda', title='World List of Brachypoda' WHERE key=1087;
UPDATE dataset SET alias='WoRMS Mystacocarida', title='World List of Mystacocarida' WHERE key=1088;
UPDATE dataset SET alias='SF Embioptera', title='Embioptera Species File' WHERE key=1089;
UPDATE dataset SET alias='WoRMS Polychaeta', title='World Polychaeta database' WHERE key=1090;
UPDATE dataset SET alias='WoRMS Remipedia', title='World Remipedia Database' WHERE key=1091;
UPDATE dataset SET alias='WoRMS Tantulocarida', title='World List of Tantulocarida' WHERE key=1092;
UPDATE dataset SET alias='WoRMS Thermosbaenacea', title='World List of Thermosbaenacea' WHERE key=1093;
UPDATE dataset SET alias='WoRMS Isopoda', title='World Marine, Freshwater and Terrestrial Isopod Crustaceans database' WHERE key=1094;
UPDATE dataset SET alias='WoRMS Asteroidea', title='World Asteroidea Database' WHERE key=1095;
UPDATE dataset SET alias='MOWD', title='MOWD: Membracoidea of the World Database' WHERE key=1096;
UPDATE dataset SET alias='Saccharomycetes', title='Saccharomycetes - ascomycetous yeast forming fungi' WHERE key=1097;
UPDATE dataset SET alias='Dothideomycetes', title='Dothideomycetes - saprobic or parasitic ascolocular ascomycetes' WHERE key=1098;
UPDATE dataset SET alias='WoRMS Oligochaeta', title='World List of Marine Oligochaeta' WHERE key=1099;
UPDATE dataset SET alias='WoRMS Xenoturbellida', title='World List of Xenoturbellida' WHERE key=1100;
UPDATE dataset SET alias='Systema Dipterorum', title='Systema Dipterorum' WHERE key=1101;
UPDATE dataset SET alias='Strepsiptera Database', title='Global Strepsiptera Database' WHERE key=1103;
UPDATE dataset SET alias='Phoronida Database', title='Phoronida Database' WHERE key=1104;
UPDATE dataset SET alias='WoRMS Leptostraca', title='World List of Leptostraca' WHERE key=1105;
UPDATE dataset SET alias='WoRMS Echinoidea', title='World Echinoidea Database' WHERE key=1106;
UPDATE dataset SET alias='WoRMS Holothuroidea', title='World List of Holothuroidea' WHERE key=1107;
UPDATE dataset SET alias='WoRMS Brachyura', title='World List of marine Brachyura' WHERE key=1108;
UPDATE dataset SET alias='WoRMS Polycystina', title='World List of Polycystina (Radiolaria)' WHERE key=1109;
UPDATE dataset SET alias='WoRMS Tanaidacea', title='World List of Tanaidacea' WHERE key=1110;
UPDATE dataset SET alias='WoRMS Hydrozoa', title='World Hydrozoa Database' WHERE key=1112;
UPDATE dataset SET alias='CilCat', title='The World Ciliate Catalog' WHERE key=1113;
UPDATE dataset SET alias='ITIS Global', title='The Integrated Taxonomic Information System' WHERE key=1115;
UPDATE dataset SET alias='HymIS', title='Hymenoptera Information SysteHymenoptera Information System' WHERE key=1118;
UPDATE dataset SET alias='FADA Nematomorpha', title='World checklist of freshwater Nematomorpha species' WHERE key=1119;
UPDATE dataset SET alias='FADA Ephemeroptera', title='World checklist of freshwater Ephemeroptera species' WHERE key=1120;
UPDATE dataset SET alias='CoL China', title='CoL China: Catalogue of Life China' WHERE key=1121;
UPDATE dataset SET alias='WoRMS Gastrotricha', title='World Gastrotricha Database' WHERE key=1122;
UPDATE dataset SET alias='WoRMS Placozoa', title='World Placozoa Database' WHERE key=1123;
UPDATE dataset SET alias='WoRMS Priapulida', title='World List of Priapulida' WHERE key=1124;
UPDATE dataset SET alias='WoRMS Gnathostomulida', title='World List of Gnathostomulida' WHERE key=1125;
UPDATE dataset SET alias='WoRMS Monogenea', title='World List of Monogenea' WHERE key=1126;
UPDATE dataset SET alias='WoRMS Cestoda', title='World List of Cestoda' WHERE key=1127;
UPDATE dataset SET alias='WoRMS Trematoda', title='World List of Trematoda' WHERE key=1128;
UPDATE dataset SET alias='WoRMS Myxozoa', title='World list of Myxozoa' WHERE key=1129;
UPDATE dataset SET alias='WoRMS Mollusca', title='MolluscaBase (2017)' WHERE key=1130;
UPDATE dataset SET alias='WoRMS Octocorallia', title='World List of Octocorallia' WHERE key=1131;
UPDATE dataset SET alias='WoRMS Chaetognatha', title='World List of Chaetognatha' WHERE key=1132;
UPDATE dataset SET alias='SF Psocodea', title='Psocodea Species File' WHERE key=1133;
UPDATE dataset SET alias='SF Coreoidea', title='Coreoidea Species File' WHERE key=1134;
UPDATE dataset SET alias='FADA Cladocera', title='World checklist of freshwater Cladocera species' WHERE key=1138;
UPDATE dataset SET alias='FADA Halacaridae', title='World checklist of freshwater Halacaridae species' WHERE key=1139;
UPDATE dataset SET alias='World Ferns', title='Checklist of Ferns and Lycophytes of the World' WHERE key=1140;
UPDATE dataset SET alias='World Plants', title='Synonymic Checklists of the Vascular Plants of the World' WHERE key=1141;
UPDATE dataset SET alias='The White-Files', title='Taxonomic checklist of the world''s whiteflies (Insecta: Hemiptera: Aleyrodidae)' WHERE key=1142;
UPDATE dataset SET alias='Tessaratomidae Database', title=' Illustrated catalog of Tessaratomidae' WHERE key=1143;
UPDATE dataset SET alias='Lace Bugs Database', title='Lace Bugs Database (Hemiptera: Tingidae)' WHERE key=1144;
UPDATE dataset SET alias='CarabCat', title='Global database of ground beetles' WHERE key=1146;
UPDATE dataset SET alias='Microsporidia', title='Microsporidia: Unicellular spore-forming protozoan parasites' WHERE key=1148;
UPDATE dataset SET alias='WoRMS Orthonectida', title='World List of Orthonectida' WHERE key=1149;
UPDATE dataset SET alias='WoRMS Rhombozoa', title='World List of Rhombozoa' WHERE key=1150;
UPDATE dataset SET alias='WoRMS Merostomata', title='World List of Merostomata' WHERE key=1152;
UPDATE dataset SET alias='WoRMS Kinorhyncha', title='World List of Kinorhyncha' WHERE key=1153;
UPDATE dataset SET alias='WoRMS Cephalochordata', title='World List of Cephalochordata' WHERE key=1154;
UPDATE dataset SET alias='WoRMS Foraminifera', title='World Foraminifera Database' WHERE key=1157;
UPDATE dataset SET alias='SF Dermaptera', title='Dermaptera Species File' WHERE key=1158;
UPDATE dataset SET alias='Brentids', title='Brentidae of the World' WHERE key=1161;
UPDATE dataset SET alias='WWW', title='World WideW attle' WHERE key=1162;
UPDATE dataset SET alias='The World List of Cycads', title='The World List of Cycads' WHERE key=1163;
UPDATE dataset SET alias='The Scorpion Files', title='The Scorpion Files' WHERE key=1164;
UPDATE dataset SET alias='3i Curculio', title='3i taxonomic databases, Curculionidae, subfamily Entiminae' WHERE key=1166;
UPDATE dataset SET alias='SF Zoraptera', title='Zoraptera Species File' WHERE key=1167;
UPDATE dataset SET alias='SF Mantophasmatodea', title='Mantophasmatodea Species File' WHERE key=1168;
UPDATE dataset SET alias='SF Chrysididae', title='Chrysididae Species File' WHERE key=1169;
UPDATE dataset SET alias='SF Grylloblattodea', title='Grylloblattodea Species File' WHERE key=1170;
UPDATE dataset SET alias='PBI Plant Bug', title='On-line Systematic Catalog of Plant Bugs (Insecta: Heteroptera: Miridae)' WHERE key=1171;
UPDATE dataset SET alias='Nepticuloidea', title='Nepticulidae and Opostegidae of the World' WHERE key=1172;
UPDATE dataset SET alias='SF Lygaeoidea', title='Lygaeoidea Species File' WHERE key=1173;
UPDATE dataset SET alias='PaleoBioDB', title='The Paleobiology Database' WHERE key=1174;
UPDATE dataset SET alias='WoRMS Ostracoda', title='World Ostracoda Database' WHERE key=1175;
UPDATE dataset SET alias='WoRMS Hexacoral', title='Hexacorallians (Actiniaria) of the World' WHERE key=1176;
UPDATE dataset SET alias='Gymnodinium', title='The dinoflagellate genus Gymnodinium checklist' WHERE key=1177;
UPDATE dataset SET alias='WoRMS Appendicularia', title='World List of Appendicularia' WHERE key=1178;
UPDATE dataset SET alias='WoRMS Ceriantharia', title='World list of Ceriantharia' WHERE key=1179;
UPDATE dataset SET alias='WoRMS Ctenophora', title='Phylum Ctenophora, a list of all valid species names harvested from the Internet between 1998 - pres' WHERE key=1180;
UPDATE dataset SET alias='WoRMS Cubozoa', title='World list of Cubozoa' WHERE key=1181;
UPDATE dataset SET alias='WoRMS Loricifera', title='World list of Loricifera' WHERE key=1182;
UPDATE dataset SET alias='WoRMS PycnoBase', title='World Pycnogonida Database' WHERE key=1183;
UPDATE dataset SET alias='WoRMS Staurozoa', title='World list of Staurozoa' WHERE key=1184;
UPDATE dataset SET alias='WoRMS Thaliacea', title='World list of Thaliacea' WHERE key=1185;
UPDATE dataset SET alias='WoRMS Ascidiacea', title='Ascidiacea World Database' WHERE key=1186;
UPDATE dataset SET alias='WoRMS Scyphozoa', title='World list of Scyphozoa' WHERE key=1188;
UPDATE dataset SET alias='3i Auchenorrhyncha', title='World Auchenorrhyncha Database' WHERE key=1189;
UPDATE dataset SET alias='Jewel Beetles', title='The World of Jewel Beetles' WHERE key=1190;
UPDATE dataset SET alias='WoRMS Copepoda', title='World of Copepods database' WHERE key=1191;
UPDATE dataset SET alias='SF Coleorrhyncha', title='Coleorrhyncha Species File' WHERE key=1192;
UPDATE dataset SET alias='WoRMS Turbellarians', title='World List of turbellarian worms, Acoelomorpha, Catenulida, Rhabditophora' WHERE key=1193;
UPDATE dataset SET alias='WoRMS Antipatharia', title='World List of Antipatharia' WHERE key=1194;
UPDATE dataset SET alias='WoRMS Corallimorpharia', title='World List of Corallimorpharia' WHERE key=1195;
UPDATE dataset SET alias='WoRMS Scleractinia', title='World List of Scleractinia' WHERE key=1196;
UPDATE dataset SET alias='WoRMS Zoantharia', title='World List of Zoantharia' WHERE key=1197;
UPDATE dataset SET alias='SF Isoptera', title='Isoptera Species File' WHERE key=1198;
UPDATE dataset SET alias='Pterophoroidea', title='Catalogue of the Pterophoroidea of the World' WHERE key=1199;
UPDATE dataset SET alias='WoRMS MilliBase', title='MilliBase' WHERE key=1200;
UPDATE dataset SET alias='Ginkgoales', title='Fossil Ginkgoales' WHERE key=1201;
UPDATE dataset SET alias='WoRMS Amphipoda', title='World Amphipoda Database' WHERE key=1202;
UPDATE dataset SET alias='ThripsWiki', title='ThripsWiki - providing information on the World''s thrips' WHERE key=1203;
UPDATE dataset SET alias='StaphBase', title='Staphyliniformia world catalog database' WHERE key=1204;
UPDATE dataset SET alias='CoL Management Classification', title='A Higher Level Classification of All Living Organisms. In: PLoS ONE 10(4): e0119248. doi:10.1371/jou' WHERE key=1500;
UPDATE dataset SET alias='IRMNG', title='Interim Register of Marine and Nonmarine Genera' WHERE key=1501;
UPDATE dataset SET alias='Animal biodiversity', title='An Outline of Higher-level Classification and Survey of Taxonomic Richness (Addenda 2013)1' WHERE key=1502;

-- take logos from repo
UPDATE dataset SET logo='https://github.com/Sp2000/colplus-repo/raw/master/logos/' || replace(alias,' ','_') || '.png' WHERE alias IS NOT NULL AND key > 999 AND key < 2000;

--------------------------
-- NEW DATASETS
--   since late 2018 managed in their own github repos
--------------------------

-- for enums we use the int ordinal, i.e. array index starting with 0:
-- origin:  http://api.col.plus/vocab/datasetorigin
--          0=EXTERNAL, 1=UPLOADED, 2=MANAGED
-- type:  http://api.col.plus/vocab/datasettype
--          0=nomenclatural, 1=taxonomic, 2=article, 3=personal, 4=otu, 5=catalogue, 6=thematic, 7=other
-- code:  http://api.col.plus/vocab/nomCode
--          0=bacterial, 1=botanical, 2=cultivars, 3=virus, 4=zoological
-- data_format:  http://api.col.plus/vocab/dataformat
--          0=dwca, 1=acef, 2=tcs, 3=coldp

-- use keys from range 1000-1500 for CoL GSD IDs+1000
INSERT INTO dataset (key, origin, type, code, title, import_frequency, created_by, modified_by, data_format, data_access) VALUES
--('1027', 0, 1, 4, 'Scarabs',           1, 0, 0, 1, 'https://github.com/Sp2000/data-scarabs/archive/master.zip'),
--('1055', 0, 1, 4, 'LDL Neuropterida',  1, 0, 0, 1, 'https://github.com/Sp2000/data-neuropterida/archive/master.zip'),
--('1074', 0, 1, 1, 'ELPT',              1, 0, 0, 1, 'https://github.com/Sp2000/data-elpt/archive/master.zip'),
--('1140', 0, 0, 1, 'World Ferns',       1, 0, 0, 1, 'https://github.com/Sp2000/data-world-ferns/archive/master.zip'),
--('1141', 0, 0, 1, 'World Plants',      1, 0, 0, 1, 'https://github.com/Sp2000/data-world-plants/archive/master.zip'),
--('1163', 0, 1, 1, 'Cycads',            1, 0, 0, 3, 'https://github.com/gdower/data-cycads/archive/master.zip'),
--
--('1202', 0, 1, 4, 'WoRMS Amphipoda',   1, 0, 0, 1, 'https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/202.tar.gz'),
--('1203', 0, 1, 4, 'ThripsWiki',        1, 0, 0, 1, 'https://github.com/Sp2000/data-thrips/archive/master.zip'),
--('1204', 0, 1, 4, 'StaphBase',         1, 0, 0, 3, 'https://github.com/Sp2000/data-staphbase/archive/master.zip'),
('1206', 'EXTERNAL', 'TAXONOMIC', 'ZOOLOGICAL', 'Sepidiini',         1, 0, 0, 'ACEF', 'https://github.com/gdower/data-sepidiini/archive/master.zip');

UPDATE dataset set alias=title WHERE key < 2000 and alias IS NULL;

INSERT INTO dataset (key, origin, type, code, title, alias, import_frequency, created_by, modified_by, data_format, data_access)
VALUES ('1000', 'EXTERNAL', 'TAXONOMIC', null, 'Col Hierarchy', 'ColH', 1, 0, 0, 'COLDP', 'https://github.com/Sp2000/col-hierarchy/archive/master.zip');


--------------------------
-- TEST DATASETS
--   since late 2018 managed in their own github repos
-- use ID range 1700-1799
ALTER SEQUENCE dataset_key_seq RESTART WITH 1700;
--------------------------

-- for enums we use the int ordinal, i.e. array index starting with 0:
-- origin:  http://api.col.plus/vocab/datasetorigin
--          0=EXTERNAL, 1=UPLOADED, 2=MANAGED
-- type:  http://api.col.plus/vocab/datasettype
--          0=nomenclatural, 1=taxonomic, 2=article, 3=personal, 4=otu, 5=catalogue, 6=thematic, 7=other
-- code:  http://api.col.plus/vocab/nomCode
--          0=bacterial, 1=botanical, 2=cultivars, 3=virus, 4=zoological
-- data_format:  http://api.col.plus/vocab/dataformat
--          0=dwca, 1=acef, 2=tcs, 3=coldp, 4=proxy
INSERT INTO dataset (origin, type, code, title, import_frequency, created_by, modified_by, data_format, data_access) VALUES
('EXTERNAL', 'OTHER', 'BOTANICAL',  'ColDP Example',           7, 0, 0, 'COLDP', 'https://github.com/Sp2000/coldp/archive/master.zip'),
('EXTERNAL', 'OTHER', 'ZOOLOGICAL', 'Testing Data ACEF',       7, 0, 0, 'ACEF', 'https://github.com/Sp2000/data-testing/archive/master.zip'),
('EXTERNAL', 'OTHER', 'ZOOLOGICAL', 'Duplicates Testing Data', 7, 0, 0, 'COLDP', 'https://raw.githubusercontent.com/Sp2000/data-unit-tests/master/duplicates.zip'),
('EXTERNAL', 'OTHER', 'ZOOLOGICAL', 'Testing Data ColDP',      7, 0, 0, 'COLDP', 'https://github.com/Sp2000/data-testing/archive/master.zip');



--------------------------
-- GBIF and others
--   for the provisional catalogue
-- key range above 2000
ALTER SEQUENCE dataset_key_seq RESTART WITH 2000;
--------------------------

-- all datasets from https://github.com/gbif/checklistbank/blob/master/checklistbank-nub/nub-sources.tsv
-- excluding CoL, the GBIF patches and entire organisation or installations which we add below as lists of datasets
-- nom codes: 0=BACTERIAL, 1=BOTANICAL, 2=CULTIVARS, 3=VIRUS, 4=ZOOLOGICAL
-- types      0=NOMENCLATURAL, 1=TAXONOMIC, 2=ARTICLE, 3=PERSONAL, 4=OTU, 5=CATALOGUE, 6=THEMATIC, 7=OTHER

INSERT INTO dataset (gbif_key, type, created_by, modified_by, origin, code, data_format, data_access, title, alias) VALUES
    (null, 'TAXONOMIC', 0, 0, 'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://storage.googleapis.com/powop-content/backbone/powoNames.zip', 'PoWO Names', 'PoWo'),
    (null, 'TAXONOMIC', 0, 0, 'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://storage.googleapis.com/powop-content/backbone/powoPlantFamilies.zip', 'PoWO Families', null),
    (null, 'NOMENCLATURAL', 0, 0, 'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://github.com/mdoering/mycobank/archive/master.zip', 'MycoBank', 'MycoBank'),
    (null, 'NOMENCLATURAL', 0, 0, 'EXTERNAL', 'BOTANICAL', 'COLDP', 'https://github.com/mdoering/data-ina/archive/master.zip', 'INA', 'INA'),
    (null, 'TAXONOMIC', 0, 0, 'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://104.198.143.165/files/WFO_Backbone/_WFOCompleteBackbone/WFO_Backbone.zip', 'WFO Backbone', 'WFO'),
    ('00e791be-36ae-40ee-8165-0b2cb0b8c84f', 'THEMATIC', 12, 12,'EXTERNAL', null, 'DWCA', 'https://github.com/mdoering/famous-organism/archive/master.zip', 'Species named after famous people', null),
    ('046bbc50-cae2-47ff-aa43-729fbf53f7c5', 'NOMENCLATURAL', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://github.com/mdoering/ipni/raw/master/ipni.zip', 'International Plant Names Index', 'IPNI'),
    ('0938172b-2086-439c-a1dd-c21cb0109ed5', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://www.irmng.org/export/IRMNG_genera_DwCA.zip', 'The Interim Register of Marine and Nonmarine Genera', 'IRMNG'),
    ('0e61f8fe-7d25-4f81-ada7-d970bbb2c6d6', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://ipt.gbif.fr/archive.do?r=taxref-test', 'TAXREF', 'TAXREF'),
    ('1c1f2cfc-8370-414f-9202-9f00ccf51413', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://rs.gbif.org/datasets/protected/euro_med.zip', 'Euro+Med PlantBase data sample', 'Euro+Med'),
    ('1ec61203-14fa-4fbd-8ee5-a4a80257b45a', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://ipt.taibif.tw/archive.do?r=taibnet_com_all', 'The National Checklist of Taiwan', null),
    ('2d59e5db-57ad-41ff-97d6-11f5fb264527', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip', 'World Register of Marine Species', 'WoRMS'),
    ('3f8a1297-3259-4700-91fc-acc4170b27ce', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://data.canadensys.net/ipt/archive.do?r=vascan', 'Database of Vascular Plants of Canada (VASCAN)', 'VASCAN'),
    ('47f16512-bf31-410f-b272-d151c996b2f6', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://rs.gbif.org/datasets/clements.zip', 'The Clements Checklist', null),
    ('4dd32523-a3a3-43b7-84df-4cda02f15cf7', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://api.biodiversitydata.nl/v2/taxon/dwca/getDataSet/nsr', 'Checklist Dutch Species Register - Nederlands Soortenregister', null),
    ('52a423d2-0486-4e77-bcee-6350d708d6ff', 'NOMENCLATURAL', 12, 12,'EXTERNAL', 'BACTERIAL', 'DWCA', 'http://rs.gbif.org/datasets/dsmz.zip', 'Prokaryotic Nomenclature Up-to-date', null),
    ('5c7bf05c-2890-48e8-9b65-a6060cb75d6d', 'OTHER', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_megophryidae_bufonidae', 'Catalogue of the type specimens of Bufonidae and Megophryidae (Amphibia: Anura) from research collections of the Zoological Institute,', null),
    ('65c9103f-2fbf-414b-9b0b-e47ca96c5df2', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.biodiversity.be/archive.do?r=afromoths', 'Afromoths, online database of Afrotropical moth species (Lepidoptera)', 'Afromoths'),
    ('66dd0960-2d7d-46ee-a491-87b9adcfe7b1', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://rs.gbif.org/datasets/grin_archive.zip', 'GRIN Taxonomy', 'GRIN'),
    ('672aca30-f1b5-43d3-8a2b-c1606125fa1b', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://rs.gbif.org/datasets/msw3.zip', 'Mammal Species of the World', 'MSW3'),
    ('6cfd67d6-4f9b-400b-8549-1933ac27936f', 'OTHER', 12, 12,'EXTERNAL', null, 'DWCA', 'http://api.gbif.org/v1/occurrence/download/request/dwca-type-specimen-checklist.zip', 'GBIF Type Specimen Names', null),
    ('7a9bccd4-32fc-420e-a73b-352b92267571', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://data.canadensys.net/ipt/archive.do?r=coleoptera-ca-ak', 'Checklist of Beetles (Coleoptera) of Canada and Alaska. Second Edition.', null),
    ('7ea21580-4f06-469d-995b-3f713fdcc37c', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://github.com/gbif/algae/archive/master.zip', 'GBIF Algae Classification', null),
    ('80b4b440-eaca-4860-aadf-d0dfdd3e856e', 'NOMENCLATURAL', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'https://github.com/gbif/iczn-lists/archive/master.zip', 'Official Lists and Indexes of Names in Zoology', null),
    ('8d431c96-9e2f-4249-8b0a-d875e3273908', 'OTHER', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_cosmopterigidae', 'Catalogue of the type specimens of Cosmopterigidae (Lepidoptera: Gelechioidea) from research collections of the Zoological Institute, R', null),
    ('8dc469b3-8e61-4f6f-b9db-c70dbbc8858c', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'https://raw.githubusercontent.com/mdoering/ion-taxonomic-hierarchy/master/classification.tsv', 'ION Taxonomic Hierarchy', null),
    ('90d9e8a6-0ce1-472d-b682-3451095dbc5a', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://rs.gbif.org/datasets/protected/fauna_europaea.zip', 'Fauna Europaea', 'FaEu'),
    ('96dfd141-7bca-4f82-9325-4420d24e0793', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://plazi.cs.umb.edu/GgServer/dwca/49CC45D6B497E6D97BDDF3C0D38289E2.zip', 'Spinnengids', null),
    ('9ca92552-f23a-41a8-a140-01abaa31c931', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://rs.gbif.org/datasets/itis.zip', 'Integrated Taxonomic Information System (ITIS)', 'ITIS'),
    ('a43ec6d8-7b8a-4868-ad74-56b824c75698', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://ipt.gbif.pt/ipt/archive.do?r=uac_checklist_madeira', 'A list of the terrestrial fungi, flora and fauna of Madeira and Selvagens archipelagos', null),
    ('a6c6cead-b5ce-4a4e-8cf5-1542ba708dec', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'https://data.gbif.no/ipt/archive.do?r=artsnavn', 'Artsnavnebasen', null),
    ('aacd816d-662c-49d2-ad1a-97e66e2a2908', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://ipt.jbrj.gov.br/jbrj/archive.do?r=lista_especies_flora_brasil', 'Brazilian Flora 2020 project - Projeto Flora do Brasil 2020', null),
    ('b267ac9b-6516-458e-bea7-7643842187f7', 'OTHER', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.zin.ru:8080/ipt/archive.do?r=zin_polycestinae', 'Catalogue of the type specimens of Polycestinae (Coleoptera: Buprestidae) from research collections of the Zoological Institute, Russia', null),
    ('bd25fbf7-278f-41d6-bc17-9f08f2632f70', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.biodiversity.be/archive.do?r=mrac_fruitfly_checklist', 'True Fruit Flies (Diptera, Tephritidae) of the Afrotropical Region', null),
    ('bf3db7c9-5e5d-4fd0-bd5b-94539eaf9598', 'NOMENCLATURAL', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://rs.gbif.org/datasets/index_fungorum.zip', 'Index Fungorum', null),
    ('c33ce2f2-c3cc-43a5-a380-fe4526d63650', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://rs.gbif.org/datasets/pbdb.zip', 'The Paleobiology Database', null),
    ('c696e5ee-9088-4d11-bdae-ab88daffab78', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://rs.gbif.org/datasets/ioc.zip', 'IOC World Bird List, v8.1', null),
    ('c8227bb4-4143-443f-8cb2-51f9576aff14', 'NOMENCLATURAL', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://zoobank.org:8080/ipt/archive.do?r=zoobank', 'ZooBank', null),
    ('d8fb1600-d636-4b35-aa0d-d4f292c1b424', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://rs.gbif.org/datasets/protected/fauna_europaea-lepidoptera.zip', 'Fauna Europaea - Lepidoptera', null),
    ('d9a4eedb-e985-4456-ad46-3df8472e00e8', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://zenodo.org/record/1194673/files/dwca.zip', 'The Plant List with literature', null),
    ('da38f103-4410-43d1-b716-ea6b1b92bbac', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.saiab.ac.za/archive.do?r=catalogueofafrotropicalbees', 'Catalogue of Afrotropical Bees', null),
    ('de8934f4-a136-481c-a87a-b0b202b80a31', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://www.gbif.se/ipt/archive.do?r=test', 'Dyntaxa. Svensk taxonomisk databas', 'Dyntaxa'),
    ('ded724e7-3fde-49c5-bfa3-03b4045c4c5f', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'http://wp5.e-taxonomy.eu/download/data/dwca/cichorieae.zip', 'International Cichorieae Network (ICN): Cichorieae Portal', null),
    ('e01b0cbb-a10a-420c-b5f3-a3b20cc266ad', 'TAXONOMIC', 12, 12,'EXTERNAL', 'VIRUS', 'DWCA', 'http://rs.gbif.org/datasets/ictv.zip', 'ICTV Master Species List', 'ICTV'),
    ('e1c9e885-9d8c-45b5-9f7d-b710ac2b303b', 'TAXONOMIC', 12, 12,'EXTERNAL', null, 'DWCA', 'http://ipt.taibif.tw/archive.do?r=taibnet_endemic', 'Endemic species in Taiwan', null),
    ('e402255a-aed1-4701-9b96-14368e1b5d6b', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ctap.inhs.uiuc.edu/dmitriev/DwCArchive.zip', '3i - Typhlocybinae Database', null),
    ('e768b669-5f12-42b3-9bc7-ede76e4436fa', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://plazi.cs.umb.edu/GgServer/dwca/61134126326DC5BE0901E529D48F9481.zip', 'Carabodes cephalotes', null),
    ('f43069fe-38c1-43e3-8293-37583dcf5547', 'TAXONOMIC', 12, 12,'EXTERNAL', 'BOTANICAL', 'DWCA', 'https://svampe.databasen.org/dwc/DMS_Fun_taxa.zip', 'Danish Mycological Society - Checklist of Fungi', null),
    ('56c83fd9-533b-4b77-a67a-cf521816866e', 'TAXONOMIC', 12, 12,'EXTERNAL', 'ZOOLOGICAL', 'DWCA', 'http://ipt.pensoft.net/archive.do?r=tenebrionidae_north_america', 'Catalogue of Tenebrionidae (Coleoptera) of North America', null);

UPDATE dataset SET import_frequency = 7
WHERE key >= 2000;
