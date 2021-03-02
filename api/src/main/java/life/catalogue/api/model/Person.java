package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.common.util.RegexUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

public class Person {
  private static final Pattern CAMELCASE = Pattern.compile("\\b(\\p{Lu})(\\p{Ll}+)\\b");

  private static final String GIVEN_NAME = "((?:\\p{Lu}\\p{Ll}+){1,3})";
  private static final String PARTICLES = "(?:al|d[aeiou]?|de[nrmls]?|e|l[ae]s?|[oO]|ter|'?t|v|v[ao]n|zu[rm]?|y)";
  private static final String FAMILY_NAME = "((?:" + PARTICLES + "(?:\\s+|\\s*['´`’]\\s*)){0,2}\\p{Lu}\\p{Ll}\\p{L}*(?:[ -]+\\p{Lu}\\p{Ll}+)?)";
  private static final String INITIALS = "(\\p{Lu}{1,2}(?:[. ]+\\p{Lu}){0,2}\\.?)";
  private static final Pattern FULLNAME = Pattern.compile("^\\s*" + GIVEN_NAME + "\\s+" + FAMILY_NAME + "\\s*$");
  private static final Pattern FULLNAME_REVERSE = Pattern.compile("^\\s*" + FAMILY_NAME + "\\s*,\\s*" + GIVEN_NAME + "\\s*$");
  private static final Pattern SHORTNAME = Pattern.compile("^\\s*" + INITIALS + "\\s+" + FAMILY_NAME + "\\s*$");
  private static final Pattern SHORTNAME_REVERSE = Pattern.compile("^\\s*" + FAMILY_NAME+"(?:\\s+|\\s*,\\s*)" + INITIALS +"\\s*$");
  private static final Pattern BRACKET_SUFFIX = Pattern.compile("^(.+)(\\(.+\\)\\.?)\\s*$");
  private static final Pattern EMAIL = Pattern.compile("<?\\s*\\b([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})\\s*>?", Pattern.CASE_INSENSITIVE);
  private String givenName;
  private String familyName;
  private String email;
  private String orcid;

  public static Person parse(final String originalName) {
    if (originalName == null) return null;
    Person p = new Person();
    parse(p, originalName);
    return p;
  }

  static void parse(Person p, final String originalName) {
    if (originalName != null && p != null) {
      // see if we have brackets at the end, often for roles
      String brackets = null;
      String name = originalName;
      Matcher m = BRACKET_SUFFIX.matcher(name);
      if (m.find()) {
        name = m.group(1);
        brackets = m.group(2);
      }

      // email?
      m = EMAIL.matcher(name);
      if (m.find()) {
        name = m.replaceFirst("");
        p.setEmail(m.group(1));
      }

      // try with 4 distinct & common patterns
      m = FULLNAME.matcher(name);
      if (m.find()) {
        p.setGivenName(m.group(1));
        p.setFamilyName(m.group(2));
      } else {

        m = FULLNAME_REVERSE.matcher(name);
        if (m.find()) {
          p.setFamilyName(m.group(1));
          p.setGivenName(m.group(2));
        } else {

          m = SHORTNAME.matcher(name);
          if (m.find()) {
            p.setGivenName(m.group(1));
            p.setFamilyName(m.group(2));
          } else {

            m = SHORTNAME_REVERSE.matcher(name);
            if (m.find()) {
              RegexUtils.log(m);
              p.setFamilyName(m.group(1));
              p.setGivenName(m.group(2));
            } else {
              // no luck
              p.setFamilyName(name);
            }
          }
        }
      }
      if (brackets != null) {
        if (p.getGivenName() == null) {
          p.setGivenName(brackets);
        } else {
          p.setGivenName(p.getGivenName() + " " + brackets);
        }
      }
    }
  }

  public static List<Person> parse(List<String> names) {
    return names == null ? null : names.stream().map(Person::parse).collect(Collectors.toList());
  }

  public Person() {
  }

  public Person(Person other) {
    this.givenName = other.givenName;
    this.familyName = other.familyName;
    this.email = other.email;
    this.orcid = other.orcid;
  }

  public Person(String unparsedName) {
    parse(this, unparsedName);
  }

  public Person(String givenName, String familyName) {
    this.givenName = givenName;
    this.familyName = familyName;
  }

  public Person(String givenName, String familyName, String email, String orcid) {
    this.givenName = givenName;
    this.familyName = familyName;
    this.email = email;
    this.orcid = orcid;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = StringUtils.trimToNull(givenName);
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = StringUtils.trimToNull(familyName);
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public String getName(){
    if (givenName == null && familyName == null) return null;

    StringBuilder sb = new StringBuilder();
    if (familyName != null) {
      sb.append(familyName);
    }
    if (givenName != null) {
      if (familyName != null) {
        sb.append(" ");
        sb.append(abbreviate(givenName));
      } else {
        sb.append(givenName);
      }
    }
    return sb.toString();
  }

  static String abbreviate(String givenName) {
    if (givenName != null) {
      Matcher m = BRACKET_SUFFIX.matcher(givenName);
      if (m.find()) {
        givenName = m.group(1).trim();
      }
      m = CAMELCASE.matcher(givenName);
      if (m.find()) {
        givenName = m.replaceAll("$1.");
      }
    }
    return givenName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getOrcid() {
    return orcid;
  }

  public void setOrcid(String orcid) {
    this.orcid = orcid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Person)) return false;
    Person person = (Person) o;
    return Objects.equals(givenName, person.givenName) &&
      Objects.equals(familyName, person.familyName) &&
      Objects.equals(email, person.email) &&
      Objects.equals(orcid, person.orcid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(givenName, familyName, email, orcid);
  }

  @Override
  public String toString() {
    return getName();
  }
}
