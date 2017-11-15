package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

import java.util.List;
import java.util.Objects;

/**
 *
 */
public class Distribution {

	@JsonIgnore
	private Integer key;
	private String area;
	private Gazetteer areaStandard;
	private DistributionStatus status;
	private List<ReferencePointer> references;

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public Gazetteer getAreaStandard() {
		return areaStandard;
	}

	public void setAreaStandard(Gazetteer areaStandard) {
		this.areaStandard = areaStandard;
	}

	public DistributionStatus getStatus() {
		return status;
	}

	public void setStatus(DistributionStatus status) {
		this.status = status;
	}

  public List<ReferencePointer> getReferences() {
    return references;
  }

  public void setReferences(List<ReferencePointer> references) {
    this.references = references;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Distribution that = (Distribution) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(area, that.area) &&
        areaStandard == that.areaStandard &&
        status == that.status &&
        Objects.equals(references, that.references);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, area, areaStandard, status, references);
  }
}
