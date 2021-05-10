package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class Title {

  private String title;
  private TitleType titleType;
  private String lang;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public TitleType getTitleType() {
    return titleType;
  }

  public void setTitleType(TitleType titleType) {
    this.titleType = titleType;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Title)) return false;
    Title title1 = (Title) o;
    return Objects.equals(title, title1.title) && titleType == title1.titleType && Objects.equals(lang, title1.lang);
  }

  @Override
  public int hashCode() {
    return Objects.hash(title, titleType, lang);
  }
}
