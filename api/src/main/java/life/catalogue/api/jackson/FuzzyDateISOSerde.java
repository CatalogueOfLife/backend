package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import life.catalogue.api.vocab.Country;
import life.catalogue.common.date.FuzzyDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link FuzzyDate}
 * that uses the ISO Date string syntax.
 */
public class FuzzyDateISOSerde {

  /**
   * Jackson {@link JsonSerializer} for {@link FuzzyDate}.
   */
  public static class Serializer extends JsonSerializer<FuzzyDate> {

    @Override
    public void serialize(FuzzyDate value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(value.toString());
      }
    }
  }
  
  /**
   * Deserializes the value from an ISO String.
   */
  public static class Deserializer extends JsonDeserializer<FuzzyDate> {

    @Override
    public FuzzyDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      String iso = p.getValueAsString();
      return FuzzyDate.of(iso);
    }
  }
}
