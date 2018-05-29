package org.col.admin.importer.neo.kryo;

import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.InputChunked;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.io.OutputChunked;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonObjSerializer extends Serializer<ObjectNode> {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  static {
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
  }

  public JsonObjSerializer() {
    super(false);
  }

  @Override
  public void write(final Kryo kryo, final Output output, final ObjectNode obj) {
    OutputChunked chunked = new OutputChunked(output, 256);
    try {
      chunked.write(MAPPER.writeValueAsBytes(obj));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    chunked.endChunks();
  }

  @Override
  public ObjectNode read(final Kryo kryo, final Input input, final Class<ObjectNode> uuidClass) {
    InputChunked chunked = new InputChunked(input, 256);
    try {
      return  (ObjectNode) MAPPER.readTree(chunked);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}