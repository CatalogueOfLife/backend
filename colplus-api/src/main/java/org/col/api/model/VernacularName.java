package org.col.api.model;

import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

public class VernacularName implements Referenced {

	@JsonIgnore
	private Integer key;
	private String name;
  private String latin;
	private Language language;
	private Country country;
	private Set<Integer> referenceKeys = Sets.newHashSet();

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

  /**
   * Transliterated name into the latin script.
   */
  public String getLatin() {
    return latin;
  }

  public void setLatin(String latin) {
    this.latin = latin;
  }

  public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public Country getCountry() {
		return country;
	}

	public void setCountry(Country country) {
		this.country = country;
	}

  @Override
  public Set<Integer> getReferenceKeys() {
    return referenceKeys;
  }

  public void setReferenceKeys(Set<Integer> referenceKeys) {
    this.referenceKeys = referenceKeys;
  }

  public void addReferenceKey(Integer referenceKey) {
    this.referenceKeys.add(referenceKey);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VernacularName that = (VernacularName) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(name, that.name) &&
        Objects.equals(latin, that.latin) &&
        language == that.language &&
        country == that.country &&
        Objects.equals(referenceKeys, that.referenceKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, name, latin, language, country, referenceKeys);
  }
}
