package life.catalogue.es;

import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.LabelPropertyFilter;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.ddl.IndexDefinition;
import life.catalogue.es.query.EsSearchRequest;
import life.catalogue.es.response.EsMultiResponse;
import life.catalogue.es.response.EsResponse;
import life.catalogue.es.response.SearchHit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson module to configure object mappers required by ES code. Various categories of objects need to be (de)serialized:
 * <p>
 * <ol>
 * <li>In order to create an index (DDL-like actions), we need to <b>write</b> document type mappings and larger structures like
 * {@link IndexDefinition} objects.
 * <li>In order to index data, we need to write name usage documents (modelled by the {@link EsNameUsage} class).
 * <li>The {@code NameUsageDocument} class is a dressed-down and flattened version of the {@link NameUsageWrapper}, but it has a field
 * containing the serialized version of the entire {@code  NameUsageWrapper} object. So in order to index data we also need to <b>write</b>
 * {@code  NameUsageWrapper} objects.
 * <li>We need to <b>write</b> Elasticsearch queries, modelled by {@link EsSearchRequest} and the other classes in
 * {@code life.catalogue.es.query}.
 * <li>We need to <b>read</b> the Elasticsearch response
 * <li>We need to <b>read</b> the {@code NameUsageDocument} instances wrapped into the {@link SearchHit} instances within the response.
 * <li>We need to <b>read</b> the {@code  NameUsageWrapper} objects within the name usage documents in order to pass them up to the higher
 * levels of the backend. (Note though that we do not need to write them out again in order to serve them to client. That is left to the
 * REST layer and the {@link ApiModule}.)
 * </ol>
 * </p>
 * <p>
 * A few things need to be aware of when deciding how to configure the mappers:
 * <ol>
 * <li>Whatever the API and the {@code ApiModule} does, we strongly prefer to save enums as integers for space and performance reasons.
 * <li>However, in queries and DDL-like objects enums must be written as strings. This is not about the objects being saved or retrieved,
 * but about instructing Elasticsearch how to do it, and here Elasticsearch expects strings like "AND" and "OR"; not "1" and "0".
 * <li>Currently, most classes in {@code life.catalogue.es.query} have no getters or setters at all. They are just there to reflect the
 * structure of the Elasticsearch query DSL. So until we fix that, we are basically forced to serialize them using fields rather than
 * getters.
 * <li>The API and the {@code ApiModule} serializes {@code NameUsageWrapper} objects using their getters, so we need to ask ourselves
 * whether this discrepancy could be dangerous. However, until that turns out to be the case, we'll assume that if the ES code simply reads
 * and writes the entire state of a {@code NameUsageWrapper} object, the {@code ApiModule} should have no problems serializing it to the
 * client. In other words we'll assume that it's save to serialize using fields whatever the category of the object being serialized.
 * <li>When querying name usage documents, we get them wrapped into an Elasticsearch response object. The name usage documents need a mapper
 * that maps (and writes) enums to integers. The Elasticsearch response object needs a mapper that maps enums to strings. That looks like a
 * conundrum. However, we are now talking about <i>reading</i> JSON, not writing it, and Jackson will just try everything to infer the
 * intended enum constant. So it doesn't matter which mapper we use for deserialization.
 * </ol>
 * </p>
 */
public class EsModule extends SimpleModule {

  /*
   * We don't expose any of the mappers, readers and writers anymore, because we want full control over which things can be read and/or
   * written, and which reader/writer to use. Before we had code fabricating readers and writers (and even mappers) all over the place.
   */

  private static final ObjectMapper esObjectMapper = configureEsMapper(new ObjectMapper());
  private static final ObjectMapper contentMapper = configureContentMapper(new ObjectMapper());

  private static final ObjectWriter ddlWriter = esObjectMapper.writerFor(IndexDefinition.class);
  private static final ObjectWriter queryWriter = esObjectMapper.writerFor(EsSearchRequest.class);

  private static final ObjectReader responseReader = contentMapper.readerFor(new TypeReference<EsResponse<EsNameUsage>>() {});
  private static final ObjectReader multiResponseReader =
      contentMapper.readerFor(new TypeReference<EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>>>() {});

  private static final ObjectReader documentReader = contentMapper.readerFor(EsNameUsage.class);
  private static final ObjectWriter documentWriter = contentMapper.writerFor(EsNameUsage.class);

  private static final ObjectReader nameUsageReader = contentMapper.readerFor(NameUsageWrapper.class);
  private static final ObjectWriter nameUsageWriter = contentMapper.writerFor(NameUsageWrapper.class);

  private static final TypeReference<Map<String, Object>> mapType = new TypeReference<Map<String, Object>>() {};

  /**
   * Generic read method.
   * 
   * @param is
   * @return
   * @throws IOException
   */
  public static Map<String, Object> readIntoMap(InputStream is) throws IOException {
    // For reading any mapper will do
    return esObjectMapper.readValue(is, mapType);
  }

  /**
   * Escapes the provided string such that in can be embedded within a json document. Note that you should NOT surround the returned string
   * with double quotes. That's already done by this method; just embed what you get.
   * 
   * @param s
   * @return
   */
  public static String escape(String s) {
    try {
      return esObjectMapper.writeValueAsString(s);
    } catch (JsonProcessingException e) { // Won't happen
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

  public static EsResponse<EsNameUsage> readEsResponse(InputStream is) throws IOException {
    return responseReader.readValue(is);
  }

  public static EsMultiResponse<EsNameUsage, EsResponse<EsNameUsage>> readEsMultiResponse(InputStream is) throws IOException {
    return multiResponseReader.readValue(is);
  }

  public static EsNameUsage readDocument(InputStream is) throws IOException {
    return documentReader.readValue(is);
  }

  public static EsNameUsage readDocument(String json) throws IOException {
    return documentReader.readValue(json);
  }

  public static NameUsageWrapper readNameUsageWrapper(InputStream is) throws IOException {
    return nameUsageReader.readValue(is);
  }

  public static NameUsageWrapper readNameUsageWrapper(String json) throws IOException {
    return nameUsageReader.readValue(json);
  }

  public static String write(IndexDefinition indexDef) throws JsonProcessingException {
    return ddlWriter.writeValueAsString(indexDef);
  }

  public static String write(EsSearchRequest query) throws JsonProcessingException {
    return queryWriter.writeValueAsString(query);
  }

  public static String write(EsNameUsage document) throws JsonProcessingException {
    return documentWriter.writeValueAsString(document);
  }

  public static String write(NameUsageWrapper nuw) throws JsonProcessingException {
    return nameUsageWriter.writeValueAsString(nuw);
  }

  public static void write(OutputStream out, NameUsageWrapper nuw) throws IOException {
    nameUsageWriter.writeValue(out, nuw);
  }

  public static String writeDebug(Object obj) {
    try {
      return DEBUG_WRITER.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new EsException(e);
    }
  }

  public static void writeDebug(OutputStream out, Object obj) {
    // Jackson would close the outputstream when done; very undesirable, especially with System.out
    String s = writeDebug(obj);
    if (out instanceof PrintStream) {
      ((PrintStream) out).println(s);
    } else {
      new PrintStream(out).println(s);
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
    // required to properly register serdes
    super.setupModule(ctxt);
    ctxt.setMixInAnnotations(NameUsage.class, NameUsageMixIn.class);
    ctxt.setMixInAnnotations(Name.class, NameMixIn.class);
  }

  /**
   * We ignore many NameUsageWrapper fields that are already indexed separately so as to make the payload (and the entire document)
   * as small as possible and to cut down as much as possible on JSON processing. It's not necessary to prune away everything that can be
   * pruned away, as long as this mirrors NameUsageWrapperConverter.enrichPayload().
   */
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

  private static ObjectMapper configureContentMapper(ObjectMapper mapper) {
    configureMapper(mapper);
    mapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
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
