package life.catalogue.es.ddl;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;
import life.catalogue.es.EsModule;
import static java.util.Collections.emptySet;
import static life.catalogue.es.ddl.MappingUtil.getClassForTypeArgument;
import static life.catalogue.es.ddl.MappingUtil.isA;
import static life.catalogue.es.ddl.MappingUtil.newAncestor;

public abstract class MappingsFactory {

  public static MappingsFactory usingFields() {
    return new FieldMappingsFactory();
  }

  public static MappingsFactory usingGetters() {
    return new GetterMappingsFactory();
  }

  private boolean mapEnumToInt = true;

  protected MappingsFactory() {}

  /**
   * Creates a document type mapping for the specified class.
   *
   * @param type
   * @return
   */
  public Mappings getMapping(Class<?> type) {
    Mappings mappings = new Mappings();
    addFields(mappings, type, newAncestor(emptySet(), type));
    return mappings;
  }

  /**
   * Whether to map enums to Elasticsearch's integer datatype or to the keyword datatype. Default true. This is more or
   * less separate from how you <i>serialize</i> enums. Obviously when serializing enums to strings, you have no choice
   * but to use the keyword datatype. But when serializing enums to integers, you still have the choice of storing them as
   * strings or as integers. And there can be good reasons to store integers as strings (see Elasticsearch performance
   * tuning guide). Specifying the datatype mapping here saves you from having to decorate each and every enum in the data
   * model with the @MapToType annotation. You can still use the @MapToType to override the global behaviour. Note that in
   * {@link EsModule} we specify that we want enums to be serialized as integers, so we are <i>indeed</i> free to choose
   * between the keyword and integer datatype.
   */
  public boolean isMapEnumToInt() {
    return mapEnumToInt;
  }

  public void setMapEnumToInt(boolean mapEnumToInt) {
    this.mapEnumToInt = mapEnumToInt;
  }

  /**
   * 
   * @param document The document or sub-document to add the fields to
   * @param type The Java type to model the (sub-)document after
   * @param ancestors All Java types in the ancestry of the (sub-) document, all the way up to the Java type that the
   *        document type mapping itself was modeled after. Used to detect circular graphs.
   */
  protected abstract void addFields(ComplexField document, Class<?> type, Set<Class<?>> ancestors);

  /**
   * Returns the Java type that should be used to look up the the Elasticsearch datatype in the {@link DataTypeMap}.
   * 
   * @param type
   * @param typeArg
   * @return
   */
  protected Class<?> getMappedType(Class<?> type, Type typeArg) {
    // For multi-valued types we map the element type, not the type itself
    if (type.isArray()) {
      return getMappedType(type.getComponentType(), null);
    }
    if (isA(type, Collection.class)) {
      return getMappedType(getClassForTypeArgument(typeArg), null);
    }
    if (type.isEnum()) {
      return mapEnumToInt ? int.class : String.class;
    }
    return type;
  }

}
