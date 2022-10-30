package com.jebsen.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import com.jebsen.entity.HttpTrafficEntity;
import com.jebsen.repository.HttpTrafficRepository;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * a filter to log http traffic, POST and GET only
 */
@Slf4j
@Component
public class HttpTrafficFilter implements GlobalFilter, Ordered {

	@Value("${spring.application.httpTrafficLog.enabled: true}")
	private boolean isHttpTrafficLogEnabled;
	
	@Value("${spring.application.httpTrafficLog.database.enabled: true}")
	private boolean isDbHttpTrafficLogEnabled;
	
	@Value("${spring.application.httpTrafficLog.database.httpHeader.enabled: false}")
	private boolean isDbHttpHeaderTrafficLogEnabled;
	
	@Value("${spring.application.name: jebsen-gateway}")
	private String trafficSource;

	@Autowired
	private HttpTrafficRepository httpTrafficRepository;

	@Override
	public int getOrder() {
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if(!isHttpTrafficLogEnabled) {
			return chain.filter(exchange);
		}
		
		try {
			final String requestMethod = exchange.getRequest().getMethodValue();
			if (requestMethod.equalsIgnoreCase("POST")) {
				return DataBufferUtils.join(exchange.getRequest().getBody()).flatMap(dataBuffer -> {
					byte[] bytes = new byte[dataBuffer.readableByteCount()];
					dataBuffer.read(bytes);

					String trafficId = null;
					String bodyString = "";
					try {
						bodyString = new String(bytes, "utf-8");
						log.info("***********************************************************************************");
						log.info("source            : {}", this.trafficSource);
						log.info("request url       : {}", exchange.getRequest().getURI() + "");
						log.info("request method    : {}", exchange.getRequest().getMethod() + "");
						log.info("request header    : {}", exchange.getRequest().getHeaders() + "");
						log.info("request date      : {}", new Timestamp(System.currentTimeMillis()));
						log.info("request body      : {}", bodyString);
						
						if(isDbHttpTrafficLogEnabled) {
							trafficId = Thread.currentThread().getId() + "-" + System.nanoTime();
							HttpTrafficEntity trafficEntity = new HttpTrafficEntity();
							trafficEntity.setTrafficId(trafficId);
							trafficEntity.setTrafficType("IN");
							trafficEntity.setTrafficExtra("remoteAddress:"+exchange.getRequest().getRemoteAddress());
							trafficEntity.setTrafficSource(this.trafficSource);
							trafficEntity.setTrafficRequestUrl(exchange.getRequest().getURI() + "");
							trafficEntity.setTrafficRequestMethod(exchange.getRequest().getMethod() + "");
							if(isDbHttpHeaderTrafficLogEnabled) {
								trafficEntity.setTrafficRequestHeader(exchange.getRequest().getHeaders() + "");
							}
							trafficEntity.setTrafficRequestBody(bodyString);
							trafficEntity.setTrafficRequestDate(new Timestamp(System.currentTimeMillis()));
							this.httpTrafficRepository.saveAndFlush(trafficEntity);
						}
					} catch (Exception e) {
						log.error("error", e);
					}
					finally {
						exchange.getAttributes().put("POST_BODY", bodyString);
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
					return chain.filter(exchange.mutate().request(mutatedRequest)
							.response(this.decorate(exchange, trafficId)).build());
				});
			} else if (requestMethod.equalsIgnoreCase("GET")) {
				MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
				
				String trafficId = null;
				String bodyString = "";
				try {
					Iterator<String> iterator = queryParams.keySet().iterator();
					while (iterator.hasNext()) {
						String key = iterator.next();
						bodyString += key + "=" + queryParams.getFirst(key) + "&";
					}
				} catch (Exception e) {
					log.error("bodyString error", e);
					bodyString = ""+e;
				}
				
				log.info("***********************************************************************************");
				log.info("source            : {}", this.trafficSource);
				log.info("request url       : {}", exchange.getRequest().getURI() + "");
				log.info("request method    : {}", exchange.getRequest().getMethod() + "");
				log.info("request header    : {}", exchange.getRequest().getHeaders() + "");
				log.info("request date      : {}", new Timestamp(System.currentTimeMillis()));
				log.info("request body      : {}", bodyString);
				
				if(isDbHttpTrafficLogEnabled) {
					trafficId = Thread.currentThread().getId() + "-" + System.nanoTime();
					HttpTrafficEntity trafficEntity = new HttpTrafficEntity();
					trafficEntity.setTrafficId(trafficId);
					trafficEntity.setTrafficType("IN");
					trafficEntity.setTrafficSource(this.trafficSource);
					trafficEntity.setTrafficRequestUrl(exchange.getRequest().getURI() + "");
					trafficEntity.setTrafficRequestMethod(exchange.getRequest().getMethod() + "");
					if(isDbHttpHeaderTrafficLogEnabled) {
						trafficEntity.setTrafficRequestHeader(exchange.getRequest().getHeaders() + "");
					}
					trafficEntity.setTrafficRequestDate(new Timestamp(System.currentTimeMillis()));
					trafficEntity.setTrafficRequestBody(bodyString);
					this.httpTrafficRepository.saveAndFlush(trafficEntity);
				}
				return chain.filter(
						exchange.mutate().response(this.decorate(exchange, trafficId)).build());
			} else {
				log.info("***********************************************************************************");
				log.info("source            : {}", this.trafficSource);
				log.info("request url       : {}", exchange.getRequest().getURI() + "");
				log.info("request method    : {}", exchange.getRequest().getMethod() + "");
				log.info("request header    : {}", exchange.getRequest().getHeaders() + "");
				log.info("request date      : {}", new Timestamp(System.currentTimeMillis()));
				log.info("request body      : {}", "Not support to log request");
				log.info("response date     : {}", new Timestamp(System.currentTimeMillis()));
				log.info("response code     : {}", "");
				log.info("response message  : {}", "");
				log.info("response body     : {}", "Not support to log response");
				log.info("***********************************************************************************");
				
				if(isDbHttpTrafficLogEnabled) {
					HttpTrafficEntity trafficEntity = new HttpTrafficEntity();
					trafficEntity.setTrafficId(Thread.currentThread().getId() + "-" + System.nanoTime());
					trafficEntity.setTrafficType("IN");
					trafficEntity.setTrafficSource(this.trafficSource);
					trafficEntity.setTrafficRequestUrl(exchange.getRequest().getURI() + "");
					trafficEntity.setTrafficRequestMethod(exchange.getRequest().getMethod() + "");
					if(isDbHttpHeaderTrafficLogEnabled) {
						trafficEntity.setTrafficRequestHeader(exchange.getRequest().getHeaders() + "");
					}
					trafficEntity.setTrafficRequestDate(new Timestamp(System.currentTimeMillis()));
					trafficEntity.setTrafficRequestBody("Not support to log request");
					trafficEntity.setTrafficResponseBody("Not support to log response");
					this.httpTrafficRepository.saveAndFlush(trafficEntity);
				}
				return chain.filter(exchange);
			}
		} catch (Exception e) {
			return chain.filter(exchange);
		}
	}

	private ServerHttpResponse decorate(ServerWebExchange exchange, final String trafficId) {
		return new ServerHttpResponseDecorator(exchange.getResponse()) {

			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				try {
					Class inClass = String.class;
					Class outClass = String.class;
	
					String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
					HttpHeaders httpHeaders = new HttpHeaders();
	
					httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);
	
					ClientResponse clientResponse = ClientResponse.create(exchange.getResponse().getStatusCode())
							.headers(headers -> headers.putAll(httpHeaders)).body(Flux.from(body)).build();
					Mono modifiedBody = clientResponse.bodyToMono(inClass).flatMap(originalBody -> {
						String responseBody = originalBody + "";
	
						log.info("response date     : {}", new Timestamp(System.currentTimeMillis()));
						log.info("response code     : {}", HttpStatus.OK.value() + "");
						log.info("response message  : {}", HttpStatus.OK.toString());
						log.info("response body     : {}", responseBody);
						log.info("***********************************************************************************");
						
						if(isDbHttpTrafficLogEnabled && trafficId != null) {
							Optional<HttpTrafficEntity> tmp = httpTrafficRepository.findById(trafficId);
							if (tmp.isPresent()) {
								HttpTrafficEntity entity = tmp.get();
								entity.setTrafficResponseBody(responseBody);
								entity.setTrafficResponseDate(new Timestamp(System.currentTimeMillis()));
								entity.setTrafficResponseCode("200");
								httpTrafficRepository.saveAndFlush(entity);
							}
						}
						return Mono.just(originalBody);
					});
					BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, outClass);
					CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
							exchange.getResponse().getHeaders());
					return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
						Flux<DataBuffer> messageBody = outputMessage.getBody();
						HttpHeaders headers = getDelegate().getHeaders();
						if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)) {
							messageBody = messageBody.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
						}
						return getDelegate().writeWith(messageBody);
					}));
				}catch(Exception e) {
					log.error("decorate error", e);
					return super.writeWith(body);
				}
			}

			@Override
			public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
				return writeWith(Flux.from(body).flatMapSequential(p -> p));
			}
		};
	}
}
