package mihon.source.magazines

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ArchiveTest {

    @Test
    fun `builds monthly sitemap and listing page addresses`() {
        Archive.MonthlySitemap("https://www.gq.com/sitemap-{yyyy}-{MM}.xml").url(2025, 3) shouldBe
            "https://www.gq.com/sitemap-2025-03.xml"
        Archive.ListingPages("https://www.topgear.com/car-news/page/{n}").url(4) shouldBe
            "https://www.topgear.com/car-news/page/4"
    }

    @Test
    fun `publications with an archive and custom magazines can reach older stories`() {
        val withArchive = MagazineCategory.entries.flatMap { it.publications }.filter { it.archive != null }
        withArchive.map { it.slug } shouldContainAll listOf("gq", "dezeen", "top-gear", "car-design-news", "monocle")

        val custom = CustomPublications.create("https://x.example/feed/", "X", "https://x.example/", null)
        custom.archive shouldBe Archive.FeedPages
        CustomPublications.fromSlug(custom.slug)?.archive shouldBe Archive.FeedPages
    }
}
