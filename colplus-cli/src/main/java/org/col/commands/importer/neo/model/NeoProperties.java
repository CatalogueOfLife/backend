package org.col.commands.importer.neo.model;

import com.google.common.base.Strings;
import org.col.api.vocab.Rank;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.neo4j.graphdb.Node;

/**
 * Property names of neo4j nodes.
 * Any property we store in normalizer should be listed here to avoid overlaps or other confusion.
 */
public class NeoProperties {
  // properties used in NeoTaxon
  public static final String CORE_ID = DcTerm.identifier.simpleName();
  public static final String NAME_ID = DwcTerm.scientificNameID.simpleName();
  public static final String TAXON_ID = DwcTerm.taxonID.simpleName();
  public static final String REFERENCE_ID = "referenceID";
  public static final String CITATION = "citation";
  public static final String RANK = "rank";
  public static final String SCIENTIFIC_NAME = "scientificName";
  public static final String AUTHORSHIP = "authorship";

  public static final String NULL_NAME = "???";

  private NeoProperties() {
  }

  public static Rank getRank(Node n, Rank defaultValue) {
    if (n.hasProperty(NeoProperties.RANK)) {
      return Rank.values()[(int) n.getProperty(NeoProperties.RANK)];
    }
    return defaultValue;
  }

  public static String getTaxonID(Node n) {
    return (String) n.getProperty(NeoProperties.TAXON_ID, null);
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
}
