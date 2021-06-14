package life.catalogue.api.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;

import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.databind.util.ClassUtil;

import life.catalogue.api.vocab.Country;

import life.catalogue.common.date.FuzzyDate;

import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for {@link FuzzyDate}
 * that uses the CSL-Date syntax using a 2 dimensional int array.
 * See https://citeproc-js.readthedocs.io/en/latest/csl-json/markup.html#date-fields
 *
 * "accessed": {
 *      "date-parts": [[ 2005, 4, 12 ]]
 * },
 */
public class FuzzyDateCSLSerde {
  private static final Logger LOG = LoggerFactory.getLogger(FuzzyDateCSLSerde.class);
  private static String DATE_PARTS= "date-parts";

  /**
   * Jackson {@link JsonSerializer} for {@link FuzzyDate}.
   */
  public static class Serializer extends JsonSerializer<FuzzyDate> {

    @Override
    public void serialize(FuzzyDate value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeStartObject();
        int[] parts = value.toCslDate().getDateParts()[0];
        jgen.writeFieldName("raw");
        jgen.writeString(value.toString());
        jgen.writeArrayFieldStart(DATE_PARTS);
        jgen.writeArray(parts, 0, parts.length);
        jgen.writeEndArray();
        jgen.writeEndObject();
      }
    }
  }
  
  /**
   * Deserializes the value from an object with field date-parts being an 2d int array
   */
  public static class Deserializer extends JsonDeserializer<FuzzyDate> {

    @Override
    public FuzzyDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      // outer object with field "date-parts" - others should be ignored
      if (p.isExpectedStartObjectToken()) {
        JsonToken t;
        FuzzyDate date = null;
        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
          if (t == JsonToken.FIELD_NAME && p.getCurrentName().equalsIgnoreCase(DATE_PARTS)) {
            // outer array next
            p.nextToken();
            if (p.isExpectedStartArrayToken()) {
              // look for inner arrays
              t = p.nextToken();
              if (t == JsonToken.START_ARRAY) {
                date = FuzzyDate.of(readArray(p, ctxt));
                // range with end date?
                t = p.nextToken();
                if (t == JsonToken.START_ARRAY) {
                  int[] end = readArray(p, ctxt);
                  LOG.debug("csl end date {} found, but fuzzy date cannot handle this", end);
                }
              }
            }
          }
        }
        return date;
      }

      // if we reach here some token was wrong
      ctxt.handleUnexpectedToken(FuzzyDate.class, p);
      return null;
    }

    private int[] readArray(JsonParser p, DeserializationContext ctxt) throws IOException {
      int idx = 0;
      int[] array = new int[3];
      JsonToken t;
      while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
        if (t == JsonToken.VALUE_NUMBER_INT) {
          if (idx==3) {
            ctxt.handleUnexpectedToken(FuzzyDate.class, p);
          }
          array[idx++] = p.getIntValue();
        }
      }
      return idx == 3 ? array : Arrays.copyOf(array, idx);
    }
  }

}
