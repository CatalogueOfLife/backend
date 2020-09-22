package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonTest {

  @Test
  public void getName() {
    Person p = new Person("Markus", "Döring");
    assertEquals("Markus Döring", p.getName());

    p.setGivenName(null);
    assertEquals("Döring", p.getName());

    p.setFamilyName(null);
    assertNull(p.getName());
  }
}