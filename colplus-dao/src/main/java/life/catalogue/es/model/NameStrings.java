package life.catalogue.es.model;

import java.util.Objects;

import life.catalogue.api.model.Name;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.es.mapping.Analyzers;
import org.gbif.nameparser.api.Rank;

import static life.catalogue.es.mapping.Analyzer.AUTO_COMPLETE;
import static life.catalogue.es.mapping.Analyzer.KEYWORD;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.name.NameUsageWrapperConverter.normalizeWeakly;

/**
 * An object embedded within the name usage document solely aimed at optimizing searchability. The name strings within
 * this class do not contribute to the response returned to the client ({@link NameUsageSearchResponse}). They are meant to
 * match search phrases as best and as cheaply as possible. A {@code NameStrings} object is created from the
 * {@link Name} in the indexed documents on the one hand and from the search phrase on the other, and the matching the
 * search phrase against the documents is done via the {@code NameStrings} object. To ensure that the search phrase is
 * analyzed just like the names, the search phrase is first converted into a (pretty artificial) name, and it is this
 * name that gets converted again into a {@code NameStrings} object. Finally note that we must store the name components
 * separately in order to be able to make preefix queries against each of them separately.
 * 
 */
public class NameStrings {

  @Analyzers({KEYWORD, AUTO_COMPLETE})
  private String genusOrMonomialWN;
  /*
   * We store the 1st letter of the genus separately to allow for fast term queries for search phrases like "P. major" or
   * "P major". Also note that the minimum ngram token size is 2, so we couldn't use an ngram search for this type of
   * search phrases. We could of course fall back on a prefix search for the genus in these cases (like we do for all
   * components of the search phrase if their length exceeds the **maximum** ngram token size), but these are not
   * efficient.
   */
  private String genusLetter;
  @Analyzers({KEYWORD, AUTO_COMPLETE})
  private String specificEpithetSN;
  @Analyzers({KEYWORD, AUTO_COMPLETE})
  private String infraspecificEpithetSN;

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    if (name.getUninomial() != null) {
      genusOrMonomialWN = normalizeWeakly(name.getUninomial());
      if (name.getRank() == Rank.GENUS) {
        genusLetter = String.valueOf(name.getUninomial().charAt(0)).toLowerCase();
      }
    } else if (name.getGenus() != null) {
      genusLetter = String.valueOf(name.getGenus().charAt(0)).toLowerCase();
      genusOrMonomialWN = normalizeWeakly(name.getGenus());
    }
    if (name.getSpecificEpithet() != null) {
      specificEpithetSN = normalizeStrongly(name.getSpecificEpithet());
    }
    if (name.getInfraspecificEpithet() != null) {
      infraspecificEpithetSN = normalizeStrongly(name.getInfraspecificEpithet());
    }
  }

  public NameStrings() {}

  public String getGenusLetter() {
    return genusLetter;
  }

  public void setGenusLetter(String genusLetter) {
    this.genusLetter = genusLetter;
  }

  public String getGenusOrMonomialWN() {
    return genusOrMonomialWN;
  }

  public void setGenusOrMonomialWN(String genusWN) {
    this.genusOrMonomialWN = genusWN;
  }

  public String getSpecificEpithetSN() {
    return specificEpithetSN;
  }

  public void setSpecificEpithetSN(String specificEpithetSN) {
    this.specificEpithetSN = specificEpithetSN;
  }

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
