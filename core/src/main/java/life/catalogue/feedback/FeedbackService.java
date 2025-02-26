package life.catalogue.feedback;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.User;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public interface FeedbackService {

  URI create(Optional<User> user, DSID<String> usageKey, String message) throws NotFoundException, IOException;

}
