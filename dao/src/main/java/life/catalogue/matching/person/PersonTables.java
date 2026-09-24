package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.mapper.PersonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;

/**
 * Reads and replaces the person registry in Postgres: the tables person, person_id, person_name and person_relation.
 * A write replaces every row in the caller's transaction, so readers see the old registry until it commits. The any id
 * index and the derived forms are computed here, from the persons and their names; names and relations may refer to a
 * person by any of its ids and are written with its own.
 */
public final class PersonTables {
  private static final List<String> TABLES = List.of("person_relation", "person_name", "person_id", "person");

  private PersonTables() {
  }

  /**
   * Keeps other writers out, not readers, until the transaction ends.
   */
  public static void lock(SqlSession session) throws SQLException {
    try (Statement st = session.getConnection().createStatement()) {
      st.execute("LOCK TABLE person, person_id, person_name, person_relation IN EXCLUSIVE MODE");
    }
  }

  /**
   * @return the registry as its files hold it: names and relations by the persons' own ids, no derived forms
   */
  public static PersonFiles.Content read(SqlSession session) {
    var mapper = session.getMapper(PersonMapper.class);
    return new PersonFiles.Content(
      mapper.list().stream().map(PersonMapper.PersonRow::toPerson).toList(),
      mapper.listNames().stream().map(PersonMapper.NameRow::toName).toList(),
      mapper.listRelations().stream().map(PersonMapper.RelationRow::toRelation).toList());
  }

  /**
   * Checks the registry as {@link MemoryPersonStore#problems()} does and replaces the tables by it in one transaction.
   *
   * @throws IllegalArgumentException for an inconsistent registry, nothing written
   */
  public static void replace(SqlSessionFactory factory, PersonFiles.Content c) throws SQLException, IOException {
    List<String> problems = new MemoryPersonStore(c).problems();
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("The person registry is inconsistent, nothing written: "
        + String.join("; ", problems.subList(0, Math.min(20, problems.size()))));
    }
    transaction(factory, session -> write(session, c));
  }

  /**
   * Work on the registry within the transaction of {@link #transaction}.
   */
  @FunctionalInterface
  public interface Work {
    void run(SqlSession session) throws SQLException, IOException;
  }

  /**
   * Runs the work in one transaction that keeps other writers out, committed only if the work completes. Anything it
   * throws, an Error included, rolls it back: the writes go past MyBatis, whose session would otherwise commit them when
   * it closes and resets autocommit.
   */
  public static void transaction(SqlSessionFactory factory, Work work) throws SQLException, IOException {
    try (SqlSession session = factory.openSession(false)) {
      boolean committed = false;
      try {
        lock(session);
        work.run(session);
        session.commit(true);
        committed = true;
      } finally {
        if (!committed) {
          session.rollback(true);
        }
      }
    }
  }


  /**
   * Replaces every row by the registry, its derived forms and every id of its persons. The caller holds the lock and
   * commits. A person keeps its created timestamp and, unless its row changed, its modified one.
   */
  public static void write(SqlSession session, PersonFiles.Content c) throws SQLException {
    Map<String, PersonMapper.PersonRow> before = new HashMap<>();
    session.getMapper(PersonMapper.class).list().forEach(r -> before.put(r.id, r));
    Map<String, String> own = new HashMap<>();
    c.persons().forEach(p -> p.allIds().forEach(id -> own.put(id, p.id())));
    try (Statement st = session.getConnection().createStatement()) {
      for (String table : TABLES) {
        st.executeUpdate("DELETE FROM " + table);
      }
    }
    CopyManager mgr = new CopyManager(InitDbUtils.toPgConnection(session.getConnection()));
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    try (Copy copy = new Copy(mgr, "person(id, wikidata, ipni, zoobank, former_ids, family, given, suffix, born, died, "
      + "active_from, active_to, groups, source, retired, successor, created, modified)")) {
      for (Person p : c.persons()) {
        var b = before.get(p.id());
        LocalDateTime created = b == null ? now : b.created;
        LocalDateTime modified = b != null && b.toPerson().equals(p) ? b.modified : now;
        copy.row(p.id(), p.wikidata(), p.ipni(), p.zoobank(), p.formerIds(), p.family(), p.given(), p.suffix(), p.born(),
          p.died(), p.activeFrom(), p.activeTo(), p.groups().stream().map(Enum::name).sorted().toList(), p.source(),
          p.retired(), p.successor(), created, modified);
      }
      copy.end();
    }
    try (Copy copy = new Copy(mgr, "person_id(any_id, person_id)")) {
      for (var e : own.entrySet()) {
        copy.row(e.getKey(), e.getValue());
      }
      copy.end();
    }
    Map<String, List<PersonName>> names = new HashMap<>();
    for (PersonName n : c.names()) {
      String id = own.get(n.person());
      if (id == null) {
        throw new IllegalArgumentException("name " + n.form() + " refers to unknown person " + n.person());
      }
      names.computeIfAbsent(id, k -> new ArrayList<>()).add(n);
    }
    try (Copy copy = new Copy(mgr, "person_name(person_id, form, kind, code, source, key)")) {
      for (Person p : c.persons()) {
        List<PersonName> forms = names.getOrDefault(p.id(), List.of());
        // the keys the person has a form of every code under, which no derived form needs to repeat
        Set<String> any = new HashSet<>();
        for (PersonName n : forms) {
          String key = PersonKeys.key(n.form());
          copy.row(p.id(), n.form(), n.kind(), n.code(), n.source(), key);
          if (key != null && n.code() == PersonFormCode.ANY) {
            any.add(key);
          }
        }
        List<String> derived = new ArrayList<>(PersonForms.of(p));
        for (PersonName n : forms) {
          String d = PersonForms.of(p, n);
          if (d != null) {
            derived.add(d);
          }
        }
        for (String form : derived) {
          String key = PersonKeys.key(form);
          if (key != null && any.add(key)) {
            copy.row(p.id(), form, PersonNameKind.DERIVED, PersonFormCode.ANY, p.source(), key);
          }
        }
      }
      copy.end();
    }
    Set<List<Object>> seen = new HashSet<>();
    try (Copy copy = new Copy(mgr, "person_relation(person_id, relation, other_id, source)")) {
      for (PersonRelation r : c.relations()) {
        String a = own.get(r.person());
        String b = own.get(r.other());
        if (a == null || b == null) {
          throw new IllegalArgumentException("relation " + r.person() + " " + r.relation() + " " + r.other()
            + " refers to unknown person " + (a == null ? r.person() : r.other()));
        }
        if (seen.add(List.of(a, r.relation(), b))) {
          copy.row(a, r.relation(), b, r.source());
        }
      }
      copy.end();
    }
  }

  /**
   * One COPY in csv, fed row by row. Closing it without {@link #end()} cancels it.
   */
  private static final class Copy implements AutoCloseable {
    private final CopyIn in;

    Copy(CopyManager mgr, String table) throws SQLException {
      in = mgr.copyIn("COPY " + table + " FROM STDIN WITH (FORMAT csv)");
    }

    void row(Object... cells) throws SQLException {
      byte[] line = csv(cells).getBytes(StandardCharsets.UTF_8);
      in.writeToCopy(line, 0, line.length);
    }

    void end() throws SQLException {
      in.endCopy();
    }

    @Override
    public void close() throws SQLException {
      if (in.isActive()) {
        in.cancelCopy();
      }
    }
  }

  /**
   * @return a csv line, every value quoted and an unquoted empty cell for null, which COPY reads as NULL
   */
  static String csv(Object[] cells) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) sb.append(',');
      Object x = cells[i];
      if (x == null) continue;
      String s = x instanceof Collection<?> values ? array(values) : x instanceof Enum<?> e ? e.name() : x.toString();
      sb.append('"').append(s.replace("\"", "\"\"")).append('"');
    }
    return sb.append('\n').toString();
  }

  /**
   * @return a Postgres array literal with every element quoted: {"wd:Q1","ipni:1-1"}
   */
  static String array(Collection<?> values) {
    return values.stream()
      .map(v -> '"' + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"')
      .collect(Collectors.joining(",", "{", "}"));
  }
}
