package org.col.api;

import java.util.Objects;

import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;

/**
 * A nomenclatural act such as a species description, type designation or
 * conservation of a name.
 */
public class NameAct {
	private Integer key;
	private Integer datasetKey;
	private NomActType type;

	/**
	 * The new status established through this act.
	 */
	private NomStatus status;

	private Integer nameKey;
	private Integer relatedNameKey;
	private String description;
	private Integer referenceKey;
	private String referencePage;

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer datasetKey) {
		this.datasetKey = datasetKey;
	}

	public NomActType getType() {
		return type;
	}

	public void setType(NomActType type) {
		this.type = type;
	}

	public NomStatus getStatus() {
		return status;
	}

	public void setStatus(NomStatus status) {
		this.status = status;
	}

	public Integer getNameKey() {
		return nameKey;
	}

	public void setNameKey(Integer nameKey) {
		this.nameKey = nameKey;
	}

	public Integer getRelatedNameKey() {
		return relatedNameKey;
	}

	public void setRelatedNameKey(Integer key) {
		this.relatedNameKey = key;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Integer getReferenceKey() {
		return referenceKey;
	}

	public void setReferenceKey(Integer key) {
		this.referenceKey = key;
	}

	public String getReferencePage() {
		return referencePage;
	}

	public void setReferencePage(String referencePage) {
		this.referencePage = referencePage;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		NameAct nameAct = (NameAct) o;
		return Objects.equals(key, nameAct.key)
		    && Objects.equals(datasetKey, nameAct.datasetKey)
		    && type == nameAct.type
		    && status == nameAct.status
		    && Objects.equals(nameKey, nameAct.nameKey)
		    && Objects.equals(relatedNameKey, nameAct.relatedNameKey)
		    && Objects.equals(description, nameAct.description)
		    && Objects.equals(referencePage, nameAct.referencePage)
		    && Objects.equals(referenceKey, nameAct.referenceKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, datasetKey, type, status, nameKey, relatedNameKey, referencePage,
		    referenceKey);
	}

	// public boolean equalsShallow(NameAct nameAct) {
	// if (this == nameAct) {
	// return true;
	// }
	// if (nameAct == null) {
	// return false;
	// }
	// return Objects.equals(key, nameAct.key)
	// && type == nameAct.type
	// && status == nameAct.status
	// && ApiUtil.equalsShallow(dataset, nameAct.dataset)
	// && ApiUtil.equalsShallow(name, nameAct.name)
	// && ApiUtil.equalsShallow(relatedName, nameAct.relatedName)
	// && ApiUtil.equalsShallow(reference, nameAct.reference)
	// && Objects.equals(referencePage, nameAct.referencePage);
	// }
}
