package life.catalogue.feedback;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.User;
import life.catalogue.common.Managed;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * The Feedback component
 */
public interface FeedbackService extends Managed {

  URI create(Optional<User> user, DSID<String> usageKey, String message) throws NotFoundException, IOException;

}
