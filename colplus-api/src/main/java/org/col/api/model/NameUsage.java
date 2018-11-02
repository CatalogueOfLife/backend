package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
//@JsonSubTypes({@JsonSubTypes.Type(value = Taxon.class, name = "T"),
//    @JsonSubTypes.Type(value = BareName.class, name = "B"),
//    @JsonSubTypes.Type(value = Synonym.class, name = "S")})
public interface NameUsage {

  Name getName();

  TaxonomicStatus getStatus();

  String getAccordingTo();
}
