package org.col.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.col.api.vocab.Issue;

/**
 * Simplified citation class linked to an optional serial container.
 */
public class Reference implements VerbatimEntity {

	/**
	 * Internal surrogate key of the reference as provided by postgres.
	 */
	private Integer key;

	/**
	 * Original key as provided by the dataset.
	 */
	private String id;

	/**
	 * Key to dataset instance. Defines context of the reference key.
	 */
	private Integer datasetKey;

	private Integer verbatimKey;

	/**
	 * Reference metadata encoded as CSL-JSON.
	 */
	private CslData csl;

	/**
	 * Parsed integer of the year of publication. Extracted from CSL data, but kept
	 * separate to allow sorting on int order.
	 */
	private Integer year;

  /**
   * Issues related to this reference
   */
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

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer datasetKey) {
		this.datasetKey = datasetKey;
	}

	@Override
	public Integer getVerbatimKey() {
		return verbatimKey;
	}

	@Override
	public void setVerbatimKey(Integer verbatimKey) {
		this.verbatimKey = verbatimKey;
	}

	public CslData getCsl() {
		return csl;
	}

	public void setCsl(CslData csl) {
		this.csl = csl;
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
		r.csl = new CslData();
		return r;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Reference reference = (Reference) o;
		return Objects.equals(key, reference.key) &&
				Objects.equals(id, reference.id) &&
				Objects.equals(datasetKey, reference.datasetKey) &&
				Objects.equals(csl, reference.csl) &&
				Objects.equals(year, reference.year) &&
				Objects.equals(issues, reference.issues);
	}

	@Override
	public int hashCode() {

		return Objects.hash(key, id, datasetKey, csl, year, issues);
	}

	@Override
  public String toString() {
    return "Reference{" +
        "key=" + key +
        ", id='" + id + '\'' +
        ", csl='" + csl + '\'' +
        '}';
  }

  /**
   * Sets the exact page in the underlying CSL item.
	 * We add this delegation method as we keep the page separate from the rest of the citation in our data model.
   * @param page
   */
  public void setPage(String page) {
	  csl.setPage(page);
  }

	/**
	 * Gets the exact page from the underlying CSL item.
	 * We add this delegation method as we keep the page separate from the rest of the citation in our data model.
	 */
	public String getPage() {
		return csl.getPage();
	}

}
