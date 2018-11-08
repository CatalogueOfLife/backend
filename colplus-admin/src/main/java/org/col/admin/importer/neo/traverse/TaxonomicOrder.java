package org.col.admin.importer.neo.traverse;


import java.util.Comparator;

import org.col.admin.importer.neo.model.NeoProperties;
import org.neo4j.graphdb.Node;

/**
 * Orders taxon nodes by their rank first, then canonical name and scientificName ultimately.
 */
public class TaxonomicOrder implements Comparator<Node> {
  
  @Override
  public int compare(Node n1, Node n2) {
    int r1 = (int) n1.getProperty(NeoProperties.RANK, Integer.MAX_VALUE);
    int r2 = (int) n2.getProperty(NeoProperties.RANK, Integer.MAX_VALUE);
    
    if (r1 != r2) {
      return r1 - r2;
    }
    String c1 = NeoProperties.getScientificName(n1);
    String c2 = NeoProperties.getScientificName(n2);
    int canonicalComparison = c1.compareTo(c2);
    if (canonicalComparison == 0) {
      return NeoProperties.getScientificNameWithAuthor(n1).compareTo(NeoProperties.getScientificNameWithAuthor(n2));
    }
    return canonicalComparison;
  }
  
}
