package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Issue;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public class Taxon implements PrimaryEntity, NameUsage {

	/**
	 * Internal surrogate key of the taxon as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	private Integer key;

	private String id;

	private Integer datasetKey;

  /**
   * Clearinghouse taxon concept identifier based on the synonymy of the taxon and its siblings
   */
  private Integer taxonID;

	private Name name;

	private boolean doubtful = false;

	private Origin origin;

	private Integer parentKey;

	private String accordingTo;

	private LocalDate accordingToDate;

	private Boolean fossil;

	private Boolean recent;

	private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);

	private URI datasetUrl;

	private Integer speciesEstimate;

	private Integer speciesEstimateReferenceKey;

	private String remarks;


  private Set<Issue> issues = EnumSet.noneOf(Issue.class);

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

  public Integer getTaxonID() {
    return taxonID;
  }

  public void setTaxonID(Integer taxonID) {
    this.taxonID = taxonID;
  }

  public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer key) {
		this.datasetKey = key;
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

	public Integer getParentKey() {
		return parentKey;
	}

	public void setParentKey(Integer key) {
		this.parentKey = key;
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

	public Integer getSpeciesEstimate() {
		return speciesEstimate;
	}

	public void setSpeciesEstimate(Integer speciesEstimate) {
		this.speciesEstimate = speciesEstimate;
	}

	public Integer getSpeciesEstimateReferenceKey() {
		return speciesEstimateReferenceKey;
	}

	public void setSpeciesEstimateReferenceKey(Integer speciesEstimateReferenceKey) {
		this.speciesEstimateReferenceKey = speciesEstimateReferenceKey;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public Set<Issue> getIssues() {
		return issues;
	}

	public void setIssues(Set<Issue> issues) {
		this.issues = issues;
	}

	public void addIssue(Issue issue) {
		issues.add(issue);
	}

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Taxon taxon = (Taxon) o;
    return Objects.equals(key, taxon.key) &&
        Objects.equals(id, taxon.id) &&
        Objects.equals(datasetKey, taxon.datasetKey) &&
        Objects.equals(taxonID, taxon.taxonID) &&
        Objects.equals(name, taxon.name) &&
        doubtful == taxon.doubtful &&
        origin == taxon.origin &&
        Objects.equals(parentKey, taxon.parentKey) &&
        Objects.equals(accordingTo, taxon.accordingTo) &&
        Objects.equals(accordingToDate, taxon.accordingToDate) &&
        Objects.equals(fossil, taxon.fossil) &&
        Objects.equals(recent, taxon.recent) &&
        Objects.equals(lifezones, taxon.lifezones) &&
        Objects.equals(datasetUrl, taxon.datasetUrl) &&
        Objects.equals(speciesEstimate, taxon.speciesEstimate) &&
        Objects.equals(speciesEstimateReferenceKey, taxon.speciesEstimateReferenceKey) &&
        Objects.equals(remarks, taxon.remarks) &&
        Objects.equals(issues, taxon.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, datasetKey, taxonID, name, doubtful, origin, parentKey, accordingTo, accordingToDate, fossil, recent, lifezones, datasetUrl, speciesEstimate, speciesEstimateReferenceKey, remarks, issues);
  }
}
