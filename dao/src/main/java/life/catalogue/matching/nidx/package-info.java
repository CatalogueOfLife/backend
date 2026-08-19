/**
 * The names index: a {@code normalized-String -> nidx-int} registry for taxonomic name matching, not a
 * cached copy of the names themselves.
 *
 * <p>Postgres {@code names_index(id, scientific_name, normalized UNIQUE)} is the single append-only
 * source of truth. Every row is a <em>canonical</em> name — standard rank {@code UNRANKED} and no
 * authorship — and is immutable once written: a new or changed canonical name always gets a new
 * {@code id} rather than being updated in place. There is no {@code canonical_id} column: an entry's
 * canonical key is simply its own {@link life.catalogue.api.model.NameIndexEntry#getKey() key}, so every
 * {@code name_match.index_id} points straight at a canonical entry. There are no rank/author-specific
 * child rows and no canonical&rarr;children grouping; {@code byCanonical()} returns an empty
 * collection.</p>
 *
 * <p>The in-memory {@link life.catalogue.matching.nidx.NameIndexStore} is only the reverse lookup for
 * that registry: a normalized canonical bucket key mapped to its {@code nidx} id — a persistent
 * Chronicle map ({@link life.catalogue.matching.nidx.NameIndexChronicleStore}) in production, a heap
 * map ({@link life.catalogue.matching.nidx.NameIndexMapStore}) in tests. It holds no full row instances;
 * full {@link life.catalogue.api.model.NameIndexEntry} rows are looked up on demand from postgres via
 * {@link life.catalogue.matching.nidx.NameIndex#get get}. On startup the store is not fully reloaded —
 * it catches up incrementally from its own {@code maxKey()} to postgres's current max id.</p>
 *
 * <p>{@link life.catalogue.matching.nidx.NameIndex#match match} compares only the normalized canonical
 * name string of a query against the stored entry and returns a bare
 * {@link life.catalogue.api.model.NameMatch NameMatch(Integer nidx, boolean matched)} — there is no
 * {@code IndexName} model, no MapDB store, no KryoPool registration, and no names-index-level
 * {@code MatchType} anymore (all removed). Authorship and rank are deliberately absent from the index.</p>
 *
 * <p>EXACT/VARIANT classification is no longer computed by this package at all — it is computed at the
 * USAGE layer by {@link life.catalogue.matching.UsageMatcher} from the live usage labels, once a nidx
 * match has narrowed candidates. Homonym separation (telling apart usages that share one canonical nidx
 * but differ by author) also lives in {@code UsageMatcher}, unchanged: it compares the live usage
 * authorship (via {@code AuthorComparator}, with a lenient year comparison for year-only authorship) and
 * the live ranks.</p>
 */
package life.catalogue.matching.nidx;
