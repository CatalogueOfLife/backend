package org.col.admin.importer.reference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.IssueContainer;
import org.col.api.model.Reference;
import org.col.api.vocab.CSLRefType;
import org.col.api.vocab.Issue;
import org.col.csl.CslUtil;
import org.col.parser.Parser;
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
  private final Parser<CslData> cslParser;
  private ReferenceStore store;

  public ReferenceFactory(Integer datasetKey, Parser<CslData> cslParser, ReferenceStore store) {
    this.datasetKey = datasetKey;
    this.cslParser = cslParser;
    this.store = store;
  }


  /**
   * Tries to find an existing reference by its id or exact citation.
   * Returns null if not found
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

  private Reference newRef(String id) {
    Reference ref = new Reference();
    ref.setDatasetKey(datasetKey);
    ref.setId(id);
    return ref;
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
  public Reference fromACEF(String referenceID, String authors, String year, String title, String details, IssueContainer issues) {
    Reference ref = find(referenceID, null);
    if (ref == null) {
      ref = parse(referenceID, buildCitation(authors, year, details), issues);
      ref.getCsl().setTitle(title);
      postParse(ref, issues);
    }
    return ref;
  }

  public Reference fromCitation(String id, String citation, IssueContainer issues) {
    Reference ref = find(id, citation);
    if (ref == null) {
      ref = parse(id, citation, issues);
      postParse(ref, issues);
    }
    return ref;
  }

  public Reference fromDWC(String publishedInID, String publishedIn, String publishedInYear, IssueContainer issues) {
    Reference ref = find(publishedInID, publishedIn);
    if (ref == null) {
      ref = parse(publishedInID, publishedIn, issues);
      if (ref.getCsl().getIssued() == null && publishedInYear != null) {
        Integer y = parseYear(publishedInYear);
        if (y != null) {
          int[][] dateParts = {{y}};
          CslDate cslDate = new CslDate();
          cslDate.setDateParts(dateParts);
          ref.getCsl().setIssued(cslDate);
        }
      }
      postParse(ref, issues);
    }
    return ref;
  }

  /**
   * Very similar to fromACEF but optionally providing a full citation given as bibliographicCitation
   * @param identifier
   * @param bibliographicCitation
   * @param creator
   * @param date
   * @param title
   * @param source
   * @param issues
   * @return
   */
  public Reference fromDC(String identifier, String bibliographicCitation, String creator, String date, String title, String source, IssueContainer issues) {
    Reference ref = find(identifier, bibliographicCitation);
    if (ref == null) {
      if (bibliographicCitation != null) {
        ref = parse(identifier, bibliographicCitation, issues);
      } else {
        ref = parse(identifier, buildCitation(creator, date, source), issues);
        ref.getCsl().setTitle(title);
      }
      postParse(ref, issues);
    }
    return ref;
  }

  private Reference parse(String id, String citation, IssueContainer issues) {
    Reference ref = newRef(id);
    try {
      cslParser.parse(citation).ifPresent(ref::setCsl);
    } catch (UnparsableException | RuntimeException e) {
      issues.addIssue(Issue.UNPARSABLE_REFERENCE);
      ref.setCitation(citation);
    }
    return ref;
  }

  private static String buildCitation(String authors, String year, String details) {
    StringBuilder sb = new StringBuilder();
    if (!Strings.isNullOrEmpty(authors)) {
      sb.append(authors)
        .append(" ");
    }
    if (!Strings.isNullOrEmpty(year)) {
      sb.append("(")
        .append(year)
        .append(")");
    }
    //TODO: add dummy title???
    if (!Strings.isNullOrEmpty(details)) {
      sb.append(". ")
        .append(details);
    }
    return sb.toString();
  }

  private Reference postParse(Reference ref, IssueContainer issues) {
    if (ref.getCsl() != null) {
      // missing ref type?
      if (ref.getCsl().getType() == null) {
        issues.addIssue(Issue.UNPARSABLE_REFERENCE_TYPE);
        ref.getCsl().setType(CSLRefType.ARTICLE);
      }
      // extract int year
      if (ref.getCsl().getIssued() != null) {
        CslDate date = ref.getCsl().getIssued();
        if (date.getDateParts() != null) {
          ref.setYear(parseYear(date));
        } else {
          ref.setYear(parseYear(ref.getCsl().getYearSuffix()));
        }
      }
      // build citation if not there
      if (ref.getCitation() == null) {
        ref.setCitation(CslUtil.buildCitation(ref.getCsl()));
      }
      //
    }
    // TODO lookup by citation once again to avoid duplicates???
    // persist
    store.put(ref);
    return ref;
  }

  private static Integer parseYear(CslDate date) {

    if (!ArrayUtils.isEmpty(date.getDateParts()) && !ArrayUtils.isEmpty(date.getDateParts()[0])
        && date.getDateParts()[0][0] != 0) {
      return date.getDateParts()[0][0];
    }
    return null;
  }

  private static Integer parseYear(String yearString) {
    if (yearString == null) {
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

}
