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

	/**
	 * Primary key of the taxon as given in the dataset as taxonID. Only guaranteed
	 * to be unique within a dataset and can follow any kind of schema.
	 */
	private String id;

	/**
	 * Key to dataset instance. Defines context of the taxon key.
	 */
	private Dataset dataset;

	/**
	 *
	 */
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
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Taxon taxon = (Taxon) o;
		return Objects.equals(key, taxon.key)
		    && Objects.equals(id, taxon.id)
		    && Objects.equals(dataset, taxon.dataset)
		    && Objects.equals(name, taxon.name)
		    && status == taxon.status
		    && rank == taxon.rank
		    && origin == taxon.origin
		    && Objects.equals(parent, taxon.parent)
		    && Objects.equals(accordingTo, taxon.accordingTo)
		    && Objects.equals(accordingToDate, taxon.accordingToDate)
		    && Objects.equals(fossil, taxon.fossil)
		    && Objects.equals(recent, taxon.recent)
		    && Objects.equals(lifezones, taxon.lifezones)
		    && Objects.equals(speciesEstimate, taxon.speciesEstimate)
		    && Objects.equals(speciesEstimateReference, taxon.speciesEstimateReference)
		    && Objects.equals(remarks, taxon.remarks);
	}

	public boolean equalsShallow(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Taxon taxon = (Taxon) o;
		boolean equal = Objects.equals(key, taxon.key)
		    && Objects.equals(id, taxon.id)
		    && Objects.equals(dataset, taxon.dataset)
		    && status == taxon.status
		    && rank == taxon.rank
		    && Objects.equals(accordingTo, taxon.accordingTo)
		    && Objects.equals(accordingToDate, taxon.accordingToDate)
		    && Objects.equals(fossil, taxon.fossil)
		    && Objects.equals(recent, taxon.recent)
		    && Objects.equals(lifezones, taxon.lifezones)
		    && Objects.equals(speciesEstimate, taxon.speciesEstimate);
		if (equal) {
			if (parent == null) {
				equal = taxon.parent == null;
			} else {
				equal = taxon.parent != null && Objects.equals(parent.key, taxon.parent.key);
			}
		}
		if (equal) {
			if (name == null) {
				equal = taxon.name == null;
			} else {
				equal = taxon.name != null && Objects.equals(name.getKey(), taxon.parent.getKey());
			}
		}
		if (equal) {
			if (speciesEstimateReference == null) {
				equal = taxon.speciesEstimateReference == null;
			} else {
				Reference ref0 = speciesEstimateReference;
				Reference ref1 = taxon.speciesEstimateReference;
				equal = ref1 != null && Objects.equals(ref0.getKey(), ref1.getKey());
			}
		}
		return equal;
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, id, dataset, name, status, rank, origin, parent, accordingTo,
		    accordingToDate, fossil, recent, lifezones, speciesEstimate, speciesEstimateReference,
		    remarks);
	}
}
