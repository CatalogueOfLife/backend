package org.col.es.mapping;

import java.util.LinkedHashMap;

/**
 * Class representing a complex structure within an Elasticsearch type mapping.
 * This can be either the entire type mapping or a nested document (objects of
 * type {@link ESDataType#NESTED "nested"} or {@link ESDataType#OBJECT
 * "object"}. The {@link Mapping} subclass is used to represent the entire type
 * mapping.
 */
public class ComplexField extends ESField {

	private final LinkedHashMap<String, ESField> properties;

	public ComplexField()
	{
		this(null);
	}

	public ComplexField(ESDataType type)
	{
		this.type = type;
		properties = new LinkedHashMap<>();
	}

	public LinkedHashMap<String, ESField> getProperties()
	{
		return properties;
	}

	public void addField(String name, ESField f)
	{
		properties.put(name, f);
	}

}
