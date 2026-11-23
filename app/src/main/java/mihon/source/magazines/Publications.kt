package mihon.source.magazines

/**
 * A magazine (or magazine-style site) that publishes an open RSS/Atom feed.
 *
 * @param slug stable id, used in the entry's URL; never change it once shipped or library entries break
 * @param feed the publication's feed; entries are rebuilt from it on every refresh
 * @param fullTextInFeed true when the feed carries whole articles, so article pages needn't be fetched
 * @param color masthead background for the generated cover, as 0xRRGGBB
 */
data class Publication(
    val slug: String,
    val name: String,
    val homepage: String,
    val feed: String,
    val tagline: String,
    val color: Int,
    val fullTextInFeed: Boolean = false,
)

/** A group of publications, shown as its own built-in source. */
enum class MagazineCategory(val title: String, val publications: List<Publication>) {
    MENS_STYLE(
        "Men's Style & Fashion",
        listOf(
            Publication(
                "gq",
                "GQ",
                "https://www.gq.com",
                "https://www.gq.com/feed/rss",
                "Style, culture and the men who shape it.",
                0x111111,
            ),
            Publication(
                "esquire",
                "Esquire",
                "https://www.esquire.com",
                "https://www.esquire.com/rss/all.xml/",
                "Style, food, culture and long reads.",
                0xC8102E,
            ),
            Publication(
                "esquire-style",
                "Esquire Style",
                "https://www.esquire.com/style/",
                "https://www.esquire.com/rss/style.xml/",
                "Esquire's menswear, watches and grooming desk.",
                0x8B0A1A,
            ),
            Publication(
                "esquire-uk",
                "Esquire UK",
                "https://www.esquire.com/uk/",
                "https://www.esquire.com/uk/rss/all.xml/",
                "The British edition of Esquire.",
                0x1D1D1B,
            ),
            Publication(
                "mens-health",
                "Men's Health",
                "https://www.menshealth.com",
                "https://www.menshealth.com/rss/all.xml/",
                "Fitness, health, gear and grooming.",
                0xD2232A,
            ),
            Publication(
                "highsnobiety",
                "Highsnobiety",
                "https://www.highsnobiety.com",
                "https://www.highsnobiety.com/feed/",
                "Streetwear, sneakers and fashion culture.",
                0x000000,
                fullTextInFeed = true,
            ),
            Publication(
                "put-this-on",
                "Put This On",
                "https://putthison.com",
                "https://putthison.com/feed/",
                "Classic menswear, dressing well on a budget.",
                0x2E4A62,
                fullTextInFeed = true,
            ),
            Publication(
                "permanent-style",
                "Permanent Style",
                "https://www.permanentstyle.com",
                "https://www.permanentstyle.com/feed",
                "Tailoring, craft and quality menswear.",
                0x3B2F2A,
                fullTextInFeed = true,
            ),
            Publication(
                "die-workwear",
                "Die, Workwear!",
                "https://dieworkwear.com",
                "https://dieworkwear.com/feed/",
                "Essays on menswear, tailoring and taste.",
                0x4A4A3A,
            ),
            Publication(
                "gear-patrol",
                "Gear Patrol",
                "https://www.gearpatrol.com",
                "https://www.gearpatrol.com/feed/",
                "Gear, watches, cars and style.",
                0x1F3A2F,
                fullTextInFeed = true,
            ),
            Publication(
                "hodinkee",
                "Hodinkee",
                "https://www.hodinkee.com",
                "https://www.hodinkee.com/articles/rss.xml",
                "Watches, reviewed and explained.",
                0x0B2545,
                fullTextInFeed = true,
            ),
            Publication(
                "dmarge",
                "DMARGE",
                "https://www.dmarge.com",
                "https://www.dmarge.com/feed",
                "Men's style, travel and luxury.",
                0x1A1A2E,
                fullTextInFeed = true,
            ),
            Publication(
                "man-of-many",
                "Man of Many",
                "https://manofmany.com",
                "https://manofmany.com/feed",
                "Lifestyle, fashion, cars and tech for men.",
                0x222222,
            ),
            Publication(
                "vogue",
                "Vogue",
                "https://www.vogue.com",
                "https://www.vogue.com/feed/rss",
                "Fashion, beauty and culture.",
                0x000000,
            ),
            Publication(
                "vogue-fashion",
                "Vogue Fashion",
                "https://www.vogue.com/fashion",
                "https://www.vogue.com/feed/fashion/rss",
                "Runway, trends and fashion news from Vogue.",
                0x2B2B2B,
            ),
            Publication(
                "fashionista",
                "Fashionista",
                "https://fashionista.com",
                "https://fashionista.com/.rss/full/",
                "Fashion industry news and street style.",
                0xE6007E,
                fullTextInFeed = true,
            ),
        ),
    ),
    CARS(
        "Cars & Concept Cars",
        listOf(
            Publication(
                "car-and-driver",
                "Car and Driver",
                "https://www.caranddriver.com",
                "https://www.caranddriver.com/rss/all.xml/",
                "Reviews, first drives and new-car news.",
                0xD71920,
            ),
            Publication(
                "road-and-track",
                "Road & Track",
                "https://www.roadandtrack.com",
                "https://www.roadandtrack.com/rss/all.xml/",
                "Performance cars, racing and car culture.",
                0x0A0A0A,
            ),
            Publication(
                "motor1",
                "Motor1",
                "https://www.motor1.com",
                "https://www.motor1.com/rss/news/all/",
                "New models, spy shots and concept reveals.",
                0xE30613,
            ),
            Publication(
                "carscoops",
                "Carscoops",
                "https://www.carscoops.com",
                "https://www.carscoops.com/feed/",
                "Concepts, debuts and design news from the car world.",
                0x0057B8,
                fullTextInFeed = true,
            ),
            Publication(
                "the-drive",
                "The Drive",
                "https://www.thedrive.com",
                "https://www.thedrive.com/feed",
                "Car news, reviews and features.",
                0x111111,
                fullTextInFeed = true,
            ),
            Publication(
                "jalopnik",
                "Jalopnik",
                "https://jalopnik.com",
                "https://jalopnik.com/rss",
                "Car culture with opinions.",
                0x5A2D82,
                fullTextInFeed = true,
            ),
            Publication(
                "hagerty",
                "Hagerty",
                "https://www.hagerty.com/media/",
                "https://www.hagerty.com/media/feed/",
                "Classic, collector and enthusiast cars.",
                0x00263E,
                fullTextInFeed = true,
            ),
            Publication(
                "insideevs",
                "InsideEVs",
                "https://insideevs.com",
                "https://insideevs.com/rss/news/all/",
                "Electric cars, new models and concepts.",
                0x00A651,
            ),
            Publication(
                "electrek",
                "Electrek",
                "https://electrek.co",
                "https://electrek.co/feed/",
                "Electric vehicles and clean transport.",
                0x1E73BE,
            ),
            Publication(
                "new-atlas-auto",
                "New Atlas Auto",
                "https://newatlas.com/automotive/",
                "https://newatlas.com/automotive/index.rss",
                "Automotive design, concepts and engineering.",
                0x0D2C54,
            ),
        ),
    ),
    DESIGN(
        "Design & Architecture",
        listOf(
            Publication(
                "dezeen",
                "Dezeen",
                "https://www.dezeen.com",
                "https://www.dezeen.com/feed/",
                "Architecture, interiors and design, including car design.",
                0x000000,
                fullTextInFeed = true,
            ),
            Publication(
                "designboom",
                "designboom",
                "https://www.designboom.com",
                "https://www.designboom.com/feed/",
                "Architecture, design, art and technology.",
                0xE2001A,
                fullTextInFeed = true,
            ),
            Publication(
                "designboom-tech",
                "designboom Tech",
                "https://www.designboom.com/technology/",
                "https://www.designboom.com/technology/feed/",
                "Vehicles, concept cars and industrial design.",
                0xB00016,
                fullTextInFeed = true,
            ),
            Publication(
                "wallpaper",
                "Wallpaper*",
                "https://www.wallpaper.com",
                "https://www.wallpaper.com/feeds/all",
                "Design, interiors, fashion, art and travel.",
                0x1C1C1C,
                fullTextInFeed = true,
            ),
            Publication(
                "archdaily",
                "ArchDaily",
                "https://www.archdaily.com",
                "https://www.archdaily.com/feed",
                "The world's most visited architecture site.",
                0x333333,
                fullTextInFeed = true,
            ),
            Publication(
                "core77",
                "Core77",
                "https://www.core77.com",
                "https://www.core77.com/blog/rss.xml",
                "Industrial design news and features.",
                0xF15A24,
            ),
            Publication(
                "cool-hunting",
                "Cool Hunting",
                "https://coolhunting.com",
                "https://coolhunting.com/feed/",
                "Design, culture, travel and style.",
                0x00A0B0,
                fullTextInFeed = true,
            ),
            Publication(
                "creative-boom",
                "Creative Boom",
                "https://www.creativeboom.com",
                "https://www.creativeboom.com/feed/",
                "Graphic design, illustration and creative work.",
                0xFF4F00,
                fullTextInFeed = true,
            ),
        ),
    ),
    ART(
        "Art & Photography",
        listOf(
            Publication(
                "colossal",
                "Colossal",
                "https://www.thisiscolossal.com",
                "https://www.thisiscolossal.com/feed/",
                "Contemporary art and visual culture.",
                0x111111,
                fullTextInFeed = true,
            ),
            Publication(
                "artnews",
                "ARTnews",
                "https://www.artnews.com",
                "https://www.artnews.com/feed/",
                "News from the art world.",
                0xC8102E,
                fullTextInFeed = true,
            ),
            Publication(
                "hyperallergic",
                "Hyperallergic",
                "https://hyperallergic.com",
                "https://hyperallergic.com/feed/",
                "Art criticism, reviews and news.",
                0xD4145A,
                fullTextInFeed = true,
            ),
            Publication(
                "petapixel",
                "PetaPixel",
                "https://petapixel.com",
                "https://petapixel.com/feed/",
                "Photography news, gear and technique.",
                0x1E88E5,
            ),
        ),
    ),
    TECH(
        "Tech & Science",
        listOf(
            Publication(
                "the-verge",
                "The Verge",
                "https://www.theverge.com",
                "https://www.theverge.com/rss/index.xml",
                "Technology, science and culture.",
                0x5200FF,
                fullTextInFeed = true,
            ),
            Publication(
                "wired",
                "Wired",
                "https://www.wired.com",
                "https://www.wired.com/feed/rss",
                "How technology is changing the world.",
                0x000000,
            ),
            Publication(
                "ars-technica",
                "Ars Technica",
                "https://arstechnica.com",
                "https://feeds.arstechnica.com/arstechnica/index",
                "Tech, science and policy in depth.",
                0xFF4E00,
                fullTextInFeed = true,
            ),
            Publication(
                "engadget",
                "Engadget",
                "https://www.engadget.com",
                "https://www.engadget.com/rss.xml",
                "Gadgets and consumer tech.",
                0x6001D2,
                fullTextInFeed = true,
            ),
            Publication(
                "new-atlas",
                "New Atlas",
                "https://newatlas.com",
                "https://newatlas.com/index.rss",
                "Science, design and innovation.",
                0x0D2C54,
            ),
            Publication(
                "popular-science",
                "Popular Science",
                "https://www.popsci.com",
                "https://www.popsci.com/feed/",
                "Science and technology explained.",
                0x0B3D91,
                fullTextInFeed = true,
            ),
        ),
    ),
}

/**
 * Magazines added by the user from any website's feed.
 *
 * Nothing is stored on the side: the feed, name and site are packed into the entry's slug
 * ("custom/<token>"), so the entry keeps working across restarts, backups and restores.
 */
object CustomPublications {

    const val PREFIX = "custom/"

    private const val MAX_TAGLINE = 140

    /** Muted magazine-rack colors; each site gets one picked from its address. */
    private val PALETTE = intArrayOf(
        0x1B263B, 0x6B2737, 0x2D5D4F, 0x7A4E2D, 0x3D3B6E, 0x8C2F39, 0x264653, 0x5E548E, 0x9C6644, 0x2F3E46,
    )

    fun create(feed: String, name: String, homepage: String, tagline: String?): Publication {
        val shortTagline = tagline?.takeIf { it.isNotBlank() }?.let {
            if (it.length > MAX_TAGLINE) it.take(MAX_TAGLINE).substringBeforeLast(' ') + "…" else it
        } ?: "Stories from ${hostOf(homepage)}."
        val color = PALETTE[Math.floorMod(hostOf(homepage).hashCode(), PALETTE.size)]
        val token = encode(listOf(feed, name, homepage, shortTagline))
        return Publication(PREFIX + token, name, homepage, feed, shortTagline, color)
    }

    /** The publication a "custom/<token>" slug stands for, or null if it isn't one. */
    fun fromSlug(slug: String): Publication? {
        if (!slug.startsWith(PREFIX)) return null
        val fields = decode(slug.removePrefix(PREFIX)) ?: return null
        if (fields.size < 4) return null
        val (feed, name, homepage, tagline) = fields
        val color = PALETTE[Math.floorMod(hostOf(homepage).hashCode(), PALETTE.size)]
        return Publication(slug, name, homepage, feed, tagline, color)
    }

    fun hostOf(url: String) = url.substringAfter("://").substringBefore('/').removePrefix("www.")

    // Fields joined with a unit separator, then base64url: compact and safe in paths and fragments.
    private fun encode(fields: List<String>): String = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(fields.joinToString("\u001F") { it.replace("\u001F", " ") }.toByteArray())

    private fun decode(token: String): List<String>? = runCatching {
        String(java.util.Base64.getUrlDecoder().decode(token.substringBefore('?').substringBefore('#')))
            .split('\u001F')
    }.getOrNull()
}
