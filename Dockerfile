# Сборка проекта
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Кеширование зависимостей
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходники
COPY src ./src

# Собираем проект
RUN mvn clean package -DskipTests -B

# Финальный образ
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Устанавливаем curl для healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Копируем JAR файлы
RUN mkdir -p ./libs
COPY --from=builder /app/target/*.jar ./libs/

# Создаем стартовый скрипт
RUN echo '#!/bin/sh' > start.sh \
    && echo 'echo "Содержимое папки libs:"' >> start.sh \
    && echo 'ls -l libs' >> start.sh \
    && echo 'JAR=$(ls libs | grep -v "sources\\|javadoc" | head -n 1)' >> start.sh \
    && echo 'echo "Запускаем $JAR"' >> start.sh \
    && echo 'exec java -jar "libs/$JAR"' >> start.sh

RUN chmod +x start.sh

# ENTRYPOINT через скрипт
ENTRYPOINT ["./start.sh"]

EXPOSE 8080