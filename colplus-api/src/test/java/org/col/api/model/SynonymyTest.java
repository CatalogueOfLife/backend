package org.col.api.model;

import org.col.api.TestEntityGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class SynonymyTest {

  @Test
  public void isEmpty() throws Exception {
    Synonymy s = new Synonymy();
    assertTrue(s.isEmpty());

    s = TestEntityGenerator.newSynonymy();
    assertFalse(s.isEmpty());
  }

  @Test
  public void size() throws Exception {
    Synonymy s = new Synonymy();
    s.addHomotypicGroup(TestEntityGenerator.newNames(4));
    s.addHomotypicGroup(TestEntityGenerator.newNames(1));
    s.addHomotypicGroup(TestEntityGenerator.newNames(8));
    assertEquals(13, s.size());
  }

}