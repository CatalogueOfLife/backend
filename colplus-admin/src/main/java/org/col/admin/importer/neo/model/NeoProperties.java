package org.col.admin.importer.neo.model;

import com.google.common.base.Strings;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;

/**
 * Property names of neo4j nodes and relations.
 * Any property we store in normalizer should be listed here to avoid overlaps or other confusion.
 */
public class NeoProperties {
  // propLabel used in NeoTaxon
  public static final String RANK = "rank";
  public static final String SCIENTIFIC_NAME = "scientificName";
  public static final String AUTHORSHIP = "authorship";
  // nomrel props
  public static final String NOTE = "note";
  public static final String REF_ID = "refid";
  public static final String VERBATIM_KEY = "vkey";

  public static final String NULL_NAME = "???";

  private NeoProperties() {
  }
  
  public static Rank getRank(Node n, Rank defaultValue) {
    if (n.hasProperty(NeoProperties.RANK)) {
      return Rank.values()[(int) n.getProperty(NeoProperties.RANK)];
    }
    return defaultValue;
  }

  public static String getScientificName(Node n) {
    return getScientificName(n, NULL_NAME);
  }
  
  public static String getScientificName(Node n, String defaultName) {
    return (String) n.getProperty(NeoProperties.SCIENTIFIC_NAME, defaultName);
  }

  public static String getAuthorship(Node n) {
    return (String) n.getProperty(NeoProperties.AUTHORSHIP, null);
  }
  
  public static String getScientificNameWithAuthor(Node n) {
    return getScientificNameWithAuthor(n, NULL_NAME);
  }
  
  public static String getScientificNameWithAuthor(Node n, String defaultName) {
    StringBuilder sb = new StringBuilder();
    String sciname = getScientificName(n, defaultName);
    if (!Strings.isNullOrEmpty(sciname)) {
      sb.append(sciname);
    }
    String authorship = getAuthorship(n);
    if (!Strings.isNullOrEmpty(authorship)) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb.toString();
  }

  /**
   * Reads a ranked name instance purely from neo4j propLabel
   */
  public static RankedName getRankedName(Node n) {
    return new RankedName(n,
        getScientificName(n),
        getAuthorship(n),
        getRank(n, Rank.UNRANKED)
    );
  }
  
  /**
   * Reads a ranked usage instance purely from neo4j propLabels,
   * retrieving just the related name node.
   */
  public static RankedUsage getRankedUsage(Node u) {
    Node n = getNameNode(u);
    return new RankedUsage(u, n,
        getScientificName(n),
        getAuthorship(n),
        getRank(n, Rank.UNRANKED)
    );
  }
  
  
  /**
   * Reads a ranked usage instance purely from neo4j propLabels,
   * retrieving just the related name node.
   */
  public static RankedUsage getRankedUsage(NeoUsage u) {
    if (u.node != null && u.nameNode != null && u.usage.getName() != null) {
      return new RankedUsage(u.node, u.nameNode,
          u.usage.getName().getScientificName(), u.usage.getName().authorshipComplete(), u.usage.getName().getRank());
    } else {
      return getRankedUsage(u.node);
    }
  }
  
  /**
   * @return the related, single name node for a usage node.
   */
  public static Node getNameNode(Node usageNode) {
    return usageNode.getSingleRelationship(RelType.HAS_NAME, Direction.OUTGOING).getOtherNode(usageNode);
  }
}
