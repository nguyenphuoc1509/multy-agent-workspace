---
sessionId: session-261027-153112-kq0c
---

# Diagnosis

### What happened

**Run 1: `release version 25 not supported`.** Maven was running on **JDK 23.0.2** (ArchUnit logs `Detected Java version 23.0.2`). `javac` from JDK 23 cannot target release 25. This is already fixed: `pom.xml` now has `<java.version>21</java.version>`, and run 2 compiles with `release 21`.

**Run 2: the console output stops at `OpenApiExportTest`, but the tests did fail.** The files in `target/surefire-reports/` show that **every Testcontainers-based test failed for the same reason**. That covers `OpenApiExportTest`, `SchemaTest`, `AuthApiTest`, `FirstUserBootstrapTest` and `ProjectApiTest`. Only `ArchitectureTest` (8/8) passed. The error is:

```
FlywaySqlUnableToConnectToDbException: Unable to obtain connection from database:
FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"   (SQL State 22023)
```

### Root cause
1. Windows is set to the time zone *SE Asia Standard Time*. The JDK maps this to the old zone ID **`Asia/Saigon`** and uses it as the JVM's default time zone.
2. When the PostgreSQL JDBC driver connects, it sends the JVM's default time zone ID as the `TimeZone` setting.
3. The `postgres:16` container (started in `IntegrationTestBase`) uses a recent version of the time zone database. That version no longer includes old alias names such as `Asia/Saigon` (the current name is `Asia/Ho_Chi_Minh`). The server rejects the connection, so Flyway fails, and then the Spring context fails to start.

`application.yml` already sets `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`. That only affects how Hibernate binds timestamps. It does not change the time zone the driver sends when it connects.

### Long-term fix
Set the JVM time zone to **UTC** everywhere the app runs: in tests, with `spring-boot:run`, and with `java -jar`. This matches the UTC setting Hibernate already uses, and the build no longer depends on the developer's or CI machine's local time zone. We will not touch the database container or switch to `Asia/Ho_Chi_Minh`, because that would only fix this one machine.

# Technical Design

### Proposed Changes

**`pom.xml`** (the `<build><plugins>` section):
- Add `maven-surefire-plugin` with `<argLine>-Duser.timezone=UTC</argLine>`. The version comes from the Spring Boot parent. This covers every `IntegrationTestBase` subclass.
- Configure `spring-boot-maven-plugin` with `<jvmArguments>-Duser.timezone=UTC</jvmArguments>` so `mvn spring-boot:run` behaves the same way.

**`src/main/java/com/workspace/WorkspaceApplication.java`**:
- In `main`, call `TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))` before `SpringApplication.run(...)`. This covers the packaged jar and IDE runs that don't pass the JVM flag.

```java
public static void main(String[] args) {
    TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    SpringApplication.run(WorkspaceApplication.class, args);
}
```

**`README.md`**: add a short note saying the app and tests run with the JVM time zone set to UTC, and why.

### Risks
- If a Mockito or JaCoCo agent is added later, it must be appended to `argLine` rather than replace it. Today there is no such agent.
- The tests need Docker running for Testcontainers. Docker is already running, because the container started and the failure only happened when connecting to it.

# Testing

### Validation Approach
- Run `mvn verify` (on JDK 23, release 21).
- Check that all test classes pass, especially the 5 Testcontainers suites that failed before.
- Check the `target/surefire-reports/*.txt` files for any remaining errors.

### Key Scenarios
- `OpenApiExportTest` passes and regenerates `docs/api/openapi.yaml`.
- `SchemaTest`, `AuthApiTest`, `FirstUserBootstrapTest` and `ProjectApiTest` start the Spring context, and the Flyway migrations run against `postgres:16`.
- `ArchitectureTest` still passes (8/8).

### Edge Cases
- If other errors show up once the context starts, fix them separately, guided by the reports.

# Delivery Steps

### ✓ Step 1: Pin JVM timezone to UTC in Maven build
Tests and `spring-boot:run` start the JVM with `user.timezone=UTC`, so the PostgreSQL JDBC driver no longer sends `Asia/Saigon` when it connects.

- In `pom.xml`, add a `maven-surefire-plugin` entry with `<argLine>-Duser.timezone=UTC</argLine>`. The version comes from the Spring Boot parent.
- In `pom.xml`, add `<configuration><jvmArguments>-Duser.timezone=UTC</jvmArguments></configuration>` to `spring-boot-maven-plugin`.
- Run `mvn verify`.
- Confirm that `OpenApiExportTest`, `SchemaTest`, `AuthApiTest`, `FirstUserBootstrapTest` and `ProjectApiTest` pass, using `target/surefire-reports`.

### ✓ Step 2: Set UTC default at application startup and document it
The packaged jar and IDE runs also use UTC, whatever the host's time zone is.

- In `WorkspaceApplication.main`, call `TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC))` before `SpringApplication.run`.
- Add a short note to `README.md` that the JVM time zone is set to UTC, and explain the `Asia/Saigon` / PostgreSQL problem it avoids.
- Run `mvn verify` again and confirm the whole build passes.