package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Identifier;

import java.io.IOException;

public class IdentifierSerde {

  public static class Deserializer extends JsonDeserializer<Identifier> {
    public Deserializer() {
    }

    public Identifier deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return p != null && p.getTextLength() > 0 ? new Identifier(p.getText()) : null;
    }
  }

  public static class Serializer extends JsonSerializer<Identifier> {
    public Serializer() {
    }

    public void serialize(Identifier value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(value.toString());
    }
  }
}
