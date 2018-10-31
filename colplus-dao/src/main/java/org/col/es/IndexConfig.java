package org.col.es;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.col.es.query.EsSearchRequest;

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
   * out of ES, but not much because cardinality will be low for enums. And it saves space, notably
   * in the "payload" field of EsNameUsage. On the other hand, it makes the index harder to read in
   * Kibana.
   */
  public Boolean storeEnumAsInt = Boolean.TRUE;

  private ObjectReader reader;
  private ObjectWriter writer;
  private ObjectWriter queryWriter;

  /**
   * Returns a simple ObjectMapper, basically just taking the storeEnumAsInt into account.
   * 
   * @return
   */
  public ObjectMapper getMapper() {
    if (simpleMapper == null) {
      simpleMapper = new ObjectMapper();
      if (storeEnumAsInt) {
        simpleMapper.enable(SerializationFeature.WRITE_ENUMS_USING_INDEX);
      }
      simpleMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
      simpleMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
      simpleMapper.setSerializationInclusion(Include.NON_EMPTY);
    }
    return simpleMapper;
  }

  /**
   * Returns a specialized ObjectReader used to read ES documents into EsNameUsage instances.
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
   * Returns a specialized ObjectWriter used to serialize EsNameUsage instances.
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
   * Returns a specialized ObjectWriter used to serialize Elasticsearch queries (which are actually
   * embodied by the EsSearchRequest class, not the Query interface).
   * 
   * @return
   */
  public ObjectWriter getQueryWriter() {
    if (queryWriter == null) {
      queryWriter = getMapper().writerFor(EsSearchRequest.class);
    }
    return queryWriter;
  }

}
