package org.col.admin.importer.reference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import org.apache.commons.lang3.ArrayUtils;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
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

  public ReferenceFactory(Integer datasetKey, Parser<CslData> cslParser) {
    this.datasetKey = datasetKey;
    this.cslParser = cslParser;
  }

  private Reference create(String id) {
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
   * @param referenceType taxonomic status of reference: NomRef: Nomenclatural Reference (just one
   *        reference which contains the original (validating) publication of taxon name or new name
   *        combination or TaxAccRef: Taxonomic Acceptance Reference(s) (one or more bibliographic
   *        references, where the name is mentioned in the same taxonomic status (i.e. as a species
   *        or as a synonym) or ComNameRef: Common Name Reference(s) (one or more bibliographic
   *        references that contain common names)
   * @param authors author (or many) of publication
   * @param year of publication
   * @param title of paper or book
   * @param details title of periodicals, volume number, and other common bibliographic details
   * @return
   */
  public Reference fromACEF(String referenceID, String referenceType, String authors, String year,
      String title, String details) {
    Reference ref = create(referenceID);

    if (details != null && (title == null || details.length() > title.length())) {
      // consider details to be the entire citation
      parse(ref, details);
    } else {
      parse(ref, buildCitation(authors, year, title, details));
    }
    return postParse(ref);
  }

  public Reference fromCitation(String id, String citation) {
    Reference ref = create(id);
    parse(ref, citation);
    return postParse(ref);
  }

  public Reference fromDWC(String publishedInID, String publishedIn, String publishedInYear) {
    Reference ref = create(publishedInID);
    parse(ref, publishedIn);
    if (ref.getCsl().getIssued() == null && publishedInYear != null) {
      Integer y = parseYear(publishedInYear);
      if (y != null) {
        int[][] dateParts = {{y}};
        CslDate cslDate = new CslDate();
        cslDate.setDateParts(dateParts);
        ref.getCsl().setIssued(cslDate);
      }
    }
    return postParse(ref);
  }

  public Reference fromDC(String identifier, String bibliographicCitation, String title,
      String creator, String date, String source) {
    Reference ref = create(identifier);

    if (bibliographicCitation != null) {
      parse(ref, bibliographicCitation);
    } else {
      parse(ref, buildCitation(creator, date, title, source));
    }
    return postParse(ref);
  }

  private void parse(Reference ref, String citation) {
    try {
      cslParser.parse(citation).ifPresent(ref::setCsl);
    } catch (UnparsableException | RuntimeException e) {
      ref.addIssue(Issue.UNPARSABLE_REFERENCE);
      ref.setCitation(citation);
    }
  }

  private static String buildCitation(String authors, String year, String title, String details) {
    StringBuilder sb = new StringBuilder();
    if (!Strings.isNullOrEmpty(authors)) {
      sb.append(authors).append(" ");
    }
    if (!Strings.isNullOrEmpty(year)) {
      sb.append(year).append(". ");
    }
    sb.append(title);
    if (!Strings.isNullOrEmpty(details)) {
      sb.append(". ").append(details);
    }
    return sb.toString();
  }

  private static Reference postParse(Reference ref) {
    if (ref.getCsl() != null) {
      // missing ref type?
      if (ref.getCsl().getType() == null) {
        ref.addIssue(Issue.UNPARSABLE_REFERENCE_TYPE);
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
    }
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
