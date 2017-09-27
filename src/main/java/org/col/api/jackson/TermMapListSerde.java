package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.Lists;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * lists of term maps as used in the verbatim class.
 * It renders terms with their full term URI.
 */
public class TermMapListSerde {

  /**
   * Serializes list of maps of terms values.
   */
  public static class Serializer extends JsonSerializer<List<Map<Term, String>>> {

    @Override
    public void serialize(List<Map<Term, String>> value, JsonGenerator jgen, SerializerProvider provider) throws IOException {

      if ((value == null || value.isEmpty()) && provider.getConfig().isEnabled(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)) {
        jgen.writeStartArray();
        jgen.writeEndArray();

      } else {
        jgen.writeStartArray();
        for (Map<Term, String> extension : value) {
          jgen.writeStartObject();
          for (Map.Entry<Term, String> entry : extension.entrySet()) {
            jgen.writeStringField(entry.getKey().qualifiedName(), entry.getValue());
          }
          jgen.writeEndObject();
        }
        jgen.writeEndArray();
      }
    }
  }

  /**
   * Deserializes list of maps of terms values.
   */
  public static class Deserializer extends JsonDeserializer<List<Map<Term, String>>> {

    private final TermFactory termFactory = TermFactory.instance();

    @Override
    public List<Map<Term, String>> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.START_ARRAY) {
        JsonDeserializer<Object> listDeserializer = ctxt.findContextualValueDeserializer(ctxt.constructType(List.class),null);
        List<Map<String, String>> verbatimTerms = (List<Map<String, String>>) listDeserializer.deserialize(jp, ctxt);
        List<Map<Term, String>> interpretedTerms = Lists.newArrayList();
        for (Map<String, String> verbExtension : verbatimTerms) {
          Map<Term, String> extension = new HashMap<Term, String>();
          for (Map.Entry<String, String> entry : verbExtension.entrySet()) {
            Term term = termFactory.findTerm(entry.getKey());
            if (term == null && ctxt.getConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
              throw ctxt.mappingException("Term not found " + entry.getKey());
            }
            extension.put(term, entry.getValue());
          }
          interpretedTerms.add(extension);
        }
        return interpretedTerms;
      }
      throw ctxt.mappingException("Expected JSON String");
    }
  }

}
