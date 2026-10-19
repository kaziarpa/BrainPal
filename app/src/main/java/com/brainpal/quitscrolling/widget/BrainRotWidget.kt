package com.brainpal.quitscrolling.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.brainpal.quitscrolling.data.local.BrainPalDatabase
import com.example.MainActivity
import java.time.LocalDate

class BrainRotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = BrainPalDatabase.getInstance(context)
        val today = LocalDate.now().toString()
        val todayCount = runCatching {
            db.scrollEventDao().getDailyCountOnce(today)
        }.getOrDefault(0)

        val mascotBitmap = MascotBitmapRenderer.renderMascotBitmap(
            reelCount = todayCount,
            sizePx = 220
        )

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF141418)))
                    .cornerRadius(24.dp)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(mascotBitmap),
                        contentDescription = "Brain State",
                        modifier = GlanceModifier.size(68.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "$todayCount",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Reels Today",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF9E9EA8)),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
