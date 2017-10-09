package org.col.commands.importer.neo.model;

import com.google.common.collect.Lists;
import org.col.api.*;
import org.neo4j.graphdb.Node;

import java.util.List;
import java.util.Objects;

/**
 * Simple wrapper to hold a normalizer node together with all data for a record
 * inlcuding a name and a taxon instance.
 * <p>
 * The modified flag can be used to (manually) track if an instance has changed and needs to be persisted.
 */
public class NeoTaxon {
  public Node node;
  public VerbatimRecord verbatim;
  public Taxon taxon;
  public List<NameAct> acts = Lists.newArrayList();
  public List<VernacularName> vernacularNames = Lists.newArrayList();
  public List<Distribution> distributions = Lists.newArrayList();
  public List<Reference> references = Lists.newArrayList();

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
        Objects.equals(references, neoTaxon.references);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, verbatim, taxon, acts, vernacularNames, distributions, references);
  }
}