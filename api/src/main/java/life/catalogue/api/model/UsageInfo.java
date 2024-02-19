package life.catalogue.api.model;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.vocab.TaxGroup;

/**
 * Aggregated usage information, mostly for taxa, but also for synonyms
 */
public class UsageInfo {
  
  private final NameUsageBase usage;
  private List<SimpleName> classification;
  private TaxGroup group;
  private VerbatimSource source;
  private Treatment treatment;
  private List<NameUsageRelation> nameRelations;
  // taxa only
  private Synonymy synonyms;
  private List<Distribution> distributions;
  private List<VernacularName> vernacularNames;
  private List<Media> media;
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

  /**
   * Lookup of usageID to list of decisions that were applied to form the given usage in a release
   */
  private Map<String, EditorialDecision> decisions = new HashMap<>();

  public UsageInfo(NameUsageBase usage) {
    this.usage = usage;
  }

  @JsonIgnore
  public String getId() {
    return usage.getId();
  }

  public NameUsageBase getUsage() {
    return usage;
  }

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public TaxGroup getGroup() {
    return group;
  }

  public void setGroup(TaxGroup group) {
    this.group = group;
  }

  @Deprecated
  public NameUsageBase getTaxon() {
    return usage;
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
    return references.getOrDefault(usage.getName().getPublishedInId(), null);
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

  public Map<String, EditorialDecision> getDecisions() {
    return decisions;
  }

  public void setDecisions(Map<String, EditorialDecision> decisions) {
    this.decisions = decisions;
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

  public List<NameUsageRelation> getNameRelations() {
    return nameRelations;
  }

  public void setNameRelations(List<NameUsageRelation> nameRelations) {
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
    if (o == null || getClass() != o.getClass()) return false;
    UsageInfo usageInfo = (UsageInfo) o;
    return Objects.equals(usage, usageInfo.usage) && Objects.equals(classification, usageInfo.classification) && group == usageInfo.group && Objects.equals(source, usageInfo.source) && Objects.equals(treatment, usageInfo.treatment) && Objects.equals(nameRelations, usageInfo.nameRelations) && Objects.equals(synonyms, usageInfo.synonyms) && Objects.equals(distributions, usageInfo.distributions) && Objects.equals(vernacularNames, usageInfo.vernacularNames) && Objects.equals(media, usageInfo.media) && Objects.equals(properties, usageInfo.properties) && Objects.equals(conceptRelations, usageInfo.conceptRelations) && Objects.equals(speciesInteractions, usageInfo.speciesInteractions) && Objects.equals(typeMaterial, usageInfo.typeMaterial) && Objects.equals(references, usageInfo.references) && Objects.equals(names, usageInfo.names) && Objects.equals(taxa, usageInfo.taxa) && Objects.equals(decisions, usageInfo.decisions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, classification, group, source, treatment, nameRelations, synonyms, distributions, vernacularNames, media, properties, conceptRelations, speciesInteractions, typeMaterial, references, names, taxa, decisions);
  }
}
