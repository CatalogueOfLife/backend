package org.col.api.model.coldp;

import java.util.Objects;

public class ColdpReference {
  private String citation;
  private String author;
  private String title;
  private String year;
  private String source;
  private String details;
  private String doi;
  private String link;
  
  public String getCitation() {
    return citation;
  }
  
  public void setCitation(String citation) {
    this.citation = citation;
  }
  
  public String getAuthor() {
    return author;
  }
  
  public void setAuthor(String author) {
    this.author = author;
  }
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }
  
  public String getYear() {
    return year;
  }
  
  public void setYear(String year) {
    this.year = year;
  }
  
  public String getSource() {
    return source;
  }
  
  public void setSource(String source) {
    this.source = source;
  }
  
  public String getDetails() {
    return details;
  }
  
  public void setDetails(String details) {
    this.details = details;
  }
  
  public String getDoi() {
    return doi;
  }
  
  public void setDoi(String doi) {
    this.doi = doi;
  }
  
  public String getLink() {
    return link;
  }
  
  public void setLink(String link) {
    this.link = link;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ColdpReference that = (ColdpReference) o;
    return Objects.equals(citation, that.citation) &&
        Objects.equals(author, that.author) &&
        Objects.equals(title, that.title) &&
        Objects.equals(year, that.year) &&
        Objects.equals(source, that.source) &&
        Objects.equals(details, that.details) &&
        Objects.equals(doi, that.doi) &&
        Objects.equals(link, that.link);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(citation, author, title, year, source, details, doi, link);
  }
}
