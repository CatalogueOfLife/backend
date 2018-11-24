package org.col.admin.importer.neo.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.col.api.model.*;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

/**
 * Simple wrapper to hold a normalizer node together with all data for a record
 * including a name and a taxon instance. This name usage conflates a name with a taxon and/or synonym
 * as it comes in in some formats like DwC and ACEF.
 *
 * It allows us to store the data as it is and then start normalizing it into cleanly delimited Name, Taxon and Synonym instances.
 * <p />
 * The modified flag can be used to (manually) track if an instance has changed and needs to be persisted.
 */
public class NeoUsage implements NeoNode, ID, VerbatimEntity {
  private static final Label[] TAX_LABELS = new Label[]{Labels.USAGE, Labels.TAXON};
  private static final Label[] SYN_LABELS = new Label[]{Labels.USAGE, Labels.SYNONYM};

  public Node node;
  // the neo4j name node, related via HAS_NAME
  public Node nameNode;
  // either a taxon or a synonym - this can change during normalisation!
  public NameUsage usage;
  public boolean homotypic = false;

  // supplementary infos for a taxon
  public List<Description> descriptions = Lists.newArrayList();
  public List<Distribution> distributions = Lists.newArrayList();
  public List<Media> media = Lists.newArrayList();
  public List<VernacularName> vernacularNames = Lists.newArrayList();
  public Set<String> bibliography = Sets.newHashSet();

  // extra stuff not covered by above for normalizer only
  public Classification classification;
  public List<String> remarks = Lists.newArrayList();
  
  public static NeoUsage createTaxon(Origin origin, boolean provisional) {
    return createTaxon(origin, null, provisional);
  }
  
  public static NeoUsage createTaxon(Origin origin, Name name, boolean provisional) {
    NeoUsage u = new NeoUsage();
    Taxon tax = new Taxon();
    tax.setProvisional(provisional);
    tax.setOrigin(origin);

    if (name != null) {
      tax.setName(name);
      name.setOrigin(origin);
    }
    
    u.usage = tax;
    return u;
  }
  
  public static NeoUsage createSynonym(Origin origin, TaxonomicStatus status) {
    return createSynonym(origin, null, status);
  }
  
  public static NeoUsage createSynonym(Origin origin, Name name, TaxonomicStatus status) {
    NeoUsage u = new NeoUsage();
    Synonym syn = new Synonym();
    syn.setOrigin(origin);
    syn.setStatus(status);
    if (name != null) {
      syn.setName(name);
      name.setOrigin(origin);
    }
    u.usage = syn;
    return u;
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
  
  public Taxon getTaxon() {
    return usage instanceof Taxon ? (Taxon) usage : null;
  }
  
  public Synonym getSynonym() {
    return usage instanceof Synonym ? (Synonym) usage : null;
  }
  
  public boolean isSynonym() {
    return usage instanceof Synonym;
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
  
  public void addRemark(String remark) {
    remarks.add(remark);
  }
  
  public Taxon convertToSynonym(TaxonomicStatus status) {
    Preconditions.checkArgument(!isSynonym(), "Usage needs to be a taxon");
    Preconditions.checkArgument(status.isSynonym(), "Status needs to be a synonym status");
    final Taxon t = getTaxon();
    final Synonym syn = new Synonym();
    syn.setId(t.getId());
    syn.setName(t.getName());
    syn.setOrigin(t.getOrigin());
    syn.setAccordingTo(t.getAccordingTo());
    syn.setVerbatimKey(t.getVerbatimKey());
    syn.setStatus(status);
    usage = syn;
    
    return t;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoUsage neoUsage = (NeoUsage) o;
    return this.equalNode(neoUsage) &&
        Objects.equals(usage, neoUsage.usage) &&
        Objects.equals(descriptions, neoUsage.descriptions) &&
        Objects.equals(distributions, neoUsage.distributions) &&
        Objects.equals(media, neoUsage.media) &&
        Objects.equals(vernacularNames, neoUsage.vernacularNames) &&
        Objects.equals(bibliography, neoUsage.bibliography) &&
        Objects.equals(classification, neoUsage.classification) &&
        Objects.equals(remarks, neoUsage.remarks);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(node, usage, descriptions, distributions, media, vernacularNames, bibliography, classification, remarks);
  }
}