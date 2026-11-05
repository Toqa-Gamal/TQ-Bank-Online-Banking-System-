# TQ Bank — Web-ready frontend

The existing Spring Boot backend is preserved. The browser interface is now organized as separate files:

- `src/main/resources/static/index.html` — page structure
- `src/main/resources/static/css/style.css` — styling
- `src/main/resources/static/js/app.js` — browser logic and API calls
- `src/main/resources/static/img/` — logos

## Run locally

1. Install Java 17+ and Maven.
2. From this folder, run `mvn spring-boot:run`.
3. Open `http://localhost:8080/`.

The frontend calls `/api` on the same origin. This is intentional: for a fully working public website, deploy the Spring Boot application (not only the static HTML folder) to a Java-capable host with persistent storage for the SQLite database. A static-only host can serve the page but cannot run the Java API or persist banking transactions.

## Before public deployment

- Configure a persistent disk for SQLite, or migrate to a managed production database.
- Configure production secrets and an admin account; do not use demo credentials.
- Enable HTTPS and review authentication/session handling, CORS (if frontend and API use different domains), rate limits, and security settings.
- This is a demonstration/student banking project, not a real banking service. Do not enter real financial or personal data.
