package life.catalogue.api.constraints;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Makes sure properties dependent on origin are existing
 */
public class DatasetOriginValidator implements ConstraintValidator<ValidDataset, Dataset> {
  
  @Override
  public boolean isValid(Dataset d, ConstraintValidatorContext context) {
    if (d == null) {
      return true;
    }

    if (d.getOrigin() == null) {
      // origin required
      return false;
    }
    if (d.getOrigin() != DatasetOrigin.MANAGED) {
      // require data format for all but managed datasets
      if (d.getDataFormat() == null) return false;
    }
    if (d.getOrigin() == DatasetOrigin.EXTERNAL) {
      // also require data access for external datasets
      return d.getDataAccess() != null;
    }
    return true;
  }
}
