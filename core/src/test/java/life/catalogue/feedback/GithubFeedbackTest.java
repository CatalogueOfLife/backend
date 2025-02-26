package life.catalogue.feedback;

import life.catalogue.api.model.DSID;

import life.catalogue.api.model.User;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GithubFeedbackTest {

  public static Feedback fb(String msg) {
    return fb(msg, null);
  }
  static Feedback fb(String msg, String email) {
    var fb = new Feedback();
    fb.message = msg;
    fb.email = email;
    return fb;
  }
  @Test
  void buildMessage() {
    var feedback = new GithubFeedback(new GithubConfig(), URI.create("https://www.checklistbank.org"), null, null);
    var msg = feedback.buildMessage(Optional.empty(), DSID.of(321, "ABCD"), fb("I cannot find what I am looking for."), null);
    assertEquals("I cannot find what I am looking for.\n" +
      "\n" +
      "---\n" +
      "https://www.checklistbank.org/dataset/321/nameusage/ABCD", msg);

    var user = new User();
    user.setKey(100);
    user.setUsername("streber");
    user.setFirstname("Frank");
    user.setLastname("Streber");
    msg = feedback.buildMessage(Optional.of(user), DSID.of(321, "ABCD"), fb("I cannot find what I am looking for.", "peter@nope.com"), "Puma concolor L.");
    assertEquals("Puma concolor L.\n" +
      "\n" +
      "I cannot find what I am looking for.\n" +
      "\n" +
      "---\n" +
      "https://www.checklistbank.org/dataset/321/nameusage/ABCD\n" +
      "Submitted by: 100\n" +
      "Email: peter@nope.com", msg);
  }
}