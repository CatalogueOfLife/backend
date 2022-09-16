package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * iNaturalist terms as used in their dwca taxonomy file.
 */
public enum InatTerm implements Term, AlternativeNames {
  lexicon;

  private static final String PREFIX = "inat";
  private static final String NS = "https://www.inaturalist.org/terms/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  InatTerm() {
    this(false);
  }

  InatTerm(boolean isClass) {
    this.alternatives = new String[0];
    this.isClass = isClass;
  }
  
  @Override
  public String prefix() {
    return PREFIX;
  }
  
  @Override
  public URI namespace() {
    return NS_URI;
  }
  
  @Override
  public String simpleName() {
    return name();
  }
  
  @Override
  public String toString() {
    return prefixedName();
  }
  
  @Override
  public String[] alternativeNames() {
    return this.alternatives;
  }
  
  @Override
  public boolean isClass() {
    return isClass;
  }
  
}
