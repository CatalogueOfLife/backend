package org.col.admin.task.importer.acef;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.io.FilenameUtils;
import org.col.admin.task.importer.NormalizationFailedException;
import org.col.admin.task.importer.VerbatimRecordFactory;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.util.io.CharsetDetection;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 *
 */
public class AcefReader {
  private static final Logger LOG = LoggerFactory.getLogger(AcefReader.class);
  private static final Pattern EMPTY = Pattern.compile("^[, \t\r\n\f\"]*$");
  private static final Splitter TAB = Splitter.on('\t').trimResults();
  private static final Splitter CSV = Splitter.on(',').trimResults();
  private static final Map<String, Splitter> DATA_FILE_TYPES = ImmutableMap.<String, Splitter>builder()
      .put("csv",  CSV)
      .put("txt",  TAB)
      .put("tsv",  TAB)
      .put("tab",  TAB)
      .put("text", TAB)
      .build();

  private final File folder;
  private final Map<Term, Schema> schemas = Maps.newHashMap();

  private AcefReader(File folder) {
    this.folder = folder;
  }

  private static class Schema {
    final Path file;
    final Charset encoding;
    final Splitter splitter;
    final boolean quoted;
    final boolean header;
    final List<Term> columns;

    private Schema(Path file, Charset encoding, Splitter splitter, List<Term> columns) {
      this.file = file;
      this.encoding = encoding;
      this.splitter = splitter;
      this.columns = columns;
      this.quoted = false;
      this.header = true;
    }

    public boolean isEmpty() {
      return columns.isEmpty();
    }
  }

  /**
   * Scans a folder with files for ACEF files, named as ACEF class terms.
   * If present a header row will be used in each file to determine the column mapping.
   * Otherwise a standard ordering given in the
   * CoL Data Submission Format, ver. 4 of 29th September 2014 is used.
   */
  public static AcefReader from(File folder) throws IOException {
    if (!folder.exists() || !folder.isDirectory()) {
      throw new FileNotFoundException("ACEF folder does not exist: " + folder.getAbsolutePath());
    }

    AcefReader reader = new AcefReader(folder);
    for (Path df : listDataFiles(folder)) {
      Term rowType = VocabularyUtils.TF.findTerm(AcefTerm.PREFIX + ":" + PathUtils.getBasename(df));
      LOG.debug("Detecting schema for file {}, rowType={}", PathUtils.getFilename(df), rowType);
      if (rowType != null && rowType instanceof AcefTerm) {
        Schema s = buildSchema(df);
        if (s != null) {
          reader.schemas.put(rowType, s);
        }
      }
    }

    reader.validate();

    return reader;
  }

  private static Schema buildSchema(Path df) {

    Charset charset = null;
    try {
      charset = CharsetDetection.detectEncoding(df);
      if (charset.equals(Charsets.UTF_8)) {
        LOG.debug("Using encoding {} for file {}", charset, df);
      } else {
        LOG.warn("Using non standard encoding {} for file {}", charset, df);
      }

      Optional<String> header = Files.lines(df, charset).findFirst();
      if (!header.isPresent()) {
        throw new IllegalArgumentException("Header rows with ACEF terms required for " + df);

      } else {
        List<Term> columns = Lists.newArrayList();
        Splitter splitter = DATA_FILE_TYPES.getOrDefault(FilenameUtils.getExtension(df.toFile().getName()).toLowerCase(), TAB);
        for (String col : splitter.split(header.get())) {
          Term term = VocabularyUtils.TF.findTerm("acef:" + col);
          columns.add(term);
          if (!(term instanceof AcefTerm)) {
            LOG.warn("Non ACEF Term found: {}", term.qualifiedName());
          }
        }
        LOG.debug("{} file schema with {} column found for file {}", splitter.equals(TAB) ? "TAB" : "CSV", columns.size(), df);
        return new Schema(df, charset, splitter, columns);
      }

    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", df, e);
    }
    return null;
  }

  private static Iterable<Path> listDataFiles(File folder) throws IOException {
    return Files.newDirectoryStream(folder.toPath(), new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && DATA_FILE_TYPES.containsKey(PathUtils.getFileExtension(p));
      }
    });
  }

  private static String filename(AcefTerm rowType) {
    if (!rowType.isClass()) {
      throw new IllegalArgumentException("No class term given as row type");
    }
    return rowType.name().toLowerCase() + ".txt";
  }

  private void validate() throws NormalizationFailedException.SourceInvalidException {
    if (!hasData(AcefTerm.AcceptedSpecies)) {
      throw new NormalizationFailedException.SourceInvalidException(filename(AcefTerm.AcceptedSpecies) + " file required but missing from " + folder);
    }

    for (AcefTerm t : AcefTerm.values()) {
      if (t.isClass()) {
        if (!hasData(t)) {
          LOG.info("{} missing from ACEF in {}", t.name(), folder);
        }
      }
    }
  }

  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public Stream<TermRecord> read(AcefTerm rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType "+rowType+" is not a class term");
    if (schemas.containsKey(rowType)) {
      return read(schemas.get(rowType));
    } else {
      return Stream.empty();
    }
  }

  /**
   * Returns the first row of the given data file
   */
  public Optional<TermRecord> readFirstRow(AcefTerm rowType) {
    if (schemas.containsKey(rowType)) {
      return read(schemas.get(rowType)).findFirst();
    }
    return Optional.empty();
  }

  private Stream<TermRecord> read(final Schema s) {
    final AtomicInteger line = new AtomicInteger(0);
    final String filename = PathUtils.getFilename(s.file);
    final int cols = s.columns.size();

    Stream<String> lines;
    try {
      lines = Files.lines(s.file, s.encoding);

    } catch (IOException e) {
      LOG.error("Filed to read {}", s.file, e);
      return Stream.empty();
    }

    return lines
        // ignore header
        .skip(s.header ? 1 : 0)
        // inc line number & skip whitespace only rows
        .filter(row -> {
          line.incrementAndGet();
          if (EMPTY.matcher(row).find()) {
            LOG.debug("Skip empty row. {} line {}", filename, line);
            return false;
          }
          return true;
        })
        .map(s.splitter::splitToList)
        .filter(row -> {
          if (row.size() < cols) {
            LOG.warn("Skip row with {} columns not {}. {} line {}", row.size(), s.columns.size(), filename, line);
            return false;
          }
          return true;
        })
        // convert into TermRecord
        .map(row -> {
          TermRecord tr = new TermRecord();
          for (int i = 0; i<cols; i++) {
            Term t = s.columns.get(i);
            if (t != null) {
              if (!Strings.isNullOrEmpty(row.get(i))) {
                String val = VerbatimRecordFactory.clean(row.get(i));
                tr.put(t, val);
              }
            }
          }
          return tr;
        });
    }

}
