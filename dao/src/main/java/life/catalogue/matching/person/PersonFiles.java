package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.io.Resources;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Reads and writes the three files of the person registry. They are tab delimited with a header that is verified,
 * lists are pipe separated and an empty cell is null. Written sorted, so a harvest diffs line by line.
 */
public class PersonFiles {
  public static final String RESOURCE_DIR = "authorship/persons/";
  static final String PERSONS = "persons.tsv";
  static final String NAMES = "names.tsv";
  static final String RELATIONS = "relations.tsv";
  static final List<String> PERSON_COLUMNS = List.of("id", "wikidata", "ipni", "zoobank", "formerIds", "family", "given",
    "suffix", "born", "died", "activeFrom", "activeTo", "groups", "source");
  static final List<String> NAME_COLUMNS = List.of("person", "form", "kind", "code", "source");
  static final List<String> RELATION_COLUMNS = List.of("person", "relation", "other", "source");
  private static final String LIST_SEPARATOR = "|";

  public record Content(List<Person> persons, List<PersonName> names, List<PersonRelation> relations) {
    public static Content empty() {
      return new Content(List.of(), List.of(), List.of());
    }
  }

  private PersonFiles() {
  }

  private interface Opener {
    BufferedReader open(String file) throws IOException;
  }

  /**
   * @return the registry that ships with the code
   */
  public static Content readResources() throws IOException {
    return read(f -> Resources.reader(RESOURCE_DIR + f));
  }

  public static Content read(Path dir) throws IOException {
    return read(f -> Files.newBufferedReader(dir.resolve(f), StandardCharsets.UTF_8));
  }

  private static Content read(Opener opener) throws IOException {
    return new Content(
      rows(opener, PERSONS, PERSON_COLUMNS, PersonFiles::person),
      rows(opener, NAMES, NAME_COLUMNS, r -> new PersonName(r[0], r[1], PersonNameKind.valueOf(r[2]), PersonFormCode.valueOf(r[3]), PersonSource.of(r[4]))),
      rows(opener, RELATIONS, RELATION_COLUMNS, r -> new PersonRelation(r[0], PersonRelationType.valueOf(r[1]), r[2], PersonSource.of(r[3])))
    );
  }

  private static <T> List<T> rows(Opener opener, String file, List<String> columns, Function<String[], T> parser) throws IOException {
    try (BufferedReader reader = opener.open(file)) {
      String header = reader.readLine();
      if (header == null || !Arrays.asList(header.split("\t", -1)).equals(columns)) {
        throw new IllegalArgumentException("Unexpected header in " + file + ": " + header);
      }
      List<T> list = new ArrayList<>();
      String line;
      int n = 1;
      while ((line = reader.readLine()) != null) {
        n++;
        if (line.isEmpty()) continue;
        String[] row = line.split("\t", -1);
        if (row.length != columns.size()) {
          throw new IllegalArgumentException(file + " line " + n + " has " + row.length + " columns, not " + columns.size());
        }
        try {
          list.add(parser.apply(row));
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(file + " line " + n + ": " + e.getMessage(), e);
        }
      }
      return list;
    }
  }

  private static Person person(String[] r) {
    return new Person(r[0], str(r[1]), str(r[2]), str(r[3]), list(r[4]), str(r[5]), str(r[6]), str(r[7]),
      num(r[8]), num(r[9]), num(r[10]), num(r[11]), groups(r[12]), PersonSource.of(r[13]));
  }

  @Nullable
  private static String str(String x) {
    return StringUtils.trimToNull(x);
  }

  @Nullable
  private static Integer num(String x) {
    return StringUtils.isBlank(x) ? null : Integer.valueOf(x.trim());
  }

  private static List<String> list(String x) {
    return StringUtils.isBlank(x) ? List.of() : List.of(StringUtils.split(x, LIST_SEPARATOR));
  }

  private static Set<TaxGroup> groups(String x) {
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    for (String g : list(x)) {
      groups.add(TaxGroup.valueOf(g));
    }
    return groups;
  }

  public static void write(Path dir, Content c) throws IOException {
    Files.createDirectories(dir);
    write(dir.resolve(PERSONS), PERSON_COLUMNS, c.persons().stream()
      .sorted(Comparator.comparing(Person::id))
      .map(p -> new Object[]{p.id(), p.wikidata(), p.ipni(), p.zoobank(), String.join(LIST_SEPARATOR, p.formerIds()),
        p.family(), p.given(), p.suffix(), p.born(), p.died(), p.activeFrom(), p.activeTo(),
        p.groups().stream().sorted().map(Enum::name).collect(Collectors.joining(LIST_SEPARATOR)), p.source().value()})
      .toList());
    write(dir.resolve(NAMES), NAME_COLUMNS, c.names().stream()
      .sorted(Comparator.comparing(PersonName::person).thenComparing(PersonName::kind).thenComparing(PersonName::code)
        .thenComparing(PersonName::form))
      .map(n -> new Object[]{n.person(), n.form(), n.kind(), n.code(), n.source().value()})
      .toList());
    write(dir.resolve(RELATIONS), RELATION_COLUMNS, c.relations().stream()
      .sorted(Comparator.comparing(PersonRelation::person).thenComparing(PersonRelation::relation)
        .thenComparing(PersonRelation::other))
      .map(r -> new Object[]{r.person(), r.relation(), r.other(), r.source().value()})
      .toList());
  }

  private static void write(Path file, List<String> columns, List<Object[]> rows) throws IOException {
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write(String.join("\t", columns));
      w.write('\n');
      for (Object[] row : rows) {
        w.write(Arrays.stream(row).map(PersonFiles::cell).collect(Collectors.joining("\t")));
        w.write('\n');
      }
    }
  }

  /**
   * A tab or line break inside a value would break the row, Wikidata labels hold them now and then.
   */
  private static String cell(@Nullable Object x) {
    return x == null ? "" : StringUtils.normalizeSpace(x.toString().replaceAll("[\\t\\r\\n]", " "));
  }
}
