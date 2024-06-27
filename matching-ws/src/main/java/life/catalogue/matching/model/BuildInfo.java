package life.catalogue.matching.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Information about the build of the software
 */
@Data
@Builder
@Schema(description = "", title = "BuildInfo", type = "object")
@AllArgsConstructor
@NoArgsConstructor
public class BuildInfo {
  @Schema(description = "The git commit the code was built from")
  String sha;
  @Schema(description = "URL to JSON information about the git commit")
  String url;
  @Schema(description = "URL to the git commit")
  String html_url;
  @Schema(description = "The commit message")
  String message;
  @Schema(description = "The author of the commit")
  String name;
  @Schema(description = "The author email of the commit")
  String email;
  @Schema(description = "The date of the commit")
  String date;
}

