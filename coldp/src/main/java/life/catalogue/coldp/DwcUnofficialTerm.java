package life.catalogue.coldp;

import org.gbif.dwc.terms.AlternativeNames;
import org.gbif.dwc.terms.Term;

import java.net.URI;

/**
 * Darwin Core terms that do not officially exist but are either used for
 * name relations and other CoL achievements in the context of Darwin Core archives
 * or found in use somewhere else.
 */
public enum DwcUnofficialTerm implements Term, AlternativeNames {
  NameRelation(true),
  relatedNameUsageID,
  relationType,
  relationPublishedIn,
  relationPublishedInID,
  relationRemarks,

  superkingdom,
  superphylum,
  superclass,
  superorder,
  superfamily,
  subkingdom,
  subphylum,
  subclass,
  suborder,
  //subfamily # this term is already part of DwC
  tribe;

  public static final DwcUnofficialTerm[] HIGHER_RANKS = {
    superkingdom,
    subkingdom,
    superphylum,
    subphylum,
    superclass,
    subclass,
    superorder,
    suborder,
    superfamily,
    tribe
  };

  private static final String PREFIX = "coldwc";
  private static final String NS = "http://rs.catalogueoflife.org/terms/dwc/";
  private static final String DWC_NS = "http://rs.tdwg.org/dwc/terms/";
  private static final URI NS_URI = URI.create(NS);
  
  private final boolean isClass;
  private final String[] alternatives;

  DwcUnofficialTerm() {
    this(false);
  }

  DwcUnofficialTerm(boolean isClass) {
    this.alternatives = new String[]{DWC_NS + simpleName()};
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
