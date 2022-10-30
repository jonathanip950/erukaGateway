package com.jebsen.gateway.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class AuthRequest {
	
	private String client_id;
	private String client_secret;
//	private String secret;
	private String refresh_token;
	
}
