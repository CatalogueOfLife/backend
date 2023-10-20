package life.catalogue.api.model;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Aggregated taxon information
 */
public class TaxonInfo {
  
  private Taxon taxon;
  private Treatment treatment;
  private VerbatimSource source;
  private Synonymy synonyms;
  private List<Distribution> distributions;
  private List<VernacularName> vernacularNames;
  private List<Media> media;
  private List<NameRelation> nameRelations;
  private List<TaxonProperty> properties;
  private List<TaxonConceptRelation> conceptRelations;
  private List<SpeciesInteraction> speciesInteractions;

  /**
   * Lookup of types for a given nameID
   */
  private Map<String, List<TypeMaterial>> typeMaterial = new HashMap<>();

  /**
   * Lookup of referenceID to reference so the same reference can be shared across different objects
   * saving json serialization size.
   */
  private Map<String, Reference> references = new HashMap<>();

  /**
   * Lookup of nameID to name so the same name can be shared across different objects, i.e. name relations
   */
  private Map<String, Name> names = new HashMap<>();

  /**
   * Lookup of taxonID to taxon so the same taxon can be shared across different objects, i.e. concept relations or species interactions
   */
  private Map<String, Taxon> taxa = new HashMap<>();

  public TaxonInfo() {
  }

  public TaxonInfo(Taxon taxon) {
    this.taxon = taxon;
  }

  @JsonIgnore
  public String getId() {
    return taxon.getId();
  }

  public Taxon getTaxon() {
    return taxon;
  }
  
  public void setTaxon(Taxon taxon) {
    this.taxon = taxon;
  }

  public VerbatimSource getSource() {
    return source;
  }

  public void setSource(VerbatimSource source) {
    this.source = source;
  }

  public Synonymy getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(Synonymy synonyms) {
    this.synonyms = synonyms;
  }

  public List<VernacularName> getVernacularNames() {
    return vernacularNames;
  }
  
  public void setVernacularNames(List<VernacularName> vernacularNames) {
    this.vernacularNames = vernacularNames;
  }
  
  public List<Distribution> getDistributions() {
    return distributions;
  }
  
  public void setDistributions(List<Distribution> distributions) {
    this.distributions = distributions;
  }
  
  public List<Media> getMedia() {
    return media;
  }
  
  public void setMedia(List<Media> media) {
    this.media = media;
  }
  
  public Treatment getTreatment() {
    return treatment;
  }
  
  public void setTreatment(Treatment treatment) {
    this.treatment = treatment;
  }
  
  public Reference getReference(String id) {
    return references.getOrDefault(id, null);
  }

  @JsonIgnore
  public Reference getPublishedInReference() {
    return references.getOrDefault(taxon.getName().getPublishedInId(), null);
  }

  public Map<String, Reference> getReferences() {
    return references;
  }
  
  public void setReferences(Map<String, Reference> references) {
    this.references = references;
  }
  
  public void addReference(Reference ref) {
    if (!references.containsKey(ref.getId())) {
      references.put(ref.getId(), ref);
    }
  }
  
  public void addReferences(Iterable<? extends Reference> refs) {
    refs.forEach(this::addReference);
  }

  public Map<String, Name> getNames() {
    return names;
  }

  public void addName(Name n) {
    if (!names.containsKey(n.getId())) {
      names.put(n.getId(), n);
    }
  }

  public void addNames(Iterable<? extends Name> names) {
    names.forEach(this::addName);
  }

  public void setNames(Map<String, Name> names) {
    this.names = names;
  }

  public Map<String, Taxon> getTaxa() {
    return taxa;
  }

  public void addTaxon(Taxon t) {
    if (!taxa.containsKey(t.getId())) {
      taxa.put(t.getId(), t);
    }
  }

  public void addTaxa(Iterable<? extends Taxon> taxa) {
    taxa.forEach(this::addTaxon);
  }

  public void setTaxa(Map<String, Taxon> taxa) {
    this.taxa = taxa;
  }

  public List<TypeMaterial> getTypeMaterial(String nameID) {
    return typeMaterial.getOrDefault(nameID, Collections.emptyList());
  }

  public Map<String, List<TypeMaterial>> getTypeMaterial() {
    return typeMaterial;
  }

  public void setTypeMaterial(Map<String, List<TypeMaterial>> typeMaterial) {
    this.typeMaterial = typeMaterial;
  }

  public List<NameRelation> getNameRelations() {
    return nameRelations;
  }

  public void setNameRelations(List<NameRelation> nameRelations) {
    this.nameRelations = nameRelations;
  }

  public List<TaxonProperty> getProperties() {
    return properties;
  }

  public void setProperties(List<TaxonProperty> properties) {
    this.properties = properties;
  }

  public List<TaxonConceptRelation> getConceptRelations() {
    return conceptRelations;
  }

  public void setConceptRelations(List<TaxonConceptRelation> conceptRelations) {
    this.conceptRelations = conceptRelations;
  }

  public List<SpeciesInteraction> getSpeciesInteractions() {
    return speciesInteractions;
  }

  public void setSpeciesInteractions(List<SpeciesInteraction> speciesInteractions) {
    this.speciesInteractions = speciesInteractions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaxonInfo)) return false;
    TaxonInfo taxonInfo = (TaxonInfo) o;
    return Objects.equals(taxon, taxonInfo.taxon)
           && Objects.equals(treatment, taxonInfo.treatment)
           && Objects.equals(source, taxonInfo.source)
           && Objects.equals(synonyms, taxonInfo.synonyms)
           && Objects.equals(distributions, taxonInfo.distributions)
           && Objects.equals(vernacularNames, taxonInfo.vernacularNames)
           && Objects.equals(media, taxonInfo.media)
           && Objects.equals(nameRelations, taxonInfo.nameRelations)
           && Objects.equals(properties, taxonInfo.properties)
           && Objects.equals(conceptRelations, taxonInfo.conceptRelations)
           && Objects.equals(speciesInteractions, taxonInfo.speciesInteractions)
           && Objects.equals(typeMaterial, taxonInfo.typeMaterial)
           && Objects.equals(references, taxonInfo.references)
           && Objects.equals(names, taxonInfo.names)
           && Objects.equals(taxa, taxonInfo.taxa);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taxon, treatment, source, synonyms, distributions, vernacularNames, media, nameRelations, properties, conceptRelations, speciesInteractions, typeMaterial, references, names, taxa);
  }
}
