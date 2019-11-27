package life.catalogue.dw.auth;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.jackson.Discoverable;
import life.catalogue.dw.auth.gbif.GBIFAuthenticationFactory;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value= GBIFAuthenticationFactory.class),
    @JsonSubTypes.Type(value= MapAuthenticationFactory.class)
})
public interface AuthenticationProviderFactory extends Discoverable {
  
  AuthenticationProvider createAuthenticationProvider();

}
