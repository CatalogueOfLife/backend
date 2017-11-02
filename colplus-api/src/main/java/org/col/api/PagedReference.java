package org.col.api;

import java.util.Objects;

public class PagedReference extends Reference {

	private String page;

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
		if (!super.equals(obj)) {
			return false;
		}
		PagedReference other = (PagedReference) obj;
		return Objects.equals(page, other.page);
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), page);
	}

}
