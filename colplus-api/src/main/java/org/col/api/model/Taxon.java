package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public class Taxon implements NameUsage, VerbatimEntity {

	private String id;

	private Integer datasetKey;

	private Integer verbatimKey;

	private Name name;

	private boolean doubtful = false;

	private Origin origin;

	private String parentId;

	private String accordingTo;

	private LocalDate accordingToDate;

	private Boolean fossil;

	private Boolean recent;

	private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);

	private URI datasetUrl;

	private Integer childCount;

	private Integer speciesEstimate;

	private String speciesEstimateReferenceId;

	private String remarks;

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

	@Override
	public Integer getVerbatimKey() {
		return verbatimKey;
	}

	@Override
	public void setVerbatimKey(Integer verbatimKey) {
		this.verbatimKey = verbatimKey;
	}

	@Override
	public Name getName() {
		return name;
	}

	public void setName(Name name) {
		this.name = name;
	}

	@Override
	public TaxonomicStatus getStatus() {
		return doubtful ? TaxonomicStatus.DOUBTFUL : TaxonomicStatus.ACCEPTED;
	}

  public boolean isDoubtful() {
    return doubtful;
  }

  public void setDoubtful(boolean doubtful) {
    this.doubtful = doubtful;
  }

  public Origin getOrigin() {
		return origin;
	}

	public void setOrigin(Origin origin) {
		this.origin = origin;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String key) {
		this.parentId = key;
	}

  @Override
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

	public Boolean isFossil() {
		return fossil;
	}

	public void setFossil(Boolean fossil) {
		this.fossil = fossil;
	}

	public Boolean isRecent() {
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

	public Integer getChildCount() {
		return childCount;
	}

	public void setChildCount(Integer childCount) {
		this.childCount = childCount;
	}

	public Integer getSpeciesEstimate() {
		return speciesEstimate;
	}

	public void setSpeciesEstimate(Integer speciesEstimate) {
		this.speciesEstimate = speciesEstimate;
	}

	public String getSpeciesEstimateReferenceId() {
		return speciesEstimateReferenceId;
	}

	public void setSpeciesEstimateReferenceId(String speciesEstimateReferenceId) {
		this.speciesEstimateReferenceId = speciesEstimateReferenceId;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Taxon taxon = (Taxon) o;
		return doubtful == taxon.doubtful &&
				Objects.equals(id, taxon.id) &&
				Objects.equals(datasetKey, taxon.datasetKey) &&
				Objects.equals(verbatimKey, taxon.verbatimKey) &&
				Objects.equals(name, taxon.name) &&
				origin == taxon.origin &&
				Objects.equals(parentId, taxon.parentId) &&
				Objects.equals(accordingTo, taxon.accordingTo) &&
				Objects.equals(accordingToDate, taxon.accordingToDate) &&
				Objects.equals(fossil, taxon.fossil) &&
				Objects.equals(recent, taxon.recent) &&
				Objects.equals(lifezones, taxon.lifezones) &&
				Objects.equals(datasetUrl, taxon.datasetUrl) &&
				Objects.equals(childCount, taxon.childCount) &&
				Objects.equals(speciesEstimate, taxon.speciesEstimate) &&
				Objects.equals(speciesEstimateReferenceId, taxon.speciesEstimateReferenceId) &&
				Objects.equals(remarks, taxon.remarks);
	}

	@Override
	public int hashCode() {

		return Objects.hash(id, datasetKey, verbatimKey, name, doubtful, origin, parentId, accordingTo, accordingToDate, fossil, recent, lifezones, datasetUrl, childCount, speciesEstimate, speciesEstimateReferenceId, remarks);
	}
}
