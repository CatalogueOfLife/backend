package org.col.dw.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.col.dw.api.vocab.Language;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link Language}
 * that uses the ISO 3 letter codes instead of enum names.
 */
public class LanguageSerde {
  public static final SimpleModule MODULE = new SimpleModule();
  static {
    MODULE.addSerializer(Language.class, new Serializer());
    MODULE.addDeserializer(Language.class, new Deserializer());
  }

  /**
   * Jackson {@link JsonSerializer} for {@link Language}.
   */
  public static class Serializer extends JsonSerializer<Language> {

    @Override
    public void serialize(Language value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(value.getIso3LetterCode());
      }
    }
  }

  /**
   * Deserializes the value from a 3 (or 2) letter ISO format.
   */
  public static class Deserializer extends JsonDeserializer<Language> {

    @Override
    public Language deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        return Language.fromIsoCode(jp.getText());
      }
      throw ctxt.mappingException("Expected String");
    }
  }
}
