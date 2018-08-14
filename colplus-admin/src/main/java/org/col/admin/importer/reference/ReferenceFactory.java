package org.col.admin.importer.reference;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.CslName;
import org.col.api.model.IssueContainer;
import org.col.api.model.Reference;
import org.col.api.vocab.Issue;
import org.col.common.date.FuzzyDate;
import org.col.parser.DateParser;
import org.col.parser.UnparsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataset specific factory for reference instances. It mostly manages the CSL parsing and works
 * with variously structured input forms. Responsible for detecting and flagging of issues in
 * reference.issues.
 *
 * In the future we can expect dataset specific configuration hints to be added.
 */
public class ReferenceFactory {

  private static final Logger LOG = LoggerFactory.getLogger(ReferenceFactory.class);
  private static final Pattern YEAR_PATTERN = Pattern.compile("(^|\\D+)(\\d{4})($|\\D+)");

  private final Integer datasetKey;
  private ReferenceStore store;

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
   *
   * Example: Ross, J.H. | 1979 | A conspectus of African Acacia | Mem. Bot. Surv. S. Afr. 44: 1-150
   * | TaxAccRef
   *
   * @param referenceID
   * @param authors author (or many) of publication
   * @param year of publication
   * @param title of paper or book
   * @param details title of periodicals, volume number, and other common bibliographic details
   * @return
   */
  public Reference fromACEF(String referenceID, String authors, String year, String title,
      String details, IssueContainer issues) {
    Reference ref = find(referenceID, null);
    if (ref == null) {
      ref = newReference(referenceID);
      ref.setYear(parseYear(year));
      if (!allEmpty(authors, year, title, details)) {
        ref.setCitation(buildCitation(authors, year, title, details));
        CslData csl = new CslData();
        ref.setCsl(csl);
        csl.setAuthor(getAuthors(authors));
        csl.setTitle(title);
        csl.setIssued(yearToDate(ref.getYear()));
        if (!StringUtils.isBlank(details)) {
          csl.setContainerTitle(details);
          issues.addIssue(Issue.CSL_CONTAINER_UNPARSED);
        }
      }
      store.put(ref);
    }
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
  public Reference fromDC(String identifier, String bibliographicCitation, String creator,
      String date, String title, String source, IssueContainer issues) {
    Reference ref = find(identifier, bibliographicCitation);
    if (ref == null) {
      ref = newReference(identifier);
      ref.setCitation(bibliographicCitation);
      ref.setYear(parseYear(date));
      if (!allEmpty(creator, date, title, source)) {
        ref.setCitation(buildCitation(creator, date, title, source));
        CslData csl = new CslData();
        ref.setCsl(csl);
        csl.setAuthor(getAuthors(creator));
        csl.setTitle(nullIfEmpty(title));
        csl.setIssued(toCslDate(date));
        if (!StringUtils.isEmpty(source)) {
          csl.setContainerTitle(source);
          issues.addIssue(Issue.CSL_CONTAINER_UNPARSED);
        }
      }
      store.put(ref);
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
  public Reference fromDWC(String publishedInID, String publishedIn, String publishedInYear,
      IssueContainer issues) {
    Reference ref = find(publishedInID, publishedIn);
    if (ref == null) {
      ref = newReference(publishedInID);
      if (!StringUtils.isEmpty(publishedIn)) {
        ref.setCitation(publishedIn);
        issues.addIssue(Issue.CITATION_UNPARSED);
      }   
      ref.setYear(parseYear(publishedInYear));
      store.put(ref);
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
      store.put(ref);
    }
    return ref;
  }

  private static CslDate yearToDate(Integer y) {
    if (y == null) {
      return null;
    }
    int[][] dateParts = new int[][] {{y, 1, 1}};
    CslDate d = new CslDate();
    d.setDateParts(dateParts);
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
      int[][] dateParts = new int[][] {{ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth()}};
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

  private static CslName[] getAuthors(String authorString) {
    if (StringUtils.isBlank(authorString)) {
      return null;
    }
    CslName name = new CslName();
    name.setLiteral(authorString);
    return new CslName[] {name};
  }

  private static String nullIfEmpty(String s) {
    return StringUtils.isEmpty(s) ? null : s;
  }

  private static String buildCitation(String authors, String year, String title, String container) {
    StringBuilder sb = new StringBuilder();
    if (!StringUtils.isEmpty(authors)) {
      sb.append(authors.trim());
    }
    if (!StringUtils.isEmpty(year)) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append("(").append(year).append(")");
    }
    if (sb.length() > 0) {
      sb.append('.');
    }
    if (!StringUtils.isEmpty(title)) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(title.trim());
      if (sb.charAt(sb.length() - 1) == '.') {
        sb.append('.');
      }
    }
    if (!StringUtils.isEmpty(container)) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(container.trim());
      if (sb.charAt(sb.length() - 1) == '.') {
        sb.append('.');
      }
    }
    return sb.toString();
  }

  private Reference newReference(String id) {
    Reference ref = new Reference();
    ref.setId(id);
    ref.setDatasetKey(datasetKey);
    return ref;
  }

  private static boolean allEmpty(String... strings) {
    for (String s : strings) {
      if (!StringUtils.isEmpty(s)) {
        return false;
      }
    }
    return true;
  }

}
