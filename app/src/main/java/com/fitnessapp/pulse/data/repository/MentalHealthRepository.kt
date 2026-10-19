//package com.fitnessapp.pulse.data.repository
//
//import com.fitnessapp.pulse.data.local.dao.CheckInDao
//import com.fitnessapp.pulse.data.local.entity.CheckInEntity
//import com.fitnessapp.pulse.data.remote.ChatMessage
//import com.fitnessapp.pulse.data.remote.ChatRequest
//import com.fitnessapp.pulse.data.remote.RetrofitInstance
//import kotlinx.coroutines.flow.Flow
//
//class MentalHealthRepository(
//    private val checkInDao: CheckInDao
//) {
//    fun getCheckInHistory(userId: String): Flow<List<CheckInEntity>> =
//        checkInDao.getCheckInsForUser(userId)
//
//    suspend fun getReflection(journalEntry: String, moodRating: Int, userId: String): String {
//        val systemPrompt = """
//            You are a supportive wellness check-in assistant, not a therapist.
//            Read the user's mood rating (1-5) and journal entry.
//            Respond with:
//            1. A short empathetic acknowledgement (2-3 sentences).
//            2. One gentle, practical suggestion (e.g. breathing exercise, short walk, journaling prompt).
//            Never diagnose any condition. Never give medical or clinical advice.
//            If the entry suggests crisis, self-harm, or severe distress, gently and clearly
//            recommend they reach out to a trusted person or a professional/helpline,
//            and keep your tone calm and non-alarming.
//        """.trimIndent()
//
//        val request = ChatRequest(
//            messages = listOf(
//                ChatMessage(role = "system", content = systemPrompt),
//                ChatMessage(role = "user", content = "Mood rating: $moodRating/5. Entry: $journalEntry")
//            )
//        )
//
//        val response = RetrofitInstance.api.getChatCompletion(request)
//        val reflection = response.choices.first().message.content
//
//        checkInDao.insert(
//            CheckInEntity(
//                userId = userId,
//                moodRating = moodRating,
//                journalEntry = journalEntry,
//                aiReflection = reflection
//            )
//        )
//
//        return reflection
//    }
//}