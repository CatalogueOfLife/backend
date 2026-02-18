package life.catalogue.es;

import life.catalogue.api.jackson.*;
import life.catalogue.api.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.gbif.nameparser.api.Rank;

/**
 * Jackson module to configure object mappers required by ES code.
 * <p>
 * With the migration to the typed ES Java client, most serialization for queries and responses
 * is handled by the client library. This module now primarily handles:
 * <ul>
 *   <li>Serialization of EsNameUsage documents (with enums as integers)</li>
 *   <li>Serialization/deserialization of NameUsageWrapper payloads</li>
 *   <li>General-purpose map reading from ES responses</li>
 * </ul>
 */
public class EsModule extends SimpleModule {

  private static final ObjectMapper esObjectMapper = configureEsMapper(new ObjectMapper());
  static final ObjectMapper contentMapper = configureContentMapper(new ObjectMapper());

  /**
   * Returns the content mapper configured for ES document serialization (enums as ints, field-level access).
   * Used by the ES Java client's JacksonJsonpMapper transport layer.
   */
  public static ObjectMapper contentMapper() {
    return contentMapper;
  }

  private static final ObjectWriter documentWriter = contentMapper.writerFor(EsNameUsage.class);
  private static final ObjectReader documentReader = contentMapper.readerFor(EsNameUsage.class);

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {};

  /**
   * Generic read method.
   */
  public static Map<String, Object> readIntoMap(InputStream is) throws IOException {
    return esObjectMapper.readValue(is, mapType);
  }

  /**
   * Escapes the provided string such that it can be embedded within a json document.
   */
  public static String escape(String s) {
    try {
      return esObjectMapper.writeValueAsString(s);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static <T> T convertValue(Object object, Class<T> cls) {
    return esObjectMapper.convertValue(object, cls);
  }

  public static <T> T readObject(InputStream is, Class<T> cls) throws IOException {
    return esObjectMapper.readValue(is, cls);
  }

  public static <T> T readObject(InputStream is, TypeReference<T> tr) throws IOException {
    return esObjectMapper.readValue(is, tr);
  }

  public static <T> T readObject(String json, Class<T> cls) throws IOException {
    return esObjectMapper.readValue(json, cls);
  }

  public static <T> T readObject(String json, TypeReference<T> tr) throws IOException {
    return esObjectMapper.readValue(json, tr);
  }

  public static EsNameUsage readDocument(InputStream is) throws IOException {
    return documentReader.readValue(is);
  }

  public static EsNameUsage readDocument(String json) throws IOException {
    return documentReader.readValue(json);
  }

  public static String write(EsNameUsage document) throws JsonProcessingException {
    return documentWriter.writeValueAsString(document);
  }

  public static String writeDebug(Object obj) {
    try {
      return DEBUG_WRITER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  private static final ObjectWriter DEBUG_WRITER = configureEsMapper(new ObjectMapper())
      .writer()
      .withDefaultPrettyPrinter();

  public EsModule() {
    super("Elasticsearch");
    FastutilsSerde.register(this);
  }

  @Override
  public void setupModule(SetupContext ctxt) {
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(NameUsage.class, NameUsageMixIn.class);
    ctxt.setMixInAnnotations(Name.class, NameMixIn.class);
    // we use name keyword for all enums, but not Rank:
    addSerializer(Rank.class, new RankOrdinalSerde.Serializer());
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@")
  @JsonSubTypes({@JsonSubTypes.Type(value = Taxon.class, name = "T"),
      @JsonSubTypes.Type(value = BareName.class, name = "B"),
      @JsonSubTypes.Type(value = Synonym.class, name = "S")})
  abstract static class NameUsageMixIn {
    @JsonIgnore abstract String getLabel();
    @JsonIgnore abstract String getLabelHtml();
  }
  abstract static class NameMixIn {
    @JsonIgnore abstract String getLabelHtml();
  }

  static ObjectMapper configureContentMapper(ObjectMapper mapper) {
    configureMapper(mapper);
    //mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
    FilterProvider filters = new SimpleFilterProvider().addFilter(LabelPropertyFilter.NAME, new LabelPropertyFilter());
    mapper.setFilterProvider(filters);
    return mapper;
  }

  private static ObjectMapper configureEsMapper(ObjectMapper mapper) {
    configureMapper(mapper);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    return mapper;
  }

  private static ObjectMapper configureMapper(ObjectMapper mapper) {
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new EsModule());
    return mapper;
  }

}
