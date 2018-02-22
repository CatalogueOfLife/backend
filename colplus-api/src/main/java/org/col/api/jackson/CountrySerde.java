package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.col.api.vocab.Country;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link Country}
 * that uses the ISO 2 letter codes instead of enum names.
 */
public class CountrySerde {

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
      try {
        if (jp != null && jp.getTextLength() > 0) {
          return Country.fromIsoCode(jp.getText());
        } else {
          return Country.UNKNOWN; // none provided
        }
      } catch (RuntimeException e) {
        return (Country) ctxt.handleUnexpectedToken(Country.class, jp.getCurrentToken(), jp, "Unable to deserialize country from provided value (not an ISO 2 character?): "+jp.getText());
      }
    }

  }

}
