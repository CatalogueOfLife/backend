package life.catalogue.api.vocab;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * Terms to represent content in Text Tree format.
 */
public enum TxtTreeTerm implements Term, AlternativeNames {
  Tree(true),
  indent,
  content;

  private static final String PREFIX = "tt";
  private static final String NS = "http://gbif.org/txttree/";
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
