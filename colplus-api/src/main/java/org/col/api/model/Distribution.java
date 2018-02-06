package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;

import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class Distribution implements Referenced {

	@JsonIgnore
	private Integer key;
	private String area;
	private Gazetteer areaStandard;
	private DistributionStatus status;
  private Set<Integer> referenceKeys = Sets.newHashSet();

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

  @Override
  public Set<Integer> getReferenceKeys() {
    return referenceKeys;
  }

  public void setReferenceKeys(Set<Integer> referenceKeys) {
    this.referenceKeys = referenceKeys;
  }

  public void addReferenceKey(Integer referenceKey) {
    this.referenceKeys.add(referenceKey);
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

	@Override
	public String toString() {
		return status == null ? "Unknown" : status + " in " + areaStandard + ":" + area;
	}
}
