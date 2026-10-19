# MobileAppDev
# Pulse — Technical Implementation Guide
**AMIT3353 Mobile Application Development — SDG 3: Good Health and Well-being**

---

## 1. Project Architecture

Use **MVVM** (Model-View-ViewModel) — it's the pattern Google recommends for Compose, and it's easy to justify to your tutor for CLO2 ("architecture in Android apps development").

```
com.yourteam.pulse
├── data
│   ├── remote
│   │   ├── OpenAiApi.kt          // Retrofit interface
│   │   ├── OpenAiModels.kt       // Request/response data classes
│   │   └── RetrofitInstance.kt   // Singleton client
│   ├── repository
│   │   ├── WorkoutRepository.kt
│   │   ├── MacroRepository.kt
│   │   ├── MentalHealthRepository.kt
│   │   └── AuthRepository.kt     // Firebase Auth wrapper
│   └── local
│       ├── PulseDatabase.kt      // Room database
│       ├── dao/
│       │   ├── WorkoutDao.kt
│       │   ├── MealLogDao.kt
│       │   └── CheckInDao.kt
│       └── entity/
│           ├── WorkoutEntity.kt
│           ├── MealLogEntity.kt
│           └── CheckInEntity.kt
├── domain
│   └── (optional use-case classes if you want cleaner separation)
├── ui
│   ├── auth/
│   │   ├── LoginScreen.kt
│   │   ├── SignUpScreen.kt
│   │   └── AuthViewModel.kt
│   ├── workout/
│   │   ├── WorkoutPlannerScreen.kt
│   │   └── WorkoutViewModel.kt
│   ├── macro/
│   │   ├── MacroDetectorScreen.kt
│   │   └── MacroViewModel.kt
│   ├── mentalhealth/
│   │   ├── MentalHealthScreen.kt
│   │   └── MentalHealthViewModel.kt
│   └── navigation/
│       └── PulseNavGraph.kt
└── MainActivity.kt
```

**Where Firebase and Room fit in:** Firebase Auth handles *who the user is* (sign up/login), Room stores *their data locally* (workout history, meal logs, check-ins), and each Room row is tagged with the Firebase `uid` so data stays separated per user even though it's stored on-device. OpenAI never sees the user's identity — you only send it the prompt content.

**Data flow (one-way):** `Screen (Compose UI)` → calls ViewModel function → ViewModel calls `Repository` → Repository calls `Retrofit API` → result flows back up as `StateFlow` → UI recomposes.

This one pattern powers all three features — you're just changing the prompt and the screen.

---

## 2. Gradle Setup

`app/build.gradle.kts`:

```kotlin
dependencies {
    // Retrofit + JSON
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ViewModel + Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Coil (for showing food photos in the macro detector)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Firebase Auth (use the BoM so versions stay in sync)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // Room (local storage for workout history, meal logs, check-ins)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
```

You'll also need the `com.google.gms.google-services` plugin for Firebase and `com.google.devtools.ksp` for Room — add both to your top-level `build.gradle.kts` `plugins {}` block and apply them in `app/build.gradle.kts`. Android Studio's "Firebase Assistant" (Tools > Firebase) will do most of this wiring for you automatically when you connect a Firebase project — it's the easiest path.

---

## 3. Securing Your API Key (do this first — examiners check this)

**Never hardcode the key in a `.kt` file**, especially since you're zipping and submitting your source code. Use `local.properties` (already gitignored by default in Android Studio):

`local.properties` (not committed to git):
```
OPENAI_API_KEY=sk-yourkeyhere
```

`app/build.gradle.kts` — expose it as `BuildConfig`:
```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

android {
    defaultConfig {
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties["OPENAI_API_KEY"]}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}
```

Now `BuildConfig.OPENAI_API_KEY` is available anywhere in your Kotlin code, and the actual key never appears in a file you hand in. Mention this explicitly in your report/appendix — tutors like seeing this.

---

## 4. Firebase Authentication (sign up / login)

1. In the Firebase console, create a project, add your Android app (use the applicationId from `build.gradle.kts`), download `google-services.json` and drop it in your `app/` folder.
2. Enable **Email/Password** sign-in under Authentication > Sign-in method (simplest for a student project; you can add Google Sign-In later if you want extra polish).

`data/repository/AuthRepository.kt`:
```kotlin
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun signUp(email: String, password: String): Result<String> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        Result.success(result.user!!.uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signIn(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        Result.success(result.user!!.uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun signOut() = auth.signOut()
}
```

`ui/auth/AuthViewModel.kt`:
```kotlin
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        repo.signUp(email, password)
            .onSuccess { _authState.value = AuthState.Success(it) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Sign up failed") }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        repo.signIn(email, password)
            .onSuccess { _authState.value = AuthState.Success(it) }
            .onFailure { _authState.value = AuthState.Error(it.message ?: "Sign in failed") }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val uid: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
```

Gate your `PulseNavGraph.kt` start destination on whether `FirebaseAuth.getInstance().currentUser` is null — send them to `LoginScreen` if not logged in, otherwise straight into the app.

---

## 5. Room — Local Storage

Room is a good fit here because your data (workout history, meal logs, mood check-ins) is personal, doesn't need to sync across devices for this assignment's scope, and Room gives you clean CLO2 material to show your tutor during code inspection.

**Entities** — each tagged with the Firebase `uid` so multiple users on the same device/emulator don't see each other's data:

`data/local/entity/WorkoutEntity.kt`:
```kotlin
@Entity(tableName = "workout_logs")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val goal: String,
    val planText: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

`data/local/entity/MealLogEntity.kt`:
```kotlin
@Entity(tableName = "meal_logs")
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val loggedAt: Long = System.currentTimeMillis()
)
```

`data/local/entity/CheckInEntity.kt`:
```kotlin
@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val moodRating: Int,
    val journalEntry: String,
    val aiReflection: String,
    val loggedAt: Long = System.currentTimeMillis()
)
```

**DAO example** (the other two follow the same shape):

`data/local/dao/MealLogDao.kt`:
```kotlin
@Dao
interface MealLogDao {
    @Insert
    suspend fun insert(meal: MealLogEntity)

    @Query("SELECT * FROM meal_logs WHERE userId = :userId ORDER BY loggedAt DESC")
    fun getMealsForUser(userId: String): Flow<List<MealLogEntity>>

    @Query("SELECT SUM(calories) FROM meal_logs WHERE userId = :userId AND loggedAt >= :sinceMillis")
    suspend fun getCaloriesSince(userId: String, sinceMillis: Long): Int?
}
```

**Database class:**

`data/local/PulseDatabase.kt`:
```kotlin
@Database(
    entities = [WorkoutEntity::class, MealLogEntity::class, CheckInEntity::class],
    version = 1
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun mealLogDao(): MealLogDao
    abstract fun checkInDao(): CheckInDao

    companion object {
        @Volatile private var INSTANCE: PulseDatabase? = null

        fun getDatabase(context: Context): PulseDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, PulseDatabase::class.java, "pulse_db")
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```

**Wiring it into a repository** — e.g. after the Macro Detector gets a result back from OpenAI, save it locally:

```kotlin
class MacroRepository(private val mealLogDao: MealLogDao) {
    suspend fun analyzeFood(imageBase64: String, userId: String): MacroResult {
        // ... same OpenAI call as before to get `result` ...
        mealLogDao.insert(
            MealLogEntity(
                userId = userId,
                foodName = result.foodName,
                calories = result.calories,
                proteinG = result.proteinG,
                carbsG = result.carbsG,
                fatG = result.fatG
            )
        )
        return result
    }
}
```

Same pattern applies to the Workout Planner (save the generated plan) and Mental Health Checker (save mood + reflection) — call `dao.insert(...)` right after you get the AI response back, before returning it to the ViewModel.

This also gives you an easy **history screen** for each feature (e.g. "past workout plans", "meal log this week", "mood over time") using `dao.getXForUser(userId).collectAsState()` in Compose — a nice, low-effort way to add depth to your final demo.

---

## 6. Retrofit Setup (shared by all 3 features)

`data/remote/OpenAiModels.kt`:
```kotlin
data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)

data class ChatMessage(
    val role: String,          // "system" | "user" | "assistant"
    val content: List<ContentPart>
)

// content can be plain text OR text+image (needed for the macro detector)
sealed class ContentPart {
    data class Text(val type: String = "text", val text: String) : ContentPart()
    data class ImageUrl(val type: String = "image_url", val image_url: ImageUrlData) : ContentPart()
}
data class ImageUrlData(val url: String) // can be a base64 data URL: "data:image/jpeg;base64,...."

data class ChatResponse(
    val choices: List<Choice>
)
data class Choice(
    val message: ResponseMessage
)
data class ResponseMessage(
    val role: String,
    val content: String
)
```

`data/remote/OpenAiApi.kt`:
```kotlin
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface OpenAiApi {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun getChatCompletion(@Body request: ChatRequest): ChatResponse
}
```

`data/remote/RetrofitInstance.kt`:
```kotlin
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.OPENAI_API_KEY}")
            .build()
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val api: OpenAiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAiApi::class.java)
    }
}
```

That's the entire "plumbing" — every feature below reuses this exact setup, only the prompt changes.

---

## 7. Feature 1 — Workout Planner (text in, text out)

`data/repository/WorkoutRepository.kt`:
```kotlin
class WorkoutRepository {
    suspend fun generatePlan(
        goal: String,          // e.g. "lose fat"
        level: String,         // e.g. "beginner"
        daysPerWeek: Int,
        equipment: String      // e.g. "bodyweight only"
    ): String {
        val systemPrompt = """
            You are a certified fitness coach. Generate a structured weekly workout plan.
            Return it as clean, numbered days with exercises, sets and reps.
            Keep it realistic and safe for the stated fitness level.
            Do not give medical advice; suggest consulting a doctor for injuries.
        """.trimIndent()

        val userPrompt = "Goal: $goal. Level: $level. Days available: $daysPerWeek. Equipment: $equipment."

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(ContentPart.Text(text = systemPrompt))),
                ChatMessage("user", listOf(ContentPart.Text(text = userPrompt)))
            )
        )
        val response = RetrofitInstance.api.getChatCompletion(request)
        return response.choices.first().message.content
    }
}
```

`ui/workout/WorkoutViewModel.kt`:
```kotlin
class WorkoutViewModel(
    private val repo: WorkoutRepository = WorkoutRepository()
) : ViewModel() {

    private val _plan = MutableStateFlow<String?>(null)
    val plan: StateFlow<String?> = _plan

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun requestPlan(goal: String, level: String, days: Int, equipment: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _plan.value = repo.generatePlan(goal, level, days, equipment)
            } catch (e: Exception) {
                _plan.value = "Error generating plan: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

The Compose screen just collects `plan` and `isLoading` as state and renders a form + result — straightforward once the ViewModel is done.

---

## 8. Feature 2 — Macro Detector (image in, structured data out)

This is your most impressive feature for the demo. The trick: send the photo as base64 and **force the model to reply in strict JSON** so you can parse it into a data class instead of freeform text.

```kotlin
data class MacroResult(
    val foodName: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int
)

class MacroRepository {
    suspend fun analyzeFood(imageBase64: String): MacroResult {
        val systemPrompt = """
            You are a nutrition estimation assistant. Look at the food photo and estimate
            its nutritional content for the visible portion.
            Respond with ONLY valid JSON, no markdown, no extra text, in this exact shape:
            {"foodName": "...", "calories": 0, "proteinG": 0, "carbsG": 0, "fatG": 0}
            These are estimates, not exact lab measurements.
        """.trimIndent()

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(ContentPart.Text(text = systemPrompt))),
                ChatMessage("user", listOf(
                    ContentPart.Text(text = "Estimate the macros in this meal."),
                    ContentPart.ImageUrl(image_url = ImageUrlData("data:image/jpeg;base64,$imageBase64"))
                ))
            )
        )
        val response = RetrofitInstance.api.getChatCompletion(request)
        val rawJson = response.choices.first().message.content
        return Gson().fromJson(rawJson, MacroResult::class.java)
    }
}
```

To turn a picked/captured photo into base64 in your ViewModel:
```kotlin
fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
    return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
}
```

**Important disclaimer to put in your app UI and report:** label results as "AI estimate, not medical/dietary advice." This matters both for academic integrity and because macro estimates from a photo are inherently approximate — you don't want the app implying clinical accuracy.

---

## 9. Feature 3 — Mental Health Checker (careful design required)

This is the feature where you should be most deliberate. Treat it as a **wellness check-in / mood journal with AI-generated reflective feedback** — not a diagnostic tool. This framing is both the responsible approach and the one your tutor will find easier to accept.

```kotlin
class MentalHealthRepository {
    suspend fun getReflection(journalEntry: String, moodRating: Int): String {
        val systemPrompt = """
            You are a supportive wellness check-in assistant, not a therapist.
            Read the user's mood rating (1-5) and journal entry.
            Respond with:
            1. A short empathetic acknowledgement (2-3 sentences).
            2. One gentle, practical suggestion (e.g. breathing exercise, short walk, journaling prompt).
            Never diagnose any condition. Never give medical or clinical advice.
            If the entry suggests crisis, self-harm, or severe distress, gently and clearly
            recommend they reach out to a trusted person or a professional/helpline,
            and keep your tone calm and non-alarming.
        """.trimIndent()

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(ContentPart.Text(text = systemPrompt))),
                ChatMessage("user", listOf(ContentPart.Text(
                    text = "Mood rating: $moodRating/5. Entry: $journalEntry"
                )))
            )
        )
        val response = RetrofitInstance.api.getChatCompletion(request)
        return response.choices.first().message.content
    }
}
```

**For the assignment write-up**, explicitly state your ethical guardrails — this is good CLO1/CLO3 material and shows maturity:
- Always visible disclaimer: "This is not a substitute for professional mental health support."
- No storage of journal entries sent to third-party servers beyond what's needed for the API call (mention this if you're asked about privacy).
- A visible "Get help now" button linking to a helpline resource, shown permanently, not just on trigger.

---

## 10. Suggested Build Order (fits your Week 6 prototype deadline)

1. **Week 1:** Navigation scaffold + static UI for all 3 screens (no AI yet) — enough for the prototype presentation slides on "Navigation structure" and "Screen design (draft)."
2. **Week 2:** Firebase project setup + Login/Sign-up screens wired to `AuthRepository`. Get this working early — every other feature depends on having a `userId` to tag data with.
3. **Week 3:** Wire up Retrofit + the Workout Planner (simplest, text-only) end to end, save results with Room.
4. **Week 4:** Macro Detector (image handling is the fiddliest part — budget extra time for camera/gallery picker permissions).
5. **Week 5:** Mental Health Checker + disclaimers/UX polish.
6. **Week 6:** Prototype presentation — you can demo Workout Planner + login live even if the other two are still mocked.
7. **Weeks 7–12:** Finish Room-backed history screens for meals/check-ins, error handling, loading states, final polish, APK build, Appendix docs.

---

## 11. Deliverables Checklist (from the brief)

- [ ] Prototype slides (≤10 slides): title, intro, problem/solution, navigation structure, draft screens, conclusion, Q&A
- [ ] Final slides: intro, live demo, code walkthrough for tutor
- [ ] Slide with all main app screens
- [ ] Appendix A–D (check with your tutor exactly what these need to contain — typically: user stories/requirements, wireframes, testing, individual contribution)
- [ ] Zipped source code
- [ ] APK build (`Build > Generate Signed Bundle / APK` in Android Studio)

---

## 12. A note on cost

At GPT-4o Mini rates, testing all three features a few hundred times during development will cost you well under $1–2 total — very manageable on a student budget. Just don't commit your API key to git, and consider setting a hard spend limit in your OpenAI account dashboard as a safety net.
