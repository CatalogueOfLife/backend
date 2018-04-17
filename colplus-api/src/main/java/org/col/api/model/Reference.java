package org.col.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.col.api.vocab.Issue;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Simplified literature reference class linked to an optional serial container.
 */
public class Reference implements PrimaryEntity {

	/**
	 * Internal surrogate key of the reference as provided by postgres. This key is
	 * unique across all datasets but not exposed in the API.
	 */
	@JsonIgnore
	private Integer key;

	/**
	 * Original key as provided by the dataset.
	 */
	private String id;

	/**
	 * Key to dataset instance. Defines context of the reference key.
	 */
	private Integer datasetKey;

  /**
   * Full reference citation as alternative to structured CSL data.
   */
  private String citation;

	/**
	 * Reference metadata encoded as CSL-JSON.
	 */
	private CslItemData csl;

	/**
	 * Serial container, defining the CSL container properties.
	 */
	private Integer serialKey;

	/**
	 * Parsed integer of the year of publication. Extracted from CSL data, but kept
	 * separate to allow sorting on int order.
	 */
	private Integer year;

  /**
   * Issues related to this reference
   */
  private Set<Issue> issues = EnumSet.noneOf(Issue.class);
  private String page;


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

	public void setDatasetKey(Integer datasetKey) {
		this.datasetKey = datasetKey;
	}

  public void setCitation(String citation) {
    this.citation = citation;
  }

  public CslItemData getCsl() {
		return csl;
	}

	public void setCsl(CslItemData csl) {
		this.csl = csl;
	}

  public Integer getSerialKey() {
		return serialKey;
	}

	public void setSerialKey(Integer serialKey) {
		this.serialKey = serialKey;
	}

	public Integer getYear() {
		return year;
	}

	public void setYear(Integer year) {
		this.year = year;
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

  /**
	 * @return An empty reference instance with an empty csl JsonNode.
	 */
	public static Reference create() {
		Reference r = new Reference();
		r.csl = new CslItemData();
		return r;
	}

  // VARIOUS METHODS DELEGATING TO THE UNDERLYING CSL JsonObject instance
	@JsonIgnore
	public String getTitle() {
		return csl.getTitle();
	}

	public void setTitle(String title) {
		csl.setTitle(title);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		// use instanceof - not getClass()! See ReferenceWithPage.equals()
		if (o == null || !(o instanceof Reference))
			return false;
		Reference reference = (Reference) o;
		return Objects.equals(key, reference.key)
		    && Objects.equals(datasetKey, reference.datasetKey)
		    && Objects.equals(id, reference.id)
        && Objects.equals(citation, reference.citation)
		    && Objects.equals(csl, reference.csl)
		    && Objects.equals(serialKey, reference.serialKey)
        && Objects.equals(year, reference.year)
		    && Objects.equals(issues, reference.issues);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, datasetKey, id, citation, csl, serialKey, year, issues);
	}

  @Override
  public String toString() {
    return "Reference{" +
        "key=" + key +
        ", id='" + id + '\'' +
        ", citation='" + citation + '\'' +
        '}';
  }

  public void setPage(String page) {
    this.page = page;
  }
}
