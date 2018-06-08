package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.NomRelType;

/**
 * A nomenclatural name relation between two names pointing back in time from the nameKey to the relatedNameKey.
 */
public class NameRelation {
	private Integer key;
	private Integer datasetKey;
	private NomRelType type;
	private Integer nameKey;
	private Integer relatedNameKey;
	private Integer publishedInKey;
	private String note;

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

	public NomRelType getType() {
		return type;
	}

	public void setType(NomRelType type) {
		this.type = type;
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

	public Integer getPublishedInKey() {
		return publishedInKey;
	}

	public void setPublishedInKey(Integer publishedInKey) {
		this.publishedInKey = publishedInKey;
	}

	public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NameRelation that = (NameRelation) o;
		return Objects.equals(key, that.key) &&
				Objects.equals(datasetKey, that.datasetKey) &&
				type == that.type &&
				Objects.equals(nameKey, that.nameKey) &&
				Objects.equals(relatedNameKey, that.relatedNameKey) &&
				Objects.equals(publishedInKey, that.publishedInKey) &&
				Objects.equals(note, that.note);
	}

	@Override
	public int hashCode() {

		return Objects.hash(key, datasetKey, type, nameKey, relatedNameKey, publishedInKey, note);
	}
}
