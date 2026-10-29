package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ScientificName;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Objects;

import javax.annotation.Nullable;

import static life.catalogue.api.vocab.TaxonomicStatus.MISAPPLIED;

/**
 * Decides whether two versions of a name usage - typically one from a previous release and one from the project
 * about to be released - are the same name, and how strongly the data says so.
 *
 * Every attribute is compared three valued ({@link Equality}), because "we cannot tell" and "these differ" are very
 * different answers when an identifier is at stake. Adding or removing an authorship, or an unranked name gaining a
 * rank, is missing information and must not cost a name its identifier - see
 * <a href="https://github.com/CatalogueOfLife/backend/issues/1326">#1326</a>. A genuinely changed authorship
 * (<code>Mill.</code> to <code>DC.</code>) on the other hand is a different name and must not keep the old one.
 * Disparate taxonomic groups or nomenclatural codes separate two names only unless their authorship positively agrees:
 * both follow the placement and the source metadata, which change for one and the same usage from release to release.
 *
 * Authorship therefore goes through {@link AuthorComparator}, which knows that <code>Mill.</code> and
 * <code>Miller</code>, or <code>L.</code> and <code>Linné</code>, are the same person, and ranks through
 * {@link RankComparator}, which knows that UNRANKED tells us nothing. The scientific name itself is not compared here
 * at all: the caller has already grouped both sides into the same canonical names index bucket, which is what "the
 * same name string" means in this codebase.
 */
public class NameIdentity {

  /**
   * How much the data says two usages are the same name, worst to best. The enum order is the ranking order.
   */
  public enum Evidence {
    /** at least one attribute says these are different names. Never reuse an identifier across this. */
    CONTRADICTED,
    /** nothing contradicts, but nothing corroborates either - e.g. both sides unranked and unauthored. */
    WEAK,
    /** nothing contradicts and either the authorship or the rank is positively equal. */
    PLAUSIBLE,
    /** nothing contradicts and both the authorship and the rank are positively equal. */
    CONFIRMED
  }

  /**
   * The outcome for one pair, ordered worst to best. Corroboration counts how many attributes positively agree and
   * only ever separates two candidates that already share the same {@link Evidence}.
   */
  public static class Verdict implements Comparable<Verdict> {
    public final Evidence evidence;
    public final int corroboration;

    Verdict(Evidence evidence, int corroboration) {
      this.evidence = evidence;
      this.corroboration = corroboration;
    }

    public boolean isContradicted() {
      return evidence == Evidence.CONTRADICTED;
    }

    @Override
    public int compareTo(Verdict o) {
      int c = evidence.compareTo(o.evidence);
      return c != 0 ? c : Integer.compare(corroboration, o.corroboration);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Verdict)) return false;
      Verdict v = (Verdict) o;
      return corroboration == v.corroboration && evidence == v.evidence;
    }

    @Override
    public int hashCode() {
      return Objects.hash(evidence, corroboration);
    }

    @Override
    public String toString() {
      return evidence + "/" + corroboration;
    }
  }

  private static final Verdict CONTRADICTED = new Verdict(Evidence.CONTRADICTED, 0);

  /**
   * One side of a comparison. Build one per usage and reuse it for every candidate it is compared against:
   * the authorship is parsed once and cached here.
   */
  public static class Facts {
    final Rank rank;
    final String authorship;
    final String phrase;
    final TaxonomicStatus status;
    final NomCode code;
    final TaxGroup group;
    /** scientific name of the accepted name for synonyms, never an identifier. Null if unknown. */
    final String parentName;
    private ScientificName sciName;

    public Facts(Rank rank, String authorship, String phrase, TaxonomicStatus status,
                 @Nullable NomCode code, @Nullable TaxGroup group, @Nullable String parentName) {
      this.rank = rank;
      this.authorship = authorship;
      this.phrase = phrase;
      this.status = status;
      this.code = code;
      this.group = group;
      this.parentName = parentName;
    }

    /**
     * @param parentName scientific name of the accepted name for synonyms - the simple names parent is a usage id in
     *                   most stores, so it cannot be used here
     */
    public Facts(SimpleName sn, @Nullable String parentName) {
      this(sn.getRank(), sn.getAuthorship(), sn.getPhrase(), sn.getStatus(), sn.getCode(), sn.getGroup(), parentName);
    }

    boolean hasAuthorship() {
      return !isBlank(authorship);
    }

    /**
     * The parsed authorship, computed on first use. Parsing is not free and most usages are compared against several
     * candidates, so it is worth keeping.
     */
    ScientificName sciName() {
      if (sciName == null) {
        Name n = new Name();
        n.setCode(code);
        NameParser.PARSER.parseAuthorship(authorship, code).ifPresent(a -> {
          n.setCombinationAuthorship(a.getCombinationAuthorship());
          n.setBasionymAuthorship(a.getBasionymAuthorship());
        });
        sciName = n;
      }
      return sciName;
    }
  }

  private final AuthorComparator authComp;

  public NameIdentity() {
    this(new AuthorComparator(AuthorshipNormalizer.INSTANCE));
  }

  public NameIdentity(AuthorComparator authComp) {
    this.authComp = authComp;
  }

  /**
   * @return how strongly the data says a and b are the same name. Never null.
   */
  public Verdict compare(Facts a, Facts b) {
    // a misapplied name is never the same name as anything else - it is a statement about a misuse, not about a taxon
    final boolean misA = a.status == MISAPPLIED;
    if (misA != (b.status == MISAPPLIED)) {
      return CONTRADICTED;
    }
    // the name phrase (sensu ...) is what tells two misapplied names apart
    final Equality phrase = compareText(a.phrase, b.phrase);
    if (misA && phrase == Equality.DIFFERENT) {
      return CONTRADICTED;
    }
    final Equality rank = RankComparator.compare(a.rank, b.rank);
    if (rank == Equality.DIFFERENT) {
      return CONTRADICTED;
    }
    final Equality authorship = compareAuthorship(a, b);
    if (authorship == Equality.DIFFERENT) {
      return CONTRADICTED;
    }
    // a name cannot move between disparate parts of the tree of life, and two different nomenclatural codes are two
    // different names - the classic Oenanthe bird versus plant homonym. But both are properties of the placement and of
    // the source metadata rather than of the name, and both change for one and the same usage: a species attached to a
    // homonym genus in another kingdom, a fungus published with the zoological code and fixed in the next import.
    // A positively agreeing authorship overrules them - true cross code homonyms have different authors - and only
    // leaves the pairing plausible, never confirmed.
    final boolean placementConflict = (a.group != null && b.group != null && a.group.isDisparateTo(b.group))
                                      || (a.code != null && b.code != null && a.code != b.code);
    if (placementConflict && authorship != Equality.EQUAL) {
      return CONTRADICTED;
    }

    int corroboration = 0;
    if (authorship == Equality.EQUAL) corroboration++;
    if (rank == Equality.EQUAL) corroboration++;
    if (phrase == Equality.EQUAL) corroboration++;
    if (statusClass(a.status) == statusClass(b.status)) corroboration++;
    if (a.group != null && a.group == b.group) corroboration++;
    if (a.code != null && a.code == b.code) corroboration++;
    // the accepted name keeps pro parte synonyms of one and the same name apart, and only those:
    // an accepted names parent changes with every reclassification and would be noise here
    if (isSynonym(a.status) && isSynonym(b.status) && compareText(a.parentName, b.parentName) == Equality.EQUAL) {
      corroboration++;
    }

    final Evidence evidence;
    if (authorship == Equality.EQUAL && rank == Equality.EQUAL && !placementConflict) {
      evidence = Evidence.CONFIRMED;
    } else if (authorship == Equality.EQUAL || rank == Equality.EQUAL) {
      evidence = Evidence.PLAUSIBLE;
    } else {
      evidence = Evidence.WEAK;
    }
    return new Verdict(evidence, corroboration);
  }

  /**
   * @return EQUAL or DIFFERENT only when both sides are authored. An authorship that was added or removed is missing
   *   information, not a difference, and must never block an identifier from being kept.
   */
  private Equality compareAuthorship(Facts a, Facts b) {
    if (!a.hasAuthorship() || !b.hasAuthorship()) {
      return Equality.UNKNOWN;
    }
    return authComp.compare(a.sciName(), b.sciName());
  }

  /**
   * Compares two free text values ignoring case, whitespace and anything that is neither a digit nor an ascii letter,
   * the same folding the release id mapping has always used. Returns UNKNOWN unless both sides have content.
   */
  private static Equality compareText(@Nullable String a, @Nullable String b) {
    if (isBlank(a) || isBlank(b)) {
      return Equality.UNKNOWN;
    }
    return StringUtils.equalsDigitOrAsciiLettersIgnoreCase(a, b) ? Equality.EQUAL : Equality.DIFFERENT;
  }

  private static boolean isBlank(@Nullable String x) {
    return x == null || x.trim().isEmpty();
  }

  private static boolean isSynonym(@Nullable TaxonomicStatus status) {
    return status != null && status.isSynonym();
  }

  /**
   * Accepted and provisionally accepted are the same kind of thing, and so are the synonym flavours - a taxon that is
   * sunk into synonymy keeps its identifier. Only a misapplied name is a class of its own, and that is already a
   * contradiction above.
   */
  private static TaxonomicStatus statusClass(@Nullable TaxonomicStatus status) {
    if (status == null) return null;
    if (status == MISAPPLIED) return MISAPPLIED;
    return status.isSynonym() ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED;
  }

}
