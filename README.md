# SpendWise

SpendWise is an Android Kotlin MVP for tracking a budget cycle and calculating a daily safe-to-spend allowance.

## Current MVP

- Monthly budget initialized at PHP 30,000
- Expense entry
- Income entry with a 10% emergency-fund allocation
- Live remaining balance
- Safe-to-spend-today calculation
- Low-balance warning
- Local persistence through `SharedPreferences`

## Open the project

1. Open this folder in Android Studio Ladybug or newer.
2. Allow Android Studio to sync the Gradle project.
3. Run the `app` configuration on an Android emulator or device.

The project currently does not include a Gradle wrapper. Android Studio can generate or download the configured Gradle distribution during sync.

## Calculation

```text
remaining balance =
    budget
  + income added to spending
  - expenses
  - emergency-fund allocations

safe to spend today =
    max(0, remaining balance / remaining days)
```

All values are stored as integer cents in the ViewModel and persistence layer to avoid floating-point money calculations.

## Next milestones

1. Add a first-run setup screen for daily, weekly, and monthly cycles.
2. Replace the summary totals with Room entities for transaction history.
3. Add configurable emergency-fund allocation rules.
4. Add cycle reports and notification scheduling.
5. Add a Spring Boot or Django API and synchronize local data.
