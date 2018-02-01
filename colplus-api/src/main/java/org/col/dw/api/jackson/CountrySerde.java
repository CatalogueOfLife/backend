package org.col.dw.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.col.dw.api.vocab.Country;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link Country}
 * that uses the ISO 2 letter codes instead of enum names.
 */
public class CountrySerde {

  public static final SimpleModule MODULE = new SimpleModule();
  static {
    MODULE.addSerializer(Country.class, new Serializer());
    MODULE.addDeserializer(Country.class, new Deserializer());
  }

  /**
   * Jackson {@link JsonSerializer} for {@link Country}.
   */
  public static class Serializer extends JsonSerializer<Country> {

    @Override
    public void serialize(Country value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(value.getIso2LetterCode());
      }
    }
  }

  /**
   * Deserializes the value from a 2 (or 3) letter ISO format.
   */
  public static class Deserializer extends JsonDeserializer<Country> {

    @Override
    public Country deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        return Country.fromIsoCode(jp.getText());
      }
      throw ctxt.mappingException("Expected String");
    }
  }

}
