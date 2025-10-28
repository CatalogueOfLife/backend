package life.catalogue.es;

import life.catalogue.api.model.Name;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.ddl.Analyzers;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import static life.catalogue.es.ddl.Analyzer.SCINAME_AUTO_COMPLETE;
import static life.catalogue.es.ddl.Analyzer.SCINAME_IGNORE_CASE;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeWeakly;

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
  private String[] genusOrMonomial; // Lower case and weakly normalized versions of genus/uninomial
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String[] infragenericEpithet; // Lower case and strongly normalized versions of infrageneric epithet
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String[] specificEpithet; // Lower case and strongly normalized versions of specific epithet
  @Analyzers({SCINAME_IGNORE_CASE, SCINAME_AUTO_COMPLETE})
  private String[] infraspecificEpithet; // Lower case and strongly normalized versions of infraspecific epithet

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    if (!StringUtils.isBlank(name.getScientificName())) {
      sciNameLetter = Character.toLowerCase(name.getScientificName().charAt(0));
    }
    if (!StringUtils.isBlank(name.getGenus())) {
      genusLetter = Character.toLowerCase(name.getGenus().charAt(0));
      genusOrMonomial = getStrings(name.getGenus(), normalizeWeakly(name.getGenus()));
    } else if (!StringUtils.isBlank(name.getUninomial())) {
      genusOrMonomial = getStrings(name.getUninomial().toLowerCase(), normalizeWeakly(name.getUninomial()));
    }
    // we used to use the strong normaliser to index species/infraspecific epithets...
    // But that caused more problems than it helped...
    if (!StringUtils.isBlank(name.getInfragenericEpithet())) {
      infragenericEpithet = getStrings(name.getInfragenericEpithet().toLowerCase(), normalizeWeakly(name.getInfragenericEpithet()));
    }
    if (!StringUtils.isBlank(name.getSpecificEpithet())) {
      specificEpithet = getStrings(name.getSpecificEpithet().toLowerCase(), normalizeWeakly(name.getSpecificEpithet()));
    }
    if (!StringUtils.isBlank(name.getInfraspecificEpithet())) {
      infraspecificEpithet = getStrings(name.getInfraspecificEpithet().toLowerCase(), normalizeWeakly(name.getInfraspecificEpithet()));
    }
  }

  public NameStrings() {}

  public char getSciNameLetter() {
    return sciNameLetter;
  }

  public char getGenusLetter() {
    return genusLetter;
  }

  public String[] getGenusOrMonomial() {
    return genusOrMonomial;
  }

  public String[] getInfragenericEpithet() {
    return infragenericEpithet;
  }

  public String[] getSpecificEpithet() {
    return specificEpithet;
  }

  public String[] getInfraspecificEpithet() {
    return infraspecificEpithet;
  }

  public void setSciNameLetter(char sciNameLetter) {
    this.sciNameLetter = sciNameLetter;
  }

  public void setGenusLetter(char genusLetter) {
    this.genusLetter = genusLetter;
  }

  public void setGenusOrMonomialWN(String[] genusOrMonomial) {
    this.genusOrMonomial = genusOrMonomial;
  }

  public void setInfragenericEpithet(String[] infragenericEpithet) {
    this.infragenericEpithet = infragenericEpithet;
  }

  public void setSpecificEpithetSN(String[] specificEpithet) {
    this.specificEpithet = specificEpithet;
  }

  public void setInfraspecificEpithetSN(String[] infraspecificEpithet) {
    this.infraspecificEpithet = infraspecificEpithet;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NameStrings)) return false;
    NameStrings that = (NameStrings) o;
    return sciNameLetter == that.sciNameLetter && genusLetter == that.genusLetter && Objects.deepEquals(genusOrMonomial, that.genusOrMonomial) && Objects.deepEquals(infragenericEpithet, that.infragenericEpithet) && Objects.deepEquals(specificEpithet, that.specificEpithet) && Objects.deepEquals(infraspecificEpithet, that.infraspecificEpithet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sciNameLetter, genusLetter, Arrays.hashCode(genusOrMonomial), Arrays.hashCode(infragenericEpithet), Arrays.hashCode(specificEpithet), Arrays.hashCode(infraspecificEpithet));
  }

  private static String[] getStrings(String literal, String normalized) {
    literal = literal.toLowerCase();
    return literal.equals(normalized) ? new String[] {literal} : new String[] {literal, normalized};
  }

}
