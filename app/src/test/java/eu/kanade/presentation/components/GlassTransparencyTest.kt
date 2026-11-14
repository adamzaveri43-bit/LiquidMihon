package eu.kanade.presentation.components

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.presentation.core.components.glass.glassAlpha

@Execution(ExecutionMode.CONCURRENT)
class GlassTransparencyTest {

    @Test
    fun `default transparency keeps the designed alpha`() {
        glassAlpha(0.42f, 0.5f) shouldBe (0.42f plusOrMinus 0.0001f)
    }

    @Test
    fun `no transparency is opaque`() {
        glassAlpha(0.42f, 0f) shouldBe (1f plusOrMinus 0.0001f)
    }

    @Test
    fun `full transparency is clear`() {
        glassAlpha(0.42f, 1f) shouldBe (0f plusOrMinus 0.0001f)
    }

    @Test
    fun `values in between move steadily`() {
        glassAlpha(0.4f, 0.25f) shouldBe (0.7f plusOrMinus 0.0001f)
        glassAlpha(0.4f, 0.75f) shouldBe (0.2f plusOrMinus 0.0001f)
    }

    @Test
    fun `out of range transparency is clamped`() {
        glassAlpha(0.4f, -1f) shouldBe (1f plusOrMinus 0.0001f)
        glassAlpha(0.4f, 2f) shouldBe (0f plusOrMinus 0.0001f)
    }
}
