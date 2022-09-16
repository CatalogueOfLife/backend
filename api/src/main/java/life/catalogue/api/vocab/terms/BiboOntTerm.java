package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * Reference terms from the bibo ontology as used by Plazi for EOL references.
 */
public enum BiboOntTerm implements Term, AlternativeNames {
  pages,
  pageStart,
  pageEnd,
  journal,
  volume,
  authorList,
  editorList,
  uri,
  doi;

  private static final String PREFIX = "bibo";
  private static final String NS = "http://purl.org/ontology/bibo/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  BiboOntTerm() {
    this(false);
  }

  BiboOntTerm(boolean isClass) {
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
