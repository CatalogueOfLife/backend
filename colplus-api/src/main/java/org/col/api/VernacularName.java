package org.col.api;

import java.util.Objects;

import javax.annotation.Nullable;

import org.col.api.vocab.Country;
import org.col.api.vocab.Language;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class VernacularName {

	@JsonIgnore
	private Integer key;

	private String name;

	private Dataset dataset;

	private Taxon taxon;

	private Language language;

	private Country country;

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

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Taxon getTaxon() {
		return taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
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

	public boolean equalsShallow(@Nullable VernacularName vn) {
		if (this == vn) {
			return true;
		}
		if (vn == null) {
			return false;
		}
		boolean equal = Objects.equals(key, vn.key)
		    && Objects.equals(name, vn.name)
		    && Objects.equals(language, vn.language)
		    && Objects.equals(country, vn.country);
		if (equal) {
			if (dataset == null) {
				equal = vn.dataset == null;
			} else {
				equal = vn.dataset != null && Objects.equals(dataset.getKey(), vn.dataset.getKey());
			}
		}
		if (equal) {
			if (taxon == null) {
				equal = vn.taxon == null;
			} else {
				equal = vn.taxon != null && Objects.equals(taxon.getKey(), vn.taxon.getKey());
			}
		}
		return equal;
	}

}
