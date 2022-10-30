package com.jebsen.gateway.filter;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.jebsen.entity.HttpTrafficEntity;
import com.jebsen.gateway.handler.AuthHandler;
import com.jebsen.repository.HttpTrafficRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * a filter to check authentication
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

	@Value("${spring.application.httpTrafficLog.enabled: true}")
	private boolean isHttpTrafficLogEnabled;
	
	@Value("${spring.application.httpTrafficLog.database.enabled: true}")
	private boolean isDbHttpTrafficLogEnabled;
	
	@Value("${spring.application.httpTrafficLog.database.httpHeader.enabled: false}")
	private boolean isDbHttpHeaderTrafficLogEnabled;
	
	@Value("${spring.application.name: jebsen-gateway}")
	private String trafficSource;
	
	private final String AUTHENTICATION_HTTP_HEADER = "authorization";
	
	@Value("#{'${auth.token.whiteList:/getAccessToken;/refreshToken}'.split(';')}")
	private List<String> listOfStringsWithCustomDelimiter;

	@Autowired
	private AuthHandler authHandler;
	@Autowired
	private HttpTrafficRepository httpTrafficRepository;

	@Override
	public int getOrder() {
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (this.isWhiteList(exchange.getRequest().getURI() + "")) {
			return chain.filter(exchange);
		}
		/*
		log.info("getURI:{}", exchange.getRequest().getURI());
		log.info("getPath:{}", exchange.getRequest().getURI().getPath());
		log.info("getFragment:{}", exchange.getRequest().getURI().getFragment());
		log.info("getQuery:{}", exchange.getRequest().getURI().getQuery());
		log.info("getScheme:{}", exchange.getRequest().getURI().getScheme());
		log.info("getSchemeSpecificPart:{}", exchange.getRequest().getURI().getSchemeSpecificPart());
		log.info("getUserInfo:{}", exchange.getRequest().getURI().getUserInfo());
		log.info("getRawAuthority:{}", exchange.getRequest().getURI().getRawAuthority());
		log.info("getRawFragment:{}", exchange.getRequest().getURI().getRawFragment());
		log.info("getRawPath:{}", exchange.getRequest().getURI().getRawPath());
		log.info("getRawQuery:{}", exchange.getRequest().getURI().getRawQuery());
		log.info("getRawSchemeSpecificPart:{}", exchange.getRequest().getURI().getRawSchemeSpecificPart());
		log.info("getRawUserInfo:{}", exchange.getRequest().getURI().getRawUserInfo());
		*/
		String routingServiceName = "";
		try {
			String path = exchange.getRequest().getURI().getPath();
			log.info("routing path:{}", path);
			routingServiceName = path.substring(1, path.indexOf("/", 1)>1?path.indexOf("/", 1):path.length());
			log.info("routing to application:{}", routingServiceName);
		}catch(Exception e) {
			log.error("retrieve routing application error", e);
		}
		final String application = routingServiceName;
		String method = exchange.getRequest().getMethodValue();
		if (method.equalsIgnoreCase("POST")) {
			return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				boolean isValid = false;
				String tokenFromHeader = null;
				try {
					tokenFromHeader = exchange.getRequest().getHeaders().get(AUTHENTICATION_HTTP_HEADER).get(0)
							.substring(AuthHandler.AUTH_TYPE.length()).trim();
					;
					isValid = this.authHandler.isValidToken(application, tokenFromHeader);
				} catch (Exception e) {
					isValid = false;
				}
				String bodyString = "";
				try {
					bodyString = new String(bytes, "utf-8");
					if (!isValid) {
						// log 401 http traffic here.
						addUnanthorizedTraffic(exchange.getRequest(), bodyString);
					}
					exchange.getAttributes().put("POST_BODY", bodyString);
				} catch (Exception e) {
				}
				DataBufferUtils.release(dataBuffer);
				Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
					DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
					return Mono.just(buffer);
				});

				ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
					@Override
					public Flux<DataBuffer> getBody() {
						return cachedFlux;
					}
				};

				if (isValid) {
					return chain.filter(exchange.mutate().request(mutatedRequest).build());
				} else {
					exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
					return exchange.getResponse().setComplete();
				}

			});
		} else if (method.equalsIgnoreCase("GET")) {
			boolean isValid = false;
			String tokenFromHeader = null;
			try {
				tokenFromHeader = exchange.getRequest().getHeaders().get(AUTHENTICATION_HTTP_HEADER).get(0)
						.substring(AuthHandler.AUTH_TYPE.length()).trim();
				;
				isValid = this.authHandler.isValidToken(application, tokenFromHeader);
			} catch (Exception e) {
				isValid = false;
			}
			if (isValid) {
				return chain.filter(exchange);
			} else {
				String requestBody = "";
				try {
					Iterator<String> iterator = exchange.getRequest().getQueryParams().keySet().iterator();
					while (iterator.hasNext()) {
						String key = iterator.next();
						requestBody += key + "=" + exchange.getRequest().getQueryParams().getFirst(key) + "&";
					}
				} catch (Exception e) {
				}
				this.addUnanthorizedTraffic(exchange.getRequest(), requestBody);
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}
		} else {
			return chain.filter(exchange);
		}
	}

	public boolean isWhiteList(String uri) {
		try {
			for (String whiteListPath : this.listOfStringsWithCustomDelimiter) {
				if (uri.indexOf(whiteListPath) >= 0)
					return true;
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	public void addUnanthorizedTraffic(ServerHttpRequest request, String requestBody) {
		if(!isHttpTrafficLogEnabled) return;
		
		log.info("***********************************************************************************");
		log.info("source            : {}", this.trafficSource);
		log.info("request url       : {}", request.getURI() + "");
		log.info("request method    : {}", request.getMethod() + "");
		log.info("request header    : {}", request.getHeaders() + "");
		log.info("request date      : {}", new Timestamp(System.currentTimeMillis()));
		log.info("request body      : {}", requestBody);
		log.info("response date     : {}", new Timestamp(System.currentTimeMillis()));
		log.info("response code     : {}", HttpStatus.UNAUTHORIZED.value() + "");
		log.info("response body     : {}", HttpStatus.UNAUTHORIZED.toString());
		log.info("***********************************************************************************");
		
		if(!isDbHttpTrafficLogEnabled) return;
		
		HttpTrafficEntity trafficEntity = new HttpTrafficEntity();
		trafficEntity.setTrafficId(Thread.currentThread().getId() + "-" + System.nanoTime());
		trafficEntity.setTrafficType("IN");
		trafficEntity.setTrafficExtra("remoteAddress:"+request.getRemoteAddress());
		trafficEntity.setTrafficSource(this.trafficSource);
		trafficEntity.setTrafficRequestUrl(request.getURI() + "");
		trafficEntity.setTrafficRequestMethod(request.getMethod() + "");
		if(isDbHttpHeaderTrafficLogEnabled) {
			trafficEntity.setTrafficRequestHeader(request.getHeaders() + "");
		}
		trafficEntity.setTrafficRequestBody(requestBody);
		trafficEntity.setTrafficRequestDate(new Timestamp(System.currentTimeMillis()));
		trafficEntity.setTrafficResponseBody(HttpStatus.UNAUTHORIZED.toString());
		trafficEntity.setTrafficResponseCode(HttpStatus.UNAUTHORIZED.value() + "");
		trafficEntity.setTrafficResponseDate(new Timestamp(System.currentTimeMillis()));
		trafficEntity.setTrafficResponseMessage(HttpStatus.UNAUTHORIZED.toString());
		this.httpTrafficRepository.saveAndFlush(trafficEntity);
	}
}
