package life.catalogue.doi.datacite.model;

import java.util.Objects;

public class Subject {

  private String subject;
  private String subjectScheme;
  private String schemeURI;
  private String valueURI;
  private String lang;

  public Subject() {
  }

  public Subject(String subject) {
    this.subject = subject;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getSubjectScheme() {
    return subjectScheme;
  }

  public void setSubjectScheme(String subjectScheme) {
    this.subjectScheme = subjectScheme;
  }

  public String getSchemeURI() {
    return schemeURI;
  }

  public void setSchemeURI(String schemeURI) {
    this.schemeURI = schemeURI;
  }

  public String getValueURI() {
    return valueURI;
  }

  public void setValueURI(String valueURI) {
    this.valueURI = valueURI;
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
    if (!(o instanceof Subject)) return false;
    Subject subject1 = (Subject) o;
    return Objects.equals(subject, subject1.subject) && Objects.equals(subjectScheme, subject1.subjectScheme) && Objects.equals(schemeURI, subject1.schemeURI) && Objects.equals(valueURI, subject1.valueURI) && Objects.equals(lang, subject1.lang);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subject, subjectScheme, schemeURI, valueURI, lang);
  }
}
