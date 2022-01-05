package life.catalogue.api.jackson;

import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.common.text.StringUtils;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * A module for (de)serializing enum values that uses lower camel case, e.g. datasetKey.
 * <p/>
 * This deserializer is more permissive in the following ways:
 * <ul>
 * <li>Whitespace is permitted but trimmed from the input.</li>
 * <li>Dashes, periods and inner whitespace in the value are converted to single underscores.</li>
 * <li>Matching against the enum values is case insensitive.</li>
 * </ul>
 */
public class LowerCamelCaseEnumSerde {
  // enums that are treated with the special lower camel case serde code
  static final Set<Class> ENUM_CLASSES_LCC = ImmutableSet.of(NameUsageSearchParameter.class);

  static class LowerCamelCaseEnumSerializers extends PermissiveEnumSerde.PermissiveEnumSerializers {

    public LowerCamelCaseEnumSerializers() {
      super(StringUtils::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  static class LowerCamelCaseEnumKeySerializers extends PermissiveEnumSerde.PermissiveEnumKeySerializers {
    public LowerCamelCaseEnumKeySerializers() {
      super(StringUtils::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  public static class LowerCamelCaseEnumDeserializers extends PermissiveEnumSerde.PermissiveEnumDeserializers {
    public LowerCamelCaseEnumDeserializers() {
      super(StringUtils::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  public static class LowerCamelCaseEnumKeyDeserializers extends PermissiveEnumSerde.PermissiveEnumKeyDeserializers {
    public LowerCamelCaseEnumKeyDeserializers() {
      super(StringUtils::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

}
