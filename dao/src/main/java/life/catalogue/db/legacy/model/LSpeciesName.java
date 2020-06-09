package life.catalogue.db.legacy.model;

import java.util.Objects;

public class LSpeciesName extends LHigherName {
  private String onlineResource;
  private String sourceDatabase;
  private String sourceDatabaseUrl;
  private String bibliographicCitation;
  private String author;

  @Override
  public String getNameHtml() {
    String html = super.getNameHtml();
    if (author != null) {
      html = html + " " + author;
    }
    return html;
  }

  public String getOnlineResource() {
    return onlineResource;
  }

  public void setOnlineResource(String onlineResource) {
    this.onlineResource = onlineResource;
  }

  public String getSourceDatabase() {
    return sourceDatabase;
  }

  public void setSourceDatabase(String sourceDatabase) {
    this.sourceDatabase = sourceDatabase;
  }

  public String getSourceDatabaseUrl() {
    return sourceDatabaseUrl;
  }

  public void setSourceDatabaseUrl(String sourceDatabaseUrl) {
    this.sourceDatabaseUrl = sourceDatabaseUrl;
  }

  public String getBibliographicCitation() {
    return bibliographicCitation;
  }

  public void setBibliographicCitation(String bibliographicCitation) {
    this.bibliographicCitation = bibliographicCitation;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LSpeciesName)) return false;
    if (!super.equals(o)) return false;
    LSpeciesName that = (LSpeciesName) o;
    return Objects.equals(onlineResource, that.onlineResource) &&
      Objects.equals(sourceDatabase, that.sourceDatabase) &&
      Objects.equals(sourceDatabaseUrl, that.sourceDatabaseUrl) &&
      Objects.equals(bibliographicCitation, that.bibliographicCitation) &&
      Objects.equals(author, that.author);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), onlineResource, sourceDatabase, sourceDatabaseUrl, bibliographicCitation, author);
  }
}