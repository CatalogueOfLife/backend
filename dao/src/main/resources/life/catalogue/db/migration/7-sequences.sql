
-- GLOBAL SEQUENCES
SELECT setval('public.dataset_key_seq', (select max(key)+1 from dataset));
SELECT setval('public.names_index_id_seq', (SELECT COALESCE(max(id),1) FROM names_index));
SELECT setval('public.user_key_seq', (SELECT COALESCE(max(key),1) FROM "user"));
