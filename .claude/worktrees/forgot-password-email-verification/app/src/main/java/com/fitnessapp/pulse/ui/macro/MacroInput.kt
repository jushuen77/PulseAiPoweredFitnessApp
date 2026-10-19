package com.fitnessapp.pulse.ui.macro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.fitnessapp.pulse.ui.theme.Ember

/** Highest value allowed for a single macro field (calories/protein/carbs/fat) — keeps input and display sane. */
const val MAX_MACRO_VALUE = 99_999

/** Formats a macro number with thousand separators, e.g. 1234 -> "1,234". */
fun Int.formatMacro(): String = "%,d".format(this)

/** Null for a valid 0..MAX_MACRO_VALUE number; an error message for blank, non-numeric, or out-of-range text. */
fun macroFieldError(text: String): String? {
    if (text.isBlank()) return "Enter a number (0 if none)"
    val value = text.toIntOrNull()
    return if (value == null || value !in 0..MAX_MACRO_VALUE) {
        "Enter a number between 0 and ${MAX_MACRO_VALUE.formatMacro()}"
    } else null
}

/** Parses a macro field, falling back to [current] when blank, invalid, or out of range. */
fun parseMacroField(text: String, current: Int): Int =
    text.toIntOrNull()?.takeIf { it in 0..MAX_MACRO_VALUE } ?: current

/** Displays a digits-only field with thousand separators (e.g. "1234" -> "1,234") as the user types, without altering the underlying raw value. */
private class ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formatted = digits.reversed().chunked(3).joinToString(",").reversed()
        val totalCommas = (digits.length - 1) / 3

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, digits.length)
                val remaining = digits.length - clamped
                val commasToRight = if (remaining > 0) (remaining - 1) / 3 else 0
                return clamped + (totalCommas - commasToRight)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                return clamped - formatted.take(clamped).count { it == ',' }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/** A calories/protein/carbs/fat input: digits only (negative signs can't be typed), with a max-value error message. */
@Composable
fun MacroNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier
) {
    val error = macroFieldError(value)
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit)) },
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            visualTransformation = ThousandsSeparatorTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = Ember, style = MaterialTheme.typography.labelSmall) }
    }
}
