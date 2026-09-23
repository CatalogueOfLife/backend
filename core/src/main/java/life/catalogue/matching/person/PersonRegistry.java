package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.Nullable;

/**
 * The persons of the registry with every name form they are cited by, looked up the way citations are compared:
 * under their {@link AuthorshipNormalizer#normalize(String)} key. Next to the forms of the files, two forms are
 * derived per person with a family name: the initials of the given names with family name and suffix
 * ("G. B. Sowerby II") and the bare family name ("Sowerby"). A key may name several persons; that is intended, a
 * bare surname proposes candidates only.
 * <p>
 * Loaded once and only by what asks for it, so the string comparison pays nothing.
 */
public class PersonRegistry {
  private static PersonRegistry instance;

  private record Form(Person person, FormCode code) {
  }

  private final int size;
  private final Map<String, Person> byId = new HashMap<>();
  private final Map<String, List<Form>> byKey = new HashMap<>();
  private final Map<String, Set<Person>> relatives = new HashMap<>();
  private final List<String> problems = new ArrayList<>();

  public static synchronized PersonRegistry get() {
    if (instance == null) {
      try {
        instance = new PersonRegistry(PersonFiles.readResources());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return instance;
  }

  public PersonRegistry(PersonFiles.Content c) {
    size = c.persons().size();
    for (Person p : c.persons()) {
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
      if (p.family() != null) {
        add(p.family(), p, FormCode.ANY);
        add(initials(p.given()) + p.family() + (p.suffix() == null ? "" : " " + p.suffix()), p, FormCode.ANY);
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
      add(n.form(), p, n.code());
    }
    for (Person p : c.persons()) {
      if (!named.contains(p)) {
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
    }
  }

  private void add(String form, Person p, FormCode code) {
    String key = AuthorshipNormalizer.normalize(form);
    if (key != null) {
      List<Form> forms = byKey.computeIfAbsent(key, k -> new ArrayList<>(1));
      Form f = new Form(p, code);
      if (!forms.contains(f)) {
        forms.add(f);
      }
    }
  }

  /**
   * @return "G. B. " for "George Brettingham", "J. B. " for "Jean-Baptiste", empty for none
   */
  static String initials(@Nullable String given) {
    if (given == null) return "";
    StringBuilder sb = new StringBuilder();
    for (String part : given.split("[\\s-]+")) {
      String letters = part.replaceAll("^[^\\p{L}]+", "");
      if (!letters.isEmpty()) {
        sb.append(letters.charAt(0)).append(". ");
      }
    }
    return sb.toString();
  }

  /**
   * @return the persons a citation may name under the code of the name: several for an ambiguous citation such as a
   *         bare surname, none for an author the registry does not know
   */
  public Set<Person> candidates(String citation, @Nullable NomCode code) {
    String key = AuthorshipNormalizer.normalize(citation);
    if (key == null) return Set.of();
    Set<Person> persons = new LinkedHashSet<>();
    for (Form f : byKey.getOrDefault(key, List.of())) {
      if (f.code().appliesTo(code)) {
        persons.add(f.person());
      }
    }
    return persons;
  }

  /**
   * @param anyId an id, a former id or a prefixed authority id
   */
  @Nullable
  public Person get(String anyId) {
    return byId.get(anyId);
  }

  /**
   * @return parents, children and siblings
   */
  public Set<Person> relatives(Person p) {
    return relatives.getOrDefault(p.id(), Set.of());
  }

  /**
   * @return what is wrong with the files, empty for a consistent registry
   */
  public List<String> problems() {
    return Collections.unmodifiableList(problems);
  }

  public int size() {
    return size;
  }
}
