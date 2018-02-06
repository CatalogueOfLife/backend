package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

import java.util.Objects;
import java.util.Set;

public class VernacularName implements Referenced {

	@JsonIgnore
	private Integer key;
	private String name;
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
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		VernacularName other = (VernacularName) obj;
		return Objects.equals(key, other.key)
		    && Objects.equals(name, other.name)
		    && Objects.equals(language, other.language)
        && Objects.equals(referenceKeys, other.referenceKeys)
		    && Objects.equals(country, other.country);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, name, language, country, referenceKeys);
	}

}
