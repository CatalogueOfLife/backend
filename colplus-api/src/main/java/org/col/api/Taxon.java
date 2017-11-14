package org.col.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class Taxon {

	/**
	 * Internal surrogate key of the taxon as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	@JsonIgnore
	private Integer key;

	private String id;

	private Integer datasetKey;

	private Name name;

	private TaxonomicStatus status;

	private Rank rank;

	private Origin origin;

	private Integer parentKey;

	private String accordingTo;

	private LocalDate accordingToDate;

	private Boolean fossil;

	private Boolean recent;

	private Set<Lifezone> lifezones;

	private URI datasetUrl;

	private Integer speciesEstimate;

	private Reference speciesEstimateReference;

	private String remarks;

	/**
	 * Issues related to this taxon with potential values in the map
	 */
	private Map<Issue, String> issues = new EnumMap<>(Issue.class);

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer key) {
		this.datasetKey = key;
	}

	public Name getName() {
		return name;
	}

	public void setName(Name name) {
		this.name = name;
	}

	public TaxonomicStatus getStatus() {
		return status;
	}

	public void setStatus(TaxonomicStatus status) {
		this.status = status;
	}

	public Rank getRank() {
		return rank;
	}

	public void setRank(Rank rank) {
		this.rank = rank;
	}

	public Origin getOrigin() {
		return origin;
	}

	public void setOrigin(Origin origin) {
		this.origin = origin;
	}

	public Integer getParentKey() {
		return parentKey;
	}

	public void setParentKey(Integer key) {
		this.parentKey = key;
	}

	public String getAccordingTo() {
		return accordingTo;
	}

	public void setAccordingTo(String accordingTo) {
		this.accordingTo = accordingTo;
	}

	public LocalDate getAccordingToDate() {
		return accordingToDate;
	}

	public void setAccordingToDate(LocalDate accordingToDate) {
		this.accordingToDate = accordingToDate;
	}

	public Boolean getFossil() {
		return fossil;
	}

	public void setFossil(Boolean fossil) {
		this.fossil = fossil;
	}

	public Boolean getRecent() {
		return recent;
	}

	public void setRecent(Boolean recent) {
		this.recent = recent;
	}

	public Set<Lifezone> getLifezones() {
		return lifezones;
	}

	public void setLifezones(Set<Lifezone> lifezones) {
		this.lifezones = lifezones;
	}

	public URI getDatasetUrl() {
		return datasetUrl;
	}

	public void setDatasetUrl(URI datasetUrl) {
		this.datasetUrl = datasetUrl;
	}

	public Integer getSpeciesEstimate() {
		return speciesEstimate;
	}

	public void setSpeciesEstimate(Integer speciesEstimate) {
		this.speciesEstimate = speciesEstimate;
	}

	public Reference getSpeciesEstimateReference() {
		return speciesEstimateReference;
	}

	public void setSpeciesEstimateReference(Reference speciesEstimateReference) {
		this.speciesEstimateReference = speciesEstimateReference;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Map<Issue, String> getIssues() {
		return issues;
	}

	public void setIssues(Map<Issue, String> issues) {
		this.issues = issues;
	}

	public void addIssue(Issue issue) {
		issues.put(issue, null);
	}

	public void addIssue(Issue issue, Object value) {
		issues.put(issue, value.toString());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Taxon taxon = (Taxon) o;
		return Objects.equals(key, taxon.key)
		    && Objects.equals(id, taxon.id)
		    && Objects.equals(datasetKey, taxon.datasetKey)
		    && Objects.equals(name, taxon.name)
		    && status == taxon.status
		    && rank == taxon.rank
		    && origin == taxon.origin
		    && Objects.equals(parentKey, taxon.parentKey)
		    && Objects.equals(accordingTo, taxon.accordingTo)
		    && Objects.equals(accordingToDate, taxon.accordingToDate)
		    && Objects.equals(fossil, taxon.fossil)
		    && Objects.equals(recent, taxon.recent)
		    && Objects.equals(lifezones, taxon.lifezones)
		    && Objects.equals(datasetUrl, taxon.datasetUrl)
		    && Objects.equals(speciesEstimate, taxon.speciesEstimate)
		    && Objects.equals(speciesEstimateReference, taxon.speciesEstimateReference)
		    && Objects.equals(remarks, taxon.remarks)
		    && Objects.equals(issues, taxon.issues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, id, datasetKey, name, status, rank, origin, parentKey, accordingTo,
		    accordingToDate, fossil, recent, lifezones, datasetUrl, speciesEstimate,
		    speciesEstimateReference, remarks, issues);
	}

	public boolean equalsShallow(Taxon other) {
		if (this == other) {
			return true;
		}
		if (other == null) {
			return false;
		}
		return Objects.equals(key, other.key)
		    && Objects.equals(id, other.id)
		    && Objects.equals(datasetKey, other.datasetKey)
		    && ApiUtil.equalsShallow(name, other.name)
		    && status == other.status
		    && rank == other.rank
		    && origin == other.origin
		    && Objects.equals(parentKey, other.parentKey)
		    && Objects.equals(accordingTo, other.accordingTo)
		    && Objects.equals(accordingToDate, other.accordingToDate)
		    && Objects.equals(fossil, other.fossil)
		    && Objects.equals(recent, other.recent)
		    && Objects.equals(lifezones, other.lifezones)
		    && Objects.equals(speciesEstimate, other.speciesEstimate)
		    && ApiUtil.equalsShallow(speciesEstimateReference, other.speciesEstimateReference)
		    && Objects.equals(remarks, other.remarks);
	}

}
