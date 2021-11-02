package life.catalogue.api.jackson;

import life.catalogue.api.vocab.Country;

import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

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
   * Serializes a language as 3 letter codes into a json field.
   */
  public static class FieldSerializer extends JsonSerializer<Country> {
    
    @Override
    public void serialize(Country country, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (country == null) {
        jgen.writeNull();
      } else {
        jgen.writeFieldName(country.getIso2LetterCode());
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
      Optional<Country> lang = Country.fromIsoCode(key);
      if (lang.isPresent()) {
        return lang.get();
      }
      return ctxt.handleWeirdKey(Country.class, key, "Expected valid ISO 3 letter code");
    }
  }
  
  /**
   * Deserializes the value from a 2 (or 3) letter ISO format.
   */
  public static class Deserializer extends JsonDeserializer<Country> {
    
    @Override
    public Country deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      final String val = jp.getText();
      return Country.fromIsoCode(val).orElseThrow(() -> new IllegalArgumentException(val + " is no valid 2 or 3 letter ISO country code"));
    }
    
  }
  
}
