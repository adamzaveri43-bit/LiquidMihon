package mihon.source.magazines

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

class CustomMagazinesTest {

    @Test
    fun `turns typed addresses into URLs and ignores ordinary searches`() {
        CustomMagazines.toAddress("monocle.com").toString() shouldBe "https://monocle.com/"
        CustomMagazines.toAddress("  https://www.example.com/feed/ ").toString() shouldBe
            "https://www.example.com/feed/"
        CustomMagazines.toAddress("concept cars").shouldBeNull()
        CustomMagazines.toAddress("gq").shouldBeNull()
        CustomMagazines.toAddress("ftp://example.com").shouldBeNull()
    }

    @Test
    fun `picks the part of a page title that names the site`() {
        CustomMagazines.cleanName("Latest News | Monocle", "monocle.com") shouldBe "Monocle"
        CustomMagazines.cleanName("Robb Report - Luxury Lifestyle", "robbreport.com") shouldBe "Robb Report"
        CustomMagazines.cleanName("Home - Kinfolk", "kinfolk.com") shouldBe "Kinfolk"
        CustomMagazines.cleanName("Some Magazine", "other.com") shouldBe "Some Magazine"
        CustomMagazines.cleanName("  ", "x.com").shouldBeNull()
    }

    @Test
    fun `custom publications survive the round trip through their slug`() {
        val publication = CustomPublications.create(
            feed = "https://monocle.com/feed/",
            name = "Monocle",
            homepage = "https://monocle.com/",
            tagline = "Global affairs, business, culture and design.",
        )
        publication.slug shouldStartWith CustomPublications.PREFIX

        val restored = CustomPublications.fromSlug(publication.slug).shouldNotBeNull()
        restored shouldBe publication

        CustomPublications.fromSlug("gq").shouldBeNull()
        CustomPublications.fromSlug("custom/!!!").shouldBeNull()
    }

    @Test
    fun `finds advertised feeds and reads what a feed says about itself`() {
        val html = """
            <html><head>
              <link rel="alternate" type="application/rss+xml" title="Monocle" href="/feed/">
              <link rel="alternate" type="application/rss+xml" title="Comments Feed" href="/comments/feed/">
              <link rel="alternate" hreflang="fr" href="/fr/">
            </head></html>
        """.trimIndent()
        ArticleExtractor.feedLinksIn(html, "https://monocle.com/") shouldBe listOf("https://monocle.com/feed/")

        val feed = """
            <rss version="2.0"><channel>
              <title>Monocle</title>
              <link>https://monocle.com</link>
              <description>Global affairs and design.</description>
              <item><title>Story</title><link>https://monocle.com/story</link></item>
            </channel></rss>
        """.trimIndent()
        val info = ArticleExtractor.parseFeedInfo(feed, "https://monocle.com/feed/").shouldNotBeNull()
        info.title shouldBe "Monocle"
        info.homepage shouldBe "https://monocle.com"
        info.description shouldBe "Global affairs and design."

        ArticleExtractor.parseFeedInfo("<html><body>Not a feed</body></html>", "https://x.com/").shouldBeNull()
    }
}
