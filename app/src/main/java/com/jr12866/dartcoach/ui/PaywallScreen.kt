package com.jr12866.dartcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The paywall.
 *
 * It appears at the moment the player tries to use something that is in Pro,
 * not on launch and not repeatedly. The free coach keeps working, so the
 * player is never locked out of the thing they came for.
 */
@Composable
fun PaywallScreen(
    price: String?,
    error: String?,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Darts Coach Pro",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText,
        )

        Text(
            text = "One payment. No subscription, no account, no ads.",
            fontSize = 16.sp,
            color = AccentAmber,
        )

        Spacer(modifier = Modifier.height(4.dp))

        ProFeature(
            title = "Your own numbers",
            body = "The coach works out your hit rates from your own play and " +
                "gives you the routes that suit you, rather than the ones " +
                "that suit everyone. You can also set any segment by hand.",
        )

        ProFeature(
            title = "Where your misses go",
            body = "The share of your darts that landed on the bed you were " +
                "aiming at, and which part of the board is costing you.",
        )

        ProFeature(
            title = "The out chart",
            body = "Every score from 2 to 170, with the route, the chance of " +
                "checking out, and the way onto each of your doubles.",
        )

        ProFeature(
            title = "Setup coaching",
            body = "What to aim at from 171 to 350, so the leg is set up " +
                "before the finish is in sight. Every score, what it " +
                "leaves, and what to do if the first dart drops.",
        )

        ProFeature(
            title = "Your doubles",
            body = "The double you finish on is remembered between legs, " +
                "so you do not have to say it every time.",
        )

        ProFeature(
            title = "Missed-target corrections",
            body = "Say what you actually hit when you miss the intended " +
                "dart, and the coach replaces it and re-plans the rest of " +
                "your turn as a checkout or a setup.",
        )

        ProFeature(
            title = "Your statistics",
            body = "Your average turn, the share of legs you have won, and " +
                "which part of the board is costing you the most.",
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = onPurchase,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = ScreenBackground,
            ),
        ) {
            Text(
                text = "Unlock Pro - ${price ?: "$2.99"}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (error != null) {
            Text(
                text = error,
                fontSize = 14.sp,
                color = AccentAmber,
            )
        }

        Text(
            text = "Payment is handled by Google Play. The app asks for " +
                "internet access to talk to the Play Store, and for nothing " +
                "else. Your microphone audio still never leaves the phone.",
            fontSize = 13.sp,
            color = QuietText,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            ) {
                Text("Restore", color = PrimaryText)
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Not now", color = PrimaryText)
            }
        }
    }
}

/** One thing Pro does, and why it is worth the money. */
@Composable
private fun ProFeature(title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "+",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AccentGreen,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
            )
            Text(
                text = body,
                fontSize = 14.sp,
                color = QuietText,
            )
        }
    }
}
