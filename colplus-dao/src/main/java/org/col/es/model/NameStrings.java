package org.col.es.model;

import java.util.Objects;

import org.col.api.model.Name;
import org.col.es.annotations.Analyzers;

import static org.col.es.NameUsageTransfer.normalizeStrongly;
import static org.col.es.NameUsageTransfer.normalizeWeakly;
import static org.col.es.annotations.Analyzer.AUTO_COMPLETE;

/**
 * An object embedded within the main name usage document ({@link EsNameUsage}) aimed at optimizing searchability. The name strings within
 * this class don't contribute to the search response object. They are only meant to match search phrase as best and as cheaply as possible.
 * This class also exists to ensure that search phrases are "analyzed" (i.e. preprocessed) just like the names coming in from postgres.
 */
public class NameStrings {

  private String genus;
  private String genusWN;
  private String genusLetter;
  private String specificEpithet;
  private String specificEpithetSN;
  private String infraspecificEpithet;
  private String infraspecificEpithetSN;

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    String s;
    if (name.getGenus() != null) {
      genus = name.getGenus().toLowerCase();
      s = normalizeWeakly(genus);
      if (!(s = normalizeWeakly(genus)).equals(genus)) {
        genusWN = s;
      }
      genusLetter = genus.substring(0, 1).toLowerCase();
    } ;
    if (name.getSpecificEpithet() != null) {
      specificEpithet = name.getSpecificEpithet().toLowerCase();
      if (!(s = normalizeStrongly(specificEpithet)).equals(specificEpithet)) {
        specificEpithetSN = s;
      }
    }
    if (name.getInfraspecificEpithet() != null) {
      infraspecificEpithet = name.getInfraspecificEpithet().toLowerCase();
      if (!(s = normalizeStrongly(infraspecificEpithet)).equals(infraspecificEpithet)) {
        infraspecificEpithetSN = s;
      }
    }
  }

  public NameStrings() {}

  @Analyzers({AUTO_COMPLETE})
  public String getGenus() {
    return genus;
  }

  public void setGenus(String genus) {
    this.genus = genus;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getGenusWN() {
    return genusWN;
  }

  public void setGenusWN(String genusWN) {
    this.genusWN = genusWN;
  }

  public String getGenusLetter() {
    return genusLetter;
  }

  public void setGenusLetter(String genusLetter) {
    this.genusLetter = genusLetter;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getSpecificEpithet() {
    return specificEpithet;
  }

  public void setSpecificEpithet(String specificEpithet) {
    this.specificEpithet = specificEpithet;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getSpecificEpithetSN() {
    return specificEpithetSN;
  }

  public void setSpecificEpithetSN(String specificEpithetSN) {
    this.specificEpithetSN = specificEpithetSN;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  public void setInfraspecificEpithet(String infraspecificEpithet) {
    this.infraspecificEpithet = infraspecificEpithet;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getInfraspecificEpithetSN() {
    return infraspecificEpithetSN;
  }

  public void setInfraspecificEpithetSN(String infraspecificEpithetSN) {
    this.infraspecificEpithetSN = infraspecificEpithetSN;
  }

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    NameStrings other = (NameStrings) obj;
    return Objects.equals(genus, other.genus)
        && Objects.equals(specificEpithet, other.specificEpithet)
        && Objects.equals(infraspecificEpithet, other.infraspecificEpithet);
  }

  public int hashCode() {
    return Objects.hash(genus, specificEpithet, infraspecificEpithet);
  }

}
