package life.catalogue.api.vocab;

import life.catalogue.coldp.ColdpTerm;

/**
 * Enumeration of major entities in the system.
 */
public enum EntityType {
  ANY(null),
  NAME(ColdpTerm.Name),
  NAME_RELATION(ColdpTerm.NameRelation),
  NAME_USAGE(ColdpTerm.NameUsage),
  TAXON_CONCEPT_RELATION(ColdpTerm.TaxonConceptRelation),
  TAXON_PROPERTY(ColdpTerm.TaxonProperty),
  TYPE_MATERIAL(ColdpTerm.TypeMaterial),
  TREATMENT(ColdpTerm.Treatment),
  DISTRIBUTION(ColdpTerm.Distribution),
  MEDIA(ColdpTerm.Media),
  VERNACULAR(ColdpTerm.VernacularName),
  REFERENCE(ColdpTerm.Reference),
  ESTIMATE(ColdpTerm.SpeciesEstimate),
  SPECIES_INTERACTION(ColdpTerm.SpeciesInteraction);

  public final ColdpTerm coldp;

  EntityType(ColdpTerm coldp) {
    this.coldp = coldp;
  }
}