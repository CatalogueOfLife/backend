package org.col.dw.api;

import java.util.Objects;

public class ReferencePointer {

	private Integer referenceKey;
	private String page;

	public ReferencePointer() {
	}

	public ReferencePointer(Integer referenceKey, String page) {
		this.referenceKey = referenceKey;
		this.page = page;
	}

	public Integer getReferenceKey() {
		return referenceKey;
	}

	public void setReferenceKey(Integer referenceKey) {
		this.referenceKey = referenceKey;
	}

	public String getPage() {
		return page;
	}

	public void setPage(String page) {
		this.page = page;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ReferencePointer other = (ReferencePointer) obj;
		return Objects.equals(referenceKey, other.referenceKey) && Objects.equals(page, other.page);
	}

	@Override
	public int hashCode() {
		return Objects.hash(referenceKey, page);
	}

}
