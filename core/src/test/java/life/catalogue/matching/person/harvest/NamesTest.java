package life.catalogue.matching.person.harvest;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NamesTest {

  /** Wikidata's given and family names are unordered statements, the label knows their order */
  @Test
  public void ordered() {
    assertEquals("George Brettingham", Names.ordered(List.of("Brettingham", "George"), "George Brettingham Sowerby II"));
    assertEquals("Ruiz López", Names.ordered(List.of("López", "Ruiz"), "Hipólito Ruiz López"));
    // without a label the order of the statements stands
    assertEquals("Maria Anna", Names.ordered(List.of("Maria", "Anna"), null));
    assertNull(Names.ordered(List.of(), "Carl Linnaeus"));
  }

  @Test
  public void suffix() {
    assertEquals("II", Names.suffix("George Brettingham Sowerby II"));
    assertEquals("I", Names.suffix("George Brettingham Sowerby I"));
    assertEquals("Jr.", Names.suffix("John Smith Jr"));
    assertEquals("Jr.", Names.suffix("John Smith, Jr."));
    assertNull(Names.suffix("Carl Linnaeus"));
    assertNull(Names.suffix("Ai"));
    assertNull(Names.suffix(null));
  }

  @Test
  public void surnameFirst() {
    assertEquals("James DeCarle Sowerby", Names.surnameFirst("Sowerby, James DeCarle"));
    assertEquals("Nees", Names.surnameFirst("Nees"));
    assertNull(Names.surnameFirst(" "));
  }

  /** several family names of one person are alternatives - maiden and married, latinised - unless the label holds them */
  @Test
  public void family() {
    assertEquals("Ruiz López", Names.family(List.of("López", "Ruiz"), "Hipólito Ruiz López"));
    assertEquals("Linnaeus", Names.family(List.of("Linné", "von Linné", "Linnaeus"), "Carl Linnaeus"));
    assertEquals("Linné", Names.family(List.of("Linné", "von Linné"), "Carl Linnaeus"));
    assertEquals("Married", Names.family(List.of("Maiden", "Married"), "Anna Married"));
    assertEquals("Smith", Names.family(List.of("Smith"), null));
    assertNull(Names.family(List.of(), "Carl Linnaeus"));
  }
}
