# Pulse

**AI-powered fitness and wellness app for Android developed with Kotlin**

Pulse is a native Android app built for **AMIT3353 Mobile Application Development** (TAR UMT, Academic Session 202605). It addresses **UN Sustainable Development Goal 3: Good Health and Well-being** by combining three AI-powered modules in one app: a workout planner, a food macro detector, and a mental wellness check-in.

Pulse runs on phones and tablets, and uses **Supabase** (Postgres) as its backend.

---

## Features

### Authentication
- Sign up with email and password
- Show/hide password toggle and password confirmation on sign-up

### Home
- Dashboard with quick actions and an at-a-glance view of your latest workout plan, meal, and check-in

### Workout
- **AI workout planner**: enter a goal, fitness level, days per week, and equipment to generate a weekly plan
- **Workout history** and **session detail** for reviewing saved plans
- **Active workout flow**: warm-up timer, set logging, rest timer, and a completion summary

### Macros
- **Macro detector**: take a photo or pick one from the gallery and get an AI estimate of calories, protein, carbs, and fat
- **Manual entry**: describe a meal in text and get the same estimate
- **Meal history** with meal detail and delete

### Wellness
- **PHQ-9** and **DASS-21** self-report questionnaires with standard scoring and severity bands
- **Crisis support**: if PHQ-9 item 9 (thoughts of self-harm) is answered with anything other than "Not at all", support resources appear immediately
- A short, supportive AI reflection after each check-in
- **Check-in history** and a **mood trend chart** of PHQ-9 scores over time

### Profile
- Edit display name, settings, Q&A, terms and privacy, and logout

### Design
- Dark "Ink / Ember / Sage / Gold" theme
- Adaptive layout: bottom navigation bar on phones, side navigation rail on tablets and wide screens

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose (nested graphs), WindowSizeClass for adaptive layouts |
| Architecture | Unidirectional data flow: ViewModel, StateFlow, and a single UiState per screen |
| Backend | Supabase (Postgres, Row Level Security) via `supabase-kt` Postgrest |
| AI | OpenAI API (GPT-4o mini) via Retrofit, OkHttp, and Gson |
| Serialization | kotlinx.serialization (Supabase rows) |
| Images | Coil, system camera and gallery through `FileProvider` |

---

## Project Structure

```
app/src/main/java/com/fitnessapp/pulse/
├── data/
│   ├── local/            # AuthPreferences (Remember me flag)
│   ├── model/            # PHQ-9 / DASS-21 questions and scoring
│   ├── remote/           # OpenAI (Retrofit) + SupabaseInstance
│   │   └── model/        # Supabase row models
│   └── repository/       # Auth, Workout, Macro, Profile repositories
├── ui/
│   ├── auth/             # Login, Sign up, Forgot password
│   ├── home/
│   ├── workout/
│   ├── macro/
│   ├── mentalhealth/
│   ├── profile/
│   ├── components/       # Shared UI (PulseCard, PulseTopBar, BrandMark, ...)
│   ├── navigation/       # Root nav graph, tab scaffold, adaptive layout
│   └── theme/
└── MainActivity.kt
```

---

## Getting Started

### Prerequisites
- A recent version of Android Studio
- An Android emulator or physical device (minimum SDK 26)
- A [Supabase](https://supabase.com) project
- An [OpenAI API](https://platform.openai.com) key with billing enabled

### 1. Clone the repository

```bash
git clone https://github.com/jushuen77/MobileAppDev.git
```

### 2. Add your keys to `local.properties`

Create or edit `local.properties` in the project root:

```properties
OPENAI_API_KEY=your-openai-key
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-supabase-anon-key
```

Notes:
- `SUPABASE_URL` must be the **base project URL only**. Do not add `/rest/v1`, or the app will crash on startup.
- These values are read at build time into `BuildConfig`, so **sync Gradle** after changing them.
- Keep this file private. If the repository is ever made public, rotate all keys first.

### 3. Set up the Supabase database

In the Supabase **SQL Editor**, enable the extensions used by the custom auth system and create the tables:

```sql
create extension if not exists pgcrypto;
create extension if not exists pgjwt;

-- User accounts
create table public.app_users (
    id uuid primary key default gen_random_uuid(),
    email text unique not null,
    password_hash text not null,
    created_at timestamptz default now()
);

-- Workout plans
create table workout_logs (
    id bigint generated always as identity primary key,
    user_id uuid references public.app_users(id) not null,
    goal text not null,
    level text not null,
    days_per_week int not null,
    plan_text text not null,
    created_at timestamptz default now()
);

-- Meals
create table meal_logs (
    id bigint generated always as identity primary key,
    user_id uuid references public.app_users(id) not null,
    food_name text not null,
    calories int not null,
    protein_g int not null,
    carbs_g int not null,
    fat_g int not null,
    logged_at timestamptz default now()
);

-- PHQ-9 / DASS-21 check-ins
create table check_ins (
    id bigint generated always as identity primary key,
    user_id uuid references public.app_users(id) not null,
    phq9_score int not null,
    phq9_severity text not null,
    dass_depression_score int not null,
    dass_anxiety_score int not null,
    dass_stress_score int not null,
    ai_reflection text not null,
    logged_at timestamptz default now()
);

-- Profiles
create table profiles (
    user_id uuid references public.app_users(id) primary key,
    display_name text not null default '',
    updated_at timestamptz default now()
);
```

Enable **Row Level Security** on every table and add policies so users can only access their own rows:

```sql
alter table public.app_users enable row level security;
alter table workout_logs enable row level security;
alter table meal_logs enable row level security;
alter table check_ins enable row level security;
alter table profiles enable row level security;

-- Repeat for each table (shown for workout_logs)
create policy "Users can view own workouts"
    on workout_logs for select using (auth.uid() = user_id);
create policy "Users can insert own workouts"
    on workout_logs for insert with check (auth.uid() = user_id);
```

Meal logs also need a delete policy, and `profiles` needs insert and update policies, all using the same `auth.uid() = user_id` check.

The sign-up, sign-in, and OTP database functions for the custom auth system also need to be created in the SQL Editor. Replace any JWT secret placeholders with your project's JWT secret (Project Settings, API). Never commit that secret anywhere public.

### 4. Configure email for OTP codes

Sign-up and password reset send 6-digit codes by email:

1. In Supabase, go to **Authentication, Emails** and set up **custom SMTP**. Newer Supabase projects cannot edit email templates on the default provider. A Gmail App Password works for development.
2. Edit the **Confirm signup** and **Reset password** templates so the body contains `{{ .Token }}` instead of a confirmation link.

### 5. Run the app

1. Open the project in Android Studio and let Gradle sync
2. Select an emulator or connected device
3. Press **Run**

To test the tablet layout, use a tablet or resizable emulator profile.

---

## Architecture Notes

- **Unidirectional data flow**: each screen reads one `UiState` from its ViewModel and sends events back through ViewModel functions. Form input lives in the ViewModel, so it survives screen rotation.
- **Secrets**: API keys are read from `local.properties` into `BuildConfig` and are never hardcoded in Kotlin source.
- **Separate network layers**: OpenAI calls go through Retrofit/OkHttp (with extended timeouts for long AI responses). Supabase data access goes through `supabase-kt` Postgrest.
- **Row Level Security**: data access is restricted at the database level, not only filtered in the app.

---

## Disclaimers

- Macro estimates and workout plans are **AI-generated and approximate**. They are not medical or dietary advice.
- The PHQ-9 and DASS-21 questionnaires are **self-report screening tools, not a diagnosis**. Pulse is not a substitute for professional mental health care. If you are in crisis, please contact a trusted person or a local helpline.

---

## Known Limitations

- Generated workout plans are free text, so the Active Workout flow is not linked to the specific exercises in a plan.
- The Active Workout flow does not show exercise demonstrations. Exercise instruction and demo-video screens are placeholders.
- History and dashboard screens load data when opened and do not refresh automatically.
- Some navigation routes from the original design (for example, calendar and list views for mood history) are placeholders.

---
