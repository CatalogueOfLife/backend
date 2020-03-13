package life.catalogue.api.jackson;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;

/**
 * Lenient handler for deserializing CSL-JSON which contains array of strings for some properties
 * from some source like CrossRef and Mendeley where the JSON schema mand ates a simple String.
 *
 * Uses the fist entry instead.
 */
public class CslArrayMismatchHandler extends DeserializationProblemHandler {
  @Override
  public Object handleUnexpectedToken(DeserializationContext ctxt, Class<?> targetType, JsonToken t, JsonParser p, String failureMsg) throws IOException {
    // be lenient for CSL data and use the first array entry as single string value
    if (targetType.equals(String.class) && t == JsonToken.START_ARRAY){
      Object val = p.getParsingContext().getParent() == null ? p.getParsingContext().getCurrentValue() : p.getParsingContext().getParent().getCurrentValue();
      if (val instanceof CslData || val instanceof CslName || val instanceof CslDate) {
        return parseFirstArrayItem(p);
      }
    }
    return super.handleUnexpectedToken(ctxt, targetType, t, p, failureMsg);
  }
  
  private String parseFirstArrayItem(JsonParser p) throws IOException {
    String first = null;
    while (p.getCurrentToken() != null && p.getCurrentToken() != JsonToken.END_ARRAY) {
      if (first == null) {
        first = p.getValueAsString();
      }
      p.nextToken();
    }
    return first;
  }
}
