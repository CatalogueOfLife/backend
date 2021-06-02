package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

public class Citation {
  // identifier for the reference
  private String id;
  // the BibTeX Entry type, ie. article, book, inbook or misc which is the default if none is given
  private String type;
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
  private String ISBN;
  // ISSN serial number
  private String ISSN;


  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
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

  public String getISBN() {
    return ISBN;
  }

  public void setISBN(String ISBN) {
    this.ISBN = ISBN;
  }

  public String getISSN() {
    return ISSN;
  }

  public void setISSN(String ISSN) {
    this.ISSN = ISSN;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Citation)) return false;
    Citation citation = (Citation) o;
    return Objects.equals(id, citation.id) && Objects.equals(type, citation.type) && Objects.equals(doi, citation.doi) && Objects.equals(author, citation.author) && Objects.equals(editor, citation.editor) && Objects.equals(title, citation.title) && Objects.equals(booktitle, citation.booktitle) && Objects.equals(journal, citation.journal) && Objects.equals(year, citation.year) && Objects.equals(month, citation.month) && Objects.equals(series, citation.series) && Objects.equals(volume, citation.volume) && Objects.equals(number, citation.number) && Objects.equals(edition, citation.edition) && Objects.equals(chapter, citation.chapter) && Objects.equals(pages, citation.pages) && Objects.equals(publisher, citation.publisher) && Objects.equals(version, citation.version) && Objects.equals(ISBN, citation.ISBN) && Objects.equals(ISSN, citation.ISSN);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, doi, author, editor, title, booktitle, journal, year, month, series, volume, number, edition, chapter, pages, publisher, version, ISBN, ISSN);
  }
}
