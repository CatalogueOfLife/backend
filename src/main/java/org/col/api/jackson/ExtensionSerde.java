package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import org.col.api.vocab.Extension;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link Extension} that uses the rowType URI.
 */
public class ExtensionSerde {

  /**
   * Jackson {@link JsonSerializer} for {@link Extension}.
   */
  public static class Deserializer extends JsonDeserializer<Extension> {

    @Override
    public Extension deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        return Extension.fromRowType(jp.getText());
      }
      throw ctxt.mappingException("Expected JSON String");
    }
  }

  public static class Serializer extends JsonSerializer<Extension> {

    @Override
    public void serialize(Extension value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      jgen.writeString(value.getRowType());
    }
  }

  /**
   * Deserializer for {@link Extension} in key values.
   */
  public static class ExtensionKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
      return Extension.fromRowType(key);
    }
  }
}
