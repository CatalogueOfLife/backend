package org.col.api.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Sets;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Issue;

/**
 *
 */
public class Distribution implements Referenced, VerbatimEntity {

	@JsonIgnore
	private Integer key;
	private Integer verbatimKey;
	private String area;
	private Gazetteer gazetteer;
	private DistributionStatus status;
  private Set<Integer> referenceKeys = Sets.newHashSet();
	private Set<Issue> issues = EnumSet.noneOf(Issue.class);

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
	}

	@Override
	public Integer getVerbatimKey() {
		return verbatimKey;
	}

	@Override
	public void setVerbatimKey(Integer verbatimKey) {
		this.verbatimKey = verbatimKey;
	}

	public String getArea() {
		return area;
	}

	public void setArea(String area) {
		this.area = area;
	}

	public Gazetteer getGazetteer() {
		return gazetteer;
	}

	public void setGazetteer(Gazetteer gazetteer) {
		this.gazetteer = gazetteer;
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
		Distribution that = (Distribution) o;
		return Objects.equals(key, that.key) &&
				Objects.equals(verbatimKey, that.verbatimKey) &&
				Objects.equals(area, that.area) &&
				gazetteer == that.gazetteer &&
				status == that.status &&
				Objects.equals(referenceKeys, that.referenceKeys) &&
				Objects.equals(issues, that.issues);
	}

	@Override
	public int hashCode() {

		return Objects.hash(key, verbatimKey, area, gazetteer, status, referenceKeys, issues);
	}

	@Override
	public String toString() {
		return status == null ? "Unknown" : status + " in " + gazetteer + ":" + area;
	}
}
