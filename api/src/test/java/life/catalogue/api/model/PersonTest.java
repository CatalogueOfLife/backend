package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
  public void parse() {
    assertNull(Person.parse((String)null));
    assertEquals(new Person("Markus", "Döring", "markus@mailinator.com", null), Person.parse("Markus Döring <markus@mailinator.com>"));
    assertEquals(new Person(null, "Döring", "m.doering@mailinator.com", null), Person.parse("Döring<m.doering@mailinator.com>"));
    assertEquals(new Person("K. (eds).", "Fauchald"), Person.parse("Fauchald K. (eds)."));
    assertEquals(new Person("R.", "DeSalle"), Person.parse("DeSalle R."));
    assertEquals(new Person(null, "Markus"), Person.parse("Markus"));
    assertEquals(new Person("Markus", "Döring"), Person.parse("Markus Döring"));
    assertEquals(new Person("M.", "Döring"), Person.parse("Döring M."));
    assertEquals(new Person("Markus", "de la Orca"), Person.parse("Markus de la Orca"));
    assertEquals(new Person("A.S.", "Kroupa"), Person.parse("Kroupa A.S."));
    assertEquals(new Person("U.", "Neu-Becker"), Person.parse("Neu-Becker U."));
    assertEquals(new Person("F. (data managers)", "Zinetti"), Person.parse("Zinetti F. (data managers)"));
    assertEquals(new Person(null, "data managers are great people"), Person.parse("data managers are great people"));
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