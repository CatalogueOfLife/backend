package org.col.api;

import java.util.Objects;

public class PagedReference extends Reference {

	private String referencePage;
	/*
	 * The key of the referenced object (a taxon, vernacular name, distribution,
	 * etc.)
	 */
	private Integer referenceForKey;

	public String getReferencePage() {
		return referencePage;
	}

	public void setReferencePage(String page) {
		this.referencePage = page;
	}

	public Integer getReferenceForKey() {
		return referenceForKey;
	}

	public void setReferenceForKey(Integer referenceForKey) {
		this.referenceForKey = referenceForKey;
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		PagedReference other = (PagedReference) obj;
		return Objects.equals(referencePage, other.referencePage)
		    && Objects.equals(referenceForKey, other.referenceForKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), referencePage, referenceForKey);
	}

}
