package mihon.feature.reviews

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class AniListTextTest {

    @Test
    fun `Strips embedded media, layout markers and html`() {
        val text = "~~~img220(https://example.com/a.png)~~~\nGreat<br>art &amp; story<i>!</i>\n\n\n\nThe end"

        cleanAniListText(text) shouldBe "Great\nart & story!\n\nThe end"
    }

    @Test
    fun `Hides spoilers unless shown`() {
        val cleaned = cleanAniListText("Before ~!the hero dies!~ after")

        cleaned.withSpoilers(show = false) shouldBe "Before *[spoiler hidden]* after"
        cleaned.withSpoilers(show = true) shouldBe "Before the hero dies after"
    }
}
