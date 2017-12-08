package org.col.api;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.ParsedName;

import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
public class Name extends ParsedName {

	/**
	 * Internal surrogate key of the name as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	private Integer key;

	/**
	 * Primary key of the name as given in the dataset dwc:scientificNameID. Only
	 * guaranteed to be unique within a dataset and can follow any kind of schema.
	 */
	private String id;

	/**
	 * Key to dataset instance. Defines context of the name key.
	 */
	private Integer datasetKey;

  /**
   * Global name identifier from a nomenclator or the clearinghouse
   */
  private String scientificNameID;

	/**
	 * Entire canonical name string with a rank marker for infragenerics and
	 * infraspecfics, but excluding the authorship. For uninomials, e.g. families or
	 * names at higher ranks, this is just the uninomial.
	 */
	private String scientificName;

	private Origin origin;

	/**
	 * Link to the original combination. In case of [replacement
	 * names](https://en.wikipedia.org/wiki/Nomen_novum) it points back to the
	 * replaced synonym.
	 */
	private Integer basionymKey;

	/**
	 * true if the type specimen of the name is a fossil
	 */
	private Boolean fossil;

	/**
	 * Current nomenclatural status of the name taking into account all known
	 * nomenclatural acts.
	 */
	private NomStatus status;

	private URI sourceUrl;

  /**
   * Issues related to this name with potential values in the map
   */
  private Set<Issue> issues = EnumSet.noneOf(Issue.class);

  /**
   * Returns the full authorship incl basionym and sanctioning authors from individual parts.
   */
  public String getAuthorship() {
    return authorshipComplete();
  }

  public Name() {
  }

  public Name(ParsedName pn) {
    setCombinationAuthorship(pn.getCombinationAuthorship());
    setBasionymAuthorship(pn.getBasionymAuthorship());
    setSanctioningAuthor(pn.getSanctioningAuthor());
    setRank(pn.getRank());
    setCode(pn.getCode());
    setUninomial(pn.getUninomial());
    setGenus(pn.getGenus());
    setInfragenericEpithet(pn.getInfragenericEpithet());
    setSpecificEpithet(pn.getSpecificEpithet());
    setInfraspecificEpithet(pn.getInfraspecificEpithet());
    setCultivarEpithet(pn.getCultivarEpithet());
    setStrain(pn.getStrain());
    setCandidatus(pn.isCandidatus());
    setNotho(pn.getNotho());
    setSensu(pn.getSensu());
    setNomenclaturalNotes(pn.getNomenclaturalNotes());
    setRemarks(pn.getRemarks());
    setType(pn.getType());
    setDoubtful(pn.isDoubtful());
    setParsed(pn.isParsed());
    setAuthorsParsed(pn.isAuthorsParsed());
    for (String w : pn.getWarnings()) {
      addWarning(w);
    }
  }

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

  public String getScientificNameID() {
    return scientificNameID;
  }

  public void setScientificNameID(String scientificNameID) {
    this.scientificNameID = scientificNameID;
  }

  public String getScientificName() {
		return scientificName;
	}

	public void setScientificName(String scientificName) {
		this.scientificName = scientificName;
	}

	public Origin getOrigin() {
		return origin;
	}

	public void setOrigin(Origin origin) {
		this.origin = origin;
	}

	public Integer getBasionymKey() {
		return basionymKey;
	}

	public void setBasionymKey(Integer key) {
		this.basionymKey = key;
	}

	public Boolean getFossil() {
		return fossil;
	}

	public void setFossil(Boolean fossil) {
		this.fossil = fossil;
	}

	public NomStatus getStatus() {
		return status;
	}

	public void setStatus(NomStatus status) {
		this.status = status;
	}

	public URI getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(URI sourceUrl) {
		this.sourceUrl = sourceUrl;
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
    if (!super.equals(o)) return false;
    Name name = (Name) o;
    return Objects.equals(key, name.key) &&
        Objects.equals(id, name.id) &&
        Objects.equals(datasetKey, name.datasetKey) &&
        Objects.equals(scientificNameID, name.scientificNameID) &&
        Objects.equals(scientificName, name.scientificName) &&
        origin == name.origin &&
        Objects.equals(basionymKey, name.basionymKey) &&
        Objects.equals(fossil, name.fossil) &&
        status == name.status &&
        Objects.equals(sourceUrl, name.sourceUrl) &&
        Objects.equals(issues, name.issues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, id, datasetKey, scientificNameID, scientificName, origin, basionymKey, fossil, status, sourceUrl, issues);
  }

  @Override
  public String toString() {
    return key + "[" + id + "] " + canonicalNameComplete();
  }

}
