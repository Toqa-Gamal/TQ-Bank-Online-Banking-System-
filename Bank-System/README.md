# Banking Transaction System (DEPI Project 2)

Requirements: JDK 17+, Maven 3.8+

Run:      mvn spring-boot:run
Build:    mvn clean package   ->  java -jar target/banking-system.jar
Base URL: http://localhost:8080/api
DB file:  bank.db (created + seeded on first run; delete it to reset)

Database: starts EMPTY except one admin user  ->  admin@bank.com / Test@1234
Everyone else registers from the Register tab.
To load the demo data with the seeded database bugs, set  app.seed=buggy  in application.properties
(or run with --app.seed=buggy), delete bank.db and start again.   Options: admin (default) | buggy | none
Auth: POST /api/auth/login -> use "Authorization: Bearer <token>" on all other endpoints.
Pages: /  (website)   /api-docs.html   /qa-locators.html
