package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Person {
  private String givenName;
  private String familyName;
  private String email;
  private String orcid;

  public static Person parse(String name) {
    if (name == null) return null;
    Person p = new Person();
    p.setFamilyName(name);
    return p;
  }

  public static List<Person> parse(List<String> names) {
    return names == null ? null : names.stream().map(Person::parse).collect(Collectors.toList());
  }

  public Person() {
  }

  public Person(String givenName, String familyName) {
    this.givenName = givenName;
    this.familyName = familyName;
  }

  public Person(String givenName, String familyName, String email, String orcid) {
    this.givenName = givenName;
    this.familyName = familyName;
    this.email = email;
    this.orcid = orcid;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getName(){
    if (givenName == null && familyName == null) return null;

    StringBuilder sb = new StringBuilder();
    if (givenName != null) {
      sb.append(givenName);
    }
    if (familyName != null) {
      if (givenName != null) {
        sb.append(" ");
      }
      sb.append(familyName);
    }
    return sb.toString();
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getOrcid() {
    return orcid;
  }

  public void setOrcid(String orcid) {
    this.orcid = orcid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Person)) return false;
    Person person = (Person) o;
    return Objects.equals(givenName, person.givenName) &&
      Objects.equals(familyName, person.familyName) &&
      Objects.equals(email, person.email) &&
      Objects.equals(orcid, person.orcid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(givenName, familyName, email, orcid);
  }

  @Override
  public String toString() {
    return getName();
  }
}
