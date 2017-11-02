package org.col.api;

import com.google.common.collect.Lists;

import java.util.List;

public class TaxonInfo {

	private Taxon taxon;

	private List<VernacularName> vernacularNames;

	private List<Distribution> distributions;

	private List<Reference> references = Lists.newArrayList();

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

	public List<Reference> getReferences() {
		return references;
	}

	public void setReferences(List<Reference> references) {
		this.references = references;
	}

	/**
	 * @return the reference from the wrapped reference list with
	 *         Reference.key=referenceKey
	 */
	public Reference getByKey(int referenceKey) {
		for (Reference r : references) {
			if (r.getKey() == referenceKey) {
				return r;
			}
		}
		return null;
	}
}
