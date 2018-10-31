package org.col.dw.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import io.jsonwebtoken.*;
import org.col.api.model.ColUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtCoder {
  private static final Logger LOG = LoggerFactory.getLogger(JwtCoder.class);
  private final byte[] signingKey;
  
  public JwtCoder(AuthConfiguration cfg) {
    signingKey = cfg.signingKey.getBytes(StandardCharsets.UTF_8);
  }
  
  public String generate(ColUser user) throws JwtException {
    LocalDate expire = LocalDate.now().plus(1, ChronoUnit.WEEKS);
    LOG.info("Generating new token for {} {}", user.getUsername(), user.getKey());
    return Jwts.builder()
        .setIssuer("http://col.plus/")
        .setSubject(user.getKey().toString())
        .setExpiration(Date.from(expire.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        .signWith(SignatureAlgorithm.HS256, signingKey)
        .compact();
  }
  
  public Jws<Claims> parse(String token) throws JwtException {
    return Jwts.parser()
        .setSigningKey(signingKey)
        .parseClaimsJws(token);
  }
}
