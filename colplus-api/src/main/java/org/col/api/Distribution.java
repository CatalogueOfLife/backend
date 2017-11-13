package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

import java.util.Objects;

/**
 *
 */
public class Distribution {

	@JsonIgnore
	private Integer key;
	private Integer datasetKey;
	private Integer taxonKey;
	private String area;
	private Gazetteer areaStandard;
	private DistributionStatus status;

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer key) {
		this.datasetKey = key;
	}

	public Integer getTaxonKey() {
		return taxonKey;
	}

	public void setTaxonKey(Integer key) {
		this.taxonKey = key;
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
		    && Objects.equals(datasetKey, other.datasetKey)
		    && Objects.equals(taxonKey, other.taxonKey)
		    && Objects.equals(area, other.area)
		    && areaStandard == other.areaStandard
		    && status == other.status;
	}

	public int hashCode() {
		return Objects.hash(key, datasetKey, taxonKey, area, areaStandard, status);
	}

}
