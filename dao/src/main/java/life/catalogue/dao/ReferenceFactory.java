package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DoiResolution;
import life.catalogue.api.vocab.Issue;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslDataConverter;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.metadata.DoiResolver;
import life.catalogue.parser.CSLTypeParser;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.UnparsableException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;

import de.undercouch.citeproc.csl.CSLType;

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

  private static final Pattern EDS = Pattern.compile("(?:\\b|\\()Eds?\\.\\)?$");
  private static final Pattern YEAR_PATTERN = Pattern.compile("(^|\\D+)([12]\\d{3})($|\\D+)");
  private static final CharSet PUNCTUATIONS = CharSet.getInstance(".?!;:,");
  private static final Pattern NORM_WHITESPACE = Pattern.compile("\\s{2,}");
  private static final String name     = "[\\p{L} -]+";
  private static final String initials = "(?:\\p{Lu}(?:\\. ?| ))*";
  // comma separated authors with (initials/firstname) surnames
  private static final Splitter AUTHOR_SPLITTER = Splitter.on(CharMatcher.anyOf(",&")).trimResults();
  private static final Pattern AUTHOR_PATTERN_FN_SN = Pattern.compile("^(" + initials + ") *(" + name + ")$");
  private static final Pattern AUTHOR_PATTERN_SN_FN = Pattern.compile("^("+name+") +("+ initials +")$");
  // comma separated authors with surname, initials
  private static final Pattern AUTHOR_PATTERN_SN_C_FN = Pattern.compile("^(" + name + "), *(" + initials + ")$");
  // semicolon separated authors with lastname, firstnames
  private static final Splitter AUTHOR_SPLITTER_SEMI = Splitter.on(';').trimResults();
  private static final Pattern AUTHORS_PATTERN_SEMI = Pattern.compile("^("+ name +")(?:, ?("+initials+"|"+name+"))?$");
  // author parsing
  private static final Pattern AUTHOR_PREFIX = Pattern.compile("^((?:\\p{Ll}{1,5} ){1,2})(\\p{Lu})");
  // author year citation (e.g. sensu Miller, 1973)
  private static final Pattern AUTHOR_YEAR = Pattern.compile("^([\\p{L} -.,]+)\\s*,?\\s*([12]\\d{3})?\\s*$");

  private final Integer datasetKey;
  private final ReferenceStore store;
  private DoiResolution resolveDOIs = DoiResolution.NEVER;
  private final DoiResolver resolver;

  /**
   * A factory without any store, so that each created references becomes a new instance.
   */
  public ReferenceFactory(Integer datasetKey) {
    this(datasetKey, ReferenceStore.passThru(), null);
  }

  public ReferenceFactory(Integer datasetKey, DoiResolver resolver) {
    this(datasetKey, ReferenceStore.passThru(), resolver);
  }

  public ReferenceFactory(Integer datasetKey, ReferenceStore store, @Nullable DoiResolver resolver) {
    this.datasetKey = datasetKey;
    this.store = store;
    this.resolver = resolver;
  }

  public void setResolveDOIs(DoiResolution resolveDOIs) {
    this.resolveDOIs = resolveDOIs;
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
   * Builds a new reference instance from an ACEF reference record.
   * Does not persist the instance to the ref store.
   *
   * Example: Ross, J.H. | 1979 | A conspectus of African Acacia | Mem. Bot. Surv. S. Afr. 44: 1-150
   * | TaxAccRef
   *
   * @param referenceID reference identifier
   * @param authors     author (or many) of publication
   * @param year        of publication
   * @param title       of paper or book
   * @param details     title of periodicals, volume number, and other common bibliographic details
   */
  public Reference fromACEF(String referenceID, String authors, String year, String title, String details, IssueContainer issues) {
    return fromDC(true, referenceID, null, authors, year, title, details, issues);
  }
  
  public Reference fromColDP(VerbatimRecord v) {
    CslData csl = new CslData();
    csl.setId(v.get(ColdpTerm.ID));
    CSLType type = SafeParser.parse(CSLTypeParser.PARSER, v.get(ColdpTerm.type)).orNull(Issue.UNPARSABLE_REFERENCE_TYPE, v);
    csl.setType(type);
    csl.setAuthor(parseAuthors(v.get(ColdpTerm.author), v));
    csl.setEditor(parseAuthors(v.get(ColdpTerm.editor), v));
    csl.setTitle(v.get(ColdpTerm.title));
    csl.setContainerAuthor(parseAuthors(v.get(ColdpTerm.containerAuthor), v));
    csl.setContainerTitle(v.get(ColdpTerm.containerTitle));
    csl.setIssued(ReferenceFactory.toCslDate(v.get(ColdpTerm.issued)));
    csl.setAccessed(ReferenceFactory.toCslDate(v.get(ColdpTerm.accessed)));
    csl.setCollectionTitle(v.get(ColdpTerm.collectionTitle));
    csl.setCollectionEditor(parseAuthors(v.get(ColdpTerm.collectionEditor), v));
    csl.setVolume(v.get(ColdpTerm.volume));
    csl.setIssue(v.get(ColdpTerm.issue));
    csl.setEdition(v.get(ColdpTerm.edition));
    csl.setPage(v.get(ColdpTerm.page));
    csl.setPublisher(v.get(ColdpTerm.publisher));
    csl.setPublisherPlace(v.get(ColdpTerm.publisherPlace));
    csl.setVersion(v.get(ColdpTerm.version));
    csl.setISBN(v.get(ColdpTerm.isbn));
    csl.setISSN(v.get(ColdpTerm.issn));
    csl.setDOI(v.get(ColdpTerm.doi));
    csl.setURL(v.get(ColdpTerm.link));

    return fromCsl(datasetKey, csl, v.get(ColdpTerm.citation), v.get(ColdpTerm.remarks));
  }

  private void resolveDOI(Reference ref) {
    if (ref.getCsl() != null && ref.getCsl().getDOI() != null && (
      resolveDOIs == DoiResolution.ALWAYS || resolveDOIs == DoiResolution.MISSING && !ref.getCsl().hasTitleContainerOrAuthor()
    )) {
      DOI.parse(ref.getCsl().getDOI()).ifPresent(doi -> {
        Citation c = resolver.resolve(doi);
        if (c != null) {
          var csl = CslDataConverter.toCslData(c.toCSL());
          csl.setDOI(ref.getCsl().getDOI());
          ref.setCsl(csl);
        }
      });
    }
  }

  public Reference fromCsl(int datasetKey, CslData csl) {
    return fromCsl(datasetKey, csl, null, null);
  }

  public Reference fromCsl(int datasetKey, CslData csl, String citation, String remarks) {
    Reference ref = newReference(datasetKey, csl.getId());
    ref.setRemarks(remarks);
    ref.setCsl(csl);
    lookForDOI(ref);
    resolveDOI(ref); // this can create a new csl instance!
    // if a full citation is given prefer that over a CSL generated one in case we do not have structured basics
    if (!StringUtils.isBlank(citation) && !ref.getCsl().hasTitleContainerOrAuthor()) {
      ref.setCitation(citation);
    } else {
      // generate default APA citation string
      ref.setCitation(CslUtil.buildCitation(ref.getCsl()));
    }
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
   * Factory method that takes the core DublinCore set of fields just like ACEF, but optionally also providing a full citation given as
   * bibliographicCitation
   **/
  public Reference fromDC(String identifier, String bibliographicCitation,
                          String creator, String date, String title, String source,
                          IssueContainer issues) {
    return fromDC(false, identifier, bibliographicCitation, creator, date, title, source, issues);
  }

  /**
   * Factory method that takes the core DublinCore set of fields just like ACEF, but optionally also providing a full citation given as
   * bibliographicCitation
   **/
  private Reference fromDC(boolean forceNew, String identifier, String bibliographicCitation,
                          String creator, String date, String title, String source,
                          IssueContainer issues) {
    Reference ref = forceNew ? null : find(identifier, bibliographicCitation);
    if (ref == null) {
      CslData csl = new CslData();
      csl.setId(identifier);
      // ACEF keeps authors and editors in the same field - try to detect
      if (creator != null) {
        var m = EDS.matcher(creator);
        if (m.find()) {
          csl.setEditor(parseAuthors(m.replaceFirst("").trim(), issues));
        } else {
          csl.setAuthor(parseAuthors(creator, issues));
        }
      }
      csl.setTitle(title);
      csl.setIssued(ReferenceFactory.toCslDate(date));
      csl.setContainerTitle(source);
      if (!StringUtils.isBlank(source)) {
        // details can include any of the following and probably more: volume, edition, series, page, publisher
        // try to parse out volume, issues and pages
        CslUtil.parseVolumeIssuePage(source).ifPresentOrElse(vip -> {
          csl.setContainerTitle(vip.beginning);
          csl.setVolume(ObjectUtils.toString(vip.volume));
          csl.setIssue(ObjectUtils.toString(vip.issue));
          csl.setPage(ObjectUtils.toString(vip.page));
        }, () -> issues.addIssue(Issue.CITATION_DETAILS_UNPARSED));
      }
      return fromCsl(datasetKey, csl, bibliographicCitation, null);
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
      citation = String.format("%s (%s)", publishedIn, publishedInYear);
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
    return fd.map(FuzzyDate::toCslDate).orElse(null);
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
    final String authorStringNormed = NORM_WHITESPACE.matcher(authorString).replaceAll(" ");
    // try different formats and require all authors to adhere to the same structure
    return parseAuthorsSemicolon(authorStringNormed, issues).orElseGet(() ->
      parseAuthorsCommaInitialFirst(authorStringNormed, issues).orElseGet(() ->
        parseAuthorsCommaInitialBehindWS(authorStringNormed, issues).orElseGet(() ->
          parseAuthorsCommaInitialBehind(authorStringNormed, issues).orElseGet(() -> {
            // nothing works, resort to single string in literal
            CslName name = new CslName();
            name.setFamily(authorStringNormed);
            issues.addIssue(Issue.CITATION_AUTHORS_UNPARSED);
            return List.of(name);
          })
        )
      )
    ).toArray(new CslName[0]);
  }

  private static Optional<List<CslName>> parseAuthorsCommaInitialFirst(String authorString, IssueContainer issues) {
    List<CslName> names = new ArrayList<>();
    for (String a : AUTHOR_SPLITTER.split(authorString)) {
      Matcher m = AUTHOR_PATTERN_FN_SN.matcher(a);
      if (m.find()) {
        names.add(buildName(m.group(1), m.group(2)));
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(names);
  }

  private static Optional<List<CslName>> parseAuthorsCommaInitialBehind(String authorString, IssueContainer issues) {
    List<CslName> names = new ArrayList<>();
    Iterator<String> iter = AUTHOR_SPLITTER.split(authorString).iterator();
    try {
      while (iter.hasNext()) {
        String a = iter.next() + "," + iter.next();
        Matcher m = AUTHOR_PATTERN_SN_C_FN.matcher(a);
        if (m.find()) {
          names.add(buildName(m.group(2), m.group(1)));
        } else {
          return Optional.empty();
        }
      }
    } catch (NoSuchElementException e) {
      return Optional.empty();
    }
    return Optional.of(names);
  }

  private static Optional<List<CslName>> parseAuthorsCommaInitialBehindWS(String authorString, IssueContainer issues) {
    List<CslName> names = new ArrayList<>();
    for (String a : AUTHOR_SPLITTER.split(authorString)) {
      Matcher m = AUTHOR_PATTERN_SN_FN.matcher(a);
      if (m.find()) {
        names.add(buildName(m.group(2), m.group(1)));
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(names);
  }

  private static Optional<List<CslName>> parseAuthorsSemicolon(String authorString, IssueContainer issues) {
    if (authorString.contains(";")) {
      List<CslName> names = new ArrayList<>();
      for (String a : AUTHOR_SPLITTER_SEMI.split(authorString)) {
        Matcher m = AUTHORS_PATTERN_SEMI.matcher(a);
        if (m.find()) {
          names.add(buildName(m.group(2), m.group(1)));
        } else {
          return Optional.empty();
        }
      }
      return Optional.of(names);
    }
    return Optional.empty();
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
                                        @Nullable String container) {
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

  /**
   * Copies a DOI found as the identifier into the CSL DOI field.
   * @return same instance as given
   */
  private static Reference lookForDOI(Reference ref) {
    if (ref.getCsl() == null || ref.getCsl().getDOI() == null) {
      Optional<DOI> doi = DOI.parse(ref.getId());
      if (!doi.isPresent() && ref.getCsl() != null) {
        doi= DOI.parse(ref.getCsl().getId())
                .or(() -> DOI.parse(ref.getCsl().getURL()));
      }
      doi.ifPresent(d -> {
        if (ref.getCsl() == null) {
          ref.setCsl(new CslData(ref.getId()));
        }
        ref.getCsl().setDOI(d);
      });
    }
    return ref;
  }
}
