# Branch Appointment Booking System

Full-stack app for scheduling branch appointments. Java 21, Spring Boot, PostgreSQL, Angular.

## Status
Work in progress, built in stages. See commit history.

## Prerequisites
- Java 21
- Docker (for the database and mail catcher)

## Run locally
    docker compose up -d
    cd backend
    ./mvnw spring-boot:run

- API docs (Swagger UI): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Mail inbox (MailHog): http://localhost:8025

## Test
    cd backend
    ./mvnw verify