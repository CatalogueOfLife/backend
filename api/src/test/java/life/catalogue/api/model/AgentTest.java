package life.catalogue.api.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AgentTest {

  @Test
  public void getName() {
    Agent p = new Agent("Markus", "Döring");
    assertEquals("Döring M.", p.getName());

    p.setGivenName(null);
    assertEquals("Döring", p.getName());

    p.setFamilyName(null);
    assertNull(p.getName());

    p = new Agent("Markus", null);
    assertEquals("Markus", p.getName());

    // from APA guide
    // https://blog.apastyle.org/apastyle/2017/05/whats-in-a-name-two-part-surnames-in-apa-style.html
    p = new Agent("Diego J.", "Rivera-Gutierrez");
    assertEquals("Rivera-Gutierrez D. J.", p.getName());

    p = new Agent("Rena", "Torres Cacoullos");
    assertEquals("Torres Cacoullos R.", p.getName());

    p = new Agent("Ulrica", "von Thiele Schwarz");
    assertEquals("von Thiele Schwarz U.", p.getName());

    p = new Agent("Simone", "de Beauvoir");
    assertEquals("de Beauvoir S.", p.getName());

    p = new Agent("Balázs", "Harrach");
    assertEquals("Harrach B.", p.getName());

    p = new Agent("Karl-Heinz", "Rummenigge");
    assertEquals("Rummenigge K.-H.", p.getName());

    p = new Agent("Jean-Baptise", "Lamour");
    assertEquals("Lamour J.-B.", p.getName());

    p = new Agent("Jean Baptise", "Lamour");
    assertEquals("Lamour J. B.", p.getName());
  }

  @Test
  public void parse() {
    assertNull(Agent.parse((String)null));
    assertEquals(new Agent("T.", "O’Hara"), Agent.parse("O’Hara T."));
    assertEquals(new Agent("Markus", "Döring", "markus@mailinator.com", null), Agent.parse("Markus Döring <markus@mailinator.com>"));
    assertEquals(new Agent(null, "Döring", "m.doering@mailinator.com", null), Agent.parse("Döring<m.doering@mailinator.com>"));
    assertEquals(new Agent("K. (eds).", "Fauchald"), Agent.parse("Fauchald K. (eds)."));
    assertEquals(new Agent("R.", "DeSalle"), Agent.parse("DeSalle R."));
    assertEquals(new Agent(null, "Markus"), Agent.parse("Markus"));
    assertEquals(new Agent("Markus", "Döring"), Agent.parse("Markus Döring"));
    assertEquals(new Agent("M.", "Döring"), Agent.parse("Döring M."));
    assertEquals(new Agent("Markus", "de la Orca"), Agent.parse("Markus de la Orca"));
    assertEquals(new Agent("A.S.", "Kroupa"), Agent.parse("Kroupa A.S."));
    assertEquals(new Agent("U.", "Neu-Becker"), Agent.parse("Neu-Becker U."));
    assertEquals(new Agent("F. (data managers)", "Zinetti"), Agent.parse("Zinetti F. (data managers)"));
    assertEquals(new Agent(null, "data managers are great people"), Agent.parse("data managers are great people"));
  }

  @Test
  public void abbreviate() {
    assertNull(Agent.abbreviate(null));
    assertEquals("M.", Agent.abbreviate("Markus"));
    assertEquals("M. F.", Agent.abbreviate("Markus Franz"));
    assertEquals("M.-F.", Agent.abbreviate("Markus-Franz"));
    assertEquals("M. de la O.", Agent.abbreviate("Markus de la Orca"));
    assertEquals("M., K.", Agent.abbreviate("Markus, Karl"));
    assertEquals("M.", Agent.abbreviate("M."));
    assertEquals("DC", Agent.abbreviate("DC"));
    assertEquals("S.", Agent.abbreviate("Sergey (Zoological Institute RAS)"));
  }
}