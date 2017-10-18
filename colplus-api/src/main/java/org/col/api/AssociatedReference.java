package org.col.api;

import java.util.Objects;

public class AssociatedReference {

	private Reference reference;
	private String page;

	public Reference getReference() {
		return reference;
	}

	public void setReference(Reference reference) {
		this.reference = reference;
	}

	public String getPage() {
		return page;
	}

	public void setPage(String page) {
		this.page = page;
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		AssociatedReference other = (AssociatedReference) obj;
		return Objects.equals(reference, other.reference) && Objects.equals(page, other.page);
	}

	@Override
	public int hashCode() {
		return Objects.hash(reference, page);
	}

}
