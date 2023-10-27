package life.catalogue.importer.neo.model;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.importer.neo.NeoDbUtils;

import java.util.Objects;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

/**
 * Simple wrapper to hold a neo4j node together with a name
 */
public class NeoName implements NeoNode, DSID<String>, VerbatimEntity {
  private static final Label[] LABELS = new Label[]{Labels.NAME};
  
  public Node node;
  public ParsedNameUsage pnu;
  public boolean homotypic = false;

  public NeoName() {
  }
  
  public NeoName(ParsedNameUsage pnu) {
    this.pnu = pnu;
  }

  public NeoName(Node node, ParsedNameUsage pnu) {
    this(pnu);
    this.node = node;
  }

  public NeoName(Name name) {
    this.pnu = new ParsedNameUsage(name);
  }

  public NeoName(Node node, Name name) {
    this(name);
    this.node = node;
  }

  public Name getName() {
    return pnu.getName();
  }

  @Override
  public Node getNode() {
    return node;
  }
  
  @Override
  public void setNode(Node node) {
    this.node = node;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return pnu.getName().getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    pnu.getName().setVerbatimKey(verbatimKey);
  }
  
  @Override
  public String getId() {
    return pnu.getName().getId();
  }
  
  @Override
  public void setId(String id) {
    pnu.getName().setId(id);
  }
  
  @Override
  public Integer getDatasetKey() {
    return pnu.getName().getDatasetKey();
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    pnu.getName().setDatasetKey(key);
  }
  
  @Override
  public PropLabel propLabel() {
    return NeoDbUtils.neo4jProps(pnu.getName(), new PropLabel(LABELS));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NeoName)) return false;
    NeoName neoName = (NeoName) o;
    return homotypic == neoName.homotypic &&
      Objects.equals(node, neoName.node) &&
      Objects.equals(pnu, neoName.pnu);
  }

  @Override
  public int hashCode() {
    return Objects.hash(node, pnu, homotypic);
  }
}