package org.col.db.type2;

/**
 * A mybatis base type handler that translates from the generic java.util.Map<Enum, Integer> to the
 * postgres hstore database type. Any non integer values in hstore are silently ignored.
 * All enumerations are serialized by their name, not ordinal.
 * <p>
 * As we do not map all java map types to this mybatis handler apply the handler manually for the relevant hstore fields
 * in the mapper xml, for example see DatasetImportMapper.xml.
 */
abstract class HstoreEnumCountTypeHandlerBase<KEY extends Enum> extends HstoreCountTypeHandlerBase<KEY> {
  
  private final Class<KEY> enumClass;
  
  public HstoreEnumCountTypeHandlerBase(Class<KEY> enumClass) {
    this.enumClass = enumClass;
  }
  
  @Override
  KEY toKey(String x) throws IllegalArgumentException {
    return (KEY) Enum.valueOf(enumClass, x);
  }
  
}
