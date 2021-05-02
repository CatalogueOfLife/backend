package life.catalogue.api.vocab;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * New CoL terms used for name relations and other CoL+ achievements in the context of Darwin Core archives.
 */
public enum ColDwcTerm implements Term, AlternativeNames {
  NameRelations(true),
  relatedNameUsageID,
  relationType,
  publishedIn,
  publishedInID,
  relationRemarks;
  
  private static final String PREFIX = "coldwc";
  private static final String NS = "http://rs.col.plus/terms/dwc/";
  private static final URI NS_URI = URI.create(NS);
  
  private final boolean isClass;
  private final String[] alternatives;
  
  ColDwcTerm(boolean isClass, String... alternatives) {
    this.alternatives = alternatives;
    this.isClass = isClass;
  }
  
  ColDwcTerm(String... alternatives) {
    this(false, alternatives);
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
