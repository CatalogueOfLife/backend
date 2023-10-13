package life.catalogue.analytics;

import org.gbif.api.vocabulary.Country;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;


public class SerDeUtils {

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().addMixIn(Country.class, CountryMixin.class);

  public static ObjectMapper jsonDBMapper() {
    return OBJECT_MAPPER;
  }

  @JsonSerialize(
      using = Country.IsoSerializer.class,
      keyUsing = CountryMixin.CountryKeySerializer.class)
  @JsonDeserialize(
      using = Country.IsoDeserializer.class,
      keyUsing = CountryMixin.CountryKeyDeserializer.class)
  interface CountryMixin {

    class CountryKeySerializer extends JsonSerializer<Country> {

      @Override
      public void serialize(Country value, JsonGenerator jgen, SerializerProvider provider)
          throws IOException {
        jgen.writeFieldName(value.getIso2LetterCode());
      }
    }

    class CountryKeyDeserializer extends KeyDeserializer {

      @Override
      public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        try {
          if (key != null && !key.isEmpty()) {
            return Country.fromIsoCode(key);
          } else {
            return Country.UNKNOWN; // none provided
          }
        } catch (Exception e) {
          throw new IOException(
              "Unable to deserialize country from provided value (hint: are you using the iso 2 letter code?): "
                  + key);
        }
      }
    }
  }
}
