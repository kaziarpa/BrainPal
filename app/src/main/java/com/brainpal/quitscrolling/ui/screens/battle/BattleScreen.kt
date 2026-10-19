package com.brainpal.quitscrolling.ui.screens.battle

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brainpal.quitscrolling.ui.components.BrainMascotCanvas
import com.brainpal.quitscrolling.ui.theme.AccentGold
import com.brainpal.quitscrolling.ui.theme.BadgeTextRed
import com.brainpal.quitscrolling.ui.theme.ButtonDarkText
import com.brainpal.quitscrolling.ui.theme.CardBorder
import com.brainpal.quitscrolling.ui.theme.CardSurface
import com.brainpal.quitscrolling.ui.theme.CardSurfaceElevated
import com.brainpal.quitscrolling.ui.theme.ObsidianBackground
import com.brainpal.quitscrolling.ui.theme.StatusActiveGreen
import com.brainpal.quitscrolling.ui.theme.TextPrimaryWhite
import com.brainpal.quitscrolling.ui.theme.TextSecondaryMuted
import com.brainpal.quitscrolling.ui.theme.WarmGoldTitle

@Composable
fun BattleScreen(
    viewModel: BattleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showChallengeDialog by remember { mutableStateOf(false) }
    var importCodeInput by remember { mutableStateOf("") }
    var customRivalName by remember { mutableStateOf(uiState.rivalName) }
    var customRivalCountText by remember { mutableStateOf(uiState.rivalCount.toString()) }
    var importErrorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header (matches 0:22-0:23 in video)
        Text(
            text = "Pick your scroll rival",
            color = Color(0xFFB3A68E),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "CHALLENGE\nFRIENDS",
            color = WarmGoldTitle,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("battle_header_title")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live Leaderboard Rule Banner
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = if (uiState.isUserWinning) StatusActiveGreen else AccentGold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isUserWinning) {
                    "You're winning! (${uiState.rivalCount - uiState.userTodayCount} fewer reels scrolled)"
                } else {
                    "${uiState.rivalName} is winning by ${uiState.userTodayCount - uiState.rivalCount} fewer reels!"
                },
                color = if (uiState.isUserWinning) StatusActiveGreen else AccentGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 1v1 Split VS Arena with Diagonal Golden Lightning Divider Slash
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A161F),
                            Color(0xFF121218),
                            Color(0xFF23181B)
                        )
                    )
                )
                .border(1.5.dp, AccentGold.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .testTag("battle_vs_arena")
        ) {
            // Diagonal Golden Lightning Slash Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Subtle split background tint on right side
                val rightHalfPath = Path().apply {
                    moveTo(w * 0.58f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(w * 0.42f, h)
                    close()
                }
                drawPath(
                    path = rightHalfPath,
                    color = Color(0xFF2B181E).copy(alpha = 0.45f)
                )

                // Golden lightning bolt slash across the center
                val slashGlowPath = Path().apply {
                    moveTo(w * 0.58f, 0f)
                    lineTo(w * 0.49f, h * 0.46f)
                    lineTo(w * 0.55f, h * 0.49f)
                    lineTo(w * 0.42f, h)
                }
                drawPath(
                    path = slashGlowPath,
                    color = AccentGold.copy(alpha = 0.28f),
                    style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = slashGlowPath,
                    color = WarmGoldTitle,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Left Contender (YOU) & Right Contender (FRIEND)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Contender: YOU
                ContenderColumn(
                    label = uiState.userNickname,
                    reelCount = uiState.userTodayCount,
                    avatarPrimaryColor = Color(0xFF5CE1E6),
                    avatarSecondaryColor = Color(0xFF2A4B7C),
                    isWinner = uiState.isUserWinning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contender_you_column")
                )

                // Center VS Emblem
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background( Color(0xFF1F1912))
                        .border(2.dp, WarmGoldTitle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = WarmGoldTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Right Contender: FRIEND
                ContenderColumn(
                    label = uiState.rivalName,
                    reelCount = uiState.rivalCount,
                    avatarPrimaryColor = Color(0xFFFF7B54),
                    avatarSecondaryColor = Color(0xFF6B2330),
                    isWinner = !uiState.isUserWinning,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("contender_friend_column")
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Pacemaker Rival Presets
        Text(
            text = "Quick Select Rival Pacemaker:",
            color = TextSecondaryMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val rivals = listOf(
                Triple("ZEN MONK", 18, "Hard Mode"),
                Triple("ALEX", 64, "Balanced"),
                Triple("DOOM MAX", 165, "Heavy Scroller")
            )
            rivals.forEach { (rName, rCount, rTier) ->
                val isSelected = uiState.rivalName == rName && uiState.rivalCount == rCount
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) CardSurfaceElevated else CardSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentGold else CardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.setCustomRival(rName, rCount) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = rName,
                        color = if (isSelected) WarmGoldTitle else TextPrimaryWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "$rCount reels",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = rTier,
                        color = TextSecondaryMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary CTA Button: "⚔️ CHALLENGE YOUR FRIEND" (background = #D9BF93, text color #1A140B)
        Button(
            onClick = {
                customRivalName = uiState.rivalName
                customRivalCountText = uiState.rivalCount.toString()
                importErrorText = null
                showChallengeDialog = true
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD9BF93),
                contentColor = ButtonDarkText
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("challenge_friend_cta_button")
        ) {
            Text(
                text = "⚔️  CHALLENGE YOUR FRIEND",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
        }

        uiState.statusBannerMessage?.let { msg ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = msg,
                color = StatusActiveGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showChallengeDialog) {
        val myBattleCode = remember(uiState.userNickname, uiState.userTodayCount) {
            viewModel.generateShareBattleCode()
        }

        AlertDialog(
            onDismissRequest = { showChallengeDialog = false },
            containerColor = CardSurface,
            titleContentColor = TextPrimaryWhite,
            textContentColor = TextSecondaryMuted,
            title = {
                Text(
                    text = "⚔️ 1v1 Scroll Battle Sync",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "1. Share your live Battle Code with a friend so they can race against your daily reel count:",
                        color = TextSecondaryMuted,
                        fontSize = 13.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBackground)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = myBattleCode,
                            color = WarmGoldTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, viewModel.generateShareMessage())
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Challenge Friend on BrainPal")
                            runCatching { context.startActivity(shareIntent) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD9BF93),
                            contentColor = ButtonDarkText
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_share_invite_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share Battle Invite",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "2. Or paste a Friend's Battle Code:",
                        color = TextSecondaryMuted,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = importCodeInput,
                        onValueChange = {
                            importCodeInput = it
                            importErrorText = null
                        },
                        placeholder = { Text("Paste BRAINPAL-... code", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimaryWhite,
                            unfocusedTextColor = TextPrimaryWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_battle_code_input")
                    )

                    if (importErrorText != null) {
                        Text(
                            text = importErrorText!!,
                            color = BadgeTextRed,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val ok = viewModel.importFriendBattleCode(importCodeInput)
                            if (ok) {
                                importCodeInput = ""
                                showChallengeDialog = false
                            } else {
                                importErrorText = "Invalid Battle Code format. Try pasting a valid BRAINPAL-... code."
                            }
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_code_confirm_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import Friend's Code",
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "3. Or set custom Friend Name & Reel Count:",
                        color = TextSecondaryMuted,
                        fontSize = 13.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customRivalName,
                            onValueChange = { customRivalName = it },
                            label = { Text("Friend", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimaryWhite,
                                unfocusedTextColor = TextPrimaryWhite
                            ),
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = customRivalCountText,
                            onValueChange = { customRivalCountText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Reels", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextPrimaryWhite,
                                unfocusedTextColor = TextPrimaryWhite
                            ),
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val cnt = customRivalCountText.toIntOrNull() ?: 50
                            viewModel.setCustomRival(customRivalName, cnt)
                            showChallengeDialog = false
                        },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Custom Rival",
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChallengeDialog = false }) {
                    Text("Close", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ContenderColumn(
    label: String,
    reelCount: Int,
    avatarPrimaryColor: Color,
    avatarSecondaryColor: Color,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Floating Mini Brain Mascot above contender
        Box(
            modifier = Modifier
                .size(64.dp)
                .offset(y = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BrainMascotCanvas(
                reelCount = reelCount,
                compactMode = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Stylized Character Bust Avatar
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f

                // Outer ring glow
                drawCircle(
                    color = if (isWinner) StatusActiveGreen.copy(alpha = 0.22f) else avatarPrimaryColor.copy(alpha = 0.16f),
                    radius = w * 0.46f,
                    center = Offset(cx, h * 0.52f)
                )

                // Stylized hoodie shoulders
                val shouldersPath = Path().apply {
                    moveTo(w * 0.16f, h * 0.90f)
                    cubicTo(
                        w * 0.20f, h * 0.62f,
                        w * 0.80f, h * 0.62f,
                        w * 0.84f, h * 0.90f
                    )
                    close()
                }
                drawPath(
                    path = shouldersPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(avatarPrimaryColor, avatarSecondaryColor)
                    ),
                    style = Fill
                )

                // Character head silhouette
                drawCircle(
                    color = Color(0xFFF3D6C6),
                    radius = w * 0.20f,
                    center = Offset(cx, h * 0.44f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Gold Pill Badge with reelCount
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4A371C), Color(0xFF2E2212))
                    )
                )
                .border(1.5.dp, WarmGoldTitle, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            BrainMascotCanvas(
                reelCount = reelCount,
                compactMode = true,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$reelCount",
                color = WarmGoldTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contender Name ("YOU" / "FRIEND")
        Text(
            text = label,
            color = TextPrimaryWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}
