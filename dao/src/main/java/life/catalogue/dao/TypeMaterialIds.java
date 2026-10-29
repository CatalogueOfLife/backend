package life.catalogue.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * Hands out the ids of copied type material and remembers them per source specimen,
 * so a specimen typifying several copied names is copied only once and linked to all of them.
 * Instances are meant to live for a single copy job, e.g. one sector sync.
 */
public class TypeMaterialIds {
  private final Supplier<String> idGen;
  private final Map<String, String> ids = new HashMap<>();

  public TypeMaterialIds(Supplier<String> idGen) {
    this.idGen = idGen;
  }

  /**
   * @return the id of the copy of the given source specimen or null if it has not been copied yet
   */
  @Nullable
  public String get(String sourceId) {
    return ids.get(sourceId);
  }

  /**
   * Issues a new id for the copy of the given source specimen and remembers it.
   */
  public String issue(String sourceId) {
    String id = idGen.get();
    ids.put(sourceId, id);
    return id;
  }

  /**
   * Remembers an id for the copy of the given source specimen, e.g. when the copy kept the source id.
   */
  public void put(String sourceId, String id) {
    ids.put(sourceId, id);
  }
}
