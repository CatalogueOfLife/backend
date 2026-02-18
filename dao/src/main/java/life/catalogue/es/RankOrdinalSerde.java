package life.catalogue.es;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class RankOrdinalSerde {

  public static class Deserializer extends JsonDeserializer<Rank> {
    public Deserializer() {
    }

    public Rank deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return p != null && p.getTextLength() > 0 ? Rank.class.getEnumConstants()[p.getIntValue()] : null;
    }
  }

  public static class Serializer extends JsonSerializer<Rank> {
    public Serializer() {
    }

    public void serialize(Rank value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeNumber(value.ordinal());
    }
  }
}
