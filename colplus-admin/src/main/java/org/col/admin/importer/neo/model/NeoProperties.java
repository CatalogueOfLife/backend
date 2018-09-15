package org.col.admin.importer.neo.model;

import com.google.common.base.Strings;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;

/**
 * Property names of neo4j nodes and relations.
 * Any property we store in normalizer should be listed here to avoid overlaps or other confusion.
 */
public class NeoProperties {
  // properties used in NeoTaxon
  public static final String ID = "id";
  public static final String RANK = "rank";
  public static final String SCIENTIFIC_NAME = "scientificName";
  public static final String AUTHORSHIP = "authorship";
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

  public static String getID(Node n) {
    return (String) n.getProperty(NeoProperties.ID, null);
  }

  public static String getScientificName(Node n) {
    return (String) n.getProperty(NeoProperties.SCIENTIFIC_NAME, NULL_NAME);
  }

  public static String getAuthorship(Node n) {
    return (String) n.getProperty(NeoProperties.AUTHORSHIP, null);
  }

  public static String getScientificNameWithAuthor(Node n) {
    StringBuilder sb = new StringBuilder();
    sb.append(getScientificName(n));
    String authorship = getAuthorship(n);
    if (!Strings.isNullOrEmpty(authorship)) {
      sb.append(" ");
      sb.append(authorship);
    }
    return sb.toString();
  }

  /**
   * Reads a ranked name instance purely from neo4j properties
   */
  public static RankedName getRankedName(Node n) {
    return new RankedName(n,
        getScientificName(n),
        getAuthorship(n),
        getRank(n, Rank.UNRANKED)
    );
  }
}
