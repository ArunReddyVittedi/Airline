# Telusko Airline AI Project

This project is an airline booking and support application that combines conventional transactional workflows with language-model features. It includes flight search, booking management, support-ticket triage, retrieval-augmented assistance, multi-agent disruption handling, trip planning, an MCP operations server, and local observability.

I built this project as part of a Telusko course, using the course foundation and related implementation ideas, and extended it with the features and integrations documented here.

## Architecture

```text
React + Vite UI (5173)
        |
        | /api and /actuator (development proxy)
        v
Spring Boot backend (8080)
        |-- Spring Security + JWT
        |-- Spring Data JPA --------------------+
        |-- LangChain4j AI services             |
        |-- RAG + pgvector                      v
        |-- agentic workflows             PostgreSQL + pgvector (5433)
        |-- Micrometer / Actuator
        |
        +-- stdio MCP client --> Airline operations MCP server

Prometheus (9090) --> /actuator/prometheus --> Grafana (3000)
```

The backend is the system of record for users, flights, bookings, tickets, chat memory, and knowledge articles. PostgreSQL also stores the pgvector embeddings used by retrieval. The separate MCP process exposes operational tools over standard input/output and is launched by the backend from its packaged JAR.

## Features

- Public structured flight search and natural-language flight search
- JWT registration, login, and role-based authorization
- Passenger booking, cancellation, refund quotes, and rebooking
- Persistent assistant conversations with blocking and streamed responses
- Retrieval-augmented answers from airline policy and destination content
- Local tools for flights, bookings, refund calculations, and operations analytics
- MCP tools for weather, airport congestion, gate information, and peak travel periods
- Input and output guardrails for common prompt-injection patterns, passenger isolation, and invented flight numbers
- AI-assisted support-ticket categorization and prioritization
- Supervisor-based disruption handling across situation, rebooking, compensation, and passenger-message agents
- Sequence-based trip planning across destination research, flight advice, and itinerary agents
- Admin operations for analytics, disruption status changes, ticket review, and knowledge reindexing
- Micrometer metrics, Prometheus scraping, and a provisioned Grafana dashboard
- Relative-date seed data so flight search and disruption scenarios remain usable after setup

## Project layout

```text
airline-ai-project/
|-- airline-backend/      Spring Boot API, domain logic, security, persistence, RAG, and agents
|-- airline-mcp-server/   Standalone Java MCP server packaged as an executable JAR
|-- airline-ui/           React single-page application built with Vite
`-- monitoring/           Prometheus and Grafana local monitoring stack
```

The Java packages intentionally remain under the course-related `com.telusko.airline` namespace, and the UI retains the Telusko Airlines branding.

## Technology

- Java 17
- Spring Boot 3.5.16
- LangChain4j 1.18.1 and beta28 agentic/MCP modules
- PostgreSQL 16 with pgvector
- React 18, React Router 6, and Vite 5
- JWT authentication with JJWT
- Micrometer, Prometheus, and Grafana
- Maven, npm, and Docker Compose

## Prerequisites

Install the following before starting the application:

- JDK 17 or newer
- Maven 3.9 or newer
- Node.js with npm
- Docker with Docker Compose
- An OpenAI API key

The application uses `gpt-4o-mini` for chat and `text-embedding-3-small` for embeddings. These values are defined in `airline-backend/src/main/resources/application.properties`.

## Run locally

Enter the application directory first:

```bash
cd airline-ai-project
```

Run the remaining commands from that directory unless a step changes directories.

1. Package the MCP server:

   ```bash
   cd airline-mcp-server
   mvn clean package
   cd ..
   ```

2. Export the required API key:

   ```bash
   export OPENAI_API_KEY="your-key"
   ```

3. Start the backend:

   ```bash
   cd airline-backend
   mvn spring-boot:run
   ```

   Spring Boot starts the PostgreSQL container declared in `airline-backend/docker-compose.yml`. The database is exposed on host port `5433`, and the API starts on `http://localhost:8080`.

4. Start the UI in a second terminal:

   ```bash
   cd airline-ui
   npm ci
   npm run dev
   ```

5. Open `http://localhost:5173`.

Set `BACKEND_URL` before `npm run dev` to proxy the UI to a backend on a different address. Set `PORT` and `FRONTEND_URL` when changing the backend port or allowed frontend origin.

## Seeded local accounts

The startup seeder creates these accounts when the users table is empty:

| Role | Email | Password |
| --- | --- | --- |
| Passenger | `ramesh@example.com` | `telusko123` |
| Passenger | `priya@example.com` | `telusko123` |
| Administrator | `admin@telusko.com` | `telusko123` |

These credentials and the default JWT secret are for local development only. Replace them before any shared or production deployment.

## Main API areas

| Area | Base path | Access |
| --- | --- | --- |
| Authentication | `/api/auth` | Public |
| Flight browsing and status | `/api/flights` | Public for GET requests |
| Natural-language flight search | `/api/flights/search/natural` | Public |
| Bookings | `/api/bookings` | Authenticated passenger |
| Assistant | `/api/assistant` | Authenticated passenger |
| Disruption handling | `/api/disruption` | Authenticated passenger |
| Support tickets | `/api/tickets` | Authenticated passenger |
| Trip planner | `/api/trip-planner` | Authenticated passenger |
| Operations | `/api/admin` | Administrator only |
| OpenAPI UI | `/swagger-ui/index.html` | Public in the current local configuration |
| Actuator | `/actuator` | Public in the current local configuration |

The assistant exposes `/ask` for a complete response and `/stream` for server-sent text fragments. The complete response includes tool/agent trace data and retrieved-source metadata when available.

## Configuration

The committed defaults support local development. Override sensitive or environment-specific values with environment variables.

| Environment variable | Purpose | Default behavior |
| --- | --- | --- |
| `OPENAI_API_KEY` | Chat, streaming, and embedding authentication | Required; no committed default |
| `JWT_SECRET` | HS256 signing key | Development-only fallback |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5433/airline` |
| `SPRING_DATASOURCE_USERNAME` | Database user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `telusko` |
| `FRONTEND_URL` | Allowed CORS origin or comma-separated origins | `http://localhost:5173` |
| `PORT` | Backend HTTP port | `8080` |
| `BACKEND_URL` | Vite development proxy target | `http://localhost:8080` |

The backend expects the MCP JAR at `../airline-mcp-server/target/airline-ops-mcp-server.jar` relative to the backend directory. Package that module before starting or testing the backend.

## Testing

Keep Docker running and package the MCP server first, then run the existing backend test:

```bash
cd airline-mcp-server
mvn clean package
cd ../airline-backend
mvn test
```

The context test verifies AI-service wiring, the agentic systems, RAG configuration, and the four MCP tools without making model requests. Spring Boot still needs PostgreSQL, and the configured OpenAI key property must resolve even though the test does not call the models.

Build the UI with:

```bash
cd airline-ui
npm run build
```

## Monitoring

Start the optional monitoring stack while the backend is running:

```bash
docker compose -f monitoring/docker-compose.yml up -d
```

Open Prometheus at `http://localhost:9090` and Grafana at `http://localhost:3000`. Grafana is provisioned with the project dashboard and anonymous administrator access for this local stack. Do not expose that configuration publicly.

Stop the monitoring stack with:

```bash
docker compose -f monitoring/docker-compose.yml down
```

## Deployment notes

Before deploying outside a local environment:

1. Replace the default database and JWT credentials through environment variables or a secret manager.
2. Restrict Actuator and OpenAPI endpoints with network controls or Spring Security.
3. Disable anonymous Grafana administration and configure authentication.
4. Provide managed PostgreSQL with the `vector` extension enabled.
5. Package the MCP server and keep its configured JAR path valid for the backend process.
6. Build the UI with `npm run build` and serve the generated assets through the selected web tier.
7. Set the production frontend origin in `FRONTEND_URL`.

No cloud-specific deployment manifests are included in this repository.
