package com.fitnessapp.pulse.data.repository

import com.fitnessapp.pulse.data.remote.ChatMessage
import com.fitnessapp.pulse.data.remote.ChatRequest
import com.fitnessapp.pulse.data.remote.ContentPart
import com.fitnessapp.pulse.data.remote.ImageUrlData
import com.fitnessapp.pulse.data.remote.RetrofitInstance
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.MealRow
import com.google.gson.Gson
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

private data class MacroResult(
    val foodName: String,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double
)

@Serializable
private data class MealUpdate(
    @SerialName("food_name") val foodName: String,
    val calories: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int
)

class MacroRepository {
    private val photoBucket = "meal-photos"

    private suspend fun uploadMealPhoto(userId: String, imageBytes: ByteArray): String {
        val path = "$userId/${UUID.randomUUID()}.jpg"
        SupabaseInstance.client.storage.from(photoBucket).upload(path, imageBytes)
        return path
    }

    suspend fun getMealHistory(userId: String): List<MealRow> {
        return SupabaseInstance.client.from("meal_logs")
            .select {
                filter { eq("user_id", userId) }
                order("logged_at", Order.DESCENDING)
            }
            .decodeList<MealRow>()
    }


    /** Estimates macros from a food photo without persisting anything; the caller saves it explicitly via [savePhotoMeal]. */
    suspend fun estimateFood(imageBase64: String, userId: String): MealRow {
        val systemPrompt = """
            You are a nutrition estimation assistant. Look at the food photo and estimate
            its nutritional content for the visible portion.
            Respond with ONLY valid JSON, no markdown, no extra text, in this exact shape:
            {"foodName": "...", "calories": 0, "proteinG": 0, "carbsG": 0, "fatG": 0}
            These are estimates, not exact lab measurements.
        """.trimIndent()

        val request = ChatRequest(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentPart.Text(text = "Estimate the macros in this meal."),
                        ContentPart.ImageUrl(image_url = ImageUrlData("data:image/jpeg;base64,$imageBase64"))
                    )
                )
            )
        )

        val response = RetrofitInstance.api.getChatCompletion(request)
        val rawJson = response.choices.first().message.content ?: "{}"
        val result = Gson().fromJson(rawJson, MacroResult::class.java)

        return MealRow(
            userId = userId,
            foodName = result.foodName,
            calories = result.calories.roundToInt(),
            proteinG = result.proteinG.roundToInt(),
            carbsG = result.carbsG.roundToInt(),
            fatG = result.fatG.roundToInt()
        )
    }

    /** Uploads [imageBytes] then inserts a not-yet-saved photo [meal] (id == null) into history. */
    suspend fun savePhotoMeal(meal: MealRow, userId: String, imageBytes: ByteArray): MealRow {
        val photoPath = uploadMealPhoto(userId, imageBytes)
        return SupabaseInstance.client.from("meal_logs")
            .insert(meal.copy(photoPath = photoPath)) { select() }
            .decodeSingle<MealRow>()
    }

    /** Estimates macros from a text description without persisting anything; the caller saves it explicitly via [saveMeal]. */
    suspend fun estimateFoodText(description: String, userId: String): MealRow {
        val systemPrompt = """
        You are a nutrition estimation assistant. Estimate nutritional content
        based on the food description given.
        Respond with ONLY valid JSON, no markdown, no extra text, in this exact shape:
        {"foodName": "...", "calories": 0, "proteinG": 0, "carbsG": 0, "fatG": 0}
        These are estimates, not exact lab measurements.
    """.trimIndent()

        val request = ChatRequest(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = description)
            )
        )

        val response = RetrofitInstance.api.getChatCompletion(request)
        val rawJson = response.choices.first().message.content ?: "{}"
        val result = Gson().fromJson(rawJson, MacroResult::class.java)

        return MealRow(
            userId = userId,
            foodName = result.foodName,
            calories = result.calories.roundToInt(),
            proteinG = result.proteinG.roundToInt(),
            carbsG = result.carbsG.roundToInt(),
            fatG = result.fatG.roundToInt()
        )
    }

    /** Inserts a not-yet-saved [meal] (id == null) into history. */
    suspend fun saveMeal(meal: MealRow): MealRow {
        return SupabaseInstance.client.from("meal_logs").insert(meal) { select() }.decodeSingle<MealRow>()
    }

    suspend fun getMealById(id: Long): MealRow? {
        return SupabaseInstance.client.from("meal_logs")
            .select { filter { eq("id", id) } }
            .decodeList<MealRow>()
            .firstOrNull()
    }

    suspend fun updateMeal(meal: MealRow): MealRow {
        val payload = MealUpdate(
            foodName = meal.foodName,
            calories = meal.calories,
            proteinG = meal.proteinG,
            carbsG = meal.carbsG,
            fatG = meal.fatG
        )
        return SupabaseInstance.client.from("meal_logs")
            .update(payload) {
                filter {
                    eq("id", meal.id!!)
                    eq("user_id", meal.userId)
                }
                select()
            }
            .decodeSingle<MealRow>()
    }

    suspend fun deleteMeal(meal: MealRow) {
        SupabaseInstance.client.from("meal_logs")
            .delete { filter { eq("id", meal.id!!) } }
        meal.photoPath?.let { path ->
            runCatching { SupabaseInstance.client.storage.from(photoBucket).delete(path) }
        }
    }

    fun mealPhotoUrl(path: String): String =
        SupabaseInstance.client.storage.from(photoBucket).publicUrl(path)
}