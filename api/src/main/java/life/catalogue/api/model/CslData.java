package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.vocab.CSLRefType;

import java.util.Arrays;

/**
 * Official CSL variables as defined in
 * Appendix IV - Variables
 * http://docs.citationstyles.org/en/stable/specification.html#appendix-iv-variables
 */
public class CslData {
  
  private String id;
  private CSLRefType type;
  private String[] categories;
  private String language;
  private String journalAbbreviation;
  private CslName[] author;
  @JsonProperty("collection-editor")
  private CslName[] collectionEditor;
  private CslName[] composer;
  @JsonProperty("container-author")
  private CslName[] containerAuthor;
  private CslName[] director;
  private CslName[] editor;
  @JsonProperty("editorial-director")
  private CslName[] editorialDirector;
  private CslName[] interviewer;
  private CslName[] illustrator;
  @JsonProperty("original-author")
  private CslName[] originalAuthor;
  private CslName[] recipient;
  @JsonProperty("reviewed-author")
  private CslName[] reviewedAuthor;
  private CslName[] translator;
  private CslDate accessed;
  private CslDate container;
  @JsonProperty("event-date")
  private CslDate eventDate;
  private CslDate issued;
  @JsonProperty("original-date")
  private CslDate originalDate;
  private CslDate submitted;
  @JsonProperty("abstract")
  private String abstrct;
  private String annote;
  private String archive;
  @JsonProperty("arhive_location")
  private String archiveLocation;
  @JsonProperty("archive-place")
  private String archivePlace;
  private String authority;
  @JsonProperty("call-number")
  private String callNumber;
  @JsonProperty("chapter-number")
  private String chapterNumber;
  @JsonProperty("citation-number")
  private String citationNumber;
  @JsonProperty("citation-label")
  private String citationLabel;
  @JsonProperty("collection-number")
  private String collectionNumber;
  @JsonProperty("collection-title")
  private String collectionTitle;
  @JsonProperty("container-title")
  private String containerTitle;
  @JsonProperty("container-title-short")
  private String containerTitleShort;
  private String dimensions;
  private String DOI;
  private String edition;
  private String event;
  @JsonProperty("event-place")
  private String eventPlace;
  @JsonProperty("first-reference-note-number")
  private String firstReferenceNoteNumber;
  private String genre;
  private String ISBN;
  private String ISSN;
  private String issue;
  private String jurisdiction;
  private String keyword;
  private String locator;
  private String medium;
  private String note;
  private String number;
  @JsonProperty("number-of-pages")
  private String numberOfPages;
  @JsonProperty("number-of-volumes")
  private String numberOfVolumes;
  @JsonProperty("original-publisher")
  private String originalPublisher;
  @JsonProperty("original-publisher-place")
  private String originalPublisherPlace;
  @JsonProperty("original-title")
  private String originalTitle;
  private String page;
  @JsonProperty("page-first")
  private String pageFirst;
  private String PMCID;
  private String PMID;
  private String publisher;
  @JsonProperty("publisher-place")
  private String publisherPlace;
  private String references;
  @JsonProperty("reviewed-title")
  private String reviewedTitle;
  private String scale;
  private String section;
  private String source;
  private String status;
  private String title;
  @JsonProperty("title-short")
  private String titleShort;
  private String URL;
  private String version;
  private String volume;
  @JsonProperty("year-suffix")
  private String yearSuffix;
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public CSLRefType getType() {
    return type;
  }
  
  public void setType(CSLRefType type) {
    this.type = type;
  }
  
  public String[] getCategories() {
    return categories;
  }
  
  public void setCategories(String[] categories) {
    this.categories = categories;
  }
  
  public String getLanguage() {
    return language;
  }
  
  public void setLanguage(String language) {
    this.language = language;
  }
  
  public String getJournalAbbreviation() {
    return journalAbbreviation;
  }
  
  public void setJournalAbbreviation(String journalAbbreviation) {
    this.journalAbbreviation = journalAbbreviation;
  }
  
  public CslName[] getAuthor() {
    return author;
  }
  
  public void setAuthor(CslName[] author) {
    this.author = author;
  }
  
  public CslName[] getCollectionEditor() {
    return collectionEditor;
  }
  
  public void setCollectionEditor(CslName[] collectionEditor) {
    this.collectionEditor = collectionEditor;
  }
  
  public CslName[] getComposer() {
    return composer;
  }
  
  public void setComposer(CslName[] composer) {
    this.composer = composer;
  }
  
  public CslName[] getContainerAuthor() {
    return containerAuthor;
  }
  
  public void setContainerAuthor(CslName[] containerAuthor) {
    this.containerAuthor = containerAuthor;
  }
  
  public CslName[] getDirector() {
    return director;
  }
  
  public void setDirector(CslName[] director) {
    this.director = director;
  }
  
  public CslName[] getEditor() {
    return editor;
  }
  
  public void setEditor(CslName[] editor) {
    this.editor = editor;
  }
  
  public CslName[] getEditorialDirector() {
    return editorialDirector;
  }
  
  public void setEditorialDirector(CslName[] editorialDirector) {
    this.editorialDirector = editorialDirector;
  }
  
  public CslName[] getInterviewer() {
    return interviewer;
  }
  
  public void setInterviewer(CslName[] interviewer) {
    this.interviewer = interviewer;
  }
  
  public CslName[] getIllustrator() {
    return illustrator;
  }
  
  public void setIllustrator(CslName[] illustrator) {
    this.illustrator = illustrator;
  }
  
  public CslName[] getOriginalAuthor() {
    return originalAuthor;
  }
  
  public void setOriginalAuthor(CslName[] originalAuthor) {
    this.originalAuthor = originalAuthor;
  }
  
  public CslName[] getRecipient() {
    return recipient;
  }
  
  public void setRecipient(CslName[] recipient) {
    this.recipient = recipient;
  }
  
  public CslName[] getReviewedAuthor() {
    return reviewedAuthor;
  }
  
  public void setReviewedAuthor(CslName[] reviewedAuthor) {
    this.reviewedAuthor = reviewedAuthor;
  }
  
  public CslName[] getTranslator() {
    return translator;
  }
  
  public void setTranslator(CslName[] translator) {
    this.translator = translator;
  }
  
  public CslDate getAccessed() {
    return accessed;
  }
  
  public void setAccessed(CslDate accessed) {
    this.accessed = accessed;
  }
  
  public CslDate getContainer() {
    return container;
  }
  
  public void setContainer(CslDate container) {
    this.container = container;
  }
  
  public CslDate getEventDate() {
    return eventDate;
  }
  
  public void setEventDate(CslDate eventDate) {
    this.eventDate = eventDate;
  }
  
  public CslDate getIssued() {
    return issued;
  }
  
  public void setIssued(CslDate issued) {
    this.issued = issued;
  }
  
  public CslDate getOriginalDate() {
    return originalDate;
  }
  
  public void setOriginalDate(CslDate originalDate) {
    this.originalDate = originalDate;
  }
  
  public CslDate getSubmitted() {
    return submitted;
  }
  
  public void setSubmitted(CslDate submitted) {
    this.submitted = submitted;
  }
  
  public String getAbstrct() {
    return abstrct;
  }
  
  public void setAbstrct(String abstrct) {
    this.abstrct = abstrct;
  }
  
  public String getAnnote() {
    return annote;
  }
  
  public void setAnnote(String annote) {
    this.annote = annote;
  }
  
  public String getArchive() {
    return archive;
  }
  
  public void setArchive(String archive) {
    this.archive = archive;
  }
  
  public String getArchiveLocation() {
    return archiveLocation;
  }
  
  public void setArchiveLocation(String archiveLocation) {
    this.archiveLocation = archiveLocation;
  }
  
  public String getArchivePlace() {
    return archivePlace;
  }
  
  public void setArchivePlace(String archivePlace) {
    this.archivePlace = archivePlace;
  }
  
  public String getAuthority() {
    return authority;
  }
  
  public void setAuthority(String authority) {
    this.authority = authority;
  }
  
  public String getCallNumber() {
    return callNumber;
  }
  
  public void setCallNumber(String callNumber) {
    this.callNumber = callNumber;
  }
  
  public String getChapterNumber() {
    return chapterNumber;
  }
  
  public void setChapterNumber(String chapterNumber) {
    this.chapterNumber = chapterNumber;
  }
  
  public String getCitationNumber() {
    return citationNumber;
  }
  
  public void setCitationNumber(String citationNumber) {
    this.citationNumber = citationNumber;
  }
  
  public String getCitationLabel() {
    return citationLabel;
  }
  
  public void setCitationLabel(String citationLabel) {
    this.citationLabel = citationLabel;
  }
  
  public String getCollectionNumber() {
    return collectionNumber;
  }
  
  public void setCollectionNumber(String collectionNumber) {
    this.collectionNumber = collectionNumber;
  }
  
  public String getCollectionTitle() {
    return collectionTitle;
  }
  
  public void setCollectionTitle(String collectionTitle) {
    this.collectionTitle = collectionTitle;
  }
  
  public String getContainerTitle() {
    return containerTitle;
  }
  
  public void setContainerTitle(String containerTitle) {
    this.containerTitle = containerTitle;
  }
  
  public String getContainerTitleShort() {
    return containerTitleShort;
  }
  
  public void setContainerTitleShort(String containerTitleShort) {
    this.containerTitleShort = containerTitleShort;
  }
  
  /**
   * Alternative setter for short container title which is also in use by some sources
   */
  public void setShortContainerTitle(String containerTitleShort) {
    this.containerTitleShort = containerTitleShort;
  }
  
  public String getDimensions() {
    return dimensions;
  }
  
  public void setDimensions(String dimensions) {
    this.dimensions = dimensions;
  }
  
  public String getDOI() {
    return DOI;
  }
  
  public void setDOI(String dOI) {
    DOI = dOI;
  }
  
  public String getEdition() {
    return edition;
  }
  
  public void setEdition(String edition) {
    this.edition = edition;
  }
  
  public String getEvent() {
    return event;
  }
  
  public void setEvent(String event) {
    this.event = event;
  }
  
  public String getEventPlace() {
    return eventPlace;
  }
  
  public void setEventPlace(String eventPlace) {
    this.eventPlace = eventPlace;
  }
  
  public String getFirstReferenceNoteNumber() {
    return firstReferenceNoteNumber;
  }
  
  public void setFirstReferenceNoteNumber(String firstReferenceNoteNumber) {
    this.firstReferenceNoteNumber = firstReferenceNoteNumber;
  }
  
  public String getGenre() {
    return genre;
  }
  
  public void setGenre(String genre) {
    this.genre = genre;
  }
  
  public String getISBN() {
    return ISBN;
  }
  
  public void setISBN(String iSBN) {
    ISBN = iSBN;
  }
  
  public String getISSN() {
    return ISSN;
  }
  
  public void setISSN(String iSSN) {
    ISSN = iSSN;
  }
  
  public String getIssue() {
    return issue;
  }
  
  public void setIssue(String issue) {
    this.issue = issue;
  }
  
  public String getJurisdiction() {
    return jurisdiction;
  }
  
  public void setJurisdiction(String jurisdiction) {
    this.jurisdiction = jurisdiction;
  }
  
  public String getKeyword() {
    return keyword;
  }
  
  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }
  
  public String getLocator() {
    return locator;
  }
  
  public void setLocator(String locator) {
    this.locator = locator;
  }
  
  public String getMedium() {
    return medium;
  }
  
  public void setMedium(String medium) {
    this.medium = medium;
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }
  
  public String getNumber() {
    return number;
  }
  
  public void setNumber(String number) {
    this.number = number;
  }
  
  public String getNumberOfPages() {
    return numberOfPages;
  }
  
  public void setNumberOfPages(String numberOfPages) {
    this.numberOfPages = numberOfPages;
  }
  
  public String getNumberOfVolumes() {
    return numberOfVolumes;
  }
  
  public void setNumberOfVolumes(String numberOfVolumes) {
    this.numberOfVolumes = numberOfVolumes;
  }
  
  public String getOriginalPublisher() {
    return originalPublisher;
  }
  
  public void setOriginalPublisher(String originalPublisher) {
    this.originalPublisher = originalPublisher;
  }
  
  public String getOriginalPublisherPlace() {
    return originalPublisherPlace;
  }
  
  public void setOriginalPublisherPlace(String originalPublisherPlace) {
    this.originalPublisherPlace = originalPublisherPlace;
  }
  
  public String getOriginalTitle() {
    return originalTitle;
  }
  
  public void setOriginalTitle(String originalTitle) {
    this.originalTitle = originalTitle;
  }
  
  public String getPage() {
    return page;
  }
  
  public void setPage(String page) {
    this.page = page;
  }
  
  public String getPageFirst() {
    return pageFirst;
  }
  
  public void setPageFirst(String pageFirst) {
    this.pageFirst = pageFirst;
  }
  
  public String getPMCID() {
    return PMCID;
  }
  
  public void setPMCID(String pMCID) {
    PMCID = pMCID;
  }
  
  public String getPMID() {
    return PMID;
  }
  
  public void setPMID(String pMID) {
    PMID = pMID;
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
  
  public String getReferences() {
    return references;
  }
  
  public void setReferences(String references) {
    this.references = references;
  }
  
  public String getReviewedTitle() {
    return reviewedTitle;
  }
  
  public void setReviewedTitle(String reviewedTitle) {
    this.reviewedTitle = reviewedTitle;
  }
  
  public String getScale() {
    return scale;
  }
  
  public void setScale(String scale) {
    this.scale = scale;
  }
  
  public String getSection() {
    return section;
  }
  
  public void setSection(String section) {
    this.section = section;
  }
  
  public String getSource() {
    return source;
  }
  
  public void setSource(String source) {
    this.source = source;
  }
  
  public String getStatus() {
    return status;
  }
  
  public void setStatus(String status) {
    this.status = status;
  }
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }
  
  public String getTitleShort() {
    return titleShort;
  }
  
  public void setTitleShort(String titleShort) {
    this.titleShort = titleShort;
  }
  
  /**
   * Alternative setter for short title which is also in use by some sources
   */
  public void setShortTitle(String titleShort) {
    this.titleShort = titleShort;
  }

  public String getURL() {
    return URL;
  }
  
  public void setURL(String uRL) {
    URL = uRL;
  }
  
  public String getVersion() {
    return version;
  }
  
  public void setVersion(String version) {
    this.version = version;
  }
  
  public String getVolume() {
    return volume;
  }
  
  public void setVolume(String volume) {
    this.volume = volume;
  }
  
  public String getYearSuffix() {
    return yearSuffix;
  }
  
  public void setYearSuffix(String yearSuffix) {
    this.yearSuffix = yearSuffix;
  }
  
  @Override
  public int hashCode() {
    int result = 1;
    
    result = 31 * result + ((id == null) ? 0 : id.hashCode());
    result = 31 * result + ((type == null) ? 0 : type.hashCode());
    result = 31 * result + Arrays.hashCode(categories);
    result = 31 * result + ((language == null) ? 0 : language.hashCode());
    result = 31 * result + ((journalAbbreviation == null) ? 0 : journalAbbreviation.hashCode());
    result = 31 * result + Arrays.hashCode(author);
    result = 31 * result + Arrays.hashCode(collectionEditor);
    result = 31 * result + Arrays.hashCode(composer);
    result = 31 * result + Arrays.hashCode(containerAuthor);
    result = 31 * result + Arrays.hashCode(director);
    result = 31 * result + Arrays.hashCode(editor);
    result = 31 * result + Arrays.hashCode(editorialDirector);
    result = 31 * result + Arrays.hashCode(interviewer);
    result = 31 * result + Arrays.hashCode(illustrator);
    result = 31 * result + Arrays.hashCode(originalAuthor);
    result = 31 * result + Arrays.hashCode(recipient);
    result = 31 * result + Arrays.hashCode(reviewedAuthor);
    result = 31 * result + Arrays.hashCode(translator);
    result = 31 * result + ((accessed == null) ? 0 : accessed.hashCode());
    result = 31 * result + ((container == null) ? 0 : container.hashCode());
    result = 31 * result + ((eventDate == null) ? 0 : eventDate.hashCode());
    result = 31 * result + ((issued == null) ? 0 : issued.hashCode());
    result = 31 * result + ((originalDate == null) ? 0 : originalDate.hashCode());
    result = 31 * result + ((submitted == null) ? 0 : submitted.hashCode());
    result = 31 * result + ((abstrct == null) ? 0 : abstrct.hashCode());
    result = 31 * result + ((annote == null) ? 0 : annote.hashCode());
    result = 31 * result + ((archive == null) ? 0 : archive.hashCode());
    result = 31 * result + ((archiveLocation == null) ? 0 : archiveLocation.hashCode());
    result = 31 * result + ((archivePlace == null) ? 0 : archivePlace.hashCode());
    result = 31 * result + ((authority == null) ? 0 : authority.hashCode());
    result = 31 * result + ((callNumber == null) ? 0 : callNumber.hashCode());
    result = 31 * result + ((chapterNumber == null) ? 0 : chapterNumber.hashCode());
    result = 31 * result + ((citationNumber == null) ? 0 : citationNumber.hashCode());
    result = 31 * result + ((citationLabel == null) ? 0 : citationLabel.hashCode());
    result = 31 * result + ((collectionNumber == null) ? 0 : collectionNumber.hashCode());
    result = 31 * result + ((collectionTitle == null) ? 0 : collectionTitle.hashCode());
    result = 31 * result + ((containerTitle == null) ? 0 : containerTitle.hashCode());
    result = 31 * result + ((containerTitleShort == null) ? 0 : containerTitleShort.hashCode());
    result = 31 * result + ((dimensions == null) ? 0 : dimensions.hashCode());
    result = 31 * result + ((DOI == null) ? 0 : DOI.hashCode());
    result = 31 * result + ((edition == null) ? 0 : edition.hashCode());
    result = 31 * result + ((event == null) ? 0 : event.hashCode());
    result = 31 * result + ((eventPlace == null) ? 0 : eventPlace.hashCode());
    result = 31 * result
        + ((firstReferenceNoteNumber == null) ? 0 : firstReferenceNoteNumber.hashCode());
    result = 31 * result + ((genre == null) ? 0 : genre.hashCode());
    result = 31 * result + ((ISBN == null) ? 0 : ISBN.hashCode());
    result = 31 * result + ((ISSN == null) ? 0 : ISSN.hashCode());
    result = 31 * result + ((issue == null) ? 0 : issue.hashCode());
    result = 31 * result + ((jurisdiction == null) ? 0 : jurisdiction.hashCode());
    result = 31 * result + ((keyword == null) ? 0 : keyword.hashCode());
    result = 31 * result + ((locator == null) ? 0 : locator.hashCode());
    result = 31 * result + ((medium == null) ? 0 : medium.hashCode());
    result = 31 * result + ((note == null) ? 0 : note.hashCode());
    result = 31 * result + ((number == null) ? 0 : number.hashCode());
    result = 31 * result + ((numberOfPages == null) ? 0 : numberOfPages.hashCode());
    result = 31 * result + ((numberOfVolumes == null) ? 0 : numberOfVolumes.hashCode());
    result = 31 * result + ((originalPublisher == null) ? 0 : originalPublisher.hashCode());
    result =
        31 * result + ((originalPublisherPlace == null) ? 0 : originalPublisherPlace.hashCode());
    result = 31 * result + ((originalTitle == null) ? 0 : originalTitle.hashCode());
    result = 31 * result + ((page == null) ? 0 : page.hashCode());
    result = 31 * result + ((pageFirst == null) ? 0 : pageFirst.hashCode());
    result = 31 * result + ((PMCID == null) ? 0 : PMCID.hashCode());
    result = 31 * result + ((PMID == null) ? 0 : PMID.hashCode());
    result = 31 * result + ((publisher == null) ? 0 : publisher.hashCode());
    result = 31 * result + ((publisherPlace == null) ? 0 : publisherPlace.hashCode());
    result = 31 * result + ((references == null) ? 0 : references.hashCode());
    result = 31 * result + ((reviewedTitle == null) ? 0 : reviewedTitle.hashCode());
    result = 31 * result + ((scale == null) ? 0 : scale.hashCode());
    result = 31 * result + ((section == null) ? 0 : section.hashCode());
    result = 31 * result + ((source == null) ? 0 : source.hashCode());
    result = 31 * result + ((status == null) ? 0 : status.hashCode());
    result = 31 * result + ((title == null) ? 0 : title.hashCode());
    result = 31 * result + ((titleShort == null) ? 0 : titleShort.hashCode());
    result = 31 * result + ((URL == null) ? 0 : URL.hashCode());
    result = 31 * result + ((version == null) ? 0 : version.hashCode());
    result = 31 * result + ((volume == null) ? 0 : volume.hashCode());
    result = 31 * result + ((yearSuffix == null) ? 0 : yearSuffix.hashCode());
    
    return result;
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof CslData))
      return false;
    CslData other = (CslData) obj;
    
    if (id == null) {
      if (other.id != null)
        return false;
    } else if (!id.equals(other.id))
      return false;
    
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    
    if (!Arrays.equals(categories, other.categories))
      return false;
    
    if (language == null) {
      if (other.language != null)
        return false;
    } else if (!language.equals(other.language))
      return false;
    
    if (journalAbbreviation == null) {
      if (other.journalAbbreviation != null)
        return false;
    } else if (!journalAbbreviation.equals(other.journalAbbreviation))
      return false;
    
    if (!Arrays.equals(author, other.author))
      return false;
    
    if (!Arrays.equals(collectionEditor, other.collectionEditor))
      return false;
    
    if (!Arrays.equals(composer, other.composer))
      return false;
    
    if (!Arrays.equals(containerAuthor, other.containerAuthor))
      return false;
    
    if (!Arrays.equals(director, other.director))
      return false;
    
    if (!Arrays.equals(editor, other.editor))
      return false;
    
    if (!Arrays.equals(editorialDirector, other.editorialDirector))
      return false;
    
    if (!Arrays.equals(interviewer, other.interviewer))
      return false;
    
    if (!Arrays.equals(illustrator, other.illustrator))
      return false;
    
    if (!Arrays.equals(originalAuthor, other.originalAuthor))
      return false;
    
    if (!Arrays.equals(recipient, other.recipient))
      return false;
    
    if (!Arrays.equals(reviewedAuthor, other.reviewedAuthor))
      return false;
    
    if (!Arrays.equals(translator, other.translator))
      return false;
    
    if (accessed == null) {
      if (other.accessed != null)
        return false;
    } else if (!accessed.equals(other.accessed))
      return false;
    
    if (container == null) {
      if (other.container != null)
        return false;
    } else if (!container.equals(other.container))
      return false;
    
    if (eventDate == null) {
      if (other.eventDate != null)
        return false;
    } else if (!eventDate.equals(other.eventDate))
      return false;
    
    if (issued == null) {
      if (other.issued != null)
        return false;
    } else if (!issued.equals(other.issued))
      return false;
    
    if (originalDate == null) {
      if (other.originalDate != null)
        return false;
    } else if (!originalDate.equals(other.originalDate))
      return false;
    
    if (submitted == null) {
      if (other.submitted != null)
        return false;
    } else if (!submitted.equals(other.submitted))
      return false;
    
    if (abstrct == null) {
      if (other.abstrct != null)
        return false;
    } else if (!abstrct.equals(other.abstrct))
      return false;
    
    if (annote == null) {
      if (other.annote != null)
        return false;
    } else if (!annote.equals(other.annote))
      return false;
    
    if (archive == null) {
      if (other.archive != null)
        return false;
    } else if (!archive.equals(other.archive))
      return false;
    
    if (archiveLocation == null) {
      if (other.archiveLocation != null)
        return false;
    } else if (!archiveLocation.equals(other.archiveLocation))
      return false;
    
    if (archivePlace == null) {
      if (other.archivePlace != null)
        return false;
    } else if (!archivePlace.equals(other.archivePlace))
      return false;
    
    if (authority == null) {
      if (other.authority != null)
        return false;
    } else if (!authority.equals(other.authority))
      return false;
    
    if (callNumber == null) {
      if (other.callNumber != null)
        return false;
    } else if (!callNumber.equals(other.callNumber))
      return false;
    
    if (chapterNumber == null) {
      if (other.chapterNumber != null)
        return false;
    } else if (!chapterNumber.equals(other.chapterNumber))
      return false;
    
    if (citationNumber == null) {
      if (other.citationNumber != null)
        return false;
    } else if (!citationNumber.equals(other.citationNumber))
      return false;
    
    if (citationLabel == null) {
      if (other.citationLabel != null)
        return false;
    } else if (!citationLabel.equals(other.citationLabel))
      return false;
    
    if (collectionNumber == null) {
      if (other.collectionNumber != null)
        return false;
    } else if (!collectionNumber.equals(other.collectionNumber))
      return false;
    
    if (collectionTitle == null) {
      if (other.collectionTitle != null)
        return false;
    } else if (!collectionTitle.equals(other.collectionTitle))
      return false;
    
    if (containerTitle == null) {
      if (other.containerTitle != null)
        return false;
    } else if (!containerTitle.equals(other.containerTitle))
      return false;
    
    if (containerTitleShort == null) {
      if (other.containerTitleShort != null)
        return false;
    } else if (!containerTitleShort.equals(other.containerTitleShort))
      return false;
    
    if (dimensions == null) {
      if (other.dimensions != null)
        return false;
    } else if (!dimensions.equals(other.dimensions))
      return false;
    
    if (DOI == null) {
      if (other.DOI != null)
        return false;
    } else if (!DOI.equals(other.DOI))
      return false;
    
    if (edition == null) {
      if (other.edition != null)
        return false;
    } else if (!edition.equals(other.edition))
      return false;
    
    if (event == null) {
      if (other.event != null)
        return false;
    } else if (!event.equals(other.event))
      return false;
    
    if (eventPlace == null) {
      if (other.eventPlace != null)
        return false;
    } else if (!eventPlace.equals(other.eventPlace))
      return false;
    
    if (firstReferenceNoteNumber == null) {
      if (other.firstReferenceNoteNumber != null)
        return false;
    } else if (!firstReferenceNoteNumber.equals(other.firstReferenceNoteNumber))
      return false;
    
    if (genre == null) {
      if (other.genre != null)
        return false;
    } else if (!genre.equals(other.genre))
      return false;
    
    if (ISBN == null) {
      if (other.ISBN != null)
        return false;
    } else if (!ISBN.equals(other.ISBN))
      return false;
    
    if (ISSN == null) {
      if (other.ISSN != null)
        return false;
    } else if (!ISSN.equals(other.ISSN))
      return false;
    
    if (issue == null) {
      if (other.issue != null)
        return false;
    } else if (!issue.equals(other.issue))
      return false;
    
    if (jurisdiction == null) {
      if (other.jurisdiction != null)
        return false;
    } else if (!jurisdiction.equals(other.jurisdiction))
      return false;
    
    if (keyword == null) {
      if (other.keyword != null)
        return false;
    } else if (!keyword.equals(other.keyword))
      return false;
    
    if (locator == null) {
      if (other.locator != null)
        return false;
    } else if (!locator.equals(other.locator))
      return false;
    
    if (medium == null) {
      if (other.medium != null)
        return false;
    } else if (!medium.equals(other.medium))
      return false;
    
    if (note == null) {
      if (other.note != null)
        return false;
    } else if (!note.equals(other.note))
      return false;
    
    if (number == null) {
      if (other.number != null)
        return false;
    } else if (!number.equals(other.number))
      return false;
    
    if (numberOfPages == null) {
      if (other.numberOfPages != null)
        return false;
    } else if (!numberOfPages.equals(other.numberOfPages))
      return false;
    
    if (numberOfVolumes == null) {
      if (other.numberOfVolumes != null)
        return false;
    } else if (!numberOfVolumes.equals(other.numberOfVolumes))
      return false;
    
    if (originalPublisher == null) {
      if (other.originalPublisher != null)
        return false;
    } else if (!originalPublisher.equals(other.originalPublisher))
      return false;
    
    if (originalPublisherPlace == null) {
      if (other.originalPublisherPlace != null)
        return false;
    } else if (!originalPublisherPlace.equals(other.originalPublisherPlace))
      return false;
    
    if (originalTitle == null) {
      if (other.originalTitle != null)
        return false;
    } else if (!originalTitle.equals(other.originalTitle))
      return false;
    
    if (page == null) {
      if (other.page != null)
        return false;
    } else if (!page.equals(other.page))
      return false;
    
    if (pageFirst == null) {
      if (other.pageFirst != null)
        return false;
    } else if (!pageFirst.equals(other.pageFirst))
      return false;
    
    if (PMCID == null) {
      if (other.PMCID != null)
        return false;
    } else if (!PMCID.equals(other.PMCID))
      return false;
    
    if (PMID == null) {
      if (other.PMID != null)
        return false;
    } else if (!PMID.equals(other.PMID))
      return false;
    
    if (publisher == null) {
      if (other.publisher != null)
        return false;
    } else if (!publisher.equals(other.publisher))
      return false;
    
    if (publisherPlace == null) {
      if (other.publisherPlace != null)
        return false;
    } else if (!publisherPlace.equals(other.publisherPlace))
      return false;
    
    if (references == null) {
      if (other.references != null)
        return false;
    } else if (!references.equals(other.references))
      return false;
    
    if (reviewedTitle == null) {
      if (other.reviewedTitle != null)
        return false;
    } else if (!reviewedTitle.equals(other.reviewedTitle))
      return false;
    
    if (scale == null) {
      if (other.scale != null)
        return false;
    } else if (!scale.equals(other.scale))
      return false;
    
    if (section == null) {
      if (other.section != null)
        return false;
    } else if (!section.equals(other.section))
      return false;
    
    if (source == null) {
      if (other.source != null)
        return false;
    } else if (!source.equals(other.source))
      return false;
    
    if (status == null) {
      if (other.status != null)
        return false;
    } else if (!status.equals(other.status))
      return false;
    
    if (title == null) {
      if (other.title != null)
        return false;
    } else if (!title.equals(other.title))
      return false;
    
    if (titleShort == null) {
      if (other.titleShort != null)
        return false;
    } else if (!titleShort.equals(other.titleShort))
      return false;
    
    if (URL == null) {
      if (other.URL != null)
        return false;
    } else if (!URL.equals(other.URL))
      return false;
    
    if (version == null) {
      if (other.version != null)
        return false;
    } else if (!version.equals(other.version))
      return false;
    
    if (volume == null) {
      if (other.volume != null)
        return false;
    } else if (!volume.equals(other.volume))
      return false;
    
    if (yearSuffix == null) {
      if (other.yearSuffix != null)
        return false;
    } else if (!yearSuffix.equals(other.yearSuffix))
      return false;
    
    return true;
  }
  
}
