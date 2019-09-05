package org.col.es.model;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.col.api.model.Name;
import org.col.api.search.NameSearchResponse;
import org.col.es.mapping.Analyzers;
import org.col.es.name.index.NameUsageWrapperConverter;

import static org.col.es.mapping.Analyzer.AUTO_COMPLETE;
import static org.col.es.mapping.Analyzer.KEYWORD;
import static org.col.es.name.index.NameUsageWrapperConverter.normalizeStrongly;
import static org.col.es.name.index.NameUsageWrapperConverter.normalizeWeakly;

/**
 * An object embedded within the name usage document solely aimed at optimizing searchability. The name strings within
 * this class do not contribute to the response returned to the client ({@link NameSearchResponse}). They are meant to
 * match search phrases as best and as cheaply as possible. This class also exists to ensure that search phrases are
 * analyzed just like the names coming in from postgres.
 */
public class NameStrings {

  public static String[] tokenize(String searchPhrase) {
    return StringUtils.split(searchPhrase.trim(), ' ');
  }

  private String genus;
  private String genusWN;
  private char genusLetter;
  private String specificEpithet;
  private String specificEpithetSN;
  private String infraspecificEpithet;
  private String infraspecificEpithetSN;
  private String scientificNameWN;

  /**
   * Creates a {@code NameStrings} object from a search phrase, presumably coming in from the frontend.
   * 
   * @param searchPhrase
   */
  public NameStrings(String searchPhrase) {
    this(createNameFromSearchPhrase(searchPhrase));
  }

  /**
   * Creates a {@code NameStrings} object from the provided {@link Name}, presumably coming in from postgres.
   * 
   * @param name
   */
  public NameStrings(Name name) {
    scientificNameWN = NameUsageWrapperConverter.normalizeWeakly(name.getScientificName());
    String s;
    if (name.getGenus() != null) {
      genus = name.getGenus().toLowerCase();
      genusLetter = genus.charAt(0);
      s = normalizeWeakly(genus);
      if (!s.equals(genus)) {
        genusWN = s;
      }
    }
    if (name.getSpecificEpithet() != null) {
      specificEpithet = name.getSpecificEpithet().toLowerCase();
      s = normalizeStrongly(specificEpithet);
      if (!s.equals(specificEpithet)) {
        specificEpithetSN = s;
      }
    }
    if (name.getInfraspecificEpithet() != null) {
      infraspecificEpithet = name.getInfraspecificEpithet().toLowerCase();
      s = normalizeStrongly(infraspecificEpithet);
      if (!s.equals(infraspecificEpithet)) {
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

  public char getGenusLetter() {
    return genusLetter;
  }

  public void setGenusLetter(char genusLetter) {
    this.genusLetter = genusLetter;
  }

  @Analyzers({AUTO_COMPLETE})
  public String getGenusWN() {
    return genusWN;
  }

  public void setGenusWN(String genusWN) {
    this.genusWN = genusWN;
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

  @Analyzers({KEYWORD, AUTO_COMPLETE})
  public String getScientificNameWN() {
    return scientificNameWN;
  }

  public void setScientificNameWN(String scientificNameWN) {
    this.scientificNameWN = scientificNameWN;
  }

  /*
   * Artificially constructs a name from a search phrase, just so we can be sure that names and search phrases will be
   * preprocessed in exactly the same way before being matched against each other.
   */
  private static Name createNameFromSearchPhrase(String searchPhrase) {
    Name name = new Name();
    // Looks odd, but it just means that, if nothing else works, we'll let Elasticsearch match the entire search phrase
    // against the entire scientific name
    name.setScientificName(searchPhrase);
    String[] terms = tokenize(searchPhrase);
    switch (terms.length) {
      case 1:
        // Looks odd, but it just means that we are going to match the one and only term in the search phrase against genus,
        // specific epithet and infraspecific epithet
        setGenus(name, terms[0]);
        name.setSpecificEpithet(terms[0]);
        name.setInfraspecificEpithet(terms[0]);
        break;
      case 2:
        // We are going to match the 1st term in the search phrase against the genus and the 2nd against either the specific
        // epithet or the infraspecific epithet
        setGenus(name, terms[0]);
        name.setSpecificEpithet(terms[1]);
        name.setInfraspecificEpithet(terms[1]);
        break;
      case 3:
        setGenus(name, terms[0]);
        name.setSpecificEpithet(terms[1]);
        name.setInfraspecificEpithet(terms[2]);
      default:
        // Do nothing; we'll fall back on matching against entire scientific name
    }
    return name;
  }

  private static void setGenus(Name name, String term) {
    if (term.length() == 2 && term.charAt(1) == '.') {
      name.setGenus(String.valueOf(term.charAt(0)));
    } else {
      name.setGenus(term);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(genus,
        genusLetter,
        genusWN,
        infraspecificEpithet,
        infraspecificEpithetSN,
        scientificNameWN,
        specificEpithet,
        specificEpithetSN);
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
    return Objects.equals(genus, other.genus)
        && genusLetter == other.genusLetter
        && Objects.equals(genusWN, other.genusWN)
        && Objects.equals(infraspecificEpithet, other.infraspecificEpithet)
        && Objects.equals(infraspecificEpithetSN, other.infraspecificEpithetSN)
        && Objects.equals(scientificNameWN, other.scientificNameWN)
        && Objects.equals(specificEpithet, other.specificEpithet)
        && Objects.equals(specificEpithetSN, other.specificEpithetSN);
  }

}
