# Dataset templates
Projects offer various template options to build a title or citation for a release.
The templates can include lower case variables in curly brackets, e.g `{editors} ({date})`.
In general all dataset properties are allowed as variables:

 - key
 - sourceKey
 - alias
 - title
 - description
 - organisations
 - contact
 - editors
 - authors
 - editorsOrAuthors
 - license
 - version
 - released
 - citation
 - geographicScope
 - website
 - logo
 - group
 - confidence
 - completeness
 - type
 - origin
 - notes
 - created
 - modified
 - privat
 - importAttempt
 - gbifKey
 - gbifPublisherKey
 - imported

In addition the current `{date}` is supported.
All dates are formatted as ISO dates by default, but you can also give a custom date format to the template
that follows after the variable and a comma inside the brackets, e.g. `{created,YYYY}` will render the date the dataset record was created 
in ChecklistBank as a 4 digit year.
 [All patterns from the java DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns) are supported.