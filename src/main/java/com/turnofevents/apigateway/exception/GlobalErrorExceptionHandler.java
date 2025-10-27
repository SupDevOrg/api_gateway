package com.turnofevents.apigateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-2)
public class GlobalErrorExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorExceptionHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        HttpStatusCode statusCode;
        String errorMessage;

        if (ex instanceof NotFoundException) {
            statusCode = HttpStatus.NOT_FOUND;
            errorMessage = "Запрашиваемый ресурс не найден";
        } else if (ex instanceof ResponseStatusException) {
            statusCode = ((ResponseStatusException) ex).getStatusCode();
            errorMessage = ex.getMessage();
        } else {
            statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            errorMessage = "Внутренняя ошибка сервера";
        }
        
        log.error("Ошибка API Gateway: {}", ex.getMessage(), ex);
        
        response.setStatusCode(statusCode);

        byte[] bytes = ("{\"error\":\"" + errorMessage + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
} 