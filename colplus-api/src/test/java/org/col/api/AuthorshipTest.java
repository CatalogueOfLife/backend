package org.col.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class AuthorshipTest {

  @Test
  public void testToString() throws Exception {
    Authorship auth = new Authorship();
    assertNull(auth.toString());

    auth.getCombinationAuthors().add("L.");
    assertEquals("L.", auth.toString());

    auth.getOriginalAuthors().add("Bassier");
    assertEquals("(Bassier) L.", auth.toString());
    assertEquals("(Bassier) L.", auth.toString());

    auth.getCombinationAuthors().add("Rohe");
    assertEquals("(Bassier) L. & Rohe", auth.toString());
    assertEquals("(Bassier) L. & Rohe", auth.toString());
  }

}