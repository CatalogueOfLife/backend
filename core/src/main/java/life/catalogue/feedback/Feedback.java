package life.catalogue.feedback;

import java.util.Objects;

public class Feedback {
  public String message;
  public String email;

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Feedback feedback = (Feedback) o;
    return Objects.equals(message, feedback.message) && Objects.equals(email, feedback.email);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, email);
  }
}
