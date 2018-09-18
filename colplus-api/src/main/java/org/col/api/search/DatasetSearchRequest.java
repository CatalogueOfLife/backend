package org.col.api.search;

import javax.ws.rs.QueryParam;

import org.col.api.vocab.*;
import org.gbif.nameparser.api.NomCode;

public class DatasetSearchRequest {

  public static enum SortBy {
		KEY,
		TITLE,
		RELEVANCE,
		CREATED,
		MODIFIED,
		SIZE
	}

	@QueryParam("q")
	private String q;

	@QueryParam("code")
	private NomCode code;

	@QueryParam("catalogue")
	private Catalogue catalogue;

	@QueryParam("format")
	private DataFormat format;

	@QueryParam("type")
	private DatasetType type;

	@QueryParam("sortBy")
	private SortBy sortBy = SortBy.RELEVANCE;

	public static DatasetSearchRequest byQuery(String query) {
		DatasetSearchRequest q = new DatasetSearchRequest();
		q.q=query;
		return q;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public NomCode getCode() {
		return code;
	}

	public void setCode(NomCode code) {
		this.code = code;
	}

	public Catalogue getCatalogue() {
		return catalogue;
	}

	public void setCatalogue(Catalogue catalogue) {
		this.catalogue = catalogue;
	}

	public DataFormat getFormat() {
		return format;
	}

	public void setFormat(DataFormat format) {
		this.format = format;
	}

	public DatasetType getType() {
		return type;
	}

	public void setType(DatasetType type) {
		this.type = type;
	}

	public SortBy getSortBy() {
		return sortBy;
	}

	public void setSortBy(SortBy sortBy) {
		this.sortBy = sortBy;
	}
}
