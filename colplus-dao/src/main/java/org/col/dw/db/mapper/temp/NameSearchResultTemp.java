package org.col.dw.db.mapper.temp;

import java.util.Arrays;
import java.util.List;

import org.col.dw.api.Name;
import org.col.dw.api.NameSearchResult;
import org.col.dw.api.Taxon;

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
