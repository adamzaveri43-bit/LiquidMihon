package mihon.source.magazines

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class IssuesTest {

    private fun millis(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun `groups stories into monthly, weekly and daily issues`() {
        val sunday = millis("2026-11-22T16:00:00Z")
        Issues.keyFor(sunday, IssuePeriod.MONTH) shouldBe "m2026-11"
        Issues.keyFor(sunday, IssuePeriod.WEEK) shouldBe "w2026-11-16"
        Issues.keyFor(sunday, IssuePeriod.DAY) shouldBe "d2026-11-22"
        // Monday starts a new week
        Issues.keyFor(millis("2026-11-23T00:30:00Z"), IssuePeriod.WEEK) shouldBe "w2026-11-23"
    }

    @Test
    fun `labels issues for people`() {
        Issues.label("m2026-11") shouldBe "November 2026"
        Issues.label("w2026-11-16") shouldBe "Week of 16 November 2026"
        Issues.label("d2026-11-22") shouldBe "Sunday 22 November 2026"
    }

    @Test
    fun `issue addresses carry their key and magazine`() {
        val publication = MagazineCategory.MENS_STYLE.publications.first { it.slug == "gq" }
        val url = Issues.chapterUrl("d2026-11-22", publication)
        Issues.isIssueUrl(url) shouldBe true
        Issues.keyOf(url) shouldBe "d2026-11-22"
        url.substringAfter('#') shouldBe "gq"
        Issues.isIssueUrl("https://www.gq.com/story/x#gq") shouldBe false
    }

    @Test
    fun `an issue's stories survive the round trip through its memo`() {
        val stories = listOf(
            ArticleRef("https://a.example/1", "First", 1L, "https://img.example/1.jpg"),
            ArticleRef("https://a.example/2", "Second", 2L, null),
        )
        Issues.decode(Issues.encode(stories)) shouldContainExactly stories
    }

    @Test
    fun `busier magazines get shorter issues`() {
        IssuePeriod.forRate(14.0) shouldBe IssuePeriod.MONTH
        IssuePeriod.forRate(150.0) shouldBe IssuePeriod.WEEK
        IssuePeriod.forRate(500.0) shouldBe IssuePeriod.DAY
        val custom = CustomPublications.create(
            "https://x.example/feed/",
            "X",
            "https://x.example/",
            null,
            IssuePeriod.WEEK,
        )
        CustomPublications.fromSlug(custom.slug)?.issues shouldBe IssuePeriod.WEEK
    }
}
