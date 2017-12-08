package org.col.api;

import org.col.api.vocab.Issue;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;

/**
 *
 */
public class Taxon {

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

	private TaxonomicStatus status;

	private Origin origin;

	private Integer parentKey;

	private String accordingTo;

	private LocalDate accordingToDate;

	private Boolean fossil;

	private Boolean recent;

	private Set<Lifezone> lifezones;

	private URI datasetUrl;

	private Integer speciesEstimate;

	private Integer speciesEstimateReferenceKey;

	private String remarks;

	private List<ReferencePointer> references;

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

	public List<ReferencePointer> getReferences() {
		return references;
	}

	public void setReferences(List<ReferencePointer> references) {
		this.references = references;
	}

	public void createReferences(Collection<PagedReference> refs) {
		if (!refs.isEmpty()) {
			references = new ArrayList<>(refs.size());
			for (PagedReference pr : refs) {
				references.add(new ReferencePointer(pr.getKey(), pr.getReferencePage()));
			}
		}
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
        status == taxon.status &&
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
        Objects.equals(references, taxon.references) &&
        Objects.equals(issues, taxon.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, id, datasetKey, taxonID, name, status, origin, parentKey, accordingTo, accordingToDate, fossil, recent, lifezones, datasetUrl, speciesEstimate, speciesEstimateReferenceKey, remarks, references, issues);
  }
}
