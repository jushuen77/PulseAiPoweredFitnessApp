package com.fitnessapp.pulse.data.model

data class QuestionnaireItem(
    val id: Int,
    val text: String,
    val isCrisisItem: Boolean = false // flags PHQ-9's item 9
)

// PHQ-9: over the last 2 weeks, how often bothered by...
val phq9Items = listOf(
    QuestionnaireItem(1, "Little interest or pleasure in doing things"),
    QuestionnaireItem(2, "Feeling down, depressed, or hopeless"),
    QuestionnaireItem(3, "Trouble falling or staying asleep, or sleeping too much"),
    QuestionnaireItem(4, "Feeling tired or having little energy"),
    QuestionnaireItem(5, "Poor appetite or overeating"),
    QuestionnaireItem(6, "Feeling bad about yourself, or that you're a failure"),
    QuestionnaireItem(7, "Trouble concentrating on things"),
    QuestionnaireItem(8, "Moving or speaking noticeably slower, or feeling restless/fidgety"),
    QuestionnaireItem(
        9,
        "Thoughts that you would be better off dead or of hurting yourself",
        isCrisisItem = true
    )
)
val phq9Options =
    listOf("Not at all", "Several days", "More than half the days", "Nearly every day")

// DASS-21: over the past week, how much each statement applied to you
val dassDepressionItems = listOf(
    QuestionnaireItem(1, "I couldn't seem to experience any positive feeling at all"),
    QuestionnaireItem(2, "I found it difficult to work up the initiative to do things"),
    QuestionnaireItem(3, "I felt that I had nothing to look forward to"),
    QuestionnaireItem(4, "I felt down-hearted and blue"),
    QuestionnaireItem(5, "I was unable to become enthusiastic about anything"),
    QuestionnaireItem(6, "I felt I wasn't worth much as a person"),
    QuestionnaireItem(7, "I felt that life was meaningless")
)
val dassAnxietyItems = listOf(
    QuestionnaireItem(8, "I was aware of dryness of my mouth"),
    QuestionnaireItem(
        9,
        "I experienced breathing difficulty (e.g. excessively rapid breathing, breathlessness in the absence of physical exertion)"
    ),
    QuestionnaireItem(10, "I experienced trembling (e.g. in the hands)"),
    QuestionnaireItem(
        11,
        "I was worried about situations in which I might panic and make a fool of myself"
    ),
    QuestionnaireItem(12, "I felt I was close to panic"),
    QuestionnaireItem(
        13,
        "I was aware of the action of my heart in the absence of physical exertion"
    ),
    QuestionnaireItem(14, "I felt scared without any good reason")
)
val dassStressItems = listOf(
    QuestionnaireItem(15, "I found it hard to wind down"),
    QuestionnaireItem(16, "I tended to over-react to situations"),
    QuestionnaireItem(17, "I felt that I was using a lot of nervous energy"),
    QuestionnaireItem(18, "I found myself getting agitated"),
    QuestionnaireItem(19, "I found it difficult to relax"),
    QuestionnaireItem(
        20,
        "I was intolerant of anything that kept me from getting on with what I was doing"
    ),
    QuestionnaireItem(21, "I felt that I was rather touchy")
)
val dassOptions =
    listOf("Did not apply to me", "Applied to some degree", "Applied a lot", "Applied very much")