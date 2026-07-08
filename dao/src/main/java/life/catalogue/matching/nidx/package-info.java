/**
 * The names index: a single-tier, canonical-only lookup for taxonomic name matching.
 *
 * <p>Every entry is a <em>canonical</em> name — standard rank {@code UNRANKED} and no authorship — so
 * {@code names_index.canonical_id} always equals {@code id} and each entry is its own canonical.
 * Consequently every {@code name_match.index_id} points straight at a canonical entry and
 * {@link life.catalogue.api.model.IndexName#getCanonicalId()} returns the key itself. There are no
 * rank/author-specific child rows and no canonical&rarr;children grouping; {@code byCanonical()} returns
 * an empty collection.</p>
 *
 * <p>{@link life.catalogue.matching.nidx.NameIndex#match match} compares only the normalized canonical
 * name string of a query against the stored entry, producing {@link life.catalogue.api.vocab.MatchType}
 * {@code EXACT} (canonical strings identical), {@code VARIANT} (differ only by unicode/punctuation
 * normalization) or {@code NONE}. Authorship and rank are deliberately absent from the index.</p>
 *
 * <p>Because a whole canonical group shares one index id, homonyms that differ only by authorship are
 * <em>not</em> separated here — that separation is done downstream in
 * {@link life.catalogue.matching.UsageMatcher} by comparing the live usage authorship (via
 * {@code AuthorComparator}, with a lenient year comparison for year-only authorship) and the live ranks.</p>
 */
package life.catalogue.matching.nidx;
