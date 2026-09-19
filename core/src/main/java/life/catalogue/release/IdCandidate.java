package life.catalogue.release;

import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.matching.NameIdentity;
import life.catalogue.release.ReleasedIds.ReleasedId;

import jakarta.validation.constraints.NotNull;

/**
 * One possible pairing of a usage about to be released with an identifier some earlier release issued.
 *
 * The ordering is what decides which identifier a usage keeps, and it is strictly layered:
 *
 * <ol>
 *   <li>an id the last release still had, over one that has to be resurrected</li>
 *   <li>{@link NameIdentity.Verdict} - the evidence, then how many attributes corroborate it</li>
 *   <li>an id a base release has used, over one only ever issued in an extended release</li>
 *   <li>the id that appeared in more releases - the one more of the world has had the chance to cite</li>
 *   <li>the earliest release it appeared in</li>
 *   <li>the lowest id, then the name - identifiers are issued incrementally, so the smallest integer is the oldest.
 *       Both only ever settle a tie nothing above could, and exist so the outcome never depends on the order the
 *       usage store happens to return a canonical group in</li>
 * </ol>
 *
 * Keeping an id outranks the evidence because a pairing only reaches this comparison once nothing contradicts it:
 * IdProvider.assign drops every contradicted pairing, and an id the last release no longer had needs positive
 * agreement on authorship or rank before it becomes a candidate at all. Between two such candidates the one the last
 * release published is the one the world already cites, so a better corroborated resurrection must not take it away.
 * COL26.9 (attempt 629) is what the other layering costs: 34,796 names changed identifier, 31,610 of them in
 * continuous use since COL21, most of them losing to a resurrected id that merely matched an authorship their own
 * archived version did not carry.
 *
 * Longevity deliberately outranks recency among the resurrections themselves. An identifier that served twenty
 * releases and was dropped in the last one is cited far more widely than the one minted to replace it, so restoring
 * it costs less than keeping the replacement - see
 * <a href="https://github.com/CatalogueOfLife/backend/issues/1289">#1289</a>. Seniority as a real ranking term is
 * also what keeps an erroneous duplicate from taking over: when two usages of one name coexist for a while and one
 * is later removed, the decade old identifier survives and the one minted last month does not, as long as the data
 * does not actively say otherwise.
 */
class IdCandidate implements Comparable<IdCandidate> {
  final SimpleNameWithNidx name;
  final ReleasedId rid;
  final NameIdentity.Verdict verdict;

  IdCandidate(SimpleNameWithNidx name, ReleasedId rid, NameIdentity.Verdict verdict) {
    this.name = name;
    this.rid = rid;
    this.verdict = verdict;
  }

  /**
   * Best candidate first. Written out rather than chained through java.util.Comparator so the layering stays readable:
   * every step below the verdict only ever breaks a tie the steps above it left.
   */
  @Override
  public int compareTo(@NotNull IdCandidate o) {
    int c = Boolean.compare(!rid.isCurrent, !o.rid.isCurrent); // still in the last release first
    if (c != 0) return c;
    c = o.verdict.compareTo(verdict); // best evidence first
    if (c != 0) return c;
    c = Boolean.compare(rid.xrOnly, o.rid.xrOnly); // used by a base release first
    if (c != 0) return c;
    c = Integer.compare(o.rid.releaseCount, rid.releaseCount); // seen in more releases first
    if (c != 0) return c;
    c = Integer.compare(rid.attempt, o.rid.attempt); // oldest release first
    if (c != 0) return c;
    c = Integer.compare(rid.id, o.rid.id); // lowest id is the oldest id
    if (c != 0) return c;
    return name.compareTo(o.name); // never let the store's iteration order decide
  }

  @Override
  public String toString() {
    return rid.id() + " " + verdict + " -> " + name.getLabel();
  }
}
