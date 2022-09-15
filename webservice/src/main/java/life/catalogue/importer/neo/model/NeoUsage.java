package life.catalogue.importer.neo.model;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;

import java.util.List;
import java.util.Objects;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

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
public class NeoUsage implements NeoNode, DSID<String>, VerbatimEntity {
  private static final Label[] TAX_LABELS = new Label[]{Labels.USAGE, Labels.TAXON};
  private static final Label[] SYN_LABELS = new Label[]{Labels.USAGE, Labels.SYNONYM};

  public Node node;
  // the neo4j name node, related via HAS_NAME
  public Node nameNode;
  // either a taxon or a synonym - this can change during normalisation!
  public NameUsage usage;
  public boolean homotypic = false;

  // supplementary infos for a taxon
  public String taxonID;
  public String taxonConceptID;
  public Treatment treatment;
  public List<Distribution> distributions = Lists.newArrayList();
  public List<Media> media = Lists.newArrayList();
  public List<VernacularName> vernacularNames = Lists.newArrayList();
  public List<SpeciesEstimate> estimates = Lists.newArrayList();

  // extra stuff not covered by above for normalizer only
  public Classification classification;
  public List<String> remarks = Lists.newArrayList();
  
  private static NeoUsage create(NameUsage nu, Origin origin, Name name, TaxonomicStatus status) {
    NeoUsage u = new NeoUsage();
    u.usage = nu;
    u.usage.setStatus(status);
    u.usage.setOrigin(origin);
    if (name != null) {
      u.usage.setName(name);
      name.setOrigin(origin);
    }
    return u;
  }

  public static NeoUsage createBareName(Origin origin) {
    return createBareName(origin, null);
  }

  public static NeoUsage createBareName(Origin origin, Name name) {
    return create(new BareName(), origin, name, TaxonomicStatus.BARE_NAME);
  }

  public static NeoUsage createTaxon(Origin origin, TaxonomicStatus status) {
    return createTaxon(origin, null, status);
  }
  
  public static NeoUsage createTaxon(Origin origin, Name name, TaxonomicStatus status) {
    return create(new Taxon(), origin, name, status);
  }
  
  public static NeoUsage createSynonym(Origin origin, TaxonomicStatus status) {
    return createSynonym(origin, null, status);
  }
  
  public static NeoUsage createSynonym(Origin origin, Name name, TaxonomicStatus status) {
    return create(new Synonym(), origin, name, status);
  }
  
  @Override
  public Node getNode() {
    return node;
  }
  
  @Override
  public void setNode(Node node) {
    this.node = node;
  }
  
  public NeoName getNeoName() {
    Preconditions.checkNotNull(nameNode, "Name node missing");
    return new NeoName(nameNode, usage.getName());
  }
  
  @Override
  public PropLabel propLabel() {
    return new PropLabel(isSynonym() ? SYN_LABELS : TAX_LABELS);
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
  public Taxon convertToSynonym(TaxonomicStatus status) {
    Preconditions.checkArgument(!isSynonym(), "Usage needs to be a taxon");
    Preconditions.checkArgument(status.isSynonym(), "Status needs to be a synonym status");
    final Taxon t = asTaxon();
    usage = new Synonym(t);
    usage.setStatus(status);
    return t;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoUsage neoUsage = (NeoUsage) o;
    return this.equalNode(neoUsage) &&
        Objects.equals(usage, neoUsage.usage) &&
        Objects.equals(treatment, neoUsage.treatment) &&
        Objects.equals(distributions, neoUsage.distributions) &&
        Objects.equals(media, neoUsage.media) &&
        Objects.equals(vernacularNames, neoUsage.vernacularNames) &&
        Objects.equals(classification, neoUsage.classification) &&
        Objects.equals(remarks, neoUsage.remarks);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(node, usage, treatment, distributions, media, vernacularNames, classification, remarks);
  }
}