package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link Rank} that uses the common rank markers instead of enum names.
 */
public class RankSerde {

  /**
   * Jackson {@link JsonSerializer} for {@link Rank}.
   */
  public static class Serializer extends JsonSerializer<Rank> {

    @Override
    public void serialize(Rank value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
        return;
      }
      jgen.writeString(value.name().toLowerCase().replaceAll("_", " "));
    }
  }

  /**
   * Jackson {@link JsonDeserializer} for {@link Rank}.
   */
  public static class Deserializer extends JsonDeserializer<Rank> {

    @Override
    public Rank deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        try {
          return Rank.valueOf(jp.getText().toUpperCase().replaceAll(" ", "_"));
        } catch (IllegalArgumentException e) {
          // swallow
        }
        return null;
      }
      throw ctxt.mappingException("Expected String");
    }
  }
}
