package life.catalogue.dw.auth;


import life.catalogue.dw.auth.gbif.GBIFAuthenticationFactory;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.dropwizard.jackson.Discoverable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value= GBIFAuthenticationFactory.class),
    @JsonSubTypes.Type(value= MapAuthenticationFactory.class)
})
public interface AuthenticationProviderFactory extends Discoverable {
  
  AuthenticationProvider createAuthenticationProvider();

}
