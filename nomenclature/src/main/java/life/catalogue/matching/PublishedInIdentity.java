package life.catalogue.matching;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Reference;
import life.catalogue.common.text.StringUtils;

import org.gbif.nameparser.util.UnicodeUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Decides whether the publishedIn references of two versions of a name - one already in a project and one from a
 * merge sector - cite the same publication, so the merge can add the links of the source to the existing name
 * without replacing its reference, see <a href="https://github.com/CatalogueOfLife/backend/issues/1606">#1606</a>.
 *
 * Citations of the same work look very different across sources, e.g. <code>Repert. Spec. Nov. Regni Veg. 10: 6
 * (1911)</code> against a BHL volume <code>Repertorium specierum novarum regni vegetabilis. (1911). v.10</code>.
 * Like {@link NameIdentity} every attribute is therefore compared three valued ({@link Equality}): a contradiction on
 * the DOI, the year or the page rules a pair out, missing information never does, and only positive agreement allows
 * anything to be merged. Contradictions are common: BHLnames links a name to an index volume or another page quite
 * often, a third of all pairs in COL.
 *
 * The verdict distinguishes what may be merged, as a reference is shared by every name citing it:
 * <ul>
 *   <li>{@link Verdict#samePage()} or {@link Verdict#sameWork()}: the page and page link of the name itself may be added.</li>
 *   <li>{@link Verdict#doi()}: the DOI of an article may be added to the existing reference, as both cite the same work.
 *   A title only counts for articles, as the title of a volume is the journal, which every citation of an article in
 *   it also mentions.</li>
 *   <li>{@link Verdict#stub()}: the existing reference says nothing beyond the authors and year of the name, e.g.
 *   <code>Benth. (1842).</code>, and the name may cite the source reference instead. Such a stub is shared by all names
 *   of that author and year, so it must never be enriched itself.</li>
 * </ul>
 */
public class PublishedInIdentity {
  private static final Pattern YEAR = Pattern.compile("(?<!\\d)(1[5-9]\\d\\d|20\\d\\d)(?!\\d)");
  private static final Pattern YEAR_TOKEN = Pattern.compile("^(1[5-9]\\d\\d|20\\d\\d)[a-z]?$");
  // volumes of journals often span several years: v.10 (1911-1912), v. 9 (1907-16)
  private static final Pattern YEAR_RANGE = Pattern.compile("(?<!\\d)(1[5-9]\\d\\d|20\\d\\d)\\s*[-–]\\s*(\\d{2,4})(?!\\d)");
  // a page or page range given on a name: 146, p. 138., 129-131, figs. 6
  private static final Pattern PAGE = Pattern.compile("^\\s*(?:(?:pp?|pag|page)\\.?\\s*)?(\\d+)(?:\\s*[-–]+\\s*(\\d+))?");
  // a volume and issue preceding the page as found in the page of many references: 63(2), 207-213 or 17: 64-66.
  private static final Pattern VOLUME_PREFIX = Pattern.compile("^\\s*\\d+\\s*(?:\\([^)]*\\))?\\s*[:,]\\s*");
  // the page following the volume in a citation: 10: 6 (1911), 31(8): 418., 61(1):60-67, Orch. Java: 279
  // but never a DOI like doi:10.1234/5
  private static final Pattern CITATION_PAGE = Pattern.compile(":\\s*(?:pp?\\.?\\s*)?(\\d+)(?:\\s*[-–]+\\s*(\\d+))?(?!\\d|\\.\\d|/)");
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
  private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "from", "with",
    "der", "die", "das", "und", "von", "dem", "den", "des", "les", "del", "los", "las", "sur", "zur"
  );
  private static final Set<String> AUTHOR_FILLERS = Set.of("ex", "et", "al", "and", "in");
  private static final int MIN_TITLE_TOKENS = 4;
  private static final double MIN_TITLE_MATCH = 0.9;

  /**
   * @param contradicted true if the DOI, year or page differ. All other flags are false then.
   * @param reason why the pair was contradicted, for logging only
   * @param samePage the page of the source name is the one, or within the range, given by the existing name or citation
   * @param sameWork both references cite the same work: the same DOI, or the same article title and year
   * @param stub the existing reference is a mere author &amp; year stub the source reference can replace for this name
   * @param doi the DOI of the source article to add to the existing reference, null if there is nothing to add
   */
  public record Verdict(boolean contradicted, @Nullable String reason, boolean samePage, boolean sameWork, boolean stub,
                        @Nullable DOI doi) {

    static Verdict contradicted(String reason) {
      return new Verdict(true, reason, false, false, false, null);
    }
  }

  private record Pages(int from, int to) {
    boolean contains(int page) {
      return from <= page && page <= to;
    }
  }

  public static Verdict compare(Name existing, Reference existingRef, Name source, Reference sourceRef) {
    final DOI doi1 = doi(existingRef);
    final DOI doi2 = doi(sourceRef);
    final Equality doi = doi1 == null || doi2 == null ? Equality.UNKNOWN : doi1.equals(doi2) ? Equality.EQUAL : Equality.DIFFERENT;
    if (doi == Equality.DIFFERENT) {
      return Verdict.contradicted("DOI " + doi1 + " differs from " + doi2);
    }

    final Set<Integer> years1 = years(existing, existingRef);
    final Set<Integer> years2 = years(source, sourceRef);
    final Equality year = compareYears(years1, years2);
    if (year == Equality.DIFFERENT) {
      return Verdict.contradicted("year " + years1 + " differs from " + years2);
    }

    final Pages pages1 = pages(existing, existingRef);
    final Pages pages2 = pages(source.getPublishedInPage());
    final Equality page = pages1 == null || pages2 == null ? Equality.UNKNOWN :
      pages1.contains(pages2.from) ? Equality.EQUAL : Equality.DIFFERENT;
    if (page == Equality.DIFFERENT) {
      return Verdict.contradicted("page " + pages2.from + " not in " + pages1);
    }

    final boolean article = isArticle(sourceRef);
    final boolean sameWork = doi == Equality.EQUAL || article && year == Equality.EQUAL && sameTitle(existingRef, sourceRef);
    final boolean stub = year == Equality.EQUAL && isStub(existing, existingRef) && !isStub(source, sourceRef);
    final DOI newDoi = sameWork && article && !stub && doi1 == null ? doi2 : null;
    return new Verdict(false, null, page == Equality.EQUAL, sameWork, stub, newDoi);
  }

  @Nullable
  private static DOI doi(Reference ref) {
    CslData csl = ref.getCsl();
    if (csl == null) return null;
    return DOI.parse(csl.getDOI())
      .or(() -> DOI.parse(csl.getURL()))
      .orElse(null);
  }

  /**
   * All years a reference could have been published in: its year and the years its volume names, or any year found in
   * the citation if there are none, plus all years of ranges given for multi year volumes. The combination year of the name is only used if the
   * reference offers nothing - never the basionym year, which is not when this name was published.
   */
  private static Set<Integer> years(Name n, Reference ref) {
    Set<Integer> years = new HashSet<>();
    if (ref.getYear() != null) {
      years.add(ref.getYear());
    }
    CslData csl = ref.getCsl();
    if (csl != null && csl.getIssued() != null) {
      int[][] parts = csl.getIssued().getDateParts();
      if (parts != null && parts.length > 0 && parts[0] != null && parts[0].length > 0 && parts[0][0] > 0) {
        years.add(parts[0][0]);
      }
    }
    // the volume names its own year, which is not always the year of the reference, e.g. 8 (1864) for a serial starting 1857
    final String volume = csl == null ? null : csl.getVolume();
    addYears(years, volume);
    if (years.isEmpty()) {
      addYears(years, ref.getCitation());
    }
    final String text = StringUtils.concatWS(volume, ref.getCitation());
    if (text != null) {
      Matcher m = YEAR_RANGE.matcher(text);
      while (m.find()) {
        int start = Integer.parseInt(m.group(1));
        String endStr = m.group(2);
        int end = endStr.length() == 4 ? Integer.parseInt(endStr) : Integer.parseInt(m.group(1).substring(0, 4 - endStr.length()) + endStr);
        if (end > start && end - start <= 20) {
          for (int y = start; y <= end; y++) {
            years.add(y);
          }
        }
      }
    }
    if (years.isEmpty() && n.getCombinationAuthorship() != null && n.getCombinationAuthorship().getYear() != null) {
      Matcher m = YEAR.matcher(n.getCombinationAuthorship().getYear());
      if (m.find()) {
        years.add(Integer.parseInt(m.group(1)));
      }
    }
    return years;
  }

  private static void addYears(Set<Integer> years, @Nullable String text) {
    if (text != null) {
      Matcher m = YEAR.matcher(text);
      while (m.find()) {
        years.add(Integer.parseInt(m.group(1)));
      }
    }
  }

  /**
   * Years a year apart are tolerated but no evidence either, as volumes and their parts often straddle a year end.
   */
  private static Equality compareYears(Set<Integer> years1, Set<Integer> years2) {
    if (years1.isEmpty() || years2.isEmpty()) {
      return Equality.UNKNOWN;
    }
    int minDist = Integer.MAX_VALUE;
    for (int y1 : years1) {
      for (int y2 : years2) {
        minDist = Math.min(minDist, Math.abs(y1 - y2));
      }
    }
    return minDist == 0 ? Equality.EQUAL : minDist == 1 ? Equality.UNKNOWN : Equality.DIFFERENT;
  }

  /**
   * The page given on the name, else on the reference, else the one following the volume in the citation.
   */
  @Nullable
  private static Pages pages(Name n, Reference ref) {
    Pages p = pages(n.getPublishedInPage());
    if (p == null && ref.getCsl() != null && ref.getCsl().getPage() != null) {
      p = pages(VOLUME_PREFIX.matcher(ref.getCsl().getPage()).replaceFirst(""));
    }
    if (p == null && ref.getCitation() != null) {
      Matcher m = CITATION_PAGE.matcher(ref.getCitation());
      if (m.find()) {
        p = pages(m);
      }
    }
    return p;
  }

  @Nullable
  private static Pages pages(@Nullable String page) {
    if (page != null) {
      Matcher m = PAGE.matcher(page);
      if (m.find()) {
        return pages(m);
      }
    }
    return null;
  }

  /**
   * Reads a page or page range, expanding abbreviated ranges like 207-13.
   * A range ending before it starts, e.g. 48-1, tells nothing and yields null.
   */
  @Nullable
  private static Pages pages(Matcher m) {
    try {
      String fromStr = m.group(1);
      int from = Integer.parseInt(fromStr);
      if (m.group(2) == null) {
        return new Pages(from, from);
      }
      String toStr = m.group(2);
      int to = Integer.parseInt(toStr);
      if (to < from && toStr.length() < fromStr.length()) {
        to = Integer.parseInt(fromStr.substring(0, fromStr.length() - toStr.length()) + toStr);
      }
      return to < from ? null : new Pages(from, to);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * @return true for a reference with a title within some container, i.e. an article and not a volume or book
   */
  private static boolean isArticle(Reference ref) {
    CslData csl = ref.getCsl();
    return csl != null && !isBlank(csl.getTitle()) && !isBlank(csl.getContainerTitle());
  }

  /**
   * @return true if nearly all words of the source title can be found in the existing title or citation
   */
  private static boolean sameTitle(Reference existing, Reference source) {
    List<String> title = words(source.getCsl().getTitle()).stream()
      .filter(w -> w.length() > 2 && !STOPWORDS.contains(w))
      .toList();
    if (title.size() < MIN_TITLE_TOKENS) {
      return false;
    }
    Set<String> other = new HashSet<>(words(StringUtils.concatWS(
      existing.getCsl() == null ? null : existing.getCsl().getTitle(),
      existing.getCitation()
    )));
    long found = title.stream().filter(other::contains).count();
    return found >= MIN_TITLE_MATCH * title.size();
  }

  /**
   * @return true if the reference holds nothing but the authors and the year of the name, e.g. <code>Tindale, &amp; Maslin. (1976).</code>
   */
  private static boolean isStub(Name n, Reference ref) {
    if (isBlank(ref.getCitation())) {
      return false;
    }
    CslData csl = ref.getCsl();
    Set<String> authors = new HashSet<>(AUTHOR_FILLERS);
    if (csl != null) {
      if (StringUtils.hasContent(csl.getTitle(), csl.getContainerTitle(), csl.getVolume(), csl.getPage(), csl.getDOI(), csl.getURL())) {
        return false;
      }
      if (csl.getAuthor() != null) {
        for (CslName a : csl.getAuthor()) {
          authors.addAll(words(StringUtils.concatWS(a.getGiven(), a.getFamily(), a.getLiteral())));
        }
      }
    }
    authors.addAll(words(n.getAuthorship()));
    for (String w : words(ref.getCitation())) {
      // initials and years are fine
      if (w.length() > 2 && !YEAR_TOKEN.matcher(w).matches() && !authors.contains(w)) {
        return false;
      }
    }
    return true;
  }

  private static List<String> words(@Nullable String x) {
    if (isBlank(x)) {
      return List.of();
    }
    return Arrays.stream(NON_ALNUM.split(UnicodeUtils.foldToAscii(x).toLowerCase()))
      .filter(w -> !w.isEmpty())
      .toList();
  }

  private static boolean isBlank(@Nullable String x) {
    return x == null || x.isBlank();
  }
}
