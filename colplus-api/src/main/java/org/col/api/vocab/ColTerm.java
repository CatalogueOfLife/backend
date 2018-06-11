package org.col.api.vocab;

import java.net.URI;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

/**
 * New CoL terms used for name relations and other CoL+ achievements.
 */
public enum ColTerm implements Term, AlternativeNames {
  NameRelations(true),
  relatedNameUsageID,
  relationType,
  publishedIn,
  publishedInID,
  relationRemarks;

  private static final String PREFIX = "col";
  private static final String NS = "http://rs.col.plus/terms/";
  private static final URI NS_URI = URI.create(NS);

  private final boolean isClass;
  private final String[] alternatives;

  ColTerm(boolean isClass, String ... alternatives) {
    this.alternatives = alternatives;
    this.isClass = isClass;
  }

  ColTerm(String ... alternatives) {
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
