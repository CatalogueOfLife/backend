package life.catalogue.api.jackson;

import life.catalogue.api.model.EnumValue;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A module for (de)serializing enum values that implement the custom EnumValue interface.
 */
public class EnumValueSerde {

  static final Predicate<Class> PREDICATE = EnumValue.class::isAssignableFrom;
  static final Function<Enum<?>, String> TO_STRING = en -> ((EnumValue)en).value();

  static class EnumValueSerializers extends PermissiveEnumSerde.PermissiveEnumSerializers {

    public EnumValueSerializers() {
      super(TO_STRING, PREDICATE);
    }
  }

  static class EnumValueKeySerializers extends PermissiveEnumSerde.PermissiveEnumKeySerializers {
    public EnumValueKeySerializers() {
      super(TO_STRING, PREDICATE);
    }
  }

  public static class EnumValueDeserializers extends PermissiveEnumSerde.PermissiveEnumDeserializers {
    public EnumValueDeserializers() {
      super(TO_STRING, PREDICATE);
    }
  }

  public static class EnumValueKeyDeserializers extends PermissiveEnumSerde.PermissiveEnumKeyDeserializers {
    public EnumValueKeyDeserializers() {
      super(TO_STRING, PREDICATE);
    }
  }

}
