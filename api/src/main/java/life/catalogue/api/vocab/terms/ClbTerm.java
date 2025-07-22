package life.catalogue.api.vocab.terms;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;

import java.net.URI;

/**
 * ChecklistBank specific terms
 */
public enum ClbTerm implements Term, AlternativeNames {
  taxGroup,
  taxGroupFromName;

  private static final String PREFIX = "clb";
  private static final String NS = "http://rs.checklistbank.org/terms/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  ClbTerm() {
    this.alternatives = new String[0];
    this.isClass = false;
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
