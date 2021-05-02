package life.catalogue.importer.neo.model;

import org.neo4j.graphdb.*;

import java.util.Map;

/**
 * A Node implementation that only contains the entity id
 * and cannot be used directly with a neo4j GraphDatabase service.
 *
 * It is primarily used during batch insertion to pass nodes which cannot be accessed through neo at that stage.
 */
public class NodeMock implements Node {
  private final long id;
  
  public NodeMock(long id) {
    this.id = id;
  }
  
  @Override
  public long getId() {
    return id;
  }
  
  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Relationship> getRelationships() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasRelationship() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Relationship> getRelationships(RelationshipType... types) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Relationship> getRelationships(Direction direction, RelationshipType... types) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasRelationship(RelationshipType... types) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasRelationship(Direction direction, RelationshipType... types) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Relationship> getRelationships(Direction dir) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasRelationship(Direction dir) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Relationship> getRelationships(RelationshipType type, Direction dir) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasRelationship(RelationshipType type, Direction dir) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Relationship getSingleRelationship(RelationshipType type, Direction dir) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Relationship createRelationshipTo(Node otherNode, RelationshipType type) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<RelationshipType> getRelationshipTypes() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public int getDegree() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public int getDegree(RelationshipType type) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public int getDegree(Direction direction) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public int getDegree(RelationshipType type, Direction direction) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void addLabel(Label label) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void removeLabel(Label label) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasLabel(Label label) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<Label> getLabels() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public GraphDatabaseService getGraphDatabase() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public boolean hasProperty(String key) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Object getProperty(String key) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Object getProperty(String key, Object defaultValue) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void setProperty(String key, Object value) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Object removeProperty(String key) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Iterable<String> getPropertyKeys() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Map<String, Object> getProperties(String... keys) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Map<String, Object> getAllProperties() {
    throw new UnsupportedOperationException();
  }
}
