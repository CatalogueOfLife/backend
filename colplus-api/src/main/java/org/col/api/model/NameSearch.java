package org.col.api.model;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.QueryParam;
import java.util.Objects;

public class NameSearch {

  public static enum SortBy {
		RELEVANCE,
		NAME,
    KEY
	}

	@QueryParam("q")
	private String q;

	@QueryParam("datasetKey")
	private Integer datasetKey;

	@QueryParam("key")
	private Integer key;

	@QueryParam("rank")
	private Rank rank;

	@QueryParam("nomstatus")
	private NomStatus nomStatus;

	@QueryParam("taxstatus")
	private TaxonomicStatus taxStatus;

	@QueryParam("issue")
	private Issue issue;

	@QueryParam("type")
	private NameType type;

	@QueryParam("sortBy")
	private SortBy sortBy = SortBy.NAME;

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

	public Integer getKey() {
		return key;
	}

	public void setKey(Integer key) {
		this.key = key;
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

  public TaxonomicStatus getTaxStatus() {
    return taxStatus;
  }

  public void setTaxStatus(TaxonomicStatus taxStatus) {
    this.taxStatus = taxStatus;
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

	public SortBy getSortBy() {
		return sortBy;
	}

	public void setSortBy(SortBy sortBy) {
		this.sortBy = sortBy;
	}

  public boolean isEmpty() {
	  return q == null
        && datasetKey == null
        && key == null
        && rank == null
        && nomStatus == null
        && taxStatus == null
        && issue == null
        && type == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameSearch that = (NameSearch) o;
    return Objects.equals(q, that.q) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(key, that.key) &&
        rank == that.rank &&
        nomStatus == that.nomStatus &&
        taxStatus == that.taxStatus &&
        issue == that.issue &&
        type == that.type &&
        sortBy == that.sortBy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(q, datasetKey, key, rank, nomStatus, taxStatus, issue, type, sortBy);
  }
}
