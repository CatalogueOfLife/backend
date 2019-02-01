package org.col.api.jackson;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.ser.Serializers;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;

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
    private static class PermissiveEnumDeserializer extends StdScalarDeserializer<Enum<?>> {
        private static final long serialVersionUID = 1L;
    
        private static final Pattern TOKEN_DELIM = Pattern.compile("[._ -]+");
        private final Map<String, Enum<?>> values;
        private final Joiner joiner = Joiner.on(", ");
    
    
        @SuppressWarnings("unchecked")
        protected PermissiveEnumDeserializer(Class<Enum<?>> clazz) {
            super(clazz);
            values = Arrays.stream(((Class<Enum<?>>) handledType()).getEnumConstants())
                .collect(Collectors.toMap(PermissiveEnumSerde::enumValueName, t -> t));
        }

        @Override
        public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            if (StringUtils.isBlank(jp.getText())) return null;
            Enum<?> constant = values.get(
                TOKEN_DELIM.matcher(jp.getText().trim())
                    .replaceAll(" ")
                    .toLowerCase()
            );
            if (constant != null) {
                return constant;
            }
            throw ctxt.weirdStringException(jp.getText(), handledType(), jp.getText() + " was not one of " + joiner.join(values.keySet()));
        }
    }

    static class PermissiveEnumDeserializers extends Deserializers.Base {
        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
                                                        DeserializationConfig config,
                                                        BeanDescription desc) throws JsonMappingException {
            if (type.isEnum() && !ApiModule.ENUM_CLASSES.contains(type)) {
                return new PermissiveEnumDeserializer((Class<Enum<?>>) type);
            }
            return null;
        }
    }
    
    public static String enumValueName(Enum<?> val) {
        return val.name().toLowerCase().replaceAll("_+", " ");
    }
    
    private static class PermissiveEnumSerializer extends StdScalarSerializer<Enum<?>> {
        private static final long serialVersionUID = 2L;
        private final String[] names;
        
        public PermissiveEnumSerializer(Class<Enum<?>> clazz) {
            super(clazz);
            names = Arrays.stream(clazz.getEnumConstants())
                .map(PermissiveEnumSerde::enumValueName)
                .toArray(String[]::new);
        }
        
        @Override
        public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(names[value.ordinal()]);
        }
    }
    
    static class PermissiveEnumSerializers extends Serializers.Base {
        @Override
        public JsonSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription beanDesc) {
            if (type.isEnumType() && !ApiModule.ENUM_CLASSES.contains(type.getRawClass())) {
                return new PermissiveEnumSerializer((Class<Enum<?>>) type.getRawClass());
            }
            return null;
        }
    }
}
