package life.catalogue.importer.neo.traverse;


import life.catalogue.importer.neo.model.NeoProperties;

import java.util.Comparator;

import org.neo4j.graphdb.Node;

/**
 * Orders usage nodes by their rank first, then canonical name and scientificName ultimately.
 */
public class TaxonomicOrder implements Comparator<Node> {
  
  @Override
  public int compare(Node usage1, Node usage2) {
    Node n1 = NeoProperties.getNameNode(usage1);
    Node n2 = NeoProperties.getNameNode(usage2);
    
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
