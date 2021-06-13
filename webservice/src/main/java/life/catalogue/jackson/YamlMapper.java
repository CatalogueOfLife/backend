package life.catalogue.jackson;

import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.FuzzyDateCSLSerde;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Citation;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.LicenseParser;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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

  public static  class ColdpMetadataModule extends ApiModule {
    public ColdpMetadataModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
      // use CSL style dates
      // override normal deserializer to use the parser so we can accept various values for countries
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
      super.setupModule(ctxt);
      ctxt.setMixInAnnotations(Agent.class, AgentMixIn.class);
      ctxt.setMixInAnnotations(Dataset.class, DatasetMixIn.class);
    }
  }

  abstract class AgentMixIn {
    @JsonIgnore
    abstract String getName();
  }

  abstract class DatasetMixIn {

    @JsonIgnore
    abstract Integer getKey();

    @JsonIgnore
    abstract boolean isPrivat();

    @JsonIgnore
    abstract Integer getSourceKey();

    @JsonIgnore
    abstract Integer getAttempt();

    @JsonIgnore
    abstract DatasetOrigin getOrigin();

    @JsonIgnore
    abstract String getAliasOrTitle();

    @JsonIgnore
    abstract LocalDateTime getCreated();

    @JsonIgnore
    abstract Integer getCreatedBy();

    @JsonIgnore
    abstract LocalDateTime getModified();

    @JsonIgnore
    abstract Integer getModifiedBy();
  }
}
