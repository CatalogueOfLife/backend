package life.catalogue.common.csl;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CslName;
import life.catalogue.common.date.FuzzyDate;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Collectors;

import de.undercouch.citeproc.bibtex.PageParser;
import de.undercouch.citeproc.bibtex.PageRanges;
import de.undercouch.citeproc.csl.CSLDate;
import de.undercouch.citeproc.csl.CSLDateBuilder;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;

import static java.time.temporal.ChronoField.*;

/**
 * Builds citeproc {@link CSLItemData} from the COL {@link Citation} model.
 * Relocated from the former {@code Citation.toCSL()} to keep the api model citeproc-free.
 */
public class CitationConverter {

  public static CSLItemData toCSL(Citation c) {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .type(CslTypeConverter.toCiteproc(c.getType()))
      .title(c.getTitle())
      .shortTitle(c.getAlias())
      .volume(c.getVolume())
      .issue(c.getIssue())
      .edition(c.getEdition())
      .publisher(c.getPublisher())
      .publisherPlace(c.getPublisherPlace())
      .containerTitle(c.getContainerTitle())
      .collectionTitle(c.getCollectionTitle())
      .version(c.getVersion())
      .ISBN(c.getIsbn())
      .ISSN(c.getIssn())
      .URL(c.getUrl());

    // DOI
    if (c.getDoi() != null) {
      builder.DOI(c.getDoi().toString());
    }
    // names
    if (c.getAuthor() != null) {
      builder.author(toNames(c.getAuthor()));
    }
    if (c.getEditor() != null) {
      builder.editor(toNames(c.getEditor()));
    }
    if (c.getContainerAuthor() != null) {
      builder.containerAuthor(toNames(c.getContainerAuthor()));
    }
    if (c.getCollectionEditor() != null) {
      builder.collectionEditor(toNames(c.getCollectionEditor()));
    }

    // dates
    if (c.getIssued() != null) {
      builder.issued(toDate(c.getIssued()));
    }
    if (c.getAccessed() != null) {
      builder.accessed(toDate(c.getAccessed()));
    }

    // pages
    if (c.getPage() != null) {
      PageRanges pr = PageParser.parse(c.getPage());
      builder.page(pr.getLiteral());
      if (pr.getNumberOfPages() != null) {
        builder.numberOfPages(String.valueOf(pr.getNumberOfPages()));
      }
    }

    return builder.build();
  }

  static CSLName[] toNames(List<CslName> names) {
    if (names == null || names.isEmpty()) return null;
    return names.stream()
          .map(CitationConverter::toName)
          .collect(Collectors.toList())
          .toArray(CSLName[]::new);
  }

  static CSLName toName(CslName n) {
    return new CSLName(n.getFamily(), n.getGiven(), n.getDroppingParticle(),
      n.getNonDroppingParticle(), n.getSuffix(), null, null,
      null, null, n.getLiteral(), null,
      n.getIsInstitution());
  }

  static CSLDate toDate(FuzzyDate fd) {
    TemporalAccessor ta = fd.getDate();
    if (ta.isSupported(MONTH_OF_YEAR)) {
      if (ta.isSupported(DAY_OF_MONTH)) {
        return new CSLDateBuilder().dateParts(ta.get(YEAR), ta.get(MONTH_OF_YEAR), ta.get(DAY_OF_MONTH)).build();
      }
      return new CSLDateBuilder().dateParts(ta.get(YEAR), ta.get(MONTH_OF_YEAR)).build();
    }
    return new CSLDateBuilder().dateParts(ta.get(YEAR)).build();
  }
}
