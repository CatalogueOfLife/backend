package org.col.es;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.col.api.model.BareName;
import org.col.api.model.NameUsage;
import org.col.api.model.Synonym;
import org.col.api.model.Taxon;
import org.col.api.search.NameUsageWrapper;
import org.col.es.dsl.EsSearchRequest;

/**
 * Jackson module to configure an object mapper to (de)serialize data stored in Elasticsearch. It uses MixIns to modify API model classes to
 * behave differently for ES.
 */
public class EsModule extends SimpleModule {

  public static final ObjectMapper MAPPER = configureMapper(new ObjectMapper(), true);

  /*
   * Define frequently used readers and writers
   */
  public static final ObjectWriter QUERY_WRITER = writerFor(EsSearchRequest.class);
  public static final ObjectWriter NAME_USAGE_WRITER = writerFor(NameUsageWrapper.class);
  public static final ObjectReader NAME_USAGE_READER = readerFor(NameUsageWrapper.class);
  // Generic writer displaying enums as strings
  public static final ObjectWriter DEBUG_WRITER = configureMapper(new ObjectMapper(), false)
      .writer()
      .withDefaultPrettyPrinter();

  public static ObjectReader readerFor(Class<?> c) {
    return MAPPER.readerFor(c);
  }

  public static ObjectWriter writerFor(Class<?> c) {
    return MAPPER.writerFor(c);
  }

  public static ObjectMapper configureMapper(ObjectMapper mapper, boolean enumToInt) {
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    if (enumToInt) {
      mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
    } else {
      mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    }
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new EsModule());
    return mapper;
  }

  public EsModule() {
    super("Elasticsearch");
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(NameUsage.class, NameUsageMixIn.class);
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes({@JsonSubTypes.Type(value = Taxon.class, name = "T"),
      @JsonSubTypes.Type(value = BareName.class, name = "B"),
      @JsonSubTypes.Type(value = Synonym.class, name = "S")})
  abstract class NameUsageMixIn {
  }

}
