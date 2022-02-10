package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.metadata.coldp.YamlMapper;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.fasterxml.jackson.databind.ObjectWriter;

public class DatasetYamlWriter {
  private static ObjectWriter WRITER = YamlMapper.MAPPER.writerFor(Dataset.class);

  private DatasetYamlWriter() {
  }

  public static void write(Dataset d, File f) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      write(d,w);
    }
  }

  public static void write(Dataset d, OutputStream out) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      write(d,w);
    }
  }

  public static void write(Dataset d, Writer w) throws IOException {
    WRITER.writeValue(w, d);
  }

}
