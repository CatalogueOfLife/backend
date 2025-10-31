package life.catalogue.api.model;

import de.undercouch.citeproc.bibtex.PageRanges;

import life.catalogue.api.jackson.FuzzyDateCSLSerde;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.undercouch.citeproc.bibtex.PageParser;
import de.undercouch.citeproc.bibtex.PageRange;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLType;

/**
 * Class with core CSL fields to represent common citations.
 * Jackson serialisation is setup to generate proper CSL, incl in array dates.
 */
public class Citation {
  private String id;
  private CSLType type;
  @JsonAlias("doi")
  @JsonProperty("DOI")
  private DOI doi;
  private List<CslName> author;
  private List<CslName> editor;
  // The title of the work
  private String title;
  // author(s) of the container holding the item (e.g. the book author for a book chapter)
  @JsonAlias("containerAuthor")
  @JsonProperty("container-author")
  private List<CslName> containerAuthor;
  // title of the container holding the item (e.g. the book title for a book chapter, the journal title for a journal article)
  @JsonAlias("containerTitle")
  @JsonProperty("container-title")
  private String containerTitle;
  // date the item was issued/published
  @JsonSerialize(using = FuzzyDateCSLSerde.Serializer.class)
  @JsonDeserialize(using = FuzzyDateCSLSerde.Deserializer.class)
  private FuzzyDate issued;
  // date the item has been accessed
  @JsonSerialize(using = FuzzyDateCSLSerde.Serializer.class)
  @JsonDeserialize(using = FuzzyDateCSLSerde.Deserializer.class)
  private FuzzyDate accessed;
  // title of the collection holding the item (e.g. the series title for a book)
  @JsonAlias("collectionTitle")
  @JsonProperty("collection-title")
  private String collectionTitle;
  // editor of the collection holding the item (e.g. the series editor for a book)
  @JsonAlias("collectionEditor")
  @JsonProperty("collection-editor")
  private List<CslName> collectionEditor;
  // (container) volume holding the item (e.g. “2” when citing a chapter from book volume 2)
  private String volume;
  // (container) issue holding the item (e.g. “5” when citing a journal article from journal volume 2, issue 5)
  private String issue;
  // (container) edition holding the item (e.g. “3” when citing a chapter in the third edition of a book)
  private String edition;
  // range of pages the item (e.g. a journal article) covers in a container (e.g. a journal issue)
  @JsonAlias("pages")
  private String page;
  // The publisher's name
  private String publisher;
  // The publisher's name
  @JsonAlias("publisherPlace")
  @JsonProperty("publisher-place")
  private String publisherPlace;
  // dataset version
  private String version;
  // ISBN number (books)
  @JsonAlias("isbn")
  @JsonProperty("ISBN")
  private String isbn;
  // ISSN serial number
  @JsonAlias("issn")
  @JsonProperty("ISSN")
  private String issn;
  // link to webpage for electronic resources
  @JsonAlias("url")
  @JsonProperty("URL")
  private String url;
  private String note;
  private String _citation; // cache field
  private String _citationText; // cache field

  public static Citation create(String citation) {
    Citation c = new Citation();
    c.setTitle(citation);
    c.setType(CSLType.BOOK);
    return c;
  }

  public static Citation create(String citation, String identifier) {
    if (StringUtils.isBlank(citation)) return null;
    Citation c = create(citation.trim());
    if (!StringUtils.isBlank(identifier)) {
      var opt = DOI.parse(identifier);
      if (opt.isPresent()) {
        c.setDoi(opt.get());
        c.setId(c.getDoi().getDoiName());
      } else {
        try {
          URI link = URI.create(identifier);
          c.setUrl(link.toString());
        } catch (IllegalArgumentException e) {
          c.setNote(identifier);
        }
      }
    }
    return c;
  }

  public Citation() {
  }

  public Citation(Citation other) {
    this.id = other.id;
    this.type = other.type;
    this.doi = other.doi;
    this.author = other.author;
    this.editor = other.editor;
    this.title = other.title;
    this.containerAuthor = other.containerAuthor;
    this.containerTitle = other.containerTitle;
    this.issued = other.issued;
    this.accessed = other.accessed;
    this.collectionTitle = other.collectionTitle;
    this.collectionEditor = other.collectionEditor;
    this.volume = other.volume;
    this.issue = other.issue;
    this.edition = other.edition;
    this.page = other.page;
    this.publisher = other.publisher;
    this.publisherPlace = other.publisherPlace;
    this.version = other.version;
    this.isbn = other.isbn;
    this.issn = other.issn;
    this.url = other.url;
    this.note = other.note;
    this._citation = other._citation;
    this._citationText = other._citationText;
  }

  @JsonIgnore
  public boolean isUnparsed() {
    return (type==null || type==CSLType.BOOK)
           // && only title doesn't matter
                && (author == null || author.isEmpty())
                && (editor == null || editor.isEmpty())
                && (containerAuthor == null || containerAuthor.isEmpty())
                && containerTitle == null
                && issued == null
                && accessed == null
                && collectionTitle == null
                && collectionEditor == null
                && volume == null
                && issue == null
                && edition == null
                && page == null
                && publisher == null
                && publisherPlace == null
                && version == null
                && isbn == null
                && issn == null
                && url == null
                && note == null;
  }

  public CSLItemData toCSL() {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .type(type)
      .title(title)
      .volume(volume)
      .issue(issue)
      .edition(edition)
      .publisher(publisher)
      .publisherPlace(publisherPlace)
      .containerTitle(containerTitle)
      .collectionTitle(collectionTitle)
      .version(version)
      .ISBN(isbn)
      .ISSN(issn)
      .URL(url);

    // DOI
    if (doi != null) {
      builder.DOI(doi.toString());
    }
    // names
    if (author != null) {
      builder.author(toNames(author));
    }
    if (editor != null) {
      builder.editor(toNames(editor));
    }
    if (containerAuthor != null) {
      builder.containerAuthor(toNames(containerAuthor));
    }
    if (collectionEditor != null) {
      builder.collectionEditor(toNames(collectionEditor));
    }

    // dates
    if (issued != null) {
      builder.issued(issued.toCSLDate());
    }
    if (accessed != null) {
      builder.accessed(accessed.toCSLDate());
    }

    // pages
    if (page != null) {
      PageRanges pr = PageParser.parse(page);
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
          .map(CslName::toCSL)
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

  public DOI getDoi() {
    return doi;
  }

  public void setDoi(DOI doi) {
    this.doi = doi;
  }

  public List<CslName> getAuthor() {
    return author;
  }

  public void setAuthor(List<CslName> author) {
    this.author = author;
  }

  public List<CslName> getEditor() {
    return editor;
  }

  public void setEditor(List<CslName> editor) {
    this.editor = editor;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<CslName> getContainerAuthor() {
    return containerAuthor;
  }

  public void setContainerAuthor(List<CslName> containerAuthor) {
    this.containerAuthor = containerAuthor;
  }

  public String getContainerTitle() {
    return containerTitle;
  }

  public void setContainerTitle(String containerTitle) {
    this.containerTitle = containerTitle;
  }

  public FuzzyDate getIssued() {
    return issued;
  }

  public void setIssued(FuzzyDate issued) {
    this.issued = issued;
  }

  public FuzzyDate getAccessed() {
    return accessed;
  }

  public void setAccessed(FuzzyDate accessed) {
    this.accessed = accessed;
  }

  public String getCollectionTitle() {
    return collectionTitle;
  }

  public void setCollectionTitle(String collectionTitle) {
    this.collectionTitle = collectionTitle;
  }

  public List<CslName> getCollectionEditor() {
    return collectionEditor;
  }

  public void setCollectionEditor(List<CslName> collectionEditor) {
    this.collectionEditor = collectionEditor;
  }

  public String getVolume() {
    return volume;
  }

  public void setVolume(String volume) {
    this.volume = volume;
  }

  public String getIssue() {
    return issue;
  }

  public void setIssue(String issue) {
    this.issue = issue;
  }

  public String getEdition() {
    return edition;
  }

  public void setEdition(String edition) {
    this.edition = edition;
  }

  public String getPage() {
    return page;
  }

  public void setPage(String page) {
    this.page = page;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public String getPublisherPlace() {
    return publisherPlace;
  }

  public void setPublisherPlace(String publisherPlace) {
    this.publisherPlace = publisherPlace;
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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }


  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getCitation() {
    if (_citation == null) {
      _citation = CslUtil.buildCitationHtml(toCSL());
    }
    return _citation;
  }

  @JsonIgnore
  public String getCitationText() {
    if (_citationText == null) {
      _citationText = CslUtil.buildCitation(toCSL());
    }
    return _citationText;
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
           && Objects.equals(containerAuthor, citation.containerAuthor)
           && Objects.equals(containerTitle, citation.containerTitle)
           && Objects.equals(issued, citation.issued)
           && Objects.equals(accessed, citation.accessed)
           && Objects.equals(collectionTitle, citation.collectionTitle)
           && Objects.equals(collectionEditor, citation.collectionEditor)
           && Objects.equals(volume, citation.volume)
           && Objects.equals(issue, citation.issue)
           && Objects.equals(edition, citation.edition)
           && Objects.equals(page, citation.page)
           && Objects.equals(publisher, citation.publisher)
           && Objects.equals(publisherPlace, citation.publisherPlace)
           && Objects.equals(version, citation.version)
           && Objects.equals(isbn, citation.isbn)
           && Objects.equals(issn, citation.issn)
           && Objects.equals(url, citation.url)
           && Objects.equals(note, citation.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, doi, author, editor, title, containerAuthor, containerTitle, issued, accessed, collectionTitle, collectionEditor, volume, issue, edition, page, publisher, publisherPlace, version, isbn, issn, url, note);
  }
}
