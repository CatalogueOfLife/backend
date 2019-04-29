package org.col.importer.reference;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.CharSet;
import org.apache.commons.lang3.StringUtils;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.common.date.FuzzyDate;
import org.col.api.util.ObjectUtils;
import org.col.importer.neo.NeoDb;
import org.col.parser.DateParser;
import org.col.parser.UnparsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.text.StringUtils.hasContent;

/**
 * Dataset specific factory for reference instances. It mostly manages the CSL parsing and works
 * with variously structured input forms. Responsible for detecting and flagging of issues in
 * reference.issues.
 * <p>
 * In the future we can expect dataset specific configuration hints to be added.
 */
public class ReferenceFactory {
  
  private static final Logger LOG = LoggerFactory.getLogger(ReferenceFactory.class);
  private static final Pattern YEAR_PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");
  private static final CharSet PUNCTUATIONS = CharSet.getInstance(".?!;:,");

  private final Integer datasetKey;
  private final ReferenceStore store;
  
  public ReferenceFactory(NeoDb db) {
    this(db.getDataset().getKey(), db);
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
      r = store.refById(id);
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
  public Reference fromACEF(String referenceID, String authors, String year, String title, String details, IssueContainer issues) {
    Reference ref = newReference(referenceID);
    ref.setYear(parseYear(year));
    if (hasContent(authors, year, title, details)) {
      ref.setCitation(buildCitation(authors, year, title, details));
      CslData csl = new CslData();
      ref.setCsl(csl);
      csl.setAuthor(parseAuthors(authors));
      csl.setTitle(title);
      csl.setIssued(yearToDate(ref.getYear(), year));
      if (!StringUtils.isBlank(details)) {
        csl.setContainerTitle(details);
        issues.addIssue(Issue.CITATION_CONTAINER_TITLE_UNPARSED);
      }
    }
    return ref;
  }
  
  public Reference fromCsl(CslData csl) {
    Reference ref = newReference(csl.getId());
    ref.setCsl(csl);
    //TODO: generate default citation string
    updateIntYearFromCsl(ref);
    return ref;
  }
  
  public static void updateIntYearFromCsl(Reference ref) {
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
  
  
  
  public Reference fromCol(String id, String authors, String year, String title, String source, String doi, String link, IssueContainer issues) {
    Reference ref = fromACEF(id, authors, year, title, source, issues);
    // add extra link & doi
    if (ref.getCsl() == null) {
      ref.setCsl(new CslData());
    }
    //TODO: clean & verify DOI & link
    ref.getCsl().setDOI(doi);
    ref.getCsl().setURL(link);
    return ref;
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
      ref = newReference(identifier);
      if (!StringUtils.isBlank(bibliographicCitation)) {
        ref.setCitation(bibliographicCitation);
      }
      ref.setYear(parseYear(date));
      if (hasContent(creator, date, title, source)) {
        if (ref.getCitation() == null) {
          ref.setCitation(buildCitation(creator, date, title, source));
        }
        CslData csl = new CslData();
        ref.setCsl(csl);
        csl.setAuthor(parseAuthors(creator));
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
      citation = buildCitation(null, publishedInYear, publishedIn, null);
    }
    Reference ref = find(publishedInID, citation);
    if (ref == null) {
      ref = newReference(publishedInID);
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
      ref = newReference(id);
      if (!StringUtils.isEmpty(citation)) {
        ref.setCitation(citation);
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
  
  private static CslName[] parseAuthors(String authorString) {
    if (StringUtils.isBlank(authorString)) {
      return null;
    }
    CslName name = new CslName();
    name.setLiteral(authorString);
    return new CslName[]{name};
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
  protected static String buildCitation(@Nullable String authors, @Nullable String year, @Nullable String title, @Nullable String container) {
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
      sb.append(year.trim());
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

  public Reference newReference(String id) {
    Reference ref = new Reference();
    ref.setId(id);
    ref.setDatasetKey(datasetKey);
    return ref;
  }

}
