package org.col.api;

import java.util.List;
import java.util.Objects;

import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class VernacularName {

	@JsonIgnore
	private Integer key;

	private String name;

	private Language language;

	private Country country;
	
	private List<ReferencePointer> references;



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
		    && Objects.equals(country, other.country);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, name, language, country);
	}


}
