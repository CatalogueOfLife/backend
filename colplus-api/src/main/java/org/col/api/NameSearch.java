package org.col.api;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public class NameSearch {

	public static enum SortBy {
		RELEVANCE,
		NAME
	}

	@QueryParam("q")
	private String q;

	@PathParam("datasetKey")
	private Integer datasetKey;

	@QueryParam("key")
	private Integer key;

	@QueryParam("rank")
	private Rank rank;

	@QueryParam("nomstatus")
	private NomStatus nomstatus;

	@QueryParam("taxstatus")
	private TaxonomicStatus taxstatus;

	@QueryParam("issue")
	private Issue issue;

	@QueryParam("type")
	private NameType type;

	@QueryParam("sortBy")
	private SortBy sortBy;

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

	public NomStatus getNomstatus() {
		return nomstatus;
	}

	public void setNomstatus(NomStatus nomstatus) {
		this.nomstatus = nomstatus;
	}

	public TaxonomicStatus getTaxstatus() {
		return taxstatus;
	}

	public void setTaxstatus(TaxonomicStatus taxstatus) {
		this.taxstatus = taxstatus;
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

}
