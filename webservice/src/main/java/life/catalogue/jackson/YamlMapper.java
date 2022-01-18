package life.catalogue.jackson;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.fasterxml.jackson.databind.node.TextNode;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.FuzzyDateCSLSerde;
import life.catalogue.api.jackson.FuzzyDateISOSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.LicenseParser;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
      ctxt.setMixInAnnotations(Citation.class, CitationMixIn.class);
    }
  }

  abstract class AgentMixIn {
    @JsonIgnore
    abstract String getName();

    @JsonIgnore
    abstract String getOrcidAsUrlorcidAsUrl();

  }

  abstract class DatasetMixIn {

    @JsonIgnore
    abstract Integer getKey();

    @JsonIgnore
    abstract UUID getGbifKey();

    @JsonIgnore
    abstract UUID getGbifPublisherKey();

    @JsonIgnore
    abstract boolean isPrivat();

    // JsonIgnore is not applied as the original field uses JsonProperty (seems to be a jackson bug)
    // but we can mark the field as write only to not show it in serialisations
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    abstract Integer getSize();

    @JsonIgnore
    abstract Integer getSourceKey();

    @JsonIgnore
    abstract Integer getAttempt();

    @JsonIgnore
    abstract DatasetType getType();

    @JsonIgnore
    abstract DatasetOrigin getOrigin();

    @JsonIgnore
    abstract String getAliasOrTitle();

    @JsonIgnore
    abstract String getCitation();

    @JsonIgnore
    abstract String getAddress();

    @JsonIgnore
    abstract LocalDateTime getImported();

    @JsonIgnore
    abstract LocalDateTime getDeleted();

    @JsonIgnore
    abstract LocalDateTime getCreated();

    @JsonIgnore
    abstract Integer getCreatedBy();

    @JsonIgnore
    abstract LocalDateTime getModified();

    @JsonIgnore
    abstract Integer getModifiedBy();
  }

  abstract class CitationMixIn {
    @JsonAlias("DOI")
    @JsonProperty("doi")
    private DOI doi;

    @JsonAlias("ISBN")
    @JsonProperty("isbn")
    private String isbn;

    @JsonAlias("ISSN")
    @JsonProperty("issn")
    private String issn;

    @JsonAlias("URL")
    @JsonProperty("url")
    private String url;

    @JsonSerialize(using = FuzzyDateISOSerde.Serializer.class)
    @JsonDeserialize(using = FuzzyDateISOSerde.Deserializer.class)
    private FuzzyDate issued;

    @JsonSerialize(using = FuzzyDateISOSerde.Serializer.class)
    @JsonDeserialize(using = FuzzyDateISOSerde.Deserializer.class)
    private FuzzyDate accessed;

    @JsonAlias("container-title")
    @JsonProperty("containerTitle")
    private String containerTitle;

    @JsonAlias("container-author")
    @JsonProperty("containerAuthor")
    private List<CslName> containerAuthor;

    @JsonAlias("collection-title")
    @JsonProperty("collectionTitle")
    private String collectionTitle;

    @JsonAlias("collection-editor")
    @JsonProperty("collectionEditor")
    private List<CslName> collectionEditor;

    @JsonAlias("publisher-place")
    @JsonProperty("publisherPlace")
    private String publisherPlace;

    @JsonIgnore
    abstract String getCitation();

    @JsonIgnore(false)
    @JsonProperty(value = "citation", access = JsonProperty.Access.READ_ONLY)
    abstract String getCitationText();

//    @JsonProperty("issn")
//    abstract String getIssn();
//
//    @JsonProperty("isbn")
//    abstract String getIsbn();
//
//    @JsonProperty("url")
//    abstract String getUrl();
  }

}
