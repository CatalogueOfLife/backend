package life.catalogue.common.util;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 *
 */
public class YamlUtils {
  private static final YAMLMapper MAPPER = new YAMLMapper();

  static {
    MAPPER.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
    MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    MAPPER.registerModule(new ApiModule());
  }

  /**
   * Deserializes an object from a yaml input stream.
   */
  public static <T> T read(Class<T> objClass, InputStream configStream) throws IOException {
    return MAPPER.readValue(configStream, objClass);
  }

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, String resourceFile) throws IOException {
    return read(objClass, YamlUtils.class.getResourceAsStream(resourceFile));
  }

  /**
   * Deserializes an object from a yaml resource located by the system classloader.
   */
  public static <T> T read(Class<T> objClass, File configFile) throws IOException {
    return read(objClass, new FileInputStream(configFile));
  }

  /**
   * Deserializes an object from a yaml string.
   */
  public static <T> T readString(Class<T> objClass, String yaml) throws IOException {
    InputStream stream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    return read(objClass, stream);
  }

  /**
   * Serializes an object to a given yaml file
   */
  public static <T> void write(T obj, File file) throws IOException {
    MAPPER.writeValue(file, obj);
  }

  /**
   * Appends a serialized object to a given yaml file
   */
  public static <T> void write(T obj, Writer w) throws IOException {
    MAPPER.writeValue(w, obj);
  }

  /**
   * Appends a serialized object to a given yaml file
   */
  public static <T> void write(T obj, int indentation, Writer w) throws IOException {
    final String indent;
    if (indentation > 0) {
      indent = StringUtils.repeat(' ', indentation);
    } else {
      indent = "";
    }

    StringWriter yaml = new StringWriter();
    write(obj, yaml);

    try (BufferedReader br = UTF8IoUtils.readerFromString(yaml.toString())) {
      for (String line = br.readLine(); line != null; line = br.readLine()) {
        if (line.startsWith("---")) continue;
        w.write(indent);
        w.write(line);
        w.write("\n");
      }
    }
  }
}
