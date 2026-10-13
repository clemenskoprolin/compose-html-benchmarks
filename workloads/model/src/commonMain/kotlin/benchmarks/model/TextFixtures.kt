package benchmarks.model

// Built before timing. Every row has runtime data; the templates contain no fixture prose.
fun textFixture(
    escaped: Boolean = false, count: Int = 1_000,
    foo: String = BAVARIA_FOO, bar: String = BAVARIA_BAR,
): TextData = TextData(
    (1..count).map { index ->
        val prefix = if (escaped) "<item $index> & \"quoted\" 'single' 😀 café α: " else "Item $index: "
        TextPair(prefix + foo, prefix + bar)
    },
)
