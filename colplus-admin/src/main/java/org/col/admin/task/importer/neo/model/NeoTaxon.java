package org.col.admin.task.importer.neo.model;

import com.google.common.collect.Lists;
import org.col.api.model.*;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.neo4j.graphdb.Node;

import java.util.*;

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
  // we keep the name distinct from the Taxon here so we can also accomodate basionymGroup which do not have a taxon instance!
  public Name name;
  // either a taxon or a synonym, never both!
  public Taxon taxon;
  public Synonym synonym;
  public List<NameAct> acts = Lists.newArrayList();
  // supplementary infos for a taxon
  public List<VernacularName> vernacularNames = Lists.newArrayList();
  public List<Distribution> distributions = Lists.newArrayList();
  public List<Reference> bibliography = Lists.newArrayList();
  // extra stuff not covered by above for normalizer only
  public Classification classification;
  public Set<Issue> issues = EnumSet.noneOf(Issue.class);

  public List<String> remarks = Lists.newArrayList();

  public static NeoTaxon createTaxon(Origin origin, Name name, TaxonomicStatus status) {
    NeoTaxon t = new NeoTaxon();

    t.name = name;
    t.name.setOrigin(origin);

    t.taxon = new Taxon();
    t.taxon.setStatus(status);
    t.taxon.setOrigin(origin);

    return t;
  }

  public static class Synonym {
    public List<Taxon> accepted = Lists.newArrayList();
  }

  /**
   * @return list all reference placeholders with just a key.
   */
  public List<Reference> listReferencePlaceholders() {
    return Lists.newArrayList();
  }

  /**
   * @return list all reference with actual values, i.e. no placeholders, extracted from all data of this taxon.
   */
  public List<Reference> listReferences() {
    return Lists.newArrayList();
  }

  public void addIssue(Issue issue) {
    issues.add(issue);
  }

  public void addRemark(String remark) {
    remarks.add(remark);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoTaxon neoTaxon = (NeoTaxon) o;
    return Objects.equals(node, neoTaxon.node) &&
        Objects.equals(verbatim, neoTaxon.verbatim) &&
        Objects.equals(taxon, neoTaxon.taxon) &&
        Objects.equals(acts, neoTaxon.acts) &&
        Objects.equals(vernacularNames, neoTaxon.vernacularNames) &&
        Objects.equals(distributions, neoTaxon.distributions) &&
        Objects.equals(bibliography, neoTaxon.bibliography);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, verbatim, taxon, acts, vernacularNames, distributions, bibliography);
  }

  public boolean isSynonym() {
    return synonym != null;
  }

  public String getID() {
    return verbatim == null ? null : verbatim.getId();
  }

  public String getTaxonID() {
    return taxon.getId();
  }

  @Override
  public String toString() {
    String id = verbatim == null ? "" : verbatim.getId() + "/";
    return id + node + " ## " + name + " ## " + taxon;
  }
}