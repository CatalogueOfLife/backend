package org.col.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simplified literature reference class linked to an optional serial container.
 */
public class Reference {

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
	private int datasetKey;

	/**
	 * Reference metadata encoded as CSL-JSON.
	 */
	private ObjectNode csl;

	/**
	 * Serial container, defining the CSL container properties.
	 */
	private Integer serialKey;

	/**
	 * Parsed integer of the year of publication. Extracted from CSL data, but kept
	 * separate to allow sorting on int order.
	 */
	private Integer year;

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

	public int getDatasetKey() {
		return datasetKey;
	}

	public void setDatasetKey(int datasetKey) {
		this.datasetKey = datasetKey;
	}

	public ObjectNode getCsl() {
		return csl;
	}

	public void setCsl(ObjectNode csl) {
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

	/**
	 * @return An empty reference instance with an empty csl JsonNode.
	 */
	public static Reference create() {
		Reference r = new Reference();
		r.csl = JsonNodeFactory.instance.objectNode();
		return r;
	}

	// Simple way to build a canonical full representation of the reference
	@JsonIgnore
	public String getCitation() {
		// TODO: use most fields so a reference citation is build
		return cslStr("title");
	}

	// VARIOUS METHODS DELEGATING TO THE UNDERLYING CSL JsonObject instance
	@JsonIgnore
	public String getTitle() {
		return cslStr("title");
	}

	public void setTitle(String title) {
		csl.put("title", title);
	}

	private String cslStr(String path) {
		if (csl.has(path)) {
			JsonNode node = csl.get(path);
			return node.asText();
		}
		return null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || !(o instanceof Reference))
			return false;
		Reference reference = (Reference) o;
		return Objects.equals(key, reference.key)
		    && datasetKey == reference.datasetKey
		    && Objects.equals(id, reference.id)
		    && Objects.equals(csl, reference.csl)
		    && Objects.equals(serialKey, reference.serialKey)
		    && Objects.equals(year, reference.year);
	}

	@Override
	public int hashCode() {
		return Objects.hash(key, datasetKey, id, csl, serialKey, year);
	}

}
