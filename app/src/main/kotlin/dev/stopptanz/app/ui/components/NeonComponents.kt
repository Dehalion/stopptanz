package dev.stopptanz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stopptanz.app.ui.theme.NeonAccentButtonBrush
import dev.stopptanz.app.ui.theme.NeonCardBorder
import dev.stopptanz.app.ui.theme.NeonCardFill
import dev.stopptanz.app.ui.theme.NeonLabelColor
import dev.stopptanz.app.ui.theme.NeonPrimaryButtonBrush
import dev.stopptanz.app.ui.theme.NeonTextPrimary
import dev.stopptanz.app.ui.theme.NeonTextSecondary
import dev.stopptanz.app.ui.theme.NeonTimerBrush
import dev.stopptanz.app.ui.theme.NeonTitleBrush

@Composable
fun NeonTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = NeonTitleBrush,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
fun NeonLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = NeonLabelColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
fun NeonValue(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = NeonTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun NeonSubtext(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = NeonTextSecondary, fontSize = 13.sp)
}

@Composable
fun NeonTimerText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = NeonTimerBrush,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
fun NeonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(NeonCardFill, RoundedCornerShape(20.dp))
            .border(1.dp, NeonCardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun NeonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = NeonPrimaryButtonBrush,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().background(brush, RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = NeonTextPrimary),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled,
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun NeonAccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    NeonPrimaryButton(text, onClick, modifier, brush = NeonAccentButtonBrush)
}

@Composable
fun NeonOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    if (active) {
        NeonAccentButton(text, onClick, modifier)
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = NeonCardFill, contentColor = NeonTextPrimary),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCardBorder),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
