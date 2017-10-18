package org.col.api;

import java.util.Objects;

import org.col.api.vocab.DistributionStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public class Distribution {

	@JsonIgnore
	private Integer key;
	private Dataset dataset;
	private Taxon taxon;
	private String area;
	private int areaStandard;
	private DistributionStatus status;

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

	public Taxon getTaxon() {
		return taxon;
	}

	public void setTaxon(Taxon taxon) {
		this.taxon = taxon;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public int getAreaStandard() {
		return areaStandard;
	}

	public void setAreaStandard(int areaStandard) {
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
		    && Objects.equals(dataset, other.dataset)
		    && Objects.equals(taxon, other.taxon)
		    && Objects.equals(area, other.area)
		    && areaStandard == other.areaStandard
		    && status == other.status;
	}

	public int hashCode() {
		return Objects.hash(key, dataset, taxon, area, areaStandard, status);
	}

	public boolean equalsShallow(Distribution other) {
		if (this == other) {
			return true;
		}
		if (other == null) {
			return false;
		}
		return Objects.equals(key, other.key)
		    && ApiUtil.equalsShallow(dataset, other.dataset)
		    && ApiUtil.equalsShallow(taxon, other.taxon)
		    && Objects.equals(area, other.area)
		    && areaStandard == other.areaStandard
		    && status == other.status;
	}

}
