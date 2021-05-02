package life.catalogue.common.csl;

import de.undercouch.citeproc.bibtex.PageParser;
import de.undercouch.citeproc.bibtex.PageRange;
import de.undercouch.citeproc.csl.*;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.util.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DatasetNG {
  public String doi;
  public String alias;
  public String title;
  public FuzzyDate issued;
  public String version;
  public String issn;
  public Agent contact;
  public List<Agent> creator;
  public List<Agent> editor;
  public Agent publisher;
  public Agent distributor;
  public List<Agent> contributor;
  public String license;
  public String website;
  public List<Citation> source;

  public static class Agent {
    public String orcid;
    public String familyName;
    public String givenName;
    // organisation
    public String grid;
    public String rorid;
    public String organisation;
    public String department;
    public String city;
    public String state;
    public String country;
    // shared
    public String email;
    public String url;
    public String note;

    public boolean isPerson(){
      return familyName != null || givenName != null;
    }

    public boolean isOrganisation(){
      return !isPerson() && organisation != null;
    }
  }

  public static class Citation {
    public String id;
    public CSLType type;
    public String doi;
    public List<Agent> author;
    public List<Agent> editor;
    public String title;
    public String booktitle;
    public String journal;
    public String year;
    public String month;
    public String series;
    public String volume;
    public String number;
    public String edition;
    public String chapter;
    public String pages;
    public String publisher;
    public String address;
    public String version;
    public String ISBN;
    public String ISSN;
    public String url;
    public String note;

    public CSLItemData toCSL() {
      CSLItemDataBuilder builder = new CSLItemDataBuilder();
      builder
        .type(type)
        .DOI(doi)
        .volume(volume)
        .number(number)
        .issue(number)
        .edition(edition)
        .publisher(publisher)
        .publisherPlace(address)
        .version(version)
        .ISBN(ISBN)
        .ISSN(ISSN)
        .URL(url)
        .note(note);

      // title or chapter
      if (title != null) {
        builder.title(title);
      } else if (chapter != null) {
        builder.title(chapter);
      }

      // issued
      if (year != null) {
        try {
          int y = Integer.parseInt(year.trim());
          if (month != null) {
            try {
              int m = Integer.parseInt(month.trim());
              builder.issued(y, m);
            } catch (NumberFormatException e) {
              builder.issued(y);
            }
          } else {
            builder.issued(y);
          }
        } catch (NumberFormatException e) {
          // nothing
        }
      }

      // pages
      if (pages != null) {
        PageRange pr = PageParser.parse(pages);
        builder.page(pr.getLiteral());
        builder.pageFirst(pr.getPageFirst());
        if (pr.getNumberOfPages() != null) {
          builder.numberOfPages(String.valueOf(pr.getNumberOfPages()));
        }
      }

      // map journal/journaltitle, booktitle, series
      if (journal != null) {
        builder
          .containerTitle(journal)
          .collectionTitle(journal);

      } else if (booktitle != null) {
        builder
          .containerTitle(booktitle)
          .collectionTitle(booktitle);
      } else if (series != null) {
        builder
          .containerTitle(series)
          .collectionTitle(series);
      }

      if (author != null) {
        builder.author(toNames(author));
      }
      if (editor != null) {
        builder.editor(toNames(editor));
      }
      // TODO: series
      return builder.build();
    }
  }

  public CSLItemData toCSL() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .DOI(doi)
      .shortTitle(alias)
      .title(title)
      .version(version)
      .ISSN(issn)
      .URL(website);
    if (issued != null) {
      builder.issued(issued.toCSLDate());
    }
    if (creator != null) {
      builder.author(toNames(creator));
    }
    if (editor != null) {
      builder.editor(toNames(editor));
    }
    if (publisher != null) {
      builder.publisher(publisher.organisation);
      StringBuilder location = new StringBuilder();
      if (publisher.city != null) {
        location.append(publisher.city);
      }
      if (publisher.country != null) {
        if (location.length()>0) {
          location.append(", ");
        }
        location.append(publisher.country);
      }
      builder.publisherPlace(location.toString());
    }
    // no license, distributor, contributor
    return builder.build();
  }

  private static CSLName[] toNames(List<Agent> agents) {
    List<CSLName> names = new ArrayList<>();
    for (int i = 0; i < agents.size(); i++) {
      Agent a = agents.get(i);
      CSLNameBuilder builder = new CSLNameBuilder();
      if (a.isPerson()) {
        builder
          .given(a.givenName)
          .family(a.familyName)
          .isInstitution(false);
      } else if (a.isOrganisation()) {
        builder
          .family(a.organisation)
          .isInstitution(true);
      }
      names.add(builder.build());
    }
    return names.toArray(CSLName[]::new);
  }

  public static DatasetNG read(InputStream in){
    try {
      return YamlUtils.read(DatasetNG.class, in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
