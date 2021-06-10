package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import de.undercouch.citeproc.csl.CSLType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * {@link CSLType} enum that uses the specific underscore hyphen mappings needed for valid
 * CslJson. See http://docs.citationstyles.org/en/stable/specification.html#appendix-iii-types
 * <p>
 * Unknown values will be silently converted into null and an info logged.
 */
public class CSLTypeSerde {
  private static final Logger LOG = LoggerFactory.getLogger(CSLTypeSerde.class);

  public static class Serializer extends JsonSerializer<CSLType> {
    
    @Override
    public void serialize(CSLType value, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(value.toString());
      }
    }
  }

  public static CSLType parse(String value) {
    try {
      return CSLType.valueOf(value.trim().toUpperCase().replaceAll("[_ -]+", "_"));
    } catch (IllegalArgumentException e) {
      LOG.info("Invalid CSLType: {}", value);
      return null;
    }
  }

  public static class Deserializer extends JsonDeserializer<CSLType> {
    
    @Override
    public CSLType deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        return parse(jp.getText());
      }
      throw ctxt.mappingException("Expected String as CSLType");
    }
  }
}
