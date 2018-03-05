package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwc.terms.UnknownTerm;

import java.io.IOException;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * terms as used in the verbatim class.
 * It renders terms with their prefixed name or full term URI if unknown.
 */
public class TermSerde {

  private static final TermFactory TERMFACTORY = TermFactory.instance();

  /**
   * Deserializer for {@link Term} in key values.
   */
  public static class TermKeyDeserializer extends KeyDeserializer {

    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
      if (key.length() == 0) { // [JACKSON-360]
        return null;
      }
      return TERMFACTORY.findTerm(key);
    }
  }

  /**
   * Serializes a term as its prefixed name string.
   */
  public static class ValueSerializer extends JsonSerializer<Term> {

    @Override
    public void serialize(Term value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        if (value instanceof UnknownTerm) {
          jgen.writeString(value.qualifiedName());
        } else {
          jgen.writeString(value.prefixedName());
        }
      }
    }
  }

  /**
   * Serializes a term as its prefixed name string into a json field.
   */
  public static class FieldSerializer extends JsonSerializer<Term> {

    @Override
    public void serialize(Term value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        if (value instanceof UnknownTerm) {
          jgen.writeFieldName(value.qualifiedName());
        } else {
          jgen.writeFieldName(value.prefixedName());
        }
      }
    }
  }

  /**
   * Deserializes terms values given as full URIs or prefixed names.
   */
  public static class Deserializer extends JsonDeserializer<Term> {

    @Override
    public Term deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING || jp.getCurrentToken() == JsonToken.FIELD_NAME) {
        return TERMFACTORY.findTerm(jp.getText());
      }
      throw ctxt.mappingException("Expected value string or field name");
    }
  }

}
