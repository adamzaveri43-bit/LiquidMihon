package mihon.source.magazines

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class ArticleExtractorTest {

    @Test
    fun `reads RSS items with full content, media images and dates`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
                 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Cars</title>
                <item>
                  <title>The new &amp; improved concept</title>
                  <link>https://cars.example/concept</link>
                  <pubDate>Sun, 22 Nov 2026 16:00:00 +0000</pubDate>
                  <dc:creator>staff@cars.example (Jane Doe)</dc:creator>
                  <media:content url="https://img.example/lead.jpg" medium="image"/>
                  <description>Short summary</description>
                  <content:encoded><![CDATA[<p>First paragraph of the story.</p><p>Second.</p>]]></content:encoded>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val item = ArticleExtractor.parseFeed(xml, "https://cars.example/feed").single()

        item.title shouldBe "The new & improved concept"
        item.link shouldBe "https://cars.example/concept"
        item.date shouldBe 1795363200000L
        item.author shouldBe "Jane Doe"
        item.imageUrl shouldBe "https://img.example/lead.jpg"
        item.contentHtml.shouldNotBeNull() shouldContain "First paragraph"
    }

    @Test
    fun `reads Atom entries, including namespaced dates`() {
        val xml = """
            <rss xmlns:a10="http://www.w3.org/2005/Atom"><channel>
              <item><title>Roundup</title><link>https://d.example/a</link>
                <a10:updated>2026-11-20T11:00:00-05:00</a10:updated></item>
            </channel></rss>
        """.trimIndent()
        ArticleExtractor.parseFeed(xml, "https://d.example/").single().date shouldBe 1795190400000L

        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Atom story</title>
                <link rel="alternate" href="https://atom.example/story"/>
                <published>2026-11-22T10:00:00Z</published>
                <author><name>Sam</name></author>
                <content type="html">&lt;p&gt;Body&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()
        val entry = ArticleExtractor.parseFeed(atom, "https://atom.example/").single()
        entry.link shouldBe "https://atom.example/story"
        entry.author shouldBe "Sam"
        entry.contentHtml.shouldNotBeNull() shouldContain "<p>Body</p>"
    }

    @Test
    fun `keeps text and pictures in reading order and drops boilerplate`() {
        val html = """
            <div>
              <p>Our product picks are editor-tested, expert-approved. We may earn a commission.</p>
              <h2>The design</h2>
              <p>The concept has a long, low nose.</p>
              <figure><img src="https://img.example/side.jpg" width="1200"><figcaption>Side view</figcaption></figure>
              <img src="https://img.example/logo.png">
              <img src="https://img.example/tiny.jpg" width="40">
              <ul><li>500 hp</li><li>Rear-wheel drive</li></ul>
              <blockquote>It drives like nothing else.</blockquote>
              <p>Read the full article</p>
            </div>
        """.trimIndent()
        val blocks = ArticleExtractor.blocksOf(org.jsoup.Jsoup.parseBodyFragment(html).body())

        blocks shouldContainExactly listOf(
            ArticleBlock.Heading("The design"),
            ArticleBlock.Paragraph("The concept has a long, low nose."),
            ArticleBlock.Image("https://img.example/side.jpg"),
            ArticleBlock.Caption("Side view"),
            ArticleBlock.ListItem("500 hp"),
            ArticleBlock.ListItem("Rear-wheel drive"),
            ArticleBlock.Quote("It drives like nothing else."),
        )
    }

    @Test
    fun `finds the article body on a web page and ignores navigation`() {
        val body = (1..6).joinToString("") { "<p>Paragraph $it of the review, long enough to count as body text.</p>" }
        val html = """
            <html><head>
              <meta property="og:image" content="https://img.example/og.jpg">
              <meta property="og:title" content="Page title">
            </head><body>
              <nav><p>Home, News, Reviews and every other section of the site in one long line.</p></nav>
              <div class="sidebar"><p>Most popular stories this week, which should not be in the article.</p></div>
              <article><div class="article-body">$body</div></article>
              <footer><p>Copyright and a very long footer line that should never be read as content.</p></footer>
            </body></html>
        """.trimIndent()

        val article = ArticleExtractor.fromPage(html, "https://cars.example/review", item = null)

        article.title shouldBe "Page title"
        article.leadImage shouldBe "https://img.example/og.jpg"
        article.blocks.size shouldBe 6
        article.blocks.joinToString { it.text() }.apply {
            shouldNotContain("Most popular")
            shouldNotContain("Copyright")
        }
    }

    @Test
    fun `uses JSON-LD articleBody when the page splits its body up, and restores paragraphs`() {
        val sentences = (1..40).joinToString("") { "Sentence number $it is here." }
        val html = """
            <html><head><script type="application/ld+json">
              {"@context":"https://schema.org","@type":"NewsArticle","headline":"LD headline",
               "author":{"@type":"Person","name":"Alex"},"articleBody":"$sentences"}
            </script></head>
            <body><div><p>Only a short teaser paragraph is in the HTML.</p></div></body></html>
        """.trimIndent()

        val article = ArticleExtractor.fromPage(html, "https://mag.example/story", item = null)

        article.author shouldBe "Alex"
        (article.blocks.size > 1) shouldBe true
        article.blocks.first().text() shouldContain "Sentence number 1 is here."
        article.textLength shouldBe article.blocks.sumOf { it.text().length }
    }

    @Test
    fun `treats short or trailing-off feed copies as incomplete`() {
        val short = Article("t", null, null, null, listOf(ArticleBlock.Paragraph("Just a teaser…")))
        ArticleExtractor.isComplete(short) shouldBe false

        val long = Article("t", null, null, null, listOf(ArticleBlock.Paragraph("x".repeat(2500))))
        ArticleExtractor.isComplete(long) shouldBe true

        val withLead = short.copy(leadImage = "https://img.example/a.jpg")
        val picked = ArticleExtractor.pick(withLead, long).shouldNotBeNull()
        picked.textLength shouldBe 2500
        picked.leadImage shouldBe "https://img.example/a.jpg"

        ArticleExtractor.pick(null, null).shouldBeNull()
    }

    @Test
    fun `refuses bot-check pages instead of reading them as the article`() {
        val html = """
            <html><head><title>Access to this page has been denied</title></head>
            <body><p>Access to this page has been denied because we believe you are using automation tools.</p></body></html>
        """.trimIndent()
        io.kotest.assertions.throwables.shouldThrow<BlockedException> {
            ArticleExtractor.fromPage(html, "https://blocked.example/story", item = null)
        }
    }

    @Test
    fun `lists articles from a sitemap, newest first, with titles from their addresses`() {
        val xml = """
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
              <url><loc>https://cdn.example/cars/older-concept-reveal/100</loc><lastmod>2026-11-19T18:29+01:00</lastmod></url>
              <url>
                <loc>https://cdn.example/designers/love-it-or-hate-it-li-auto/200</loc>
                <lastmod>2026-11-20T11:06+01:00</lastmod>
                <image:image><image:loc>https://img.cdn.example/200.jpg</image:loc></image:image>
              </url>
            </urlset>
        """.trimIndent()

        val items = ArticleExtractor.parseSitemap(xml, limit = 10)

        items.map { it.title } shouldContainExactly listOf("Love it or hate it li auto", "Older concept reveal")
        items.first().imageUrl shouldBe "https://img.cdn.example/200.jpg"
        items.first().date shouldBe 1795169160000L
    }

    @Test
    fun `lists stories embedded in a Next js page`() {
        val html = """
            <html><body><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"nav":[{"title":"Electric","path":"/car-news/electric"}],
             "newsResults":{"results":[
               {"title":"New concept","path":"/car-news/electric/new-concept","created":"2026-11-20T17:10:00+00:00",
                "image":"/files/concept.jpg","description":"A first look",
                "categories":[{"title":"Electric","path":"/car-news/electric"}]},
               {"title":"Supercar","path":"/car-news/supercars/supercar","created":"2026-11-21T22:00:00+00:00"}
             ]}}}}
            </script></body></html>
        """.trimIndent()

        val items = ArticleExtractor.parseNextData(html, "https://www.topgear.com/car-news")

        items.map { it.title } shouldContainExactly listOf("Supercar", "New concept")
        items.last().link shouldBe "https://www.topgear.com/car-news/electric/new-concept"
        items.last().imageUrl shouldBe "https://www.topgear.com/files/concept.jpg"
        items.last().summary shouldBe "A first look"
    }

    @Test
    fun `leaves out boxes pointing to the site's other stories and newsletter pitches`() {
        val body = (1..4).joinToString("") { "<p>Paragraph $it of the story, long enough to be part of the body.</p>" }
        val html = """
            <html><body><article>
              $body
              <h3>You might like</h3>
              <a href="/car-news/other-story"><img src="https://img.example/other.jpg"><h3>Another story</h3></a>
              <p><a href="https://cars.example/car-news/older-story">Read next: an older story</a></p>
              <h2>Top Gear Newsletter</h2>
              <p>Get all the latest news, reviews and exclusives, direct to your inbox.</p>
              <p><a href="https://cars.example/buy/123">The helmet we liked</a></p>
              <p>A closing paragraph that cites <a href="https://other.example/report">a report</a> elsewhere.</p>
            </article></body></html>
        """.trimIndent()

        val article = ArticleExtractor.fromPage(html, "https://cars.example/car-news/this-story", item = null)
        val text = article.blocks.joinToString("\n") { it.text() }

        text.shouldNotContain("You might like")
        text.shouldNotContain("Another story")
        text.shouldNotContain("Read next")
        text.shouldNotContain("Newsletter")
        text.shouldNotContain("inbox")
        text shouldContain "The helmet we liked"
        text shouldContain "A closing paragraph"
        article.blocks.none { it is ArticleBlock.Image && it.url.endsWith("other.jpg") } shouldBe true
    }

    @Test
    fun `recognises resized and proxied copies of the same picture`() {
        val original = ArticleExtractor.imageKey("https://electrek.co/wp-content/uploads/2026/11/photo.jpg")
        ArticleExtractor.imageKey("https://electrek.co/wp-content/uploads/2026/11/photo-1024x683.jpg?w=1200") shouldBe
            original
        val proxied = "https://i0.wp.com/electrek.co/wp-content/uploads/2026/11/photo.jpg?resize=1200"
        ArticleExtractor.imageKey(proxied) shouldBe original
        val scaled = "https://www.electrek.co/wp-content/uploads/2026/11/photo-scaled.jpg"
        ArticleExtractor.imageKey(scaled) shouldBe original
        ArticleExtractor.imageKey(
            "https://assets.example/dims4/default/abc/resize/1920x1433!/" +
                "?url=https%3A%2F%2Felectrek.co%2Fwp-content%2Fuploads%2F2026%2F11%2Fphoto.jpg",
        ) shouldBe original

        // Different pictures stay different
        (ArticleExtractor.imageKey("https://electrek.co/wp-content/uploads/2026/11/photo-2.jpg") == original) shouldBe
            false
        (ArticleExtractor.imageKey("https://other.example/wp-content/uploads/2026/11/photo.jpg") == original) shouldBe
            false
    }
}
