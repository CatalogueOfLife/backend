package org.col.db.mapper.temp;

import org.col.api.model.Name;
import org.col.api.model.NameSearchResult;
import org.col.api.model.Taxon;

import java.util.Arrays;
import java.util.List;

public class NameSearchResultTemp extends Name {

	private Taxon taxonOfThisName;
	private List<Taxon> taxaOfAcceptedNames;

	public NameSearchResult toNameSearchResult() {
		NameSearchResult result = new NameSearchResult(this);
		if (taxonOfThisName != null) {
			result.setAcceptedName(true);
			result.setTaxa(Arrays.asList(taxonOfThisName));
		} else if (taxaOfAcceptedNames.size() != 0) {
			result.setTaxa(taxaOfAcceptedNames);
		}
		return result;
	}

	public Taxon getTaxonOfThisName() {
		return taxonOfThisName;
	}

	public void setTaxonOfThisName(Taxon taxonOfThisName) {
		this.taxonOfThisName = taxonOfThisName;
	}

	public List<Taxon> getTaxaOfAcceptedNames() {
		return taxaOfAcceptedNames;
	}

	public void setTaxaOfAcceptedNames(List<Taxon> taxaOfAcceptedNames) {
		this.taxaOfAcceptedNames = taxaOfAcceptedNames;
	}

}
