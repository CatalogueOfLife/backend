package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import life.catalogue.api.vocab.Country;

import java.util.Objects;

public class Organisation {
  private String name;
  private String department;
  private String city;
  private Country country;


  public Organisation() {
  }

  public Organisation(String name, String department, String city, Country country) {
    this.name = name;
    this.department = department;
    this.city = city;
    this.country = country;
  }

  public String getLabel() {
    if (isEmpty()) return null;

    StringBuilder sb = new StringBuilder();
    sb.append(name);
    append(sb, department);
    append(sb, city);
    if (country != null) {
      sb.append(", ");
      sb.append(country.getTitle());
    }
    return sb.toString();
  }

  @JsonIgnore
  public boolean isEmpty(){
    return name == null && department == null && city == null && country == null;
  }

  private static void append(StringBuilder sb, String x) {
    if (x != null) {
      sb.append(", ");
      sb.append(x);
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public Country getCountry() {
    return country;
  }

  public void setCountry(Country country) {
    this.country = country;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Organisation)) return false;
    Organisation that = (Organisation) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(department, that.department) &&
      Objects.equals(city, that.city) &&
      country == that.country;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, department, city, country);
  }

  @Override
  public String toString() {
    return getLabel();
  }
}
