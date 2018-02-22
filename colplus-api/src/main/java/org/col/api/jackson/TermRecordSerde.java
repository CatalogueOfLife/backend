package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * {@link TermRecord} that renders terms with their prefixed term name or full URI if no prefix is known.
 */
public class TermRecordSerde {
  private static final String KEY_TYPE = "_type";
  private static final String KEY_FILE = "_file";
  private static final String KEY_ROWNUM = "_line";
  static final String EXT_KEY = "extensions";

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
        serializeFields(value, jgen);
        jgen.writeEndObject();
      }
    }

    void serializeFields(TermRecord value, JsonGenerator jgen) throws IOException {
      if (value.getType() != null) {
        jgen.writeStringField(KEY_TYPE, value.getType().prefixedName());
      }
      if (value.getFile() != null) {
        jgen.writeStringField(KEY_FILE, value.getFile());
      }
      jgen.writeNumberField(KEY_ROWNUM, value.getLine());
      for (Map.Entry<Term, String> entry : value.termValues()) {
        jgen.writeStringField(entry.getKey().prefixedName(), entry.getValue());
      }
    }
  }

  /**
   * Deserializes list of maps of terms values.
   */
  public static class Deserializer extends DeserializerBase<TermRecord> {

    @Override
    public TermRecord deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      return deserialize(TermRecord.class, new TermRecord(), jp, ctxt);
    }

    void handleExtensions(TermRecord rec, JsonParser jp, DeserializationContext ctxt) throws IOException {
      ctxt.handleUnexpectedToken(TermRecord.class, jp);
    }
  }

  /**
   * Base deserializer that allows to be shared with other TermRecord subclasses.
   */
  abstract static class DeserializerBase<T extends TermRecord> extends JsonDeserializer<T> {

    static final TermFactory TF = TermFactory.instance();

    abstract void handleExtensions(T rec, JsonParser jp, DeserializationContext ctxt) throws IOException;

    T deserialize(Class<T> recClass, T rec, JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
        while( (jp.nextToken()) == JsonToken.FIELD_NAME ) {
          String key = jp.getText();

          if (key.equals(EXT_KEY)) {
            handleExtensions(rec, jp, ctxt);

          } else if (key.equals(KEY_TYPE)) {
            jp.nextToken();
            rec.setType(TF.findTerm(jp.getText(), true));

          } else if (key.equals(KEY_FILE)) {
            jp.nextToken();
            rec.setFile(jp.getText());

          } else if (key.equals(KEY_ROWNUM)) {
            jp.nextToken();
            rec.setLine(jp.getIntValue());

          } else {
            Term term = TF.findTerm(key);
            if (term == null && ctxt.getConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
              ctxt.handleUnknownProperty(jp, this, rec, key);
            }
            // get VALUE_STRING token
            jp.nextToken();
            rec.put(term, jp.getText());
          }
        }
        return rec;
      }
      return (T) ctxt.handleUnexpectedToken(recClass, jp);
    }
  }
}
