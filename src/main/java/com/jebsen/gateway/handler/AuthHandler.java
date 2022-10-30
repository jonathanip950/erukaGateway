package com.jebsen.gateway.handler;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Clock;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.impl.DefaultClock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Component
public class AuthHandler {

	public final static String AUTH_TYPE = "Bearer";
	public final static Clock CLOCK = DefaultClock.INSTANCE;
	
	private InputStream inputStream = null;
	private PrivateKey privateKey = null;
	private PublicKey publicKey = null;
	
	@Value("${auth.token.key.path:classpath:jebsen-auth.keystore}")
	private String keyPath;
	@Value("${auth.token.key.alias:jebsen-auth}")
    private String keyAlias;
	@Value("${auth.token.key.password:jebsen-auth}")
    private String keyPassword;
	
	/**
	 * default: 1 hour
	 */
    @Value("${auth.token.expired:3600000}")
    private int tokenExpiredTime;
	
    /**
     * default: 24 hours
     */
    @Value("${auth.refreshToken.expired:86400000}")
    private int refreshTokenExpiredTime;
    
    @PostConstruct
    public void init() {
    	try {
			KeyStore keyStore = KeyStore.getInstance("JKS");
			ClassPathResource classPathResource = new ClassPathResource(this.keyPath);
			this.inputStream = classPathResource.getInputStream();
//			this.inputStream = new FileInputStream(ResourceUtils.getFile(this.keyPath));
			keyStore.load(this.inputStream, this.keyPassword.toCharArray());
			this.privateKey = (PrivateKey) keyStore.getKey(this.keyAlias, this.keyPassword.toCharArray());
			this.publicKey = keyStore.getCertificate(this.keyAlias).getPublicKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }
    
    public String generateToken(Map<String, Object> claims, String subject) {
		return generateToken( claims,  subject, this.tokenExpiredTime);
	}
	
	public String generateRefreshToken(Map<String, Object> claims, String subject) {
		return generateToken( claims,  subject, this.refreshTokenExpiredTime);
	}
	
	private String generateToken(Map<String, Object> claims, String subject, int expiration) {
		SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.RS256;
		final Date createdDate = CLOCK.now();
		final Date expirationDate = calculateExpirationDate(createdDate, expiration);
		return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(createdDate)
				.setExpiration(expirationDate).signWith(signatureAlgorithm, privateKey).compact();
	}
	
	private Date calculateExpirationDate(Date createdDate, int expiration) {
		return new Date(createdDate.getTime() + expiration);
	}

	public Map<String, Object> parseJwtRsa256(String jwt) throws ExpiredJwtException, UnsupportedJwtException, MalformedJwtException, SignatureException, IllegalArgumentException {
		Map<String, Object> claims = Jwts.parser().setSigningKey(publicKey).parseClaimsJws(jwt).getBody();
		return claims;
	}
	
	public boolean isValidToken(String token) {
		return this.isValidToken(null, token);
	}
	
	public boolean isValidToken(String application, String token) {
		try {
			Map<String, Object> claims =  this.parseJwtRsa256(token);
			String jwtApplication = (String) claims.get("application");
			log.info("application extracted from JWT:{}; current micro-service application:{};", jwtApplication, application);
			if(application == null || application.isBlank() || jwtApplication == null || jwtApplication.isBlank() || jwtApplication.contentEquals("*")) {
				return true;
			}
			else {
				String[] jwtApps = jwtApplication.split(";");
				for(String apps: jwtApps) {
					if(!apps.isBlank() && application.equalsIgnoreCase(apps)) {
						return true;
					}
				}
				return false;
			}
		}catch(Exception e) {
			return false;
		}
	}
}
