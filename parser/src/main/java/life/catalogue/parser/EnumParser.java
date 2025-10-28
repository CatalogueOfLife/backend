package life.catalogue.parser;

import javax.annotation.Nullable;

/**
 *
 */
public abstract class EnumParser<T extends Enum> extends MapBasedParser<T> {
  private final Class<T> enumClass;
  
  public EnumParser(Class<T> enumClass) {
    super(enumClass);
    this.enumClass = enumClass;
    addNativeEnumMappings();
  }
  
  public EnumParser(String mappingResourceFile, Class<T> enumClass) {
    super(enumClass);
    this.enumClass = enumClass;
    addMappings(mappingResourceFile);
    addNativeEnumMappings();
  }

  public EnumParser(@Nullable String mappingResourceFile, boolean throwUnparsableException, Class<T> enumClass) {
    super(enumClass, throwUnparsableException);
    this.enumClass = enumClass;
    if (mappingResourceFile != null) {
      addMappings(mappingResourceFile);
    }
    addNativeEnumMappings();
  }

  @Override
  protected T mapNormalisedValue(String upperCaseValue) {
    try {
      return (T) Enum.valueOf(enumClass, upperCaseValue);
    } catch (Exception e) {
      return null;
    }
  }
  
  private void addNativeEnumMappings() {
    for (T e : enumClass.getEnumConstants()) {
      add(e.name(), e);
    }
  }
  
  public Class<T> getEnumClass() {
    return enumClass;
  }
  
}
