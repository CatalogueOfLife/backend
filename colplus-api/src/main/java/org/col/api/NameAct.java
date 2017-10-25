package org.col.api;

import org.col.api.vocab.NomActType;
import org.col.api.vocab.NomStatus;

import java.util.Objects;

/**
 * A nomenclatural act such as a species description, type designation or
 * conservation of a name.
 */
public class NameAct {
	private Integer key;
	private Dataset dataset;
	private NomActType type;

	/**
	 * The new status established through this act.
	 */
	private NomStatus status;

	private Name name;
	private Name relatedName;
	private String description;
	private String referencePage;
	private Reference reference;

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
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

	public Name getName() {
		return name;
	}

	public void setName(Name name) {
		this.name = name;
	}

	public Name getRelatedName() {
		return relatedName;
	}

	public void setRelatedName(Name relatedName) {
		this.relatedName = relatedName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getReferencePage() {
		return referencePage;
	}

	public void setReferencePage(String referencePage) {
		this.referencePage = referencePage;
	}

	public Reference getReference() {
		return reference;
	}

	public void setReference(Reference reference) {
		this.reference = reference;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		NameAct nameAct = (NameAct) o;
		return Objects.equals(key, nameAct.key)
		    && type == nameAct.type
		    && status == nameAct.status
		    && Objects.equals(name, nameAct.name)
		    && Objects.equals(relatedName, nameAct.relatedName)
		    && Objects.equals(description, nameAct.description)
		    && Objects.equals(referencePage, nameAct.referencePage)
		    && Objects.equals(reference, nameAct.reference);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, type, status, name, relatedName, referencePage, reference);
	}

	public boolean equalsShallow(NameAct nameAct) {
		if (this == nameAct) {
			return true;
		}
		if (nameAct == null) {
			return false;
		}
		return Objects.equals(key, nameAct.key)
		    && type == nameAct.type
		    && status == nameAct.status
		    && ApiUtil.equalsShallow(dataset, nameAct.dataset)
		    && ApiUtil.equalsShallow(name, nameAct.name)
		    && ApiUtil.equalsShallow(relatedName, nameAct.relatedName)
		    && ApiUtil.equalsShallow(reference, nameAct.reference)
		    && Objects.equals(referencePage, nameAct.referencePage);
	}
}
