package life.catalogue.metadata.coldp;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.parser.EnumParser;
import life.catalogue.parser.UnparsableException;

import java.io.IOException;

import org.gbif.dwc.terms.Term;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class EnumParserSerde<T extends Enum<T>> {
  private static final Logger LOG = LoggerFactory.getLogger(EnumParserSerde.class);
  
  EnumParser<T> parser;
  
  public EnumParserSerde(EnumParser<T> parser) {
    this.parser = parser;
  }
  
  /**
   * Jackson serializer using lower case enum names.
   */
  public class Serializer extends JsonSerializer<T> {
    
    @Override
    public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      if (value == null) {
        jgen.writeNull();
      } else {
        jgen.writeString(PermissiveEnumSerde.enumValueName(value));
      }
    }
  }
  
  /**
   * Deserializes the value safely from any format the enum parser understands.
   * If unparsable sets it to null instead of throwing.
   */
  public class Deserializer extends JsonDeserializer<T> {
  
    @Override
    public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
      if (jp.getCurrentToken() == JsonToken.VALUE_STRING) {
        String txt = jp.getText();
        try {
          var res = parser.parse(txt);
          return res.orElse(null);
        
        } catch (UnparsableException e) {
          LOG.warn("Unable to parse {} into {}", txt, parser.getEnumClass().getSimpleName());
          e.printStackTrace();
        }
        return null;
      }
      throw ctxt.wrongTokenException(jp, Enum.class, JsonToken.VALUE_STRING, "Expected string");
    }
  }
  
}
