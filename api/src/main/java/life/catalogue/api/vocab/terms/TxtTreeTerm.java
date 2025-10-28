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
  // content is not allowed to use space, use underscores instead but not in ID fields where they are taken as is (ID,PUB,REF

  // verbatim fields with literal values
  ID,
  PUB, // published in referenceID
  REF, // taxonomic referenceIDs, concatenated by |
  SRC, // source datasetID
  LINK,
  MERGED, // merged flag for extended releases

  // value fields, underscores are decoded into spaces
  ENV,
  VERN,
  DIST, // distribution
  CHRONO, // temporal range
  CODE,
  NOM, // nomenclatural status
  TYPE, // type species or genus
  TM, // type material as type status:specimen citations, concatenated by |
  EST, // species estimate. Can be just a number for living species estimate, prefixed with the dagger symbol for extinct estimates or both: 45000,†340

  @Deprecated
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
