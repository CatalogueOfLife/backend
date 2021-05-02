package life.catalogue.importer.reference;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.ReferenceStore;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.UnparsableException;
import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.common.text.StringUtils.hasContent;

/**
 * Dataset specific factory for reference instances. It mostly manages the CSL parsing and works
 * with variously structured input forms. Responsible for detecting and flagging of issues in
 * reference.issues.
 * <p>
 * In the future we can expect dataset specific configuration hints to be added.
 */
public class ReferenceFactory {
  
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceFactory.class);

  private static final Pattern YEAR_PATTERN = Pattern.compile("(^|\\D+)([12]\\d{3})($|\\D+)");
  private static final CharSet PUNCTUATIONS = CharSet.getInstance(".?!;:,");
  private static final Pattern NORM_WHITESPACE = Pattern.compile("\\s{2,}");
  private static final String name     = "[\\p{L} -]+";
  private static final String initials = "(?:\\p{Lu}(?:\\. ?| ))*";
  // comma separated authors with (initials/firstname) surnames
  private static final Splitter AUTHOR_SPLITTER = Splitter.on(CharMatcher.anyOf(",&")).trimResults();
  private static final Pattern AUTHOR_PATTERN = Pattern.compile("^("+initials+") *("+ name +")$");
  // comma separated authors with surname, initials
  private static final Pattern AUTHOR_PATTERN_SN_FN = Pattern.compile("^(" + name + "), *(" + initials + ")$");
  // semicolon separated authors with lastname, firstnames
  private static final Splitter AUTHOR_SPLITTER_SEMI = Splitter.on(';').trimResults();
  private static final Pattern AUTHORS_PATTERN_SEMI = Pattern.compile("^("+ name +")(?:, ?("+initials+"|"+name+"))?$");
  // author parsing
  private static final Pattern AUTHOR_PREFIX = Pattern.compile("^((?:\\p{Ll}{1,5} ){1,2})(\\p{Lu})");
  // author year citation (e.g. sensu Miller, 1973)
  private static final Pattern AUTHOR_YEAR = Pattern.compile("^([\\p{L} -.,]+)\\s*,?\\s*([12]\\d{3})?\\s*$");

  private final Integer datasetKey;
  private final ReferenceStore store;
  
  public ReferenceFactory(NeoDb db) {
    this(db.getDatasetKey(), db.references());
  }

  public ReferenceFactory(Integer datasetKey, ReferenceStore store) {
    this.datasetKey = datasetKey;
    this.store = store;
  }
  
  /**
   * Tries to find an existing reference by its id or exact citation. Returns null if not found
   */
  public Reference find(String id, String citation) {
    Reference r = null;
    if (!StringUtils.isBlank(id)) {
      r = store.get(id);
    }
    if (r == null && !StringUtils.isBlank(citation)) {
      r = store.refByCitation(citation);
    }
    return r;
  }
  
  /**
   * Builds a reference instance from a ACEF reference record.
   * First tries to lookup an existing reference by its id.
   * Does not persist the instance to the ref store.
   *
   * Example: Ross, J.H. | 1979 | A conspectus of African Acacia | Mem. Bot. Surv. S. Afr. 44: 1-150
   * | TaxAccRef
   *
   * @param referenceID
   * @param authors     author (or many) of publication
   * @param year        of publication
   * @param title       of paper or book
   * @param details     title of periodicals, volume number, and other common bibliographic details
   * @return
   */
  public static Reference fromACEF(int datasetKey, String referenceID, String authors, String year, String title, String details, IssueContainer issues) {
    Reference ref = fromAny(datasetKey, referenceID, null, authors, year, title, details, null, null, issues);
    if (ref.getCsl() != null && !StringUtils.isBlank(details)) {
      issues.addIssue(Issue.CITATION_CONTAINER_TITLE_UNPARSED);
    }
    return ref;
  }
  
  public Reference fromACEF(String referenceID, String authors, String year, String title, String details, IssueContainer issues) {
    return fromACEF(datasetKey, referenceID, authors, year, title, details, issues);
  }
  
  public static Reference fromColDP(int datasetKey, String id, String citation, String authors, String year, String title, String source, String details,
                             String doi, String link, String remarks, IssueContainer issues) {
    Reference ref = fromAny(datasetKey, id, citation, authors, year, title, source, details, remarks, issues);
    // add extra link & doi
    if (doi != null || link != null) {
      if (ref.getCsl() == null) {
        ref.setCsl(new CslData());
      }
      //TODO: clean & verify DOI & link
      ref.getCsl().setDOI(doi);
      ref.getCsl().setURL(link);
    }
    return ref;
  }
  
  public Reference fromColDP(String id, String citation, String authors, String year, String title, String source, String details,
                             String doi, String link, String remarks, IssueContainer issues) {
    return fromColDP(datasetKey, id, citation, authors, year, title, source, details, doi, link, remarks, issues);
  }
  
  private static Reference fromAny(int datasetKey, String ID, String citation, String authors, String year, String title, String source,
                                   String details, String remarks, IssueContainer issues) {
    Reference ref = newReference(datasetKey, ID);
    ref.setYear(parseYear(year));
    ref.setRemarks(remarks);
    if (hasContent(authors, year, title, source)) {
      CslData csl = new CslData();
      ref.setCsl(csl);
      csl.setAuthor(parseAuthors(authors, issues));
      csl.setTitle(title);
      csl.setIssued(yearToDate(ref.getYear(), year));
      csl.setContainerTitle(source);
      if (!StringUtils.isBlank(details)) {
        // details can include any of the following and probably more: volume, edition, series, page, publisher
        // try to parse out volume and pages ???
        csl.setPage(details);
        issues.addIssue(Issue.CITATION_DETAILS_UNPARSED);
      }
      ref.setCitation(buildCitation(authors, year, title, source, details));
    } else {
      ref.setCitation(citation);
    }
    return ref;
  }
  
  public static Reference fromCsl(int datasetKey, CslData csl) {
    Reference ref = newReference(datasetKey, csl.getId());
    ref.setCsl(csl);
    // generate default APA citation string
    ref.setCitation(CslUtil.buildCitation(csl));
    updateIntYearFromCsl(ref);
    return ref;
  }
  
  private static void updateIntYearFromCsl(Reference ref) {
    if (ref.getCsl().getIssued() != null) {
      CslDate issued = ref.getCsl().getIssued();
      if (issued.getDateParts() != null) {
        ref.setYear(ref.getCsl().getIssued().getDateParts()[0][0]);
      } else if (issued.getRaw() != null || issued.getLiteral() != null) {
        Integer year = parseYear(ObjectUtils.coalesce(issued.getRaw(), issued.getLiteral()));
        ref.setYear(year);
      }
    }
  }
  
    /**
     * Very similar to fromACEF but optionally providing a full citation given as
     * bibliographicCitation
     *
     * @param identifier
     * @param bibliographicCitation
     * @param creator
     * @param date
     * @param title
     * @param source
     * @param issues
     * @return
     */
  public Reference fromDC(String identifier, String bibliographicCitation,
                          String creator, String date, String title, String source,
                          IssueContainer issues) {
    Reference ref = find(identifier, bibliographicCitation);
    if (ref == null) {
      ref = newReference(datasetKey, identifier);
      if (!StringUtils.isBlank(bibliographicCitation)) {
        ref.setCitation(bibliographicCitation);
      }
      ref.setYear(parseYear(date));
      if (hasContent(creator, date, title, source)) {
        if (ref.getCitation() == null) {
          ref.setCitation(buildCitation(creator, date, title, source, null));
        }
        CslData csl = new CslData();
        ref.setCsl(csl);
        csl.setAuthor(parseAuthors(creator, issues));
        csl.setTitle(nullIfEmpty(title));
        csl.setIssued(toCslDate(date));
        if (!StringUtils.isEmpty(source)) {
          csl.setContainerTitle(source);
          issues.addIssue(Issue.CITATION_CONTAINER_TITLE_UNPARSED);
        }
      }
    }
    return ref;
  }
  
  /**
   * Creates a Reference instance from a DarwinCore data source.
   *
   * @param publishedInID
   * @param publishedIn
   * @param publishedInYear
   * @param issues
   * @return
   */
  public Reference fromDWC(String publishedInID, String publishedIn, String publishedInYear, IssueContainer issues) {
    String citation = publishedIn;
    if (publishedIn != null && publishedInYear != null && !publishedIn.contains(publishedInYear)) {
      citation = buildCitation(null, publishedInYear, publishedIn, null, null);
    }
    Reference ref = find(publishedInID, citation);
    if (ref == null) {
      ref = newReference(datasetKey, publishedInID);
      if (!StringUtils.isEmpty(publishedIn)) {
        ref.setCitation(citation);
        issues.addIssue(Issue.CITATION_UNPARSED);
      }
      ref.setYear(parseYear(publishedInYear));
    }
    return ref;
  }
  
  public Reference fromCitation(String id, String citation, IssueContainer issues) {
    Reference ref = find(id, citation);
    if (ref == null) {
      ref = newReference(datasetKey, id);
      if (!StringUtils.isEmpty(citation)) {
        ref.setCitation(citation);
        // try to extract year and authors
        Matcher matcher = YEAR_PATTERN.matcher(citation);
        if (matcher.find()) {
          ref.setYear(Integer.valueOf(matcher.group(2)));
          CslData csl = new CslData();
          ref.setCsl(csl);
          csl.setIssued(yearToDate(ref.getYear(), null));
          // see if leftovers are authors
          String leftover = (matcher.group(1) + matcher.group(3)).trim();
          if (!StringUtils.isBlank(leftover)) {
            IssueContainer authorIssues = new VerbatimRecord();
            CslName[] authors = parseAuthors(StringUtils.removeEnd(leftover, ",").trim(), authorIssues);
            if (!authorIssues.hasIssue(Issue.CITATION_AUTHORS_UNPARSED)) {
              // parsed authors
              csl.setAuthor(authors);
            }
          }
        }
        issues.addIssue(Issue.CITATION_UNPARSED);
      }
    }
    return ref;
  }
  
  private static CslDate yearToDate(Integer year, String raw) {
    if (year == null && raw == null) {
      return null;
    }
    
    CslDate d = new CslDate();
    if (year != null) {
      int[][] dateParts = new int[][]{{year}};
      d.setDateParts(dateParts);
    } else {
      // use raw or literal???
      d.setRaw(raw);
    }
    return d;
  }
  
  private static CslDate toCslDate(String dateString) {
    Optional<FuzzyDate> fd;
    try {
      fd = DateParser.PARSER.parse(dateString);
    } catch (UnparsableException e) {
      CslDate cslDate = new CslDate();
      cslDate.setLiteral(dateString);
      return cslDate;
    }
    if (fd.isPresent()) {
      LocalDate ld = fd.get().toLocalDate();
      int[][] dateParts = new int[][]{{ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth()}};
      CslDate cslDate = new CslDate();
      cslDate.setDateParts(dateParts);
      return cslDate;
    }
    return null;
  }
  
  private static Integer parseYear(String yearString) {
    if (StringUtils.isBlank(yearString)) {
      return null;
    }
    try {
      return Integer.valueOf(yearString);
    } catch (NumberFormatException e) {
      Matcher matcher = YEAR_PATTERN.matcher(yearString);
      if (matcher.find()) {
        String filtered = matcher.group(2);
        return Integer.valueOf(filtered);
      }
      return null;
    }
  }
  
  /**
   * Try to parse the individual authors if they adhere to well known formats.
   * Otherwise create a single author with an unparsed literal value.
   * @param authorString
   * @return
   */
  @VisibleForTesting
  static CslName[] parseAuthors(String authorString, IssueContainer issues) {
    if (StringUtils.isBlank(authorString)) {
      return null;
    }
    authorString = NORM_WHITESPACE.matcher(authorString).replaceAll(" ");
    List<CslName> names = new ArrayList<>();
    // comma with initials in front?
    for (String a : AUTHOR_SPLITTER.split(authorString)) {
      Matcher m = AUTHOR_PATTERN.matcher(a);
      if (m.find()) {
        names.add(buildName(m.group(1), m.group(2)));
      } else {
        names.clear();
        break;
      }
    }
    if (names.isEmpty()) {
      // comma with initials behind?
      Iterator<String> iter = AUTHOR_SPLITTER.split(authorString).iterator();
      try {
        while (iter.hasNext()) {
          String a = iter.next() + "," + iter.next();
          Matcher m = AUTHOR_PATTERN_SN_FN.matcher(a);
          if (m.find()) {
            names.add(buildName(m.group(2), m.group(1)));
          } else {
            names.clear();
            break;
          }
        }
      } catch (NoSuchElementException e) {
        names.clear();
      }
      // semicolons?
      if (names.isEmpty() && authorString.contains(";")) {
        for (String a : AUTHOR_SPLITTER_SEMI.split(authorString)) {
          Matcher m = AUTHORS_PATTERN_SEMI.matcher(a);
          if (m.find()) {
            names.add(buildName(m.group(2), m.group(1)));
          } else {
            names.clear();
            break;
          }
        }
      }
    }
    // use entire string as literal
    if (names.isEmpty()) {
      CslName name = new CslName();
      name.setLiteral(authorString);
      issues.addIssue(Issue.CITATION_AUTHORS_UNPARSED);
      return new CslName[]{name};
    } else {
      return names.toArray(new CslName[0]);
    }
  }
  
  private static CslName buildName(String given, String family){
    CslName name = new CslName();
    if (given != null) {
      name.setGiven(StringUtils.trimToNull(given));
    }
    Matcher pre = AUTHOR_PREFIX.matcher(family);
    if (pre.find()) {
      family = pre.replaceFirst(pre.group(2));
      name.setNonDroppingParticle(StringUtils.trimToNull(pre.group(1)));
    }
    name.setFamily(StringUtils.trimToNull(family));
    return name;
  }
  
  private static String nullIfEmpty(String s) {
    return StringUtils.isEmpty(s) ? null : s;
  }
  
  /**
   * Produces a citation string of the following format:
   * AUTHOR. TITLE. CONTAINER. YEAR
   * @param authors
   * @param year
   * @param title
   * @param container
   * @return the full citation string
   */
  @VisibleForTesting
  protected static String buildCitation(@Nullable String authors,
                                        @Nullable String year,
                                        @Nullable String title,
                                        @Nullable String container,
                                        @Nullable String details) {
    StringBuilder sb = new StringBuilder();
    if (!StringUtils.isEmpty(authors)) {
      sb.append(authors.trim());
      appendDotIfMissing(sb);
    }
  
    if (!StringUtils.isEmpty(title)) {
      appendSpaceIfContent(sb);
      sb.append(title.trim());
      appendDotIfMissing(sb);
    }
  
    if (!StringUtils.isEmpty(container)) {
      appendSpaceIfContent(sb);
      sb.append(container.trim());
      appendDotIfMissing(sb);
    }
  
    if (!StringUtils.isEmpty(details)) {
      appendSpaceIfContent(sb);
      sb.append(details.trim());
    }

    if (!StringUtils.isEmpty(year)) {
      appendSpaceIfContent(sb);
      sb.append("(");
      sb.append(year.trim());
      sb.append(")");
      appendDotIfMissing(sb);
    }

    return sb.toString();
  }
  
  private static void appendSpaceIfContent(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.append(' ');
    }
  }

  private static void appendDotIfMissing(StringBuilder sb) {
    if (sb.length() > 0 && !PUNCTUATIONS.contains(sb.charAt(sb.length() - 1))) {
      sb.append(".");
    }
  }

  private static Reference newReference(int datasetKey, String id) {
    Reference ref = new Reference();
    ref.setId(id);
    ref.setDatasetKey(datasetKey);
    return ref;
  }

}
