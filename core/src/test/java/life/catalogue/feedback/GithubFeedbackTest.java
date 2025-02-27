package life.catalogue.feedback;

import life.catalogue.api.model.DSID;

import life.catalogue.api.model.User;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GithubFeedbackTest {

  @Test
  void buildMessage() {
    var feedback = new GithubFeedback(new GithubConfig(), URI.create("https://www.checklistbank.org"), null, null);
    var msg = feedback.buildMessage(Optional.empty(), DSID.of(321, "ABCD"), "I cannot find what I am looking for.");
    assertEquals("I cannot find what I am looking for.\n" +
      "---\n" +
      "https://www.checklistbank.org/dataset/321/nameusage/ABCD", msg);

    var user = new User();
    user.setKey(100);
    user.setUsername("streber");
    user.setFirstname("Frank");
    user.setLastname("Streber");
    msg = feedback.buildMessage(Optional.of(user), DSID.of(321, "ABCD"), "I cannot find what I am looking for.");
    assertEquals("I cannot find what I am looking for.\n" +
      "---\n" +
      "https://www.checklistbank.org/dataset/321/nameusage/ABCD\n" +
      "Submitted by: 100", msg);
  }
}