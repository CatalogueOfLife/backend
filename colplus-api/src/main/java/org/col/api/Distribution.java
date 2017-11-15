package org.col.api;

import java.util.List;
import java.util.Objects;

import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		Distribution other = (Distribution) obj;
		return Objects.equals(key, other.key)
		    && Objects.equals(area, other.area)
		    && areaStandard == other.areaStandard
		    && status == other.status;
	}

	public int hashCode() {
		return Objects.hash(key, area, areaStandard, status);
	}

}
