package org.col.api.model;

import java.util.List;

public class NameSearchResult extends Name {

	private boolean acceptedName;
	private List<Taxon> taxa;

	public NameSearchResult() {
		super();
	}

	public NameSearchResult(Name n) {
	  super(n);
	}

	/**
	 * Whether or not this is an accepted name. If not, it's not necessarily a
	 * synonym; it could also be a naked name. This name only definitively is a
	 * synonym <i>if</i> there the list with taxa is not empty.
	 * 
	 * @return
	 */
	public boolean isAcceptedName() {
		return acceptedName;
	}

	public void setAcceptedName(boolean accepted) {
		this.acceptedName = accepted;
	}

	/**
	 * If this name is an accepted name, this method returns a single-element list
	 * containing the {@link Taxon} object associated with <i>this</i> name. If this
	 * name is a synonym, this method returns a list of {@link Taxon} objects
	 * associated with the <i>accepted names</i> of the synonym.
	 * 
	 * @return
	 */
	public List<Taxon> getTaxa() {
		return taxa;
	}

	public void setTaxa(List<Taxon> taxa) {
		this.taxa = taxa;
	}

}
