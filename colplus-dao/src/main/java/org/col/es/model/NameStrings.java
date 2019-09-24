package org.col.es.model;

import java.util.Objects;

import org.col.api.model.Name;
import org.col.api.search.NameSearchResponse;
import org.col.es.mapping.Analyzers;
import org.gbif.nameparser.api.Rank;

import static org.col.es.mapping.Analyzer.AUTO_COMPLETE;
import static org.col.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static org.col.es.name.NameUsageWrapperConverter.normalizeWeakly;

/**
 * An object embedded within the name usage document solely aimed at optimizing searchability. The name strings within
 * this class do not contribute to the response returned to the client ({@link NameSearchResponse}). They are meant to
 * match search phrases as best and as cheaply as possible. A {@code NameStrings} object is created from the
 * {@link Name} in the indexed documents on the one hand and from the search phrase on the other, and the matching the
 * search phrase against the documents is done via the {@code NameStrings} object. To ensure that the search phrase is
 * analyzed just like the names, the search phrase is first converted into a (pretty artificial) name, and it is this
 * name that gets converted again into a {@code NameStrings} object. Finally note that we must store the name components
 * separately in order to be able to make preefix queries against each of them separately.
 * 
 */
public class NameStrings {

  /*
   * Currently we only allow the suggest service to hit species and lower, but this does not apply to the regular search.
   */
  private String genusOrMonomialWN;
  /*
   * We store the 1st letter of the genus separately to allow for fast term queries for search phrases like "P. major" or
   * "P major". Also note that the minimum ngram token size is 2, so we couldn't use an ngram search for this type of
   * search phrases. We could of course fall back on a prefix search for the genus in these cases (like we do for all
   * components of the search phrase if their length exceeds the __maximum__ ngram token size), but these are not
   * efficient.
   */
  private String genusLetter;
  private String specificEpithetSN;
  private String infraspecificEpithetSN;

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    // Null-checks may be overly defensive
    if (name.getRank().higherThan(Rank.GENUS) && name.getUninomial() != null) {
      genusOrMonomialWN = normalizeWeakly(name.getUninomial()).toLowerCase();
    } else if (name.getGenus() != null) {
      genusLetter = String.valueOf(name.getGenus().charAt(0)).toLowerCase();
      genusOrMonomialWN = normalizeWeakly(name.getGenus()).toLowerCase();
    }
    if (name.getSpecificEpithet() != null) {
      specificEpithetSN = normalizeStrongly(name.getSpecificEpithet()).toLowerCase();
    }
    if (name.getInfraspecificEpithet() != null) {
      infraspecificEpithetSN = normalizeStrongly(name.getInfraspecificEpithet()).toLowerCase();
    }
  }

  public NameStrings() {}

  public String getGenusLetter() {
    return genusLetter;
  }

  public void setGenusLetter(String genusLetter) {
    this.genusLetter = genusLetter;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getGenusOrMonomialWN() {
    return genusOrMonomialWN;
  }

  public void setGenusOrMonomialWN(String genusWN) {
    this.genusOrMonomialWN = genusWN;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getSpecificEpithetSN() {
    return specificEpithetSN;
  }

  public void setSpecificEpithetSN(String specificEpithetSN) {
    this.specificEpithetSN = specificEpithetSN;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getInfraspecificEpithetSN() {
    return infraspecificEpithetSN;
  }

  public void setInfraspecificEpithetSN(String infraspecificEpithetSN) {
    this.infraspecificEpithetSN = infraspecificEpithetSN;
  }

  @Override
  public int hashCode() {
    return Objects.hash(genusLetter, genusOrMonomialWN, infraspecificEpithetSN, specificEpithetSN);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NameStrings other = (NameStrings) obj;
    return Objects.equals(genusLetter, other.genusLetter) && Objects.equals(genusOrMonomialWN, other.genusOrMonomialWN)
        && Objects.equals(infraspecificEpithetSN, other.infraspecificEpithetSN)
        && Objects.equals(specificEpithetSN, other.specificEpithetSN);
  }

}
