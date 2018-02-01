package org.col.dw.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import org.col.dw.api.TermRecord;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * {@link TermRecord} that renders terms with their prefixed term name or full URI if no prefix is known.
 */
public class RecTermsSerde {

  /**
   * Serializes list of maps of terms values.
   */
  public static class Serializer extends JsonSerializer<TermRecord> {

    @Override
    public void serialize(TermRecord value, JsonGenerator jgen, SerializerProvider provider) throws IOException {

      if (value == null || value.isEmpty()) {
        if (provider.getConfig().isEnabled(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)) {
          jgen.writeStartObject();
          jgen.writeEndObject();
        }

      } else {
        jgen.writeStartObject();
        for (Map.Entry<Term, String> entry : value.entrySet()) {
          jgen.writeStringField(entry.getKey().toString(), entry.getValue());
        }
        jgen.writeEndObject();
      }
    }
  }

  /**
   * Deserializes list of maps of terms values.
   */
  public static class Deserializer extends JsonDeserializer<TermRecord> {

    private final TermFactory termFactory = TermFactory.instance();

    @Override
    public TermRecord deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
        TermRecord rec = new TermRecord();
        while( (jp.nextToken()) == JsonToken.FIELD_NAME ) {
          Term term = termFactory.findTerm(jp.getText());
          if (term == null && ctxt.getConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            throw ctxt.mappingException("Term not found " + jp.getText());
          }
          // get VALUE_STRING token
          jp.nextToken();
          rec.put(term, jp.getText());
        }
        return rec;
      }
      throw ctxt.mappingException("Expected JSON Object");
    }
  }

}
