package life.catalogue.es.model;

import java.util.Objects;
import life.catalogue.api.model.Name;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.mapping.Analyzers;
import static life.catalogue.es.mapping.Analyzer.SCINAME_AUTO_COMPLETE;
import static life.catalogue.es.mapping.Analyzer.SCINAME_IGNORE_CASE;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeWeakly;

/**
 * An object embedded within the name usage document solely aimed at optimizing searchability. The name strings within this class do not
 * contribute to the response returned to the client ({@link NameUsageSearchResponse}). They are meant to match search phrases as best and
 * as cheaply as possible. A {@code NameStrings} object is created from the {@link Name} in the indexed documents on the one hand and from
 * the search phrase on the other, and the matching the search phrase against the documents is done via the {@code NameStrings} object. To
 * ensure that the search phrase is analyzed just like the names, the search phrase is first converted into a (pretty artificial) name, and
 * it is this name that gets converted again into a {@code NameStrings} object. Finally note that we must store the name components
 * separately in order to be able to make preefix queries against each of them separately.
 * 
 */
public class NameStrings {

  private char sciNameLetter; // Used for alphabetical index
  private char genusLetter; // Used for fast term queries with search phrases like "H sapiens"
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String genusOrMonomialWN;
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String specificEpithetSN;
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String infraspecificEpithetSN;

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    if (name.getScientificName() != null) {
      sciNameLetter = Character.toLowerCase(name.getScientificName().charAt(0));
    }
    if (name.getGenus() != null) {
      genusLetter = Character.toLowerCase(name.getGenus().charAt(0));
      genusOrMonomialWN = normalizeWeakly(name.getGenus());
    } else if (name.getUninomial() != null) {
      genusOrMonomialWN = normalizeWeakly(name.getUninomial());
    }
    if (name.getSpecificEpithet() != null) {
      specificEpithetSN = normalizeStrongly(name.getSpecificEpithet());
    }
    if (name.getInfraspecificEpithet() != null) {
      infraspecificEpithetSN = normalizeStrongly(name.getInfraspecificEpithet());
    }
  }

  public NameStrings() {}

  public char getSciNameLetter() {
    return sciNameLetter;
  }

  public char getGenusLetter() {
    return genusLetter;
  }

  public String getGenusOrMonomialWN() {
    return genusOrMonomialWN;
  }

  public String getSpecificEpithetSN() {
    return specificEpithetSN;
  }

  public String getInfraspecificEpithetSN() {
    return infraspecificEpithetSN;
  }

  public void setSciNameLetter(char sciNameLetter) {
    this.sciNameLetter = sciNameLetter;
  }

  public void setGenusLetter(char genusLetter) {
    this.genusLetter = genusLetter;
  }

  public void setGenusOrMonomialWN(String genusOrMonomialWN) {
    this.genusOrMonomialWN = genusOrMonomialWN;
  }

  public void setSpecificEpithetSN(String specificEpithetSN) {
    this.specificEpithetSN = specificEpithetSN;
  }

  public void setInfraspecificEpithetSN(String infraspecificEpithetSN) {
    this.infraspecificEpithetSN = infraspecificEpithetSN;
  }

  @Override
  public int hashCode() {
    return Objects.hash(genusLetter, genusOrMonomialWN, infraspecificEpithetSN, sciNameLetter, specificEpithetSN);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    NameStrings other = (NameStrings) obj;
    return genusLetter == other.genusLetter && Objects.equals(genusOrMonomialWN, other.genusOrMonomialWN)
        && Objects.equals(infraspecificEpithetSN, other.infraspecificEpithetSN) && sciNameLetter == other.sciNameLetter
        && Objects.equals(specificEpithetSN, other.specificEpithetSN);
  }

}
