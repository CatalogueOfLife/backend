package org.col.es;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

public class IndexConfig {

  private static ObjectMapper simpleMapper;

  /**
   * The model class corresponding to the type.
   */
  public String modelClass;
  public int numShards = 1;
  public int numReplicas = 0;
  /**
   * Batch size for bulk requests
   */
  public int batchSize = 1000;
  /**
   * Whether to store enums as ints or as strings. Storings as ints squeezes a bit more performance
   * out of ES, but not much because cardinality will be low anyhow. And it saves space, notably in
   * the "source" field of EsNameUsage. On the other hand, it makes the index harder to read in
   * Kibana.
   */
  public Boolean storeEnumAsInt = Boolean.TRUE;

  private ObjectReader reader;
  private ObjectWriter writer;
  private ObjectWriter prettyWriter;

  /**
   * Returns a mapper for (de)serializing Elasticseach documents and queries.
   * @return
   */
  public ObjectMapper getMapper() {
    if (simpleMapper == null) {
      simpleMapper = new ObjectMapper();
      if (storeEnumAsInt) {
        simpleMapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
        simpleMapper.setSerializationInclusion(Include.NON_EMPTY);
      }
    }
    return simpleMapper;
  }

  /**
   * ObjectReader used to deserialize ES documents into EsNameUsage instances.
   */
  public ObjectReader getObjectReader() {
    if (reader == null) {
      try {
        reader = getMapper().readerFor(Class.forName(modelClass));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return reader;
  }

  /**
   * ObjectWriter used to serialize EsNameUsage instances to ES documents.
   * 
   * @return
   */
  public ObjectWriter getObjectWriter() {
    if (writer == null) {
      try {
        writer = getMapper().writerFor(Class.forName(modelClass));
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
    return writer;
  }

  /**
   * Pretty prints EsNameUsage instances and Query instances.
   * 
   * @return
   */
  public ObjectWriter getPrettyWriter() {
    if (prettyWriter == null) {
      prettyWriter = getMapper().writer().withDefaultPrettyPrinter();
    }
    return prettyWriter;
  }

}
