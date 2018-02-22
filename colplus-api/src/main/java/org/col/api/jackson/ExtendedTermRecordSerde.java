package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.Lists;
import org.col.api.model.ExtendedTermRecord;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.util.List;

/**
 * Jackson {@link JsonSerializer} and Jackson {@link JsonDeserializer} classes for
 * {@link TermRecord} that renders terms with their prefixed term name or full URI if no prefix is known.
 */
public class ExtendedTermRecordSerde {

  /**
   * Serializes list of maps of terms values.
   */
  public static class Serializer extends JsonSerializer<ExtendedTermRecord> {
    private TermRecordSerde.Serializer tr = new TermRecordSerde.Serializer();

    @Override
    public void serialize(ExtendedTermRecord value, JsonGenerator jgen, SerializerProvider provider) throws IOException {

      if (value == null || value.isEmpty()) {
        if (provider.getConfig().isEnabled(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)) {
          jgen.writeStartObject();
          jgen.writeEndObject();
        }

      } else {
        jgen.writeStartObject();
          // first the core as a normal term record
          tr.serializeFields(value, jgen);
          // now extensions
          jgen.writeObjectFieldStart(TermRecordSerde.EXT_KEY);
          for (Term rt : value.getExtensionRowTypes()) {
            jgen.writeArrayFieldStart(rt.prefixedName());
            for (TermRecord rec : value.getExtensionRecords(rt)) {
              tr.serialize(rec, jgen, provider);
            }
            jgen.writeEndArray();
          }
          jgen.writeEndObject();
        jgen.writeEndObject();
      }
    }
  }

  /**
   * Deserializes list of maps of terms values.
   */
  public static class Deserializer extends TermRecordSerde.DeserializerBase<ExtendedTermRecord> {
    private TermRecordSerde.Deserializer tr = new TermRecordSerde.Deserializer();

    @Override
    public ExtendedTermRecord deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      return deserialize(ExtendedTermRecord.class, new ExtendedTermRecord(), jp, ctxt);
    }

    @Override
    void handleExtensions(ExtendedTermRecord rec, JsonParser jp, DeserializationContext ctxt) throws IOException {
      nextToken(jp, JsonToken.START_OBJECT, ctxt);
      while( (jp.nextToken()) == JsonToken.FIELD_NAME ) {
        Term rowType = TF.findTerm(jp.getText(), true);

        nextToken(jp, JsonToken.START_ARRAY, ctxt);
        List<TermRecord> erecs = Lists.newArrayList();
        while ((jp.nextToken()) == JsonToken.START_OBJECT) {
          erecs.add(tr.deserialize(TermRecord.class, new TermRecord(), jp, ctxt));
          //nextToken(jp, JsonToken.END_OBJECT, ctxt);
        }
        rec.setExtensionRecords(rowType, erecs);
      }
    }

    static void nextToken(JsonParser jp, JsonToken expectedToken, DeserializationContext ctxt) throws IOException {
      if (jp.nextToken() != expectedToken) {
        ctxt.handleUnexpectedToken(TermRecord.class, jp);
      }
    }
  }
}
