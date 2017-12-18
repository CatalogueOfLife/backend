package org.col.api;

import java.util.List;

public class NameSearchResult extends Name {

	private boolean accepted;
	private List<Taxon> taxa;

	/**
	 * Whether or not this is an accepted name. If not, it's not necessarily a
	 * synonym; it could also be a naked name. This name only definitively is a
	 * synonym <i>if</i> there the list with taxa is not empty.
	 * 
	 * @return
	 */
	public boolean isAccepted() {
		return accepted;
	}

	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}

	public List<Taxon> getTaxa() {
		return taxa;
	}

	public void setTaxa(List<Taxon> taxa) {
		this.taxa = taxa;
	}

}
