package org.col.commands.importer.neo.model;

import org.col.api.vocab.Issue;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.neo4j.graphdb.Node;

/**
 * Minimal interface to retrieve data stored as neo4j properties from any data class
 * to be persisted in the neodb.
 */
public interface NeoTaxon {

  /**
   * @return the neo4j node this object is stored under or null if it is not persisted (yet)
   */
  Node getNode();

  void setNode(Node node);

  String getTaxonID();

  String getScientificName();

  String getCanonicalName();

  Rank getRank();

  TaxonomicStatus getStatus();

  void setStatus(TaxonomicStatus status);

  void setAcceptedKey(int key);

  void setAccepted(String scientificName);

  void setBasionymKey(int key);

  void setBasionym(String scientificName);

  void setParentKey(int key);

  void addIssue(Issue issue);

  void addRemark(String message);


}
