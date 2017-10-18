package org.col.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

	private Dataset dataset;

	private Name name;

	private TaxonomicStatus status;

	private Rank rank;

	private Origin origin;

	private Taxon parent;

	private String accordingTo;

	private LocalDate accordingToDate;

	private Boolean fossil;

	private Boolean recent;

	private Set<Lifezone> lifezones;

	private URI datasetUrl;

	private Integer speciesEstimate;

	private Reference speciesEstimateReference;

	private String remarks;

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

	public Dataset getDataset() {
		return dataset;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
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

	public Taxon getParent() {
		return parent;
	}

	public void setParent(Taxon parent) {
		this.parent = parent;
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

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Taxon other = (Taxon) obj;
		return Objects.equals(key, other.key)
		    && Objects.equals(id, other.id)
		    && Objects.equals(dataset, other.dataset)
		    && Objects.equals(name, other.name)
		    && status == other.status
		    && rank == other.rank
		    && origin == other.origin
		    && Objects.equals(parent, other.parent)
		    && Objects.equals(accordingTo, other.accordingTo)
		    && Objects.equals(accordingToDate, other.accordingToDate)
		    && Objects.equals(fossil, other.fossil)
		    && Objects.equals(recent, other.recent)
		    && Objects.equals(lifezones, other.lifezones)
		    && Objects.equals(speciesEstimate, other.speciesEstimate)
		    && Objects.equals(speciesEstimateReference, other.speciesEstimateReference)
		    && Objects.equals(remarks, other.remarks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, id, dataset, name, status, rank, origin, parent, accordingTo,
		    accordingToDate, fossil, recent, lifezones, speciesEstimate, speciesEstimateReference,
		    remarks);
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
		    && ApiUtil.equalsShallow(dataset, other.dataset)
		    && ApiUtil.equalsShallow(name, other.name)
		    && status == other.status
		    && rank == other.rank
		    && origin == other.origin
		    && ApiUtil.equalsShallow(parent, other.parent)
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
