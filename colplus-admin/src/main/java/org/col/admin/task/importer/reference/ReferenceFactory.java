package org.col.admin.task.importer.reference;

import com.google.common.base.Strings;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.Reference;
import org.col.api.vocab.Issue;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dataset specific factory for reference instances.
 * It mostly manages the CSL parsing and works with variously structured input forms.
 * Responsible for detecting and flagging of issues in reference.issues.
 *
 * In the future we can expect dataset specific configuration hints to be added.
 */
public class ReferenceFactory {

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
   * Example: Ross, J.H. | 1979 | A conspectus of African Acacia |
   * Mem. Bot. Surv. S. Afr. 44: 1-150 | TaxAccRef
   *
   * @param referenceID
   * @param authors author (or many) of publication
   * @param title of paper or book
   * @param year of publication
   * @param source title of periodicals, volume number, and other common bibliographic details
   * @param referenceType taxonomic status of reference:
   *    NomRef: Nomenclatural Reference (just one reference which contains the original (validating) publication of taxon name or new name combination or
   *    TaxAccRef: Taxonomic Acceptance Reference(s) (one or more bibliographic references, where the name is mentioned in the same taxonomic status (i.e. as a species or as a synonym) or
   *    ComNameRef: Common Name Reference(s) (one or more bibliographic references that contain common names)
   * @param details
   * @return
   */
  public Reference fromACEF(String referenceID, String authors, String title, String year, String source, String referenceType, String details) {
    Reference ref = create(referenceID);

    if (details != null && (title == null || details.length() > title.length())) {
      // consider details to be the entire citation
      parse(ref, details);
      //TODO verify with atomized raw data and raise issues

    } else {
      // TODO: the atomised pieces contain more information than details, use them
      parse(ref, buildCitation(authors, title, year, source, details));
    }

    return postParse(ref);
  }

  private static String buildCitation(String authors, String title, String year, String source, String details) {
    StringBuilder sb = new StringBuilder();
    if (Strings.isNullOrEmpty(authors)) {
      sb.append(authors)
          .append(": ");
    }
    sb.append(title);
    if (Strings.isNullOrEmpty(year)) {
      //TODO: use dot or comma after title? Does anystyle care?
      sb.append(" ")
          .append(year);
    }
    if (Strings.isNullOrEmpty(source)) {
      // does IN confuse the parser as its normally only used for books not journals???
      sb.append(" in ")
          .append(source);
    }
    if (Strings.isNullOrEmpty(details)) {
      sb.append(" ")
          .append(details);
    }
    return sb.toString();
  }

  public Reference fromDWC(String publishedInID, String publishedIn, String publishedInYear) {
    Reference ref = create(publishedInID);

    parse(ref, publishedIn);
    if (ref.getCsl().getIssued() == null && publishedInYear != null) {
      //TODO: parse publishedInYear into CSL date
      // can we do this with a java date <-> CslDate conversion ???
      ref.getCsl().setIssued(null);
    }

    return postParse(ref);
  }

  public Reference fromDC(String identifier, String bibliographicCitation, String title, String creator, String date, String source) {
    Reference ref = create(identifier);

    if (bibliographicCitation != null) {
      parse(ref, bibliographicCitation);
      //TODO verify with atomized raw data and raise issues

    } else {
      // TODO: use atomised pieces
      parse(ref, buildCitation(creator, title, date, source, null));
    }

    return postParse(ref);
  }

  private void parse(Reference ref, String citation) {
    try {
      Optional<CslData> csl = cslParser.parse(citation);
      if (csl.isPresent()) {
        ref.setCsl(csl.get());
        return;
      }

    } catch (UnparsableException e) {
      e.printStackTrace();
    }
    ref.addIssue(Issue.REFERENCE_UNPARSABLE);
    ref.setCsl(new CslData());
  }

  private static Reference postParse(Reference ref) {
    // extract int year
    if (ref.getCsl().getIssued() != null) {
      CslDate date = ref.getCsl().getIssued();
      if (date.getDateParts() != null) {
        ref.setYear(parseYear(date));
      } else {
        ref.setYear(parseYear(ref.getCsl().getYearSuffix()));
      }
    }
    return ref;
  }

  private static Integer parseYear(CslDate date) {
    if (date.getDateParts()[0] != null && date.getDateParts()[0][0] != 0) {
      return Integer.valueOf(date.getDateParts()[0][0]);
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
