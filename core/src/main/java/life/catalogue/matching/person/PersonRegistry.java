package life.catalogue.matching.person;

import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.Nullable;

/**
 * The persons of the registry with every name form they are cited by, looked up the way citations are compared:
 * under their {@link AuthorshipNormalizer#normalize(String)} key. Next to the forms of the files, forms are derived
 * per person with a family name: the initials of the given names with family name and suffix ("G. B. Sowerby II"),
 * the same of every full name or variant that ends with the family name, as a source often lists fewer given names
 * than its label holds, the family name with its suffix ("Hooker f.") and the bare family name ("Sowerby"). Nobiliary
 * particles stay words ("A. P. de Candolle"), bracketed alternatives of a forename give no initials. A key may name
 * several persons; that is intended, a bare surname proposes candidates only.
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
      if (p.family() != null) {
        add(p.family(), p, FormCode.ANY);
        add(initials(p.given()) + p.family() + suffix(p), p, FormCode.ANY);
        if (p.suffix() != null) {
          // relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II"
          add(p.family() + suffix(p), p, FormCode.ANY);
        }
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
      if ((n.kind() == NameKind.FULL || n.kind() == NameKind.VARIANT) && p.family() != null) {
        String given = givenOf(n.form(), p);
        if (given != null) {
          add(initials(given) + p.family() + suffix(p), p, FormCode.ANY);
        }
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

  private static String suffix(Person p) {
    return p.suffix() == null ? "" : " " + p.suffix();
  }

  /**
   * @return the words of a full name before the family name, "George Brettingham" of "George Brettingham Sowerby II",
   *         null for a name that does not end with the person's family name and suffix
   */
  @Nullable
  private static String givenOf(String full, Person p) {
    String name = full.strip();
    String suffix = suffix(p);
    if (!suffix.isEmpty() && name.endsWith(suffix)) {
      name = name.substring(0, name.length() - suffix.length());
    }
    String family = " " + p.family();
    if (!name.endsWith(family)) return null;
    String given = name.substring(0, name.length() - family.length()).strip();
    return given.isEmpty() ? null : given;
  }

  /**
   * @return "G. B. " for "George Brettingham", "J. B. " for "Jean-Baptiste", "A. P. de " for "Augustin Pyramus de",
   *         empty for none
   */
  static String initials(@Nullable String given) {
    if (given == null) return "";
    StringBuilder sb = new StringBuilder();
    // IPNI lists alternative forenames in brackets: "Carl (Karl, Carel, Carolus) Bořivoj"
    for (String part : given.replaceAll("\\([^)]*\\)", " ").split("[\\s-]+")) {
      if (AuthorshipNormalizer.PARTICLES.contains(part)) {
        sb.append(part).append(' ');
        continue;
      }
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
