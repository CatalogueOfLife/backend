package life.catalogue.common.csl;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;
import de.undercouch.citeproc.helper.json.StringJsonBuilderFactory;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.vocab.CSLRefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts a CslData instance to a CSLItemData instance.
 */
public class CslDataConverter {
  private static final Logger LOG = LoggerFactory.getLogger(CslDataConverter.class);
  private static final ObjectMapper OM = new ObjectMapper();
  private static final StringJsonBuilderFactory FACTORY = new StringJsonBuilderFactory();
  
  public static CSLItemData toCSLItemData(CslData src) {
    return new CSLItemData(src.getId(),
      toCSLType(src.getType()),
      src.getCategories(),
      src.getLanguage(),
      src.getJournalAbbreviation(),
      src.getTitleShort(),
      toCSLNames(src.getAuthor()),
      toCSLNames(src.getCollectionEditor()),
      toCSLNames(src.getComposer()),
      toCSLNames(src.getContainerAuthor()),
      toCSLNames(src.getDirector()),
      toCSLNames(src.getEditor()),
      toCSLNames(src.getEditorialDirector()),
      toCSLNames(src.getInterviewer()),
      toCSLNames(src.getIllustrator()),
      toCSLNames(src.getOriginalAuthor()),
      toCSLNames(src.getRecipient()),
      toCSLNames(src.getReviewedAuthor()),
      toCSLNames(src.getTranslator()),
      toCSLDate(src.getAccessed()),
      toCSLDate(src.getContainer()),
      toCSLDate(src.getEventDate()),
      toCSLDate(src.getIssued()),
      toCSLDate(src.getOriginalDate()),
      toCSLDate(src.getSubmitted()),
      src.getAbstrct(),
      src.getAnnote(),
      src.getArchive(),
      src.getArchiveLocation(),
      src.getArchivePlace(),
      src.getAuthority(),
      src.getCallNumber(),
      src.getChapterNumber(),
      src.getCitationNumber(),
      src.getCitationLabel(),
      src.getCollectionNumber(),
      src.getCollectionTitle(),
      null,
      src.getContainerTitle(),
      src.getContainerTitleShort(),
      src.getDimensions(),
      src.getDOI(),
      src.getEdition(),
      src.getEvent(),
      src.getEventPlace(),
      src.getFirstReferenceNoteNumber(),
      src.getGenre(),
      src.getISBN(),
      src.getISSN(),
      src.getIssue(),
      src.getJurisdiction(),
      src.getKeyword(),
      src.getLocator(),
      src.getMedium(),
      src.getNote(),
      src.getNumber(),
      src.getNumberOfPages(),
      src.getNumberOfVolumes(),
      src.getOriginalPublisher(),
      src.getOriginalPublisherPlace(),
      src.getOriginalTitle(),
      src.getPage(),
      src.getPageFirst(),
      src.getPMCID(),
      src.getPMID(),
      src.getPublisher(),
      src.getPublisherPlace(),
      src.getReferences(),
      src.getReviewedTitle(),
      src.getScale(),
      src.getSection(),
      src.getSource(),
      src.getStatus(),
      src.getTitle(),
      src.getTitleShort(),
      src.getURL(),
      src.getVersion(),
      src.getVolume(),
      src.getYearSuffix());
  }
  
  public static CslData toCslData(CSLItemData src) {
    if (src != null) {
      String json = (String)src.toJson(FACTORY.createJsonBuilder());
      try {
        return ApiModule.MAPPER.readValue(json, CslData.class);
      } catch (IOException e) {
        LOG.error("Failed to convert CSLItemData JSON to CslData: {}", json, e);
        throw new IllegalArgumentException(e);
      }
    }
    return null;
  }

  private static CSLName[] toCSLNames(CslName[] src) {
    if (src == null) {
      return null;
    }
    CSLName[] target = new CSLName[src.length];
    for (int i = 0; i < src.length; i++) {
      target[i] = toCSLName(src[i]);
    }
    return target;
  }
  
  private static CSLName toCSLName(CslName src) {
    if (src == null) {
      return null;
    }
    return new CSLName(src.getFamily(), src.getGiven(), src.getDroppingParticle(),
        src.getNonDroppingParticle(), src.getSuffix(), src.getCommaPrefix(), src.getCommaSuffix(),
        src.getStaticOrdering(), src.getStaticParticles(), src.getLiteral(), src.getParseNames(),
        src.getIsInstitution());
  }
  
  @VisibleForTesting
  static CSLDate toCSLDate(CslDate src) {
    if (src == null) {
      return null;
    }
    return new CSLDate(src.getDateParts(), src.getSeason(), src.getCirca(), src.getLiteral(), src.getRaw());
  }
  
  @VisibleForTesting
  static CSLType toCSLType(CSLRefType src) {
    if (src == null) {
      // We must return something, otherwise citation generation by citeproc-java will fail.
      return CSLType.ARTICLE_JOURNAL;
    }
    return CSLType.valueOf(src.name());
  }
  
}
