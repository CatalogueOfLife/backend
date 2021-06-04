package life.catalogue.api.model;

import de.undercouch.citeproc.bibtex.PageParser;
import de.undercouch.citeproc.bibtex.PageRange;
import de.undercouch.citeproc.csl.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Citation {
  // identifier for the reference
  private String id;
  // the BibTeX Entry type, ie. article, book, inbook or misc which is the default if none is given
  private CSLType type;
  // DOI for the reference. Can be in addition to another identifier used as the ID above
  private String doi;
  // The name(s) of the author(s) as Person objects with familyName & givenName (see above)
  private List<Agent> author;
  // The name(s) of the editor(s) as Person objects
  private List<Agent> editor;
  // The title of the work
  private String title;
  // The title of the book, if only part of it is being cited
  private String booktitle;
  // The journal or magazine the work was published in
  private String journal;
  // The year of publication (or, if unpublished, the year of creation)
  private String year;
  // The month of publication (or, if unpublished, the month of creation) using integers from 1-12
  private String month;
  // The series of books the book was published in
  private String series;
  // The volume of a journal or multi-volume book
  private String volume;
  // The "(issue) number" of a journal or magazine if applicable. Note that this is not the "article number" assigned by some journals`.
  private String number;
  // The edition of a book, long form (such as "First" or "Second")
  private String edition;
  // The chapter number
  private String chapter;
  // Page numbers, separated either by commas or double-hyphens.
  private String pages;
  // The publisher's name
  private Agent publisher;

  // dataset version
  private String version;
  // ISBN number (books)
  private String isbn;
  // ISSN serial number
  private String issn;
  private String url;

  public CSLItemData toCSL() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .type(type)
      .DOI(doi)
      .volume(volume)
      .number(number)
      .issue(number)
      .edition(edition)
      .version(version)
      .ISBN(isbn)
      .ISSN(issn)
      .URL(url);

    // publisher
    if (publisher != null) {
      builder
        .publisher(publisher.getName())
        .publisherPlace(publisher.getAddress());
    }
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

  static CSLName[] toNames(List<Agent> agents) {
    if (agents == null || agents.isEmpty()) return null;
    return agents.stream()
          .map(Agent::toCSL)
          .collect(Collectors.toList())
          .toArray(CSLName[]::new);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public CSLType getType() {
    return type;
  }

  public void setType(CSLType type) {
    this.type = type;
  }

  public String getDoi() {
    return doi;
  }

  public void setDoi(String doi) {
    this.doi = doi;
  }

  public List<Agent> getAuthor() {
    return author;
  }

  public void setAuthor(List<Agent> author) {
    this.author = author;
  }

  public List<Agent> getEditor() {
    return editor;
  }

  public void setEditor(List<Agent> editor) {
    this.editor = editor;
  }

  public void setPublisher(Agent publisher) {
    this.publisher = publisher;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBooktitle() {
    return booktitle;
  }

  public void setBooktitle(String booktitle) {
    this.booktitle = booktitle;
  }

  public String getJournal() {
    return journal;
  }

  public void setJournal(String journal) {
    this.journal = journal;
  }

  public String getYear() {
    return year;
  }

  public void setYear(String year) {
    this.year = year;
  }

  public String getMonth() {
    return month;
  }

  public void setMonth(String month) {
    this.month = month;
  }

  public String getSeries() {
    return series;
  }

  public void setSeries(String series) {
    this.series = series;
  }

  public String getVolume() {
    return volume;
  }

  public void setVolume(String volume) {
    this.volume = volume;
  }

  public String getNumber() {
    return number;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  public String getEdition() {
    return edition;
  }

  public void setEdition(String edition) {
    this.edition = edition;
  }

  public String getChapter() {
    return chapter;
  }

  public void setChapter(String chapter) {
    this.chapter = chapter;
  }

  public String getPages() {
    return pages;
  }

  public void setPages(String pages) {
    this.pages = pages;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getIsbn() {
    return isbn;
  }

  public void setIsbn(String isbn) {
    this.isbn = isbn;
  }

  public String getIssn() {
    return issn;
  }

  public void setIssn(String issn) {
    this.issn = issn;
  }

  public Agent getPublisher() {
    return publisher;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Citation)) return false;
    Citation citation = (Citation) o;
    return Objects.equals(id, citation.id)
           && type == citation.type
           && Objects.equals(doi, citation.doi)
           && Objects.equals(author, citation.author)
           && Objects.equals(editor, citation.editor)
           && Objects.equals(title, citation.title)
           && Objects.equals(booktitle, citation.booktitle)
           && Objects.equals(journal, citation.journal)
           && Objects.equals(year, citation.year)
           && Objects.equals(month, citation.month)
           && Objects.equals(series, citation.series)
           && Objects.equals(volume, citation.volume)
           && Objects.equals(number, citation.number)
           && Objects.equals(edition, citation.edition)
           && Objects.equals(chapter, citation.chapter)
           && Objects.equals(pages, citation.pages)
           && Objects.equals(publisher, citation.publisher)
           && Objects.equals(version, citation.version)
           && Objects.equals(isbn, citation.isbn)
           && Objects.equals(issn, citation.issn)
           && Objects.equals(url, citation.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, doi, author, editor, title, booktitle, journal, year, month, series, volume, number, edition, chapter, pages, publisher, version, isbn, issn, url);
  }
}
