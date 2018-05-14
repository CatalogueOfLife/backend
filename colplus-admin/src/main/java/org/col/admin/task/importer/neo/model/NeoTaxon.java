package org.col.admin.task.importer.neo.model;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.Lists;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.neo4j.graphdb.Node;

/**
 * Simple wrapper to hold a normalizer node together with all data for a record
 * inlcuding a name and a taxon instance.
 * <p>
 * The modified flag can be used to (manually) track if an instance has changed and needs to be persisted.
 */
public class NeoTaxon {
  public Node node;
  // the unescaped verbatim record to be used for further interpretation
  public UnescapedVerbatimRecord verbatim;
  // we keep the name distinct from the Taxon here so we can also accomodate listByTaxon which do not have a taxon instance!
  public Name name;
  // either a taxon or a synonym, never both!
  public Taxon taxon;
  // the accepted property of synonym is NOT populated
  public Synonym synonym;
  public boolean homotypic = false;

  // supplementary infos for a taxon
  public List<VernacularName> vernacularNames = Lists.newArrayList();
  public List<Distribution> distributions = Lists.newArrayList();
  public List<Integer> bibliography = Lists.newArrayList();
  // extra stuff not covered by above for normalizer only
  public Classification classification;

  public List<String> remarks = Lists.newArrayList();

  public static NeoTaxon createTaxon(Origin origin, Name name, boolean doubtful) {
    NeoTaxon t = new NeoTaxon();

    t.name = name;
    t.name.setOrigin(origin);

    t.taxon = new Taxon();
    t.taxon.setDoubtful(doubtful);
    t.taxon.setOrigin(origin);

    return t;
  }

  public void addIssue(Issue issue) {
    taxon.addIssue(issue);
  }

  public void addRemark(String remark) {
    remarks.add(remark);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoTaxon neoTaxon = (NeoTaxon) o;
    return
        Objects.equals(node, neoTaxon.node) &&
        Objects.equals(verbatim, neoTaxon.verbatim) &&
        Objects.equals(name, neoTaxon.name) &&
        Objects.equals(taxon, neoTaxon.taxon) &&
        Objects.equals(synonym, neoTaxon.synonym) &&
        Objects.equals(vernacularNames, neoTaxon.vernacularNames) &&
        Objects.equals(distributions, neoTaxon.distributions) &&
        Objects.equals(bibliography, neoTaxon.bibliography) &&
        Objects.equals(classification, neoTaxon.classification) &&
        Objects.equals(remarks, neoTaxon.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, verbatim, name, taxon, synonym, vernacularNames, distributions, bibliography, classification, remarks);
  }

  public boolean isSynonym() {
    return synonym != null;
  }

  public String getID() {
    //return verbatim == null ? null : verbatim.getId();
    return taxon.getId();
  }

  @Override
  public String toString() {
    String id = verbatim == null ? "" : verbatim.getId() + "/";
    return id + node + " ## " + name + " ## " + taxon;
  }
}