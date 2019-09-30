package org.col.dw.auth;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.dropwizard.jackson.Discoverable;
import org.col.dw.auth.gbif.GBIFAuthenticationFactory;
import org.col.dw.auth.map.MapAuthenticationFactory;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value= GBIFAuthenticationFactory.class),
    @JsonSubTypes.Type(value= MapAuthenticationFactory.class)
})
public interface AuthenticationProviderFactory extends Discoverable {
  
  AuthenticationProvider createAuthenticationProvider();

}
