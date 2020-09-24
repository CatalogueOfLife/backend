package life.catalogue.api.jackson;

import com.google.common.collect.ImmutableSet;
import life.catalogue.api.search.NameUsageSearchParameter;
import org.apache.commons.text.WordUtils;

import java.util.Set;

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
      super(LowerCamelCaseEnumSerde::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  static class LowerCamelCaseEnumKeySerializers extends PermissiveEnumSerde.PermissiveEnumKeySerializers {
    public LowerCamelCaseEnumKeySerializers() {
      super(LowerCamelCaseEnumSerde::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  public static class LowerCamelCaseEnumDeserializers extends PermissiveEnumSerde.PermissiveEnumDeserializers {
    public LowerCamelCaseEnumDeserializers() {
      super(LowerCamelCaseEnumSerde::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  public static class LowerCamelCaseEnumKeyDeserializers extends PermissiveEnumSerde.PermissiveEnumKeyDeserializers {
    public LowerCamelCaseEnumKeyDeserializers() {
      super(LowerCamelCaseEnumSerde::lowerCamelCase, ENUM_CLASSES_LCC::contains);
    }
  }

  public static String lowerCamelCase(Enum<?> val) {
    char c[] = WordUtils.capitalizeFully(val.name(), new char[]{'_'}).replaceAll("_", "").toCharArray();
    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }

}
