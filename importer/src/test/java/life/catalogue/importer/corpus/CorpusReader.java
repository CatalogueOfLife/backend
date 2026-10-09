package life.catalogue.importer.corpus;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SettingsDeserializer;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.vocab.Setting;
import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * Reads the input of the interpreter corpus, which comes in two kinds, told apart by their header:
 * <ul>
 *   <li>the name parser corpus {@code clb-verbatim-names.tsv} of name-parser-rust: {@code rowType, scientificName,
 *   authorship, rank, code}. It carries no atoms, no dataset and no settings, so it only exercises names given as a
 *   string, with or without a separate authorship.</li>
 *   <li>the export of {@code interpreter-corpus-export.sql}: {@code dataset_key, type, terms, n}, the name terms of
 *   verbatim records as json, identical ones counted in {@code n}. Rows of type {@code dataset} carry the settings of
 *   a dataset instead and come before its records.</li>
 * </ul>
 * A row is kept by its line number and the seed, so a sample selects the same rows in every run.
 */
public class CorpusReader implements AutoCloseable {
  /**
   * The only dataset settings the name interpretation reads. Everything else is dropped, so that datasets differing
   * in other settings share one interpreter.
   */
  static final Set<Setting> NAME_SETTINGS = EnumSet.of(Setting.NOMENCLATURAL_CODE, Setting.PREFER_NAME_ATOMS,
    Setting.DONT_INFER_RANKS, Setting.EPITHET_ADD_HYPHEN);
  static final String SETTINGS_TYPE = "dataset";
  private static final DatasetSettings NO_SETTINGS = new DatasetSettings();
  private static final ObjectReader TERMS_READER = ApiModule.MAPPER.readerFor(new TypeReference<Map<Term, String>>() {});
  private static final ObjectReader RAW_READER = ApiModule.MAPPER.readerFor(new TypeReference<Map<String, Object>>() {});

  enum Kind {PARSER_CORPUS, EXPORT}

  private final BufferedReader reader;
  private final Kind kind;
  private final double fraction;
  private final HashFunction hash;
  private final Map<Integer, DatasetSettings> settings = new HashMap<>();
  private long line = 1;

  /**
   * @param fraction the share of rows to keep, 1 for all
   */
  public CorpusReader(File f, double fraction, long seed) throws IOException {
    this.reader = CorpusIO.reader(f);
    this.fraction = fraction;
    this.hash = Hashing.murmur3_128((int) seed);
    String header = reader.readLine();
    if (header == null) {
      kind = Kind.EXPORT;
    } else if (header.startsWith("rowType\t")) {
      kind = Kind.PARSER_CORPUS;
    } else if (header.startsWith("dataset_key\t")) {
      kind = Kind.EXPORT;
    } else {
      throw new IllegalArgumentException("Unknown corpus header in " + f + ": " + header);
    }
  }

  public Kind kind() {
    return kind;
  }

  /**
   * @return the next record to interpret, or null at the end of the file
   */
  @Nullable
  public CorpusRecord next() throws IOException {
    String l;
    while ((l = reader.readLine()) != null) {
      line++;
      if (l.isBlank()) continue;
      CorpusRecord r = kind == Kind.PARSER_CORPUS ? parserCorpus(l) : export(l);
      if (r != null && keep(r.line())) {
        return r;
      }
    }
    return null;
  }

  private boolean keep(long line) {
    if (fraction >= 1) return true;
    long h = hash.hashLong(line).asLong() >>> 11; // 53 bits, the precision of a double
    return h < fraction * (1L << 53);
  }

  @Nullable
  private CorpusRecord parserCorpus(String l) {
    String[] row = CorpusIO.split(l, 5);
    Term type = TermFactory.instance().findClassTerm(row[0]);
    Map<Term, String> terms = new LinkedHashMap<>();
    if (type == DwcTerm.Taxon) {
      put(terms, DwcTerm.scientificName, row[1]);
      put(terms, DwcTerm.scientificNameAuthorship, row[2]);
      put(terms, DwcTerm.taxonRank, row[3]);
      put(terms, DwcTerm.nomenclaturalCode, row[4]);
    } else if (type == ColdpTerm.Name || type == ColdpTerm.NameUsage) {
      put(terms, ColdpTerm.scientificName, row[1]);
      put(terms, ColdpTerm.authorship, row[2]);
      put(terms, ColdpTerm.rank, row[3]);
      put(terms, ColdpTerm.code, row[4]);
    } else {
      throw new IllegalArgumentException("Unsupported row type " + row[0] + " in line " + line);
    }
    return new CorpusRecord(line, 1, null, type, terms, NO_SETTINGS);
  }

  private static void put(Map<Term, String> terms, Term t, @Nullable String val) {
    if (val != null) {
      terms.put(t, val);
    }
  }

  @Nullable
  private CorpusRecord export(String l) throws IOException {
    String[] row = CorpusIO.split(l, 4);
    Integer datasetKey = row[0] == null ? null : Integer.valueOf(row[0]);
    if (SETTINGS_TYPE.equals(row[1])) {
      settings.put(datasetKey, settings(row[2]));
      return null;
    }
    Term type = TermFactory.instance().findClassTerm(row[1]);
    Map<Term, String> terms = row[2] == null ? Map.of() : TERMS_READER.readValue(row[2]);
    int n = row[3] == null ? 1 : Integer.parseInt(row[3]);
    return new CorpusRecord(line, n, datasetKey, type, terms, settings.getOrDefault(datasetKey, NO_SETTINGS));
  }

  static DatasetSettings settings(@Nullable String json) throws IOException {
    if (json == null) {
      return NO_SETTINGS;
    }
    Map<Setting, Object> map = SettingsDeserializer.keysFromJson(RAW_READER.readValue(json));
    map.keySet().retainAll(NAME_SETTINGS);
    SettingsDeserializer.convertFromJSON(map);
    return DatasetSettings.of(map);
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
