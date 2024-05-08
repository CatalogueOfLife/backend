package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * Terms to represent content in Text Tree format.
 */
public enum TxtTreeTerm implements Term, AlternativeNames {
  Tree(true),
  indent,
  content,
  // data info property keys
  ID,
  PUB,
  REF,
  ENV,
  VERN,
  SRC,
  CHRONO, // temporal range
  LINK,
  CODE,
  PROV; // provisional

  private static final String PREFIX = "tt";
  private static final String NS = "http://rs.gbif.org.org/terms/1.0/txtree/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;

  TxtTreeTerm(boolean isClass) {
    this.isClass = isClass;
  }

  TxtTreeTerm() {
    this(false);
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
    return new String[0];
  }
  
  @Override
  public boolean isClass() {
    return isClass;
  }
  
}
