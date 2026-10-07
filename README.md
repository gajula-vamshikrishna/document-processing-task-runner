# Document Task Runner

## Overview

This service demonstrates a persistent task orchestration engine for document-processing workflows. The business logic is intentionally simulated so the focus stays on dependency-aware scheduling, concurrency control, retries, failure propagation, persistence, recovery, cancellation, and observability.

## Business Scenario

The application models the employee expense automation workflow for documents such as hotel bills, flight tickets, taxi receipts, restaurant receipts, and purchase invoices. A document is uploaded and then processed through a sequence of tasks such as validation, extraction, classification, validation of extracted data, JSON generation, and result storage.

## Architecture

```text
Upload Document
      |
      v
Validate Document
      |
      +--------------------+
      |                    |
      v                    v
Extract Data        Classify Document
      |                    |
      +----------+---------+
                 |
                 v
        Validate Extracted Data
                 |
                 v
               Generate JSON
                 |
                 v
             Store Result
```

The important part is the orchestration engine behind those tasks, not OCR or external document intelligence integrations.

## Features

- dependency management
- cycle detection
- configurable concurrency
- retry with exponential backoff
- failure propagation
- blocked tasks
- persistence
- restart recovery
- cancellation
- statistics
- execution timeout

## Tech Stack

- Java 17
- Spring Boot 3.x
- Gradle
- PostgreSQL runtime database
- H2 only for tests
- JUnit 5

## Configuration

The application uses PostgreSQL as the runtime database. Credentials are supplied through environment variables and are not hardcoded.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: ${DB_DRIVER:org.postgresql.Driver}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: ${HIBERNATE_DIALECT:org.hibernate.dialect.PostgreSQLDialect}

  task:
    scheduling:
      enabled: true

 task-runner:
  max-concurrency: 5
  scheduler:
    polling-interval-ms: 500
  retry:
    max-attempts: 3
    initial-delay-ms: 1000
    multiplier: 2.0
  execution:
    default-duration-min-ms: 1000
    default-duration-max-ms: 3000
    timeout-seconds: 10
```

Example runtime environment variables:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/document_task_runner
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export DB_DRIVER=org.postgresql.Driver
```

H2 remains available only for tests via the test configuration file.

## Running Locally

1. Ensure Java 17 is installed.
2. Set up PostgreSQL and expose `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
3. Run the tests:

```bash
./gradlew clean test
```

4. Start the application:

```bash
./gradlew bootRun
```

On Windows, use:

```powershell
gradlew.bat clean test
gradlew.bat bootRun
```

## Web UI

Start the Spring Boot application and open:

http://localhost:8080/

The UI provides:
- workflow submission
- task status
- task statistics
- cancellation
- workflow visualization

The UI is implemented using plain HTML, CSS, and JavaScript and is served directly by Spring Boot.

## API Examples

### Submit Workflow

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d '{
    "document": {"fileName": "hotel_bill_1025.pdf"},
    "tasks": [
      {"name": "validate-document", "type": "VALIDATE_DOCUMENT", "dependencies": [], "maxAttempts": 3, "failureProbability": 0.1},
      {"name": "extract-data", "type": "EXTRACT_DATA", "dependencies": ["validate-document"], "maxAttempts": 3, "failureProbability": 0.2},
      {"name": "classify-document", "type": "CLASSIFY_DOCUMENT", "dependencies": ["validate-document"], "maxAttempts": 3, "failureProbability": 0.1},
      {"name": "validate-data", "type": "VALIDATE_DATA", "dependencies": ["extract-data", "classify-document"], "maxAttempts": 3, "failureProbability": 0.1},
      {"name": "generate-json", "type": "GENERATE_JSON", "dependencies": ["validate-data"], "maxAttempts": 3, "failureProbability": 0.05},
      {"name": "store-result", "type": "STORE_RESULT", "dependencies": ["generate-json"], "maxAttempts": 3, "failureProbability": 0.05}
    ]
  }'
```

### Get Task Status

```bash
curl http://localhost:8080/api/tasks/12
```

### Cancel Task

```bash
curl -X POST http://localhost:8080/api/tasks/12/cancel
```

### Get Statistics

```bash
curl http://localhost:8080/api/tasks/stats
```

## Example Workflow

```text
validate-document
    |
    +--- extract-data
    |
    +--- classify-document
            |
            v
       validate-data
            |
            v
       generate-json
            |
            v
       store-result
```

## Testing

The repository includes automated tests for:

- cycle detection
- retry calculation
- scheduler behavior
- workflow validation
- blocking on dependency failure
- cancellation
- startup recovery
- integration workflow execution

## Design Decisions

The major design decisions and system guarantees are documented in [DESIGN.md](DESIGN.md).

## Tradeoffs

The tradeoff analysis is documented in [TRADEOFFS.md](TRADEOFFS.md).

## What Is Simulated

This project intentionally simulates document processing work. OCR extraction, malware scanning, and other external AI or document intelligence integrations are not implemented. The assignment focuses on orchestration correctness and operational behavior, not on real document-processing services.
