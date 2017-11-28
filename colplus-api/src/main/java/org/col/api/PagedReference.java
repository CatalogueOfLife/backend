package org.col.api;

import java.util.Objects;

public class PagedReference extends Reference {

	private String referencePage;

	public String getReferencePage() {
		return referencePage;
	}

	public void setReferencePage(String page) {
		this.referencePage = page;
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		PagedReference other = (PagedReference) obj;
		return Objects.equals(referencePage, other.referencePage);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), referencePage);
	}

}
