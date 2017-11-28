package org.col.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxonInfo {

	private Taxon taxon;

	private List<VernacularName> vernacularNames;

	private List<Distribution> distributions;

	private Map<Integer, PagedReference> references = new HashMap<>();

	public Taxon getTaxon() {
		return taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
	}

	public List<VernacularName> getVernacularNames() {
		return vernacularNames;
	}

	public void setVernacularNames(List<VernacularName> vernacularNames) {
		this.vernacularNames = vernacularNames;
	}

	public List<Distribution> getDistributions() {
		return distributions;
	}

	public void setDistributions(List<Distribution> distributions) {
		this.distributions = distributions;
	}

	public void addReferences(List<PagedReference> refs) {
		for (PagedReference ref : refs) {
			references.put(ref.getKey(), ref);
		}
	}

}
