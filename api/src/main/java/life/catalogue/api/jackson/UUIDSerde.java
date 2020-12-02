package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link UUID}
 * that removes hyphens from the regular string.
 */
public class UUIDSerde {
  private static final Pattern UUID_PARSER = Pattern.compile("(.{8})(.{4})(.{4})(.{4})(.{12})");

  /**
   * Parses a UUID string that may have hyphens or not.
   */
  public static UUID from(String x) {
    if (x == null) return null;
    if (x.contains("-")) {
      return UUID.fromString(x);
    }
    // insert hyphens again
    Matcher m = UUID_PARSER.matcher(x);
    // ime_low                = 4*<hexOctet>
    // time_mid               = 2*<hexOctet>
    // time_high_and_version  = 2*<hexOctet>
    // variant_and_sequence   = 2*<hexOctet>
    // node                   = 6*<hexOctet>
    if (m.find()) {
      return UUID.fromString(m.replaceFirst("$1-$2-$3-$4-$5"));
    }
    throw new IllegalArgumentException("UUID string must have 32 chars or 36 if hyphens are used");
  }

  static String to(UUID x) {
    if (x == null) return null;
    return x.toString().replaceAll("-", "");
  }

  /**
   * Jackson {@link JsonSerializer} for {@link UUID}.
   */
  public static class Serializer extends JsonSerializer<UUID> {
    
    @Override
    public void serialize(UUID value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(to(value));
      }
    }
  }
  
  /**
   * Serializes a language as 3 letter codes into a json field.
   */
  public static class FieldSerializer extends JsonSerializer<UUID> {
    
    @Override
    public void serialize(UUID key, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (key == null) {
        jgen.writeNull();
      } else {
        jgen.writeFieldName(to(key));
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
      return UUIDSerde.from(key);
    }
  }
  
  /**
   * Deserializes the value from a 2 (or 3) letter ISO format.
   */
  public static class Deserializer extends JsonDeserializer<UUID> {
    
    @Override
    public UUID deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      return UUIDSerde.from(jp.getText());
    }
  }

}
