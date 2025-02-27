package life.catalogue.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpamDetectorTest {
  SpamDetector sd = new SpamDetector();

  @Test
  void tree() {
    assertEquals(84, sd.spamTokens().size());
  }

  @Test
  void isSpam() {
    assertTrue(sd.isSpam("Great races at daytona 500 every day!"));

    assertFalse(sd.isSpam("Hallo Du"));
    assertFalse(sd.isSpam("Hallo Du Afdele!"));
    assertTrue(sd.isSpam("Elon says you must vote AFD."));

    assertTrue(sd.isSpam("Do you like basketball?"));
    assertTrue(sd.isSpam("***"));
    assertTrue(sd.isSpam("Great races at daytona 500 every day!"));
    assertFalse(sd.isSpam("Great races at daytona 4 every day!"));
    assertTrue(sd.isSpam("Please check my site https://bit.ly/drfgh-de2wc"));
  }
}