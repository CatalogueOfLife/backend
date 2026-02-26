package life.catalogue.es.json;

import com.fasterxml.jackson.annotation.*;

import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.LabelPropertyFilter;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NameField;
import life.catalogue.es.EsException;

import org.gbif.nameparser.api.Rank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Set;

/**
 * Jackson module to configure object mappers for serialising content to ES.
 */
public class EsModule extends SimpleModule {

  static final ObjectMapper MAPPER = configureMapper(new ObjectMapper());

  /**
   * Returns the content mapper configured for ES document serialization (enums as ints, field-level access).
   * Used by the ES Java client's JacksonJsonpMapper transport layer.
   */
  public static ObjectMapper contentMapper() {
    return MAPPER;
  }

  private static final ObjectWriter DEBUG_WRITER = MAPPER.writer().withDefaultPrettyPrinter();

  public static String writeDebug(Object obj) {
    try {
      return DEBUG_WRITER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  public EsModule() {
    super("Elasticsearch");
    FastutilsSerde.register(this);
    addSerializer(Rank.class, new RankOrdinalSerde.Serializer());
    addDeserializer(Rank.class, new RankOrdinalSerde.Deserializer());
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(NameUsage.class, NameUsageMixIn.class);
    ctxt.setMixInAnnotations(Name.class, NameMixIn.class);
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@")
  @JsonSubTypes({
      @JsonSubTypes.Type(value = Taxon.class, name = "T"),
      @JsonSubTypes.Type(value = BareName.class, name = "B"),
      @JsonSubTypes.Type(value = Synonym.class, name = "S")
  })
  abstract static class NameUsageMixIn {
    @JsonIgnore abstract String getLabel();
    @JsonIgnore abstract String getLabelHtml();
    @JsonIgnore(false) @JsonProperty("nameFields") abstract Set<NameField> nonNullNameFields();
  }
  abstract static class NameMixIn {
    @JsonIgnore abstract String getLabelHtml();
    @JsonIgnore abstract String getBasionymOrCombinationAuthorship();
    @JsonIgnore(false) abstract String getScientificNameNormalized();
  }

  private static ObjectMapper configureMapper(ObjectMapper mapper) {
    mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new EsModule());
    //TODO: still needed ???
    FilterProvider filters = new SimpleFilterProvider().addFilter(LabelPropertyFilter.NAME, new LabelPropertyFilter());
    mapper.setFilterProvider(filters);
    return mapper;
  }

}
