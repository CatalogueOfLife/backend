package life.catalogue.api.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.ContainerSerializer;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.vocab.Country;

public class FastutilsSerde {
  
  /**
   * Jackson {@link JsonSerializer} for {@link Country}.
   */
  public static class Serializer extends ContainerSerializer<Int2IntMap> {
  
    public Serializer() {
      super(Int2IntMap.class);
    }
  
    @Override
    public JavaType getContentType() {
      return null;
    }
  
    @Override
    public JsonSerializer<?> getContentSerializer() {
      // We are not delegating
      return null;
    }
  
    @Override
    public boolean hasSingleElement(Int2IntMap value) {
      return false;
    }
  
    @Override
    protected ContainerSerializer<?> _withValueTypeSerializer(TypeSerializer vts) {
      return null;
    }
  
    @Override
    public void serialize(Int2IntMap value, JsonGenerator gen, SerializerProvider provider) throws IOException {
      gen.writeStartObject();
      for (Int2IntMap.Entry e : value.int2IntEntrySet()) {
        gen.writeNumberField(Integer.toString(e.getIntKey()), e.getIntValue());
      }
      gen.writeEndObject();
    }
  }
  
  public static class Deserializer extends StdDeserializer<Int2IntMap> {
  
    protected Deserializer() {
      super(Int2IntMap.class);
    }
  
    @Override
    public Int2IntMap deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      Int2IntMap map = new Int2IntOpenHashMap();
      // we are still at object start, get next field
      p.nextToken();
      while (p.getCurrentToken() != null && p.getCurrentToken() != JsonToken.END_OBJECT) {
        int key = 0;
        try {
          key = Integer.parseInt(p.getValueAsString());
          p.nextToken();
          int value = p.getIntValue();
          map.put(key, value);
        
        } catch (NumberFormatException e) {
          // skip this entry
          p.nextToken();
        }
        p.nextToken();
      }
      return map;
    }
  }

  
}
