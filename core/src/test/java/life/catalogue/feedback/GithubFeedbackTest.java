package life.catalogue.feedback;

import life.catalogue.api.model.DSID;

import life.catalogue.api.model.Dataset;
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
    var encryption = new EmailEncryption("tVmJMgv8ZiCNrHDGCYdg9gt6K8MFNYB4", "ff0cdb935375091f");
    final var feedback = new GithubFeedback(new GithubConfig(), URI.create("https://www.checklistbank.org"), URI.create("https://api.checklistbank.org"), null, encryption, null);

    Dataset d = new Dataset();
    d.setAlias("COL22");

    var msg = feedback.buildMessage(Optional.empty(), DSID.of(321, "ABCD"), fb("I cannot find what I am looking for."), null, d);
    assertEquals("I cannot find what I am looking for.\n" +
      "\n" +
      "---\n" +
      "Source: COL22\n" +
      "Taxon: https://www.checklistbank.org/dataset/321/nameusage/ABCD", msg);

    var user = new User();
    user.setKey(100);
    user.setUsername("streber");
    user.setFirstname("Frank");
    user.setLastname("Streber");
    msg = feedback.buildMessage(Optional.of(user), DSID.of(321, "ABCD"), fb("I cannot find what I am looking for.", "peter@nope.com"), "Puma concolor L.", d);
    assertTrue(msg.startsWith("Puma concolor L.\n" +
      "\n" +
      "I cannot find what I am looking for.\n" +
      "\n" +
      "---\n" +
      "Source: COL22\n" +
      "Taxon: https://www.checklistbank.org/dataset/321/nameusage/ABCD\n" +
      "Submitted by: 100\n" +
      "Email: https://api.checklistbank.org/admin/email?address="));
  }
}