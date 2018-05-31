package org.col.csl;

import com.google.common.annotations.VisibleForTesting;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;
import org.col.api.model.CslData;
import org.col.api.model.CslDate;
import org.col.api.model.CslName;
import org.col.api.vocab.CSLRefType;

/**
 * Converrs a CslData instance to a CSLItemData instance.
 *
 */
class CslDataConverter {

  static CSLItemData toCSLItemData(CslData src) {
    return new CSLItemData(src.getId(), toCSLType(src.getType()), src.getCategories(),
        src.getLanguage(), src.getJournalAbbreviation(), src.getTitleShort(),
        toCSLNames(src.getAuthor()), toCSLNames(src.getCollectionEditor()),
        toCSLNames(src.getComposer()), toCSLNames(src.getContainerAuthor()),
        toCSLNames(src.getDirector()), toCSLNames(src.getEditor()),
        toCSLNames(src.getEditorialDirector()), toCSLNames(src.getInterviewer()),
        toCSLNames(src.getIllustrator()), toCSLNames(src.getOriginalAuthor()),
        toCSLNames(src.getRecipient()), toCSLNames(src.getReviewedAuthor()),
        toCSLNames(src.getTranslator()), toCSLDate(src.getAccessed()),
        toCSLDate(src.getContainer()), toCSLDate(src.getEventDate()), toCSLDate(src.getIssued()),
        toCSLDate(src.getOriginalDate()), toCSLDate(src.getSubmitted()), src.getAbstrct(),
        src.getAnnote(), src.getArchive(), src.getArchiveLocation(), src.getArchivePlace(),
        src.getAuthority(), src.getCallNumber(), src.getChapterNumber(), src.getCitationNumber(),
        src.getCitationLabel(), src.getCollectionNumber(), src.getCollectionTitle(),
        src.getContainerTitle(), src.getContainerTitleShort(), src.getDimensions(), src.getDOI(),
        src.getEdition(), src.getEvent(), src.getEventPlace(), src.getFirstReferenceNoteNumber(),
        src.getGenre(), src.getISBN(), src.getISSN(), src.getIssue(), src.getJurisdiction(),
        src.getKeyword(), src.getLocator(), src.getMedium(), src.getNote(), src.getNumber(),
        src.getNumberOfPages(), src.getNumberOfVolumes(), src.getOriginalPublisher(),
        src.getOriginalPublisherPlace(), src.getOriginalTitle(), src.getPage(), src.getPageFirst(),
        src.getPMCID(), src.getPMID(), src.getPublisher(), src.getPublisherPlace(),
        src.getReferences(), src.getReviewedTitle(), src.getScale(), src.getSection(),
        src.getSource(), src.getStatus(), src.getTitle(), src.getTitleShort(), src.getURL(),
        src.getVersion(), src.getVolume(), src.getYearSuffix());
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
