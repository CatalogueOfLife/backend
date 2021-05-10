package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class Rights {

  private String rights;
  private String rightsURI;
  private String lang;

  public String getRights() {
    return rights;
  }

  public void setRights(String rights) {
    this.rights = rights;
  }

  public String getRightsURI() {
    return rightsURI;
  }

  public void setRightsURI(String rightsURI) {
    this.rightsURI = rightsURI;
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
    if (!(o instanceof Rights)) return false;
    Rights rights1 = (Rights) o;
    return Objects.equals(rights, rights1.rights) && Objects.equals(rightsURI, rights1.rightsURI) && Objects.equals(lang, rights1.lang);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rights, rightsURI, lang);
  }
}
