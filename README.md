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

## Supabase authentication setup

The Android app uses Supabase Auth for email/password sign-up and login. Keep the project URL and publishable key in the ignored `local.properties` file:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-publishable-key
```

The publishable/anon key is intended for client applications. Never put a Supabase service-role key in the Android project. Enable email authentication in Supabase under **Authentication > Providers > Email**.

For email confirmation to return to the Android app, add this exact value in Supabase under **Authentication > URL Configuration > Redirect URLs**:

```text
spendwise://auth/callback
```

The app sends this redirect when creating an account. If an older confirmation email points to an unreachable URL, create the account again or request a new confirmation email after adding the redirect URL. The old link cannot be changed by the app.

### Emulator certificate troubleshooting

If login shows `Chain validation failed`, do not disable HTTPS certificate validation. In the Android emulator:

1. Open **Settings > System > Date & time**.
2. Enable automatic date, time, and time zone.
3. In Android Studio **Device Manager**, open the emulator menu and choose **Cold Boot Now**.
4. Confirm the emulator can open `https://supabase.com` in its browser.
5. Run the app again.

This usually means the emulator clock or its trusted certificate store is stale. The Supabase endpoint was verified from the development machine over HTTPS.

## Supabase database tables

Run [001_initial_schema.sql](./supabase/migrations/001_initial_schema.sql) in the Supabase **SQL Editor**. It creates:

- `profiles`
- `budget_cycles`
- `expenses`
- `income_transactions`
- `extra_budgets`
- `emergency_fund_transactions`
- `alerts`
- `cycle_reports`

The migration also creates a profile automatically when a Supabase Auth user signs up and enables Row Level Security so users can access only their own records.

The app asks for the user's name during sign-up and sends it as `display_name` metadata. Login continues to use email and password.

### Financial data synchronization

After authentication, the Android app uses the Supabase REST API with the user's
access token. Row Level Security applies to every request. The following local
actions are persisted to the matching Supabase tables:

- Budget setup and edits -> `budget_cycles`
- Expenses -> `expenses`
- Added income -> `income_transactions`
- Extra budgets -> `extra_budgets`
- Automatic and manual savings -> `emergency_fund_transactions`

The app keeps a user-scoped `SharedPreferences` cache and synchronizes it when the
authenticated shell opens or when **Sync with backend** is pressed. Existing data
from the previous single-user cache is migrated once to the first signed-in user.
The old
`backend/` Spring Boot project is still an in-memory API prototype and is not the
source of truth for Supabase data.

Manual savings are available from the **Savings** tab. They reduce the available
budget balance and create a `CONTRIBUTION` transaction in the emergency fund.
