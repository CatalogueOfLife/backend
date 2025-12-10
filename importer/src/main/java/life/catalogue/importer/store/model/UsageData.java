package life.catalogue.importer.store.model;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.api.vocab.TaxonConceptRelType;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Simple wrapper to hold a normalizer node together with all data for a record
 * including a name and a taxon instance. This name usage conflates a name with a taxon and/or synonym
 * as it comes in in some formats like DwC and ACEF.
 *
 * It allows us to store the data as it is and then start normalizing it into cleanly delimited Name, Taxon and Synonym instances.
 * <p />
 * The modified flag can be used to (manually) track if an instance has changed and needs to be persisted.
 */
public class UsageData implements DSID<String>, VerbatimEntity {
  // either a taxon or a synonym - this can change during normalisation!
  public NameUsage usage; // we ignore and do not persist the name part !!!
  public String nameID; // instead we keep a reference to the name id only
  public final Set<String> proParteAcceptedIDs = new HashSet<>(); // optional additional accepted taxon ids allowing for multiple parents in case of pro parte synonyms

  // supplementary infos for a taxon
  public Treatment treatment;
  public final List<Distribution> distributions = new ArrayList<>();
  public final List<Media> media = new ArrayList<>();
  public final List<VernacularName> vernacularNames = new ArrayList<>();
  public final List<SpeciesEstimate> estimates = new ArrayList<>();
  public final List<TaxonProperty> properties = new ArrayList<>();
  // taxon relations
  public final List<RelationData<TaxonConceptRelType>> tcRelations = new ArrayList<>();
  public final List<RelationData<SpeciesInteractionType>> spiRelations = new ArrayList<>();

  // extra stuff not covered by above for normalizer only
  public Classification classification;
  public List<String> remarks = Lists.newArrayList();

  private static UsageData create(NameUsage nu, Origin origin, TaxonomicStatus status) {
    UsageData u = new UsageData();
    u.usage = nu;
    u.usage.setStatus(status);
    u.usage.setOrigin(origin);
    return u;
  }

  public static UsageData buildBareName(Origin origin) {
    return create(new BareName(), origin, TaxonomicStatus.BARE_NAME);
  }

  public static UsageData buildTaxon(Origin origin, TaxonomicStatus status) {
    return create(new Taxon(), origin, status);
  }

  public static UsageData buildSynonym(Origin origin, TaxonomicStatus status) {
    return create(new Synonym(), origin, status);
  }

  public Taxon asTaxon() {
    return usage instanceof Taxon ? (Taxon) usage : null;
  }
  
  public Synonym asSynonym() {
    return usage instanceof Synonym ? (Synonym) usage : null;
  }

  public NameUsageBase asNameUsageBase() {
    return usage instanceof NameUsageBase ? (NameUsageBase) usage : null;
  }

  public boolean isNameUsageBase() {
    return usage instanceof NameUsageBase;
  }

  public boolean isSynonym() {
    return usage instanceof Synonym;
  }

  public boolean isTaxon() {
    return usage instanceof Taxon;
  }

  public boolean isBareName() {
    return usage instanceof BareName;
  }

  @Override
  public Integer getVerbatimKey() {
    return usage.getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    usage.setVerbatimKey(verbatimKey);
  }
  
  @Override
  public String getId() {
    return usage.getId();
  }
  
  @Override
  public void setId(String id) {
    usage.setId(id);
  }
  
  @Override
  public Integer getDatasetKey() {
    return usage.getDatasetKey();
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    usage.setDatasetKey(key);
  }
  
  public void addRemark(String remark) {
    remarks.add(remark);
  }

  public List<RelationData<TaxonConceptRelType>> getTcRelations() {
    return tcRelations;
  }

  public void addTcRelation(RelationData<TaxonConceptRelType> rel) {
    this.tcRelations.add(rel);
  }

  public List<RelationData<SpeciesInteractionType>> getSpiRelations() {
    return spiRelations;
  }

  public void addSpiRelation(RelationData<SpeciesInteractionType> rel) {
    this.spiRelations.add(rel);
  }

  /**
   * Converts the current synonym usage into a taxon instance
   * @param status new taxon status
   * @return the replaced synonym usage
   */
  public Synonym convertToTaxon(TaxonomicStatus status) {
    Preconditions.checkArgument(isSynonym(), "Usage needs to be a synonym");
    Preconditions.checkArgument(status.isTaxon(), "Status needs to be a taxon status");
    final Synonym s = asSynonym();
    usage = new Taxon(s);
    usage.setStatus(status);
    return s;
  }

  /**
   * Converts the current taxon usage into a synonym instance
   * @param status new synonym status
   * @return the replaced taxon usage
   */
  public void convertToSynonym(TaxonomicStatus status) {
    Preconditions.checkArgument(!isSynonym(), "Usage needs to be a taxon");
    Preconditions.checkArgument(status.isSynonym(), "Status needs to be a synonym status");
    usage = new Synonym(asTaxon());
    usage.setStatus(status);
  }

  public boolean hasRelations() {
    return !tcRelations.isEmpty() || !spiRelations.isEmpty();
  }

  public boolean hasSupplementaryInfos() {
    return !distributions.isEmpty() ||
      !media.isEmpty() ||
      !vernacularNames.isEmpty() ||
      !estimates.isEmpty() ||
      !properties.isEmpty();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UsageData usageData = (UsageData) o;
    return
        Objects.equals(usage, usageData.usage) &&
        Objects.equals(treatment, usageData.treatment) &&
        Objects.equals(distributions, usageData.distributions) &&
        Objects.equals(media, usageData.media) &&
        Objects.equals(vernacularNames, usageData.vernacularNames) &&
        Objects.equals(estimates, usageData.estimates) &&
        Objects.equals(properties, usageData.properties) &&
        Objects.equals(classification, usageData.classification) &&
        Objects.equals(remarks, usageData.remarks);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage, treatment, distributions, media, vernacularNames, estimates, properties, classification, remarks);
  }

  @Override
  public String toString() {
    return String.format("%s -> %s [%s]", usage.getId(), usage.getParentId(), nameID);
  }
}