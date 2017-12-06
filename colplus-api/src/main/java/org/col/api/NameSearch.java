package org.col.api;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.col.api.vocab.TaxonomicStatus;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public class NameSearch {

	@PathParam("datasetKey")
	private int datasetKey;

	@QueryParam("q")
	private String q;

	@QueryParam("rank")
	private Rank rank;

	@QueryParam("nomstatus")
	private NomStatus nomstatus;

	@QueryParam("taxstatus")
	private TaxonomicStatus taxstatus;

	@QueryParam("issue")
	private Issue issue;

	@QueryParam("taxstatus")
	private NameType type;

	public int getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(int datasetKey) {
		this.datasetKey = datasetKey;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
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

}
