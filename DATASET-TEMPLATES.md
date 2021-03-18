# Dataset templates
Projects offer various [template options](https://data.catalogue.life/catalogue/3/options) to build a title or citation for a release.
The templates can include lower case variables in curly brackets, e.g `{editors} ({date})`.

## Variables
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
 - importAttempt  # incremental int for imports & releases
 - gbifKey
 - gbifPublisherKey
 - imported

In addition the current `{date}` is supported.
All dates are formatted as ISO dates by default, but you can also give a custom date format to the template
that follows after the variable and a comma inside the brackets, e.g. `{created,YYYY}` will render the date the dataset record was created 
in ChecklistBank as a 4 digit year.
 [All patterns from the java DateTimeFormatter](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns) are supported.
 
## Examples of classic COL templates

 - Release Title Template: `Catalogue of Life - {date,MMMM yyyy}`
 - Release Alias Template: `COL{date,yy.M}`
 - Release Citation Template: `{editors} ({date,yyyy}). Species 2000 & ITIS Catalogue of Life, {date,ddd MMMM yyyy}. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.`
 - Release Source Citation Template: `{editorsOrAuthors} ({project.released,yyyy}). {alias}: {title} (version {version}). In: {project.citation}`