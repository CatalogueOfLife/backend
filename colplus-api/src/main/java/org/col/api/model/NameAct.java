package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.NomActType;

/**
 * A nomenclatural act such as a species description, type designation or
 * conservation of a name.
 */
public class NameAct {
	private Integer key;
	private Integer datasetKey;
	private NomActType type;
	private Integer nameKey;
	private Integer relatedNameKey;
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

	public NomActType getType() {
		return type;
	}

	public void setType(NomActType type) {
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
    NameAct nameAct = (NameAct) o;
    return Objects.equals(key, nameAct.key) &&
        Objects.equals(datasetKey, nameAct.datasetKey) &&
        type == nameAct.type &&
        Objects.equals(nameKey, nameAct.nameKey) &&
        Objects.equals(relatedNameKey, nameAct.relatedNameKey) &&
        Objects.equals(note, nameAct.note);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, datasetKey, type, nameKey, relatedNameKey, note);
  }
}
