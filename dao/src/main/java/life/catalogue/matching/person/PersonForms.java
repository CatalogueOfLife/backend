package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.common.tax.AuthorshipNormalizer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The forms a person is cited by that no source lists, derived from its structured name and its full names: the bare
 * family name ("Sowerby"), the initials of the given names with family name and suffix ("G. B. Sowerby II"), the family
 * name with its suffix ("Hooker f."), and the initials of every full name or variant that ends with the family name, as
 * a source often lists fewer given names than its label holds. Nobiliary particles stay words ("A. P. de Candolle"),
 * bracketed alternatives of a forename give no initials. Derived forms apply to every code.
 */
public final class PersonForms {
  private PersonForms() {
  }

  /**
   * @return the forms derived from the structured name, none without a family name
   */
  public static List<String> of(Person p) {
    if (p.family() == null) return List.of();
    List<String> forms = new ArrayList<>(3);
    forms.add(p.family());
    forms.add(initials(p.given()) + p.family() + suffix(p));
    if (p.suffix() != null) {
      // relatives are cited by the family name and suffix alone: "Hooker f.", "Sowerby II"
      forms.add(p.family() + suffix(p));
    }
    return forms;
  }

  /**
   * @return the initials of a full name or variant that ends with the family name and suffix, followed by them; null for
   *         any other form
   */
  @Nullable
  public static String of(Person p, PersonName n) {
    if (p.family() == null || (n.kind() != PersonNameKind.FULL && n.kind() != PersonNameKind.VARIANT)) return null;
    String given = givenOf(n.form(), p);
    return given == null ? null : initials(given) + p.family() + suffix(p);
  }

  static String suffix(Person p) {
    return p.suffix() == null ? "" : " " + p.suffix();
  }

  /**
   * @return the words of a full name before the family name, "George Brettingham" of "George Brettingham Sowerby II",
   *         null for a name that does not end with the person's family name and suffix
   */
  @Nullable
  static String givenOf(String full, Person p) {
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
   * @return "G. B. " for "George Brettingham", "J. B. " for "Jean-Baptiste", "J. C. " for "J.C.", "A. P. de " for
   *         "Augustin Pyramus de", empty for none
   */
  static String initials(@Nullable String given) {
    if (given == null) return "";
    StringBuilder sb = new StringBuilder();
    // IPNI lists alternative forenames in brackets: "Carl (Karl, Carel, Carolus) Bořivoj"; a variant may be dotted: "J.C."
    for (String part : given.replaceAll("\\([^)]*\\)", " ").split("[\\s.-]+")) {
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
}
