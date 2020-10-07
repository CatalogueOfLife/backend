package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonTest {

  @Test
  public void getName() {
    Person p = new Person("Markus", "Döring");
    assertEquals("Döring M.", p.getName());

    p.setGivenName(null);
    assertEquals("Döring", p.getName());

    p.setFamilyName(null);
    assertNull(p.getName());

    p = new Person("Markus", null);
    assertEquals("Markus", p.getName());

    // from APA guide
    // https://blog.apastyle.org/apastyle/2017/05/whats-in-a-name-two-part-surnames-in-apa-style.html
    p = new Person("Diego J.", "Rivera-Gutierrez");
    assertEquals("Rivera-Gutierrez D. J.", p.getName());

    p = new Person("Rena", "Torres Cacoullos");
    assertEquals("Torres Cacoullos R.", p.getName());

    p = new Person("Ulrica", "von Thiele Schwarz");
    assertEquals("von Thiele Schwarz U.", p.getName());

    p = new Person("Simone", "de Beauvoir");
    assertEquals("de Beauvoir S.", p.getName());

  }

  @Test
  public void abbreviate() {
    assertNull(Person.abbreviate(null));
    assertEquals("M.", Person.abbreviate("Markus"));
    assertEquals("M. F.", Person.abbreviate("Markus Franz"));
    assertEquals("M.-F.", Person.abbreviate("Markus-Franz"));
    assertEquals("M. de la O.", Person.abbreviate("Markus de la Orca"));
    assertEquals("M., K.", Person.abbreviate("Markus, Karl"));
    assertEquals("M.", Person.abbreviate("M."));
    assertEquals("DC", Person.abbreviate("DC"));
  }
}