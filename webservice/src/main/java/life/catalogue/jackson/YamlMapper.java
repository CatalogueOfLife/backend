package life.catalogue.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.License;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.LicenseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;

/**
 * ColDP metadata parser that falls back to EML if no YAML metadata is found.
 */
public class YamlMapper {
  public static final ObjectMapper MAPPER;
  static {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    MAPPER = new ObjectMapper(
      new YAMLFactory()
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
    )
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .setDateFormat(dateFormat)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .registerModule(new JavaTimeModule())
    .registerModule(new ColdpMetadataModule());
  }

  public static  class ColdpMetadataModule extends SimpleModule {
    public ColdpMetadataModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
      addDeserializer(IntSet.class, new FastutilsSerde.SetDeserializer());
      addDeserializer(Country.class, new JsonDeserializer<Country>(){
        @Override
        public Country deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
          return CountryParser.PARSER.parseOrNull(jp.getText());
        }
      });
    }
    
    @Override
    public void setupModule(SetupContext ctxt) {
      // default enum serde
      ctxt.addSerializers(new PermissiveEnumSerde.PermissiveEnumSerializers());
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      super.setupModule(ctxt);
    }
  }

}
