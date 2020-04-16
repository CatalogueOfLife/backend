package life.catalogue.dw.auth;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import life.catalogue.api.model.User;
import life.catalogue.common.date.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtCodec {
  private static final Logger LOG = LoggerFactory.getLogger(JwtCodec.class);
  private static final int EXPIRE_IN_HOURS = 24;
  private static final String ISSUER = "CoL";
  private final SecretKey key;
  private final JwtParser parser;
  
  public JwtCodec(String signingKey) {
    key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
    parser = Jwts.parser()
        .requireIssuer(ISSUER)
        .setAllowedClockSkewSeconds(3 * 60) // allow for 3 minutes time skew
        .setSigningKey(key);
  }
  
  public String generate(User user) throws JwtException {
    LocalDateTime now = LocalDateTime.now();
    LOG.info("Generating new token for {} {}", user.getUsername(), user.getKey());
    JwtBuilder builder = Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setIssuer(ISSUER)
        .setIssuedAt(DateUtils.toDate(now))
        .setSubject(user.getUsername())
        .setExpiration(DateUtils.toDate(now.plus(EXPIRE_IN_HOURS, ChronoUnit.HOURS)))
        .signWith(key);
    return builder.compact();
  }
  
  public Jws<Claims> parse(String token) throws ExpiredJwtException, UnsupportedJwtException, MalformedJwtException, SignatureException, IllegalArgumentException {
    return parser.parseClaimsJws(token);
  }
  
}
