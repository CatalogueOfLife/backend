package life.catalogue.api.jackson;

import life.catalogue.api.model.DOI;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DOISerde {

  public static class Deserializer extends JsonDeserializer<DOI> {
    public Deserializer() {
    }

    public DOI deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return p != null && p.getTextLength() > 0 ? new DOI(p.getText()) : null;
    }
  }

  public static class Serializer extends JsonSerializer<DOI> {
    public Serializer() {
    }

    public void serialize(DOI value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(value.toString());
    }
  }
}
