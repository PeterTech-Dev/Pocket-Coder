package com.example.aiassistantcoder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


object HomeCompose {

    // Java-friendly: we accept a Runnable instead of a Kotlin function type
    fun setupButton(composeView: ComposeView, onClick: Runnable) {
        composeView.setContent {
            MaterialTheme {
                GenerateCodeButton(onClick)
            }
        }
    }

    fun setupHero(composeView: ComposeView) {
        composeView.setContent {
            MaterialTheme {
                MaskedHeroImage()
            }
        }
    }

    @Composable
    fun GenerateCodeButton(onClick: Runnable) {
        val shape = RoundedCornerShape(24.dp)

        // Interaction tracking
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        // Animated scale on press
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            label = "scale"
        )

        // Colors
        val normalGradient = Brush.linearGradient(
            listOf(
                Color(0xFF4F5BFF),
                Color(0xFF8F43FF)
            )
        )
        Brush.linearGradient(
            listOf(
                Color(0xFF3A47D6),
                Color(0xFF6C32D6)
            )
        )
        // Gradient border for pressed state
        val gradientBorder = Brush.linearGradient(
            listOf(
                Color(0xFF4F5BFF),
                Color(0xFF8F43FF)
            )
        )


        // Shadow adjustments
        val dropShadowAlpha = if (isPressed) 0.20f else 0.45f
        val whiteInnerAlpha = if (isPressed) 0.25f else 0.55f

        Button(
            onClick = { onClick.run() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                // Drop shadow
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = if (isPressed) 6.dp else 14.dp,
                        spread = 0.dp,
                        color = Color.Black.copy(alpha = dropShadowAlpha),
                        offset = DpOffset(5.dp, 5.dp)
                    )
                )
                // Background OR outline depending on press state
                .then(
                    if (!isPressed) {
                        Modifier.background(
                            brush = normalGradient,
                            shape = shape
                        )
                    } else {
                        Modifier.border(
                            width = 2.dp,
                            brush = gradientBorder,
                            shape = shape
                        )
                    }
                )
                // Inner white highlight
                .innerShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 2.dp,
                        spread = 0.dp,
                        color = Color.White.copy(alpha = whiteInnerAlpha),
                        offset = DpOffset(0.dp, 2.dp)
                    )
                ),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            interactionSource = interactionSource,
            shape = shape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Generate Code",
                    fontSize = 17.sp,
                    color = Color.White
                )
            }
        }
    }


    @Composable
    fun MaskedHeroImage(
        fadeOffsetFromBottom: Dp = 40.dp // how much from the bottom we start fading
    ) {
        Image(
            painter = painterResource(id = R.drawable.ai_robot),
            contentDescription = null,
            contentScale = ContentScale.Fit,   // 👈 show whole image
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)               // tweak this as you like
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    // 1) Draw original image
                    drawContent()

                    // 2) Compute where fade should start, measured from the *bottom*,
                    //    so it behaves nicely no matter what height you choose
                    val fadeHeightPx = fadeOffsetFromBottom.toPx()
                    val maskTop = size.height - fadeHeightPx

                    val maskBrush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1.0f to Color.Black
                        )
                    )

                    // 3) Apply the mask only in the bottom strip
                    clipRect(
                        left = 0f,
                        top = maskTop.coerceAtLeast(0f),
                        right = size.width,
                        bottom = size.height
                    ) {
                        drawRect(
                            brush = maskBrush,
                            blendMode = BlendMode.DstOut
                        )
                    }
                }
        )
    }
}

// ---------- PREVIEWS ----------

@Preview(
    name = "Generate Code Button",
    showBackground = true,
    backgroundColor = 0xFF101020
)
@Composable
fun GenerateCodeButtonPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            HomeCompose.GenerateCodeButton(onClick = Runnable { })
        }
    }
}

@Preview(
    name = "Hero Image (Masked)",
    showBackground = true,
    backgroundColor = 0xFF101020
)
@Composable
fun HeroImagePreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            HomeCompose.MaskedHeroImage()
        }
    }
}
