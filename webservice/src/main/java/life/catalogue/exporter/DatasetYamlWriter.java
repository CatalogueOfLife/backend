package life.catalogue.exporter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import life.catalogue.api.model.ArchivedDataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.jackson.YamlMapper;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DatasetYamlWriter {
  private static ObjectWriter WRITER = YamlMapper.MAPPER.writerFor(YamlDataset.class);

  private DatasetYamlWriter() {
  }

  public static void write(ArchivedDataset d, File f) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      write(d,w);
    }
  }

  public static void write(ArchivedDataset d, OutputStream out) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromStream(out)) {
      write(d,w);
    }
  }

  public static void write(ArchivedDataset d, Writer w) throws IOException {
    WRITER.writeValue(w, new YamlDataset(d));
  }

  static class YamlDataset extends ArchivedDataset {
    public YamlDataset(ArchivedDataset d) {
      super(d);
    }

    @Override
    @JsonIgnore
    public Integer getKey() {
      return super.getKey();
    }

    @Override
    @JsonIgnore
    public Integer getSourceKey() {
      return super.getSourceKey();
    }

    @Override
    @JsonIgnore
    public Integer getImportAttempt() {
      return super.getImportAttempt();
    }

    @Override
    @JsonIgnore
    public DatasetOrigin getOrigin() {
      return super.getOrigin();
    }

    @Override
    @JsonIgnore
    public String getAliasOrTitle() {
      return super.getAliasOrTitle();
    }

    @Override
    @JsonProperty("taxonomicScope")
    public String getGroup() {
      return super.getGroup();
    }

    @Override
    @JsonIgnore
    public LocalDateTime getCreated() {
      return super.getCreated();
    }

    @Override
    @JsonIgnore
    public Integer getCreatedBy() {
      return super.getCreatedBy();
    }

    @Override
    @JsonIgnore
    public LocalDateTime getModified() {
      return super.getModified();
    }

    @Override
    @JsonIgnore
    public Integer getModifiedBy() {
      return super.getModifiedBy();
    }

    @Override
    public LocalDate getReleased() {
      return super.getReleased();
    }
  }
}
