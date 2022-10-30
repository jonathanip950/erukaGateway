package com.jebsen.gateway;

import com.jebsen.broker.DbPropertiesPostProcessor;

public class GatewayDbPropertiesPostProcessor extends DbPropertiesPostProcessor {

	public static final String CONFIG_SOURCE = "jebsen-gateway";

	@Override
	public String getConfigSource() {
		return CONFIG_SOURCE;
	}

}
