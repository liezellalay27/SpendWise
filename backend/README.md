# SpendWise Backend

This is a separate Spring Boot + Kotlin backend for the Android app.

## Run

From the `backend` folder:

```powershell
.\gradlew bootRun
```

If the Gradle wrapper has not been generated, open the folder in IntelliJ IDEA or Android Studio and run the `SpendWiseApplication` configuration.

The server listens on `http://localhost:8080`.

## Endpoints

```text
GET  /api/v1/dashboard
POST /api/v1/expenses  {"amountCents": 25000, "name": "Lunch"}
POST /api/v1/income    {"amountCents": 100000}
PATCH /api/v1/settings/emergency-allocation {"percentage": 20}
```

The current service uses an in-memory store to make local development immediate. The next backend milestone is replacing `BudgetService` state with PostgreSQL repositories and adding authentication/user isolation.

For the Android emulator, the app uses `http://10.0.2.2:8080/` to reach the host machine.
