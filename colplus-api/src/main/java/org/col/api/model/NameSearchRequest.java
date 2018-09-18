package org.col.api.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.ws.rs.QueryParam;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NameField;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public class NameSearchRequest {

  public static enum SortBy {
		RELEVANCE,
		NAME,
    KEY
	}

	private Set<NameSearchFacet> facets = new HashSet<>();

	@QueryParam("q")
	private String q;

	@QueryParam("datasetKey")
	private Integer datasetKey;

	@QueryParam("id")
	private String id;

	@QueryParam("rank")
	private Rank rank;

	@QueryParam("nomStatus")
	private NomStatus nomStatus;

	@QueryParam("status")
	private TaxonomicStatus status;

	@QueryParam("issue")
	private Issue issue;

	@QueryParam("type")
	private NameType type;

	@QueryParam("hasField")
	private NameField hasField;

	@QueryParam("sortBy")
	private SortBy sortBy = SortBy.NAME;

	public static NameSearchRequest byQuery(String query) {
		NameSearchRequest q = new NameSearchRequest();
		q.setQ(query);
		return q;
	}

	public static NameSearchRequest byId(String id) {
		NameSearchRequest q = new NameSearchRequest();
		q.setId(id);
		return q;
	}

	public void addFacet(NameSearchFacet f) {
		facets.add(f);
	}

	public Set<NameSearchFacet> getFacets() {
		return facets;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public Integer getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(Integer datasetKey) {
		this.datasetKey = datasetKey;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Rank getRank() {
		return rank;
	}

	public void setRank(Rank rank) {
		this.rank = rank;
	}

  public NomStatus getNomStatus() {
    return nomStatus;
  }

  public void setNomStatus(NomStatus nomStatus) {
    this.nomStatus = nomStatus;
  }

	public TaxonomicStatus getStatus() {
		return status;
	}

	public void setStatus(TaxonomicStatus status) {
		this.status = status;
	}

	public Issue getIssue() {
		return issue;
	}

	public void setIssue(Issue issue) {
		this.issue = issue;
	}

	public NameType getType() {
		return type;
	}

	public void setType(NameType type) {
		this.type = type;
	}

	public NameField getHasField() {
		return hasField;
	}

	public void setHasField(NameField hasField) {
		this.hasField = hasField;
	}

	public SortBy getSortBy() {
		return sortBy;
	}

	public void setSortBy(SortBy sortBy) {
		this.sortBy = sortBy;
	}

  public boolean isEmpty() {
	  return q == null
				&& facets.isEmpty()
        && datasetKey == null
        && id == null
        && rank == null
        && nomStatus == null
        && status == null
        && issue == null
        && type == null
				&& hasField == null;
  }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NameSearchRequest that = (NameSearchRequest) o;
		return Objects.equals(facets, that.facets) &&
				Objects.equals(q, that.q) &&
				Objects.equals(datasetKey, that.datasetKey) &&
				Objects.equals(id, that.id) &&
				rank == that.rank &&
				nomStatus == that.nomStatus &&
				status == that.status &&
				issue == that.issue &&
				type == that.type &&
				hasField == that.hasField &&
				sortBy == that.sortBy;
	}

	@Override
	public int hashCode() {

		return Objects.hash(facets, q, datasetKey, id, rank, nomStatus, status, issue, type, hasField, sortBy);
	}
}
