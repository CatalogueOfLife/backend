
### FIX DATA TO ALLOW BROKEN FKs CONSTRAINTS !!!

## name usage
create table _miss_name_usage AS select u.dataset_key,u.id from name_usage u where according_to_id is not null AND not exists (
  select true from reference r where r.dataset_key=u.dataset_key and r.id=u.according_to_id
);

update name_usage u SET according_to_id=null FROM _miss_name_usage x WHERE u.dataset_key=54592 AND u.id=x.id;

ALTER TABLE public.name_usage
    ADD CONSTRAINT name_usage_dataset_key_according_to_id_fkey FOREIGN KEY (dataset_key, according_to_id) REFERENCES public.reference(dataset_key, id);

DROP table _miss_name_usage;


## name_match
create table _miss_name_match AS select m.dataset_key,m.name_id from name_match m where not exists (
  select true from name n where n.dataset_key=m.dataset_key and n.id=m.name_id
);

delete from name_match m using _miss_name_match x where m.dataset_key=x.dataset_key and m.name_id=x.name_id;

ALTER TABLE public.name_match
    ADD CONSTRAINT name_match_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id);

DROP table _miss_name_match;


## verbatim_source
create table _miss_verbatim_source AS select v.dataset_key,v.id from verbatim_source v where not exists (
  select true from name_usage u where u.dataset_key=v.dataset_key and u.id=v.id
);

delete from verbatim_source where dataset_key in (3,9910,9916,9923) and id='db7b0d1d-99a6-4b49-8b7e-b330b4f8ca5d';

ALTER TABLE public.verbatim_source
    ADD CONSTRAINT verbatim_source_dataset_key_id_fkey FOREIGN KEY (dataset_key, id) REFERENCES public.name_usage(dataset_key, id);

DROP table _miss_verbatim_source;


## name_rel
create table _miss_name_rel AS 
 select r.dataset_key,r.id, n1.id as name_id, n2.id as related_name_id 
 from name_rel r
 left join name n1 on n1.dataset_key=r.dataset_key and n1.id=r.name_id
 left join name n2 on n2.dataset_key=r.dataset_key and n2.id=r.related_name_id
 where n1.id is null OR n2.id is null;

delete from name_rel where dataset_key=9921 and id in (56631,56639,55261,55135,56563,56403,56723,55056,56562,55190,56478,56748,55578,57426,56477,56331,56870,56630,56638,56722,57462);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_name_id_fkey FOREIGN KEY (dataset_key, name_id) REFERENCES public.name(dataset_key, id);

ALTER TABLE public.name_rel
    ADD CONSTRAINT name_rel_dataset_key_related_name_id_fkey FOREIGN KEY (dataset_key, related_name_id) REFERENCES public.name(dataset_key, id);

DROP table _miss_name_rel;
