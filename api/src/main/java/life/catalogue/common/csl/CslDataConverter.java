package life.catalogue.common.csl;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.util.ObjectUtils;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.DigitStringValue;
import org.jbibtex.Key;
import org.jbibtex.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;
import de.undercouch.citeproc.helper.json.StringJsonBuilderFactory;

/**
 * Converts a CslData instance to a CSLItemData instance
 * and CSLItemData to BibTeX entries.
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
      target[i] = src[i].toCSL();
    }
    return target;
  }
  
  @VisibleForTesting
  static CSLDate toCSLDate(CslDate src) {
    if (src == null) {
      return null;
    }
    return new CSLDate(src.getDateParts(), src.getSeason(), src.getCirca(), src.getLiteral(), src.getRaw());
  }
  
  @VisibleForTesting
  static CSLType toCSLType(CSLType src) {
    if (src == null || src == CSLType.ARTICLE) {
      // We must return something, otherwise citation generation by citeproc-java will fail.
      // we remap ARTICLE to be a journal article. It is really used for legal works, but users often get this wrong!
      return CSLType.ARTICLE_JOURNAL;
    }
    return CSLType.valueOf(src.name());
  }

  @VisibleForTesting
  static String toBibTexType(CSLType type) {
    if (type != null) {
      switch (type) {
        case ARTICLE:
        case ARTICLE_JOURNAL:
        case ARTICLE_MAGAZINE:
        case ARTICLE_NEWSPAPER:
          return "article";
        case BOOK:
          return "book";
        case PAMPHLET:
          return "booklet";
        case CHAPTER:
          return "incollection"; // inbook
        case PAPER_CONFERENCE:
          return "inproceedings";
        case THESIS:
          return "phdthesis";
        case REPORT:
          return "techreport";
        case PATENT:
          return "patent";
        case WEBPAGE:
          return "online";
        case LEGISLATION:
        case LEGAL_CASE:
        case BILL:
          return "standard";
        case MANUSCRIPT:
          return "unpublished";
        case FIGURE:
        case GRAPHIC:
          return "artwork";
      }
    }
    return "misc";
  }


  public static BibTeXEntry toBibTex(CSLItemData data) {
    final CSLType type = data.getType();
    BibTeXEntry entry = new BibTeXEntry(new Key(toBibTexType(type)), new Key(data.getId()));

    addField(entry, "volume", data.getVolume());
    addField(entry, "number", data.getIssue());
    addField(entry, "edition", data.getEdition());
    addField(entry, "publisher", data.getPublisher());
    addField(entry, "address", data.getPublisherPlace());
    addField(entry, "version", data.getVersion());
    addField(entry, "isbn", data.getISBN());
    addField(entry, "issn", data.getISSN());
    addField(entry, "url", data.getURL());
    addField(entry, "doi", data.getDOI());
    addField(entry, "series", data.getCollectionTitle());
    addField(entry, "pages", data.getPage());
    addField(entry, "url", data.getURL());
    addField(entry, "note", data.getNote());
    addField(entry, "title", ObjectUtils.coalesce(data.getTitle(), data.getTitleShort()));
    addField(entry, "author", data.getAuthor());
    addField(entry, "editor", data.getEditor());

    if (type != null) {
      switch (type) {
        case ARTICLE:
        case ARTICLE_JOURNAL:
        case ARTICLE_MAGAZINE:
        case ARTICLE_NEWSPAPER:
          addField(entry, "journal", ObjectUtils.coalesce(data.getContainerTitle(), data.getContainerTitleShort()));
          addField(entry, "editor", data.getCollectionEditor());
          break;
        case CHAPTER:
          addField(entry, "booktitle", ObjectUtils.coalesce(data.getContainerTitle(), data.getContainerTitleShort()));
          addField(entry, "editor", data.getCollectionEditor());
          break;
      }
    }

    if (data.getIssued() != null) {
      int[] date = data.getIssued().getDateParts()[0];
      addField(entry, "year", date[0]);
      if (date.length>1) {
        addField(entry, "month", date[1]);
      }
    }

    //TODO: can we map this to anything ???
    // collectionEditor
    return entry;
  }

  private static void addField(BibTeXEntry entry, String field, CSLName[] value) {
    if (value != null && value.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (CSLName n : value) {
        if (n != null && (n.getFamily() != null || n.getLiteral() != null)) {
          if (sb.length()>0) {
            sb.append(" and ");
          }

          if (n.getLiteral() != null) {
            sb.append("{")
              .append(n.getLiteral())
              .append("}");
          } else {
            sb.append("{")
              .append(n.getFamily())
              .append("}");
            if (n.getGiven() != null) {
              sb.append(", {")
                .append(n.getGiven())
                .append("}");
            }
          }
        }
      }
      addField(entry, field, sb.toString());
    }
  }

  private static void addField(BibTeXEntry entry, String field, Integer value) {
    if (value != null) {
      entry.addField(new Key(field), new DigitStringValue(value.toString()));
    }
  }

  private static void addField(BibTeXEntry entry, String field, String value) {
    if (!StringUtils.isBlank(value)) {
      entry.addField(new Key(field), new StringValue(value, StringValue.Style.BRACED));
    }
  }
}
