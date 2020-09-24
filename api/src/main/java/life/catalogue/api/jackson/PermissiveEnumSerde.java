package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.KeyDeserializers;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.google.common.collect.ImmutableSet;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.CSLRefType;
import life.catalogue.api.vocab.Country;
import life.catalogue.common.func.Predicates;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.Term;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A module for deserializing enums that is more permissive than the default.
 * <p/>
 * This deserializer is more permissive in the following ways:
 * <ul>
 * <li>Whitespace is permitted but trimmed from the input.</li>
 * <li>Dashes, periods and inner whitespace in the value are converted to single underscores.</li>
 * <li>Matching against the enum values is case insensitive.</li>
 * </ul>
 */
public class PermissiveEnumSerde {
  // enums that are not treated with the regular permissive enum serde code, but something custom
  static final Set<Class> ENUM_CLASSES_SPECIAL = ImmutableSet.<Class>builder().addAll(LowerCamelCaseEnumSerde.ENUM_CLASSES_LCC)
    .add(Country.class)
    .add(CSLRefType.class)
    .add(Term.class)
    .build();

  public static String enumValueName(Enum<?> val) {
    return val.name().toLowerCase().replaceAll("_+", " ");
  }

  static class PermissiveEnumSerializers extends Serializers.Base {
    private final Function<Enum<?>, String> mapper;
    private final Predicate<Class> predicate;

    public PermissiveEnumSerializers() {
      this(PermissiveEnumSerde::enumValueName, Predicates.not(ENUM_CLASSES_SPECIAL::contains));
    }

    public PermissiveEnumSerializers(Function<Enum<?>, String> mapper, Predicate<Class> predicate) {
      this.mapper = mapper;
      this.predicate = predicate;
    }

    @Override
    public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription beanDesc) {
      if (type.isEnumType() && predicate.test(type.getRawClass())) {
        return new PermissiveEnumSerializer((Class<Enum<?>>) type.getRawClass(), mapper);
      }
      return null;
    }
  }

  static class PermissiveEnumKeySerializers extends Serializers.Base {
    private final Function<Enum<?>, String> mapper;
    private final Predicate<Class> predicate;

    public PermissiveEnumKeySerializers() {
      this(PermissiveEnumSerde::enumValueName, Predicates.not(ENUM_CLASSES_SPECIAL::contains));
    }

    public PermissiveEnumKeySerializers(Function<Enum<?>, String> mapper, Predicate<Class> predicate) {
      this.mapper = mapper;
      this.predicate = predicate;
    }

    @Override
    public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription beanDesc) {
      if (type.isEnumType() && predicate.test(type.getRawClass())) {
        return new PermissiveEnumFieldSerializer<>(mapper);
      }
      return null;
    }
  }

  public static class PermissiveEnumDeserializers extends Deserializers.Base {
    private final Function<Enum<?>, String> mapper;
    private final Predicate<Class> predicate;

    public PermissiveEnumDeserializers() {
      this(PermissiveEnumSerde::enumValueName, Predicates.not(ENUM_CLASSES_SPECIAL::contains));
    }

    public PermissiveEnumDeserializers(Function<Enum<?>, String> mapper, Predicate<Class> predicate) {
      this.mapper = mapper;
      this.predicate = predicate;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
                                                    DeserializationConfig config,
                                                    BeanDescription desc) throws JsonMappingException {
      if (type.isEnum() && predicate.test(type)) {
        return new PermissiveEnumDeserializer((Class<Enum<?>>) type, mapper);
      }
      return null;
    }
  }

  public static class PermissiveEnumKeyDeserializers implements KeyDeserializers {
    private final Function<Enum<?>, String> mapper;
    private final Predicate<Class> predicate;

    public PermissiveEnumKeyDeserializers() {
      this(PermissiveEnumSerde::enumValueName, Predicates.not(ENUM_CLASSES_SPECIAL::contains));
    }

    public PermissiveEnumKeyDeserializers(Function<Enum<?>, String> mapper, Predicate<Class> predicate) {
      this.mapper = mapper;
      this.predicate = predicate;
    }

    @Override
    public KeyDeserializer findKeyDeserializer(JavaType type, DeserializationConfig config, BeanDescription beanDesc) throws JsonMappingException {
      Class clazz = type.getRawClass();
      if (clazz.isEnum() && predicate.test(clazz)) {
        return new PermissiveEnumFieldDeserializer((Class<Enum>) clazz);
      }
      return null;
    }
  }

  private static class PermissiveEnumDeserializer extends StdScalarDeserializer<Enum<?>> {
    private static final long serialVersionUID = 1L;
    private final Class<Enum<?>> enumClass;


    @SuppressWarnings("unchecked")
    protected PermissiveEnumDeserializer(Class<Enum<?>> clazz, Function<Enum<?>, String> mapper) {
      super(clazz);
      enumClass = clazz;
    }

    @Override
    public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (StringUtils.isBlank(jp.getText())) return null;

      try {
        return VocabularyUtils.lookupEnum(jp.getText(), enumClass);
      } catch (IllegalArgumentException e) {
        throw ctxt.weirdStringException(jp.getText(), handledType(), jp.getText() + " was not a value from " + enumClass.getSimpleName());
      }
    }
  }

  private static class PermissiveEnumFieldDeserializer extends KeyDeserializer {
    private static final long serialVersionUID = 1L;
    private final Class<Enum> enumClass;

    public PermissiveEnumFieldDeserializer(Class<Enum> enumClass) {
      this.enumClass = enumClass;
    }

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
      if (key.length() == 0) { // [JACKSON-360]
        return null;
      }
      try {
        return VocabularyUtils.lookupEnum(key, enumClass);
      } catch (IllegalArgumentException e) {
        return ctxt.handleWeirdKey(enumClass, key, "Expected valid "+enumClass.getSimpleName()+" value");
      }
    }
  }

  private static class PermissiveEnumSerializer extends StdScalarSerializer<Enum<?>> {
    private static final long serialVersionUID = 2L;
    private final Function<Enum<?>, String> mapper;

    public PermissiveEnumSerializer(Class<Enum<?>> clazz, Function<Enum<?>, String> mapper) {
      super(clazz);
      this.mapper = mapper;
    }

    @Override
    public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeString(mapper.apply(value));
    }
  }

  private static class PermissiveEnumFieldSerializer<T extends Enum> extends JsonSerializer<T> {
    private final Function<Enum<?>, String> mapper;

    public PermissiveEnumFieldSerializer(Function<Enum<?>, String> mapper) {
      this.mapper = mapper;
    }

    @Override
    public void serialize(T val, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (val == null) {
        jgen.writeNull();
      } else {
        jgen.writeFieldName(mapper.apply(val));
      }
    }
  }

}
