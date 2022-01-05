package life.catalogue.api.jackson;

import life.catalogue.api.vocab.GeoTime;

import org.gbif.dwc.terms.Term;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link GeoTime}
 * that uses the times name property.
 */
public class GeoTimeSerde {
  
  /**
   * Jackson {@link JsonSerializer} for {@link GeoTime}.
   */
  public static class Serializer extends JsonSerializer<GeoTime> {
    
    @Override
    public void serialize(GeoTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(value.getName());
      }
    }
  }
  
  /**
   * Serializes a GeoTime as its name into a json field.
   */
  public static class FieldSerializer extends JsonSerializer<GeoTime> {
    
    @Override
    public void serialize(GeoTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeFieldName(value.getName());
      }
    }
  }
  
  /**
   * Deserializer for {@link Term} in key values.
   */
  public static class KeyDeserializer extends com.fasterxml.jackson.databind.KeyDeserializer {
    
    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
      if (key.length() == 0) { // [JACKSON-360]
        return null;
      }
      GeoTime gt = GeoTime.byName(key);
      if (gt != null) {
        return gt;
      }
      return ctxt.handleWeirdKey(GeoTime.class, key, "Expected valid GeoTime name");
    }
  }
  
  /**
   * Deserializes the GeoTime from its name.
   */
  public static class Deserializer extends JsonDeserializer<GeoTime> {
    
    @Override
    public GeoTime deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      return GeoTime.byName(jp.getText());
    }
    
  }
  
}
