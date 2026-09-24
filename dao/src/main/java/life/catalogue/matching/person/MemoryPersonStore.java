package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonFormCode;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.Nullable;

/**
 * The person registry in memory, built from its files: the store of the tests and the corpus tools, and the check of
 * every registry before it is written. Persons are looked up by the {@link PersonKeys#key(String)} of their forms and of
 * the forms {@link PersonForms} derives.
 */
public class MemoryPersonStore implements PersonStore {
  private static MemoryPersonStore resources;

  private record Form(Person person, PersonFormCode code) {
  }

  private record Keyed(String key, PersonFormCode code) {
  }

  private final int size;
  private final Map<String, Person> byId = new HashMap<>();
  private final Map<String, List<Form>> byKey = new HashMap<>();
  // identity: the persons the store hands out are its own instances, and records hash all their fields
  private final Map<Person, List<Keyed>> keysByPerson = new IdentityHashMap<>();
  private final Map<String, Set<Person>> relatives = new HashMap<>();
  private final Map<String, List<PersonName>> names = new HashMap<>();
  private final Map<String, List<PersonRelation>> relations = new HashMap<>();
  private final List<String> problems = new ArrayList<>();

  /**
   * @return the registry of the files on the classpath, loaded once: the corpus tools and their tests read it
   */
  public static synchronized MemoryPersonStore resources() {
    if (resources == null) {
      try {
        resources = new MemoryPersonStore(PersonFiles.readResources());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return resources;
  }

  public MemoryPersonStore(PersonFiles.Content c) {
    size = c.persons().size();
    for (Person p : c.persons()) {
      if (p.id() == null) {
        problems.add("a person without an id: " + p.family());
        continue;
      }
      for (String id : p.allIds()) {
        Person prev = byId.putIfAbsent(id, p);
        if (prev != null && prev != p) {
          problems.add("id " + id + " is held by " + prev.id() + " and " + p.id());
        }
      }
      if (p.id().startsWith(Person.LOCAL)) {
        if (p.wikidata() != null || p.ipni() != null || p.zoobank() != null) {
          problems.add(p.id() + " is local but has authority ids");
        }
      } else if (!p.id().equals(Person.idFor(p.wikidata(), p.ipni(), p.zoobank()))) {
        problems.add(p.id() + " should be " + Person.idFor(p.wikidata(), p.ipni(), p.zoobank()));
      }
      if (p.born() != null && p.died() != null && p.born() > p.died()) {
        problems.add(p.id() + " was born after it died");
      }
      if (p.born() != null && p.activeFrom() != null && p.activeFrom() < p.born()) {
        problems.add(p.id() + " was active before it was born");
      }
      for (String form : PersonForms.of(p)) {
        add(form, p, PersonFormCode.ANY);
      }
    }
    Set<Person> named = Collections.newSetFromMap(new IdentityHashMap<>());
    for (PersonName n : c.names()) {
      Person p = byId.get(n.person());
      if (p == null) {
        problems.add("name " + n.form() + " refers to unknown person " + n.person());
        continue;
      }
      named.add(p);
      names.computeIfAbsent(p.id(), k -> new ArrayList<>()).add(n);
      add(n.form(), p, n.code());
      String derived = PersonForms.of(p, n);
      if (derived != null) {
        add(derived, p, PersonFormCode.ANY);
      }
    }
    for (Person p : c.persons()) {
      if (p.id() != null && !named.contains(p)) {
        problems.add(p.id() + " has no name");
      }
    }
    for (PersonRelation r : c.relations()) {
      Person a = byId.get(r.person());
      Person b = byId.get(r.other());
      if (a == null || b == null) {
        problems.add("relation " + r.person() + " " + r.relation() + " " + r.other() + " refers to unknown person "
          + (a == null ? r.person() : r.other()));
        continue;
      }
      relatives.computeIfAbsent(a.id(), k -> new LinkedHashSet<>()).add(b);
      relatives.computeIfAbsent(b.id(), k -> new LinkedHashSet<>()).add(a);
      relations.computeIfAbsent(a.id(), k -> new ArrayList<>()).add(r);
      if (b != a) {
        relations.computeIfAbsent(b.id(), k -> new ArrayList<>()).add(r);
      }
    }
  }

  private void add(String form, Person p, PersonFormCode code) {
    String key = PersonKeys.key(form);
    if (key != null) {
      List<Form> forms = byKey.computeIfAbsent(key, k -> new ArrayList<>(1));
      Form f = new Form(p, code);
      if (!forms.contains(f)) {
        forms.add(f);
      }
      List<Keyed> keys = keysByPerson.computeIfAbsent(p, x -> new ArrayList<>(4));
      Keyed k = new Keyed(key, code);
      if (!keys.contains(k)) {
        keys.add(k);
      }
    }
  }

  @Override
  public Set<Person> byKey(String key, @Nullable NomCode code) {
    Set<Person> persons = new LinkedHashSet<>();
    for (Form f : byKey.getOrDefault(key, List.of())) {
      if (f.code().appliesTo(code)) {
        persons.add(f.person());
      }
    }
    return persons;
  }

  @Override
  public Map<String, Set<Person>> byKeys(Collection<String> keys, @Nullable NomCode code) {
    Map<String, Set<Person>> map = new HashMap<>();
    for (String key : keys) {
      Set<Person> persons = byKey(key, code);
      if (!persons.isEmpty()) {
        map.put(key, persons);
      }
    }
    return map;
  }

  @Override
  public Set<String> keys(Person p, @Nullable NomCode code) {
    Set<String> keys = new LinkedHashSet<>();
    for (Keyed k : keysByPerson.getOrDefault(p, List.of())) {
      if (k.code().appliesTo(code)) {
        keys.add(k.key());
      }
    }
    return keys;
  }

  @Override
  @Nullable
  public Person get(String anyId) {
    return byId.get(anyId);
  }

  @Override
  public Set<Person> relatives(Person p) {
    return relatives.getOrDefault(p.id(), Set.of());
  }

  @Override
  @Nullable
  public PersonInfo info(String anyId) {
    Person p = byId.get(anyId);
    if (p == null) return null;
    return new PersonInfo(p, List.copyOf(names.getOrDefault(p.id(), List.of())),
      List.copyOf(relations.getOrDefault(p.id(), List.of())));
  }

  /**
   * @return what is wrong with the registry, empty for a consistent one
   */
  public List<String> problems() {
    return Collections.unmodifiableList(problems);
  }

  public int size() {
    return size;
  }
}
