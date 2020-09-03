package life.catalogue.es.ddl;

import life.catalogue.es.EsModule;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import static java.util.Collections.emptySet;
import static life.catalogue.es.ddl.MappingUtil.*;

/**
 * Generates document type mappings from Class objects.
 */
public abstract class MappingsFactory {

  public static MappingsFactory usingFields() {
    return new FieldMappingsFactory();
  }

  public static MappingsFactory usingGetters() {
    return new GetterMappingsFactory();
  }

  private boolean mapEnumToInt = true;

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
   * Whether to map enums to Elasticsearch's integer datatype or to the keyword datatype. Default
   * true. This is more or less separate from how you <i>serialize</i> enums. Obviously when
   * serializing enums to strings, you have no choice but to use the keyword datatype. But when
   * serializing enums to integers, you still have the choice of storing them as strings or as
   * integers. And there can be good reasons to store integers as strings (see Elasticsearch
   * performance tuning guide). Specifying the datatype mapping here saves you from having to decorate
   * each and every enum in the data model with the @MapToType annotation. You can still use
   * the @MapToType to override the global behaviour. Note that in {@link EsModule} we specify that we
   * want enums to be serialized as integers, so we are <i>indeed</i> free to choose between the
   * keyword and integer datatype.
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
   * @param ancestors All Java types in the ancestry of the (sub-) document, all the way up to the
   *        Java type that the document type mapping itself was modeled after. Used to detect circular
   *        graphs.
   */
  protected abstract void addFields(ComplexField document, Class<?> type, Set<Class<?>> ancestors);

  /**
   * Returns the Java type that should be used to look up the the Elasticsearch datatype in the
   * {@link DataTypeMap}. For arrays and Collection classes we map the element type, not the type
   * itself. Elasticsearch doesn't have an array/list type. Fields are intrinsically multi-valued.
   */
  protected Class<?> getMappedType(Field f) {
    Class<?> declaredType = f.getType();
    if (declaredType.isArray()) {
      return getMappedType(declaredType.getComponentType());
    } else if (isA(declaredType, Collection.class)) {
      return getMappedType(getTypeArgument(f));
    }
    return getMappedType(declaredType);
  }

  protected Class<?> getMappedType(Method m) {
    Class<?> declaredType = m.getReturnType();
    if (declaredType.isArray()) {
      return getMappedType(declaredType.getComponentType());
    } else if (isA(declaredType, Collection.class)) {
      return getMappedType(getTypeArgument(m));
    }
    return getMappedType(declaredType);
  }

  private Class<?> getMappedType(Class<?> type) {
    if (type.isArray() || isA(type, Collection.class)) {
      throw new MappingException("Multidimensional arrays/collections not allowed in Elasticsearch data model");
    }
    if (type.isEnum()) {
      return mapEnumToInt ? int.class : String.class;
    }
    return type;
  }

}
