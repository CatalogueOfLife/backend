-- Make all chromista belong to sector 13
UPDATE taxon set sector_key = 13 where id in(
WITH RECURSIVE tree AS(
SELECT t.id
FROM taxon_1000 t JOIN name_1000 n ON t.name_id=n.id LEFT JOIN verbatim_1000 vbt ON vbt.key=t.verbatim_key LEFT JOIN verbatim_1000 vbn ON vbn.key=n.verbatim_key LEFT JOIN vernacular_name_1000 vn ON vn.taxon_id=t.id LEFT JOIN decision ed ON ed.subject_id=t.id AND ed.dataset_key=1000 WHERE t.parent_id IS NULL AND rank='kingdom' AND scientific_name='Chromista'
UNION 
SELECT t.id
FROM taxon_1000 t JOIN name_1000 n ON t.name_id=n.id LEFT JOIN verbatim_1000 vbt ON vbt.key=t.verbatim_key LEFT JOIN verbatim_1000 vbn ON vbn.key=n.verbatim_key LEFT JOIN vernacular_name_1000 vn ON vn.taxon_id=t.id LEFT JOIN decision ed ON ed.subject_id=t.id AND ed.dataset_key=1000 JOIN tree ON (tree.id = t.parent_id)) SELECT * FROM tree)


UPDATE name set sector_key = 13 where id in(
WITH RECURSIVE tree AS(
SELECT n.id
FROM taxon_1000 t JOIN name_1000 n ON t.name_id=n.id LEFT JOIN verbatim_1000 vbt ON vbt.key=t.verbatim_key LEFT JOIN verbatim_1000 vbn ON vbn.key=n.verbatim_key LEFT JOIN vernacular_name_1000 vn ON vn.taxon_id=t.id LEFT JOIN decision ed ON ed.subject_id=t.id AND ed.dataset_key=1000 WHERE t.parent_id IS NULL AND rank='kingdom' AND scientific_name='Chromista'
UNION 
SELECT n.id
FROM taxon_1000 t JOIN name_1000 n ON t.name_id=n.id LEFT JOIN verbatim_1000 vbt ON vbt.key=t.verbatim_key LEFT JOIN verbatim_1000 vbn ON vbn.key=n.verbatim_key LEFT JOIN vernacular_name_1000 vn ON vn.taxon_id=t.id LEFT JOIN decision ed ON ed.subject_id=t.id AND ed.dataset_key=1000 JOIN tree ON (tree.id = t.parent_id)) SELECT * FROM tree)