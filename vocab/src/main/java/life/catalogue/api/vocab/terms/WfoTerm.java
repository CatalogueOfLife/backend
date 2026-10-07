package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * World Flora Online terms as used in their dwca download file.
 */
public enum WfoTerm implements Term, AlternativeNames {
  localID,
  wfoID,
  tplID,
  ipniID,
  bryoID,
  majorGroup;

  private static final String PREFIX = "wfo";
  private static final String NS = "http://rs.worldfloraonline.org/terms/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass = false;
  private final String[] alternatives;

  WfoTerm() {
    // bad domain in WFO downloads!
    this.alternatives = new String[]{"http://rs.worldfloraonline/terms/"+name()};
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
