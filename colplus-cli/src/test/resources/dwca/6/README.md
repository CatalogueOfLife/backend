Tests the Index Fungorum format using a denormed classification.

All records of the genus Zignoëlla have been included in the test resources,
as only when all are present the original issue of missing higher taxa shows up.
The genus field is left out in the meta.xml, as it causes confusion when the genus is regarded as a synonyms,
but there are species within that genus still being accepted. An oddity of the nomenclatoral index fungorum
database.
Discovered with this test.

Test a synonym which should use the classification of the accepted name, not the (wrong) denormed of the synonym:

source record:
```
id;scientificName;authorship;rank;basionymID;acceptedTaxonID;nomenclaturalStatus;remarks;namePublishedIn;genus;family;order;class;phylum;kingdom
140283	Polystictus substipitatus	(Murrill) Sacc. & Trotter	sp.	208383	324805	\N		Syll. fung. (Abellini) 21: 318 (1912)	Polystictus	Hymenochaetaceae	Hymenochaetales	Incertae sedis	Agaricomycetes	Agaricomycotina	Basidiomycota	Fungi
```

Expected:
```
140283: Polystictus substipitatus (Murrill) Sacc. & Trotter
status: synonym
basionym: Coriolus substipitatus Murrill
accepted: Trametes modesta (Kunze ex Fr.) Ryvarden
parents: Trametes; Polyporaceae; Polyporales; Agaricomycetes; Basidiomycota; Fungi
```

