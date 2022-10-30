package com.jebsen.gateway.controller;

import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.jebsen.entity.UserEnitity;
import com.jebsen.gateway.entity.AuthRequest;
import com.jebsen.gateway.entity.AuthResponse;
import com.jebsen.gateway.handler.AuthHandler;
import com.jebsen.repository.UserRepository;

@RestController
public class AuthController {

	public static final String TYPE_TOKEN 						= "TOKEN";
	public static final String TYPE_REFRESH_TOKEN 				= "TYPE_REFRESH_TOKEN";
	
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AuthHandler authHandler;
	
	@Value("${auth.default.user:jebsenUser}")
	private String defaultUser;
	@Value("${auth.default.password:!P@ssword}")
	private String defaultPassword;
	
	@PostConstruct
    public void init() {
		UserEnitity admin = new UserEnitity();
		admin.setUserId(this.defaultUser);
		admin.setPassword(this.defaultPassword);
		if(!this.userRepository.findById(admin.getUserId()).isPresent()) {
			this.userRepository.saveAndFlush(admin);
		}
	}
	
	@RequestMapping(value="/getAccessToken", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public AuthResponse getAccessToken(@RequestBody AuthRequest request) throws ResponseStatusException {
		UserEnitity user = this.userRepository.findbyUserPassword(request.getClient_id(), request.getClient_secret());
		if(user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		else {
			String lastToken = user.getLastToken();
			String refreshToken = user.getRefreshToken();
			boolean needtoUpdate = false;
			if(lastToken == null || lastToken.isBlank() || !this.authHandler.isValidToken(lastToken) ) {
				Map<String, Object> claims = new HashMap<String, Object>();
				claims.put("client_id", request.getClient_id());
				claims.put("type", TYPE_TOKEN);
				claims.put("application", user.getUserApp());
				lastToken = this.authHandler.generateToken(claims, request.getClient_id());
				long now = (new Date()).getTime();
				user.setTokenUpdateDatetime(new Timestamp(now));
				user.setTokenExpire(now+this.authHandler.getTokenExpiredTime());
				user.setLastToken(lastToken);
				needtoUpdate = true;
			}
			if(refreshToken == null || refreshToken.isBlank() || !this.authHandler.isValidToken(refreshToken) ) {
				Map<String, Object> claims = new HashMap<String, Object>();
				claims.put("client_id", request.getClient_id());
				claims.put("type", TYPE_REFRESH_TOKEN);
				claims.put("application", user.getUserApp());
				refreshToken = this.authHandler.generateRefreshToken(claims, request.getClient_id());
				user.setRefreshToken(refreshToken);
				needtoUpdate = true;
			}
			
			if(needtoUpdate) {
				this.userRepository.saveAndFlush(user);
			}
			
			return AuthResponse.builder().result_code("0").result_message("success")
					.access_token(lastToken).refresh_token(refreshToken).expires_in(this.authHandler.getTokenExpiredTime())
					.token_create_date(user.getTokenUpdateDatetime())
					.token_type(AuthHandler.AUTH_TYPE)
					.build();
		}
	}
		
	@RequestMapping(value="/refreshToken", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public AuthResponse refreshToken(@RequestBody AuthRequest authRequest) {
		String refreshToken = authRequest.getRefresh_token();
		if(refreshToken == null || refreshToken.isBlank() || this.userRepository.findbyRefreshToken(refreshToken) == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		
		try {
			Map<String, Object> refreshTokenClaims = this.authHandler.parseJwtRsa256(refreshToken);
			String userId = (String) refreshTokenClaims.get("client_id");
			if(!refreshTokenClaims.get("type").equals(TYPE_REFRESH_TOKEN)) throw new Exception();
			
			UserEnitity user = this.userRepository.findById(userId).get();
			user = this.userRepository.findbyUserPassword(user.getUserId(), user.getPassword());
			long now = (new Date()).getTime();
			Map<String, Object> tokenClaims = new HashMap<String, Object>();
			tokenClaims.put("client_id", authRequest.getClient_id());
			tokenClaims.put("type", TYPE_TOKEN);
			tokenClaims.put("application", user.getUserApp());
			String lastToken = this.authHandler.generateToken(tokenClaims, authRequest.getClient_id());
			user.setTokenUpdateDatetime(new Timestamp(now));
			user.setTokenExpire(now+this.authHandler.getTokenExpiredTime());
			user.setLastToken(lastToken);
			this.userRepository.saveAndFlush(user);
			
			return AuthResponse.builder().result_code("0").result_message("success")
					.access_token(lastToken).refresh_token(refreshToken).expires_in(this.authHandler.getTokenExpiredTime())
					.token_create_date(user.getTokenUpdateDatetime())
					.token_type(AuthHandler.AUTH_TYPE)
					.build();
		}catch(Exception e) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
	}
	
}
