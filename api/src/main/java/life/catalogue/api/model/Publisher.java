package life.catalogue.api.model;

import java.util.Objects;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import life.catalogue.api.vocab.Country;

/**
 * GBIF UUID based publisher to be used in projects and releases as sector publishers.
 */
public class Publisher implements Entity<UUID> {
  private UUID key;
  private String title;
  private String description;
  private String homepage;
  private String city;
  private String province;
  private String country;
  private Double latitude;
  private Double longitude;

  @Override
  public UUID getKey() {
    return key;
  }

  @Override
  public void setKey(UUID key) {
    this.key = key;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getHomepage() {
    return homepage;
  }

  public void setHomepage(String homepage) {
    this.homepage = homepage;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getProvince() {
    return province;
  }

  public void setProvince(String province) {
    this.province = province;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Publisher)) return false;

    Publisher publisher = (Publisher) o;
    return Objects.equals(key, publisher.key) &&
      Objects.equals(title, publisher.title) &&
      Objects.equals(description, publisher.description) &&
      Objects.equals(homepage, publisher.homepage) &&
      Objects.equals(city, publisher.city) &&
      Objects.equals(province, publisher.province) &&
      Objects.equals(country, publisher.country) &&
      Objects.equals(latitude, publisher.latitude) &&
      Objects.equals(longitude, publisher.longitude);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, title, description, homepage, city, province, country, latitude, longitude);
  }
}
