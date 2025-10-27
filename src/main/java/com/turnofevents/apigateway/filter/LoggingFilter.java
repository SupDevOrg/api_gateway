package com.turnofevents.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        LOGGER.info("Запрос к {}. Метод: {}",
                exchange.getRequest().getURI().getPath(),
                exchange.getRequest().getMethod());
        
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    LOGGER.info("Ответ со статусом {}", 
                            exchange.getResponse().getStatusCode());
                }));
    }

    @Override
    public int getOrder() {
        return -1; // Высокий приоритет для запуска до основных фильтров
    }
} 