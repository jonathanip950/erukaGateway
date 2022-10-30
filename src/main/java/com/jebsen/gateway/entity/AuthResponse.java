package com.jebsen.gateway.entity;

import java.sql.Timestamp;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder(toBuilder = true)
public class AuthResponse {

	private String result_code;
	private String result_message;
//	private String client_id;
	private long expires_in;
	private String access_token;
	private String refresh_token;
	private String token_type;
	private Timestamp token_create_date;
}
