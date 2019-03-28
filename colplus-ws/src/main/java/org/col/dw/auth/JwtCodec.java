package org.col.dw.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.jsonwebtoken.*;
import org.col.api.model.ColUser;
import org.col.common.date.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtCodec {
  private static final Logger LOG = LoggerFactory.getLogger(JwtCodec.class);
  private static final int EXPIRE_IN_DAYS = 7;
  private static final String ISSUER = "col.plus";
  private final byte[] signingKey;
  
  public JwtCodec(String signingKey) {
    this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
  }
  
  public String generate(ColUser user) throws JwtException {
    LocalDateTime now = LocalDateTime.now();
    LOG.info("Generating new token for {} {}", user.getUsername(), user.getKey());
    JwtBuilder builder = Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setIssuer(ISSUER)
        .setIssuedAt(DateUtils.toDate(now))
        .setSubject(user.getKey().toString())
        .setExpiration(DateUtils.toDate(now.plus(EXPIRE_IN_DAYS, ChronoUnit.DAYS)))
        .signWith(SignatureAlgorithm.HS256, signingKey);
    return builder.compact();
  }
  
  public Jws<Claims> parse(String token) throws JwtException {
    return Jwts.parser()
        .setSigningKey(signingKey)
        .parseClaimsJws(token);
  }
}
