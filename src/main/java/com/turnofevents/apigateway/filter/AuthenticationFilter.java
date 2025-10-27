package com.turnofevents.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnofevents.apigateway.util.ErrorResponse;
import com.turnofevents.apigateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> openApiEndpoints = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Пропускаем запросы к открытым эндпоинтам без проверки JWT
        if (isOpenEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Проверяем наличие токена в заголовке Authorization
        if (!request.getHeaders().containsKey("Authorization")) {
            return onError(exchange, "Отсутствует токен авторизации", HttpStatus.UNAUTHORIZED);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return onError(exchange, "Некорректный формат токена", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtUtil.validateToken(token)) {
                return onError(exchange, "Недействительный токен", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            return onError(exchange, "Ошибка валидации токена: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        }

        // Токен валиден, добавляем информацию о пользователе в заголовки запроса
        String username = jwtUtil.getUsernameFromToken(token);
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-Auth-User", username)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    private boolean isOpenEndpoint(String path) {
        return openApiEndpoints.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String path = exchange.getRequest().getURI().getPath();
        ErrorResponse errorResponse = ErrorResponse.of(
                httpStatus.value(), 
                httpStatus.getReasonPhrase(),
                message,
                path
        );
        
        try {
            byte[] bytes = objectMapper.writeValueAsString(errorResponse).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            // В случае ошибки сериализации, возвращаем простой ответ
            byte[] bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    @Override
    public int getOrder() {
        return -100; // Высокий приоритет для запуска до других фильтров
    }
}