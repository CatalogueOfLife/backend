package org.col.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaxonInfo {

	private Taxon taxon;

	private List<VernacularName> vernacularNames;

	private List<Distribution> distributions;

	private Map<Integer, Reference> references = new HashMap<>();

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
	

	public Map<Integer, Reference> getReferences() {
		return references;
	}

	public void setReferences(Map<Integer, Reference> references) {
		this.references = references;
	}
	
	public void addReferences(Collection<PagedReference> refs) {
		for (PagedReference pr : refs) {
			if (!references.containsKey(pr.getKey())) {
				references.put(pr.getKey(), new Reference(pr));
			}
		}
	}

}
