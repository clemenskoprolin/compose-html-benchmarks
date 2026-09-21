package benchmarks.model

import kotlinx.serialization.json.*

fun catalogDataFromJson(value: JsonElement): CatalogData {
    val obj = value.jsonObject
    return CatalogData(
        title = obj.getValue("title").jsonPrimitive.content,
        platforms = obj.getValue("platforms").jsonArray.map { platformFilterFromJson(it) },
        sections = obj.getValue("sections").jsonArray.map { catalogSectionFromJson(it) },
    )
}

fun platformFilterFromJson(value: JsonElement): PlatformFilter {
    val obj = value.jsonObject
    return PlatformFilter(
        id = obj.getValue("id").jsonPrimitive.content,
        name = obj.getValue("name").jsonPrimitive.content,
        active = obj.getValue("active").jsonPrimitive.boolean,
    )
}

fun catalogSectionFromJson(value: JsonElement): CatalogSection {
    val obj = value.jsonObject
    return CatalogSection(
        title = obj.getValue("title").jsonPrimitive.content,
        projects = obj.getValue("projects").jsonArray.map { catalogProjectFromJson(it) },
    )
}

fun catalogProjectFromJson(value: JsonElement): CatalogProject {
    val obj = value.jsonObject
    return CatalogProject(
        author = obj.getValue("author").jsonPrimitive.content,
        name = obj.getValue("name").jsonPrimitive.content,
        description = obj.getValue("description").jsonPrimitive.content,
        starCount = obj.getValue("starCount").jsonPrimitive.int,
        badges = obj.getValue("badges").jsonArray.map { it.jsonPrimitive.content },
    )
}

fun formDataFromJson(value: JsonElement): FormData {
    val obj = value.jsonObject
    return FormData(
        title = obj.getValue("title").jsonPrimitive.content,
        actionUrl = obj.getValue("actionUrl").jsonPrimitive.content,
        formLabel = obj.getValue("formLabel").jsonPrimitive.content,
        isSubmitting = obj.getValue("isSubmitting").jsonPrimitive.boolean,
        sections = obj.getValue("sections").jsonArray.map { formSectionFromJson(it) },
    )
}

fun formSectionFromJson(value: JsonElement): FormSection {
    val obj = value.jsonObject
    return FormSection(
        id = obj.getValue("id").jsonPrimitive.content,
        title = obj.getValue("title").jsonPrimitive.content,
        fields = obj.getValue("fields").jsonArray.map { formFieldFromJson(it) },
    )
}

fun formFieldFromJson(value: JsonElement): FormField {
    val obj = value.jsonObject
    return FormField(
        id = obj.getValue("id").jsonPrimitive.content,
        name = obj.getValue("name").jsonPrimitive.content,
        label = obj.getValue("label").jsonPrimitive.content,
        type = FormFieldType.valueOf(obj.getValue("type").jsonPrimitive.content),
        value = obj["value"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        placeholder = obj["placeholder"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        helpText = obj["helpText"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        errorMessage = obj["errorMessage"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        required = obj.getValue("required").jsonPrimitive.boolean,
        disabled = obj.getValue("disabled").jsonPrimitive.boolean,
        readOnly = obj.getValue("readOnly").jsonPrimitive.boolean,
        checked = obj.getValue("checked").jsonPrimitive.boolean,
        multiple = obj.getValue("multiple").jsonPrimitive.boolean,
        autoComplete = obj["autoComplete"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        min = obj["min"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        max = obj["max"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        minLength = obj["minLength"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.int },
        maxLength = obj["maxLength"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.int },
        pattern = obj["pattern"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        step = obj["step"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.double },
        options = obj["options"]?.takeUnless { it is JsonNull }?.let { it.jsonArray.map { selectOptionFromJson(it) } },
    )
}

fun selectOptionFromJson(value: JsonElement): SelectOption {
    val obj = value.jsonObject
    return SelectOption(
        value = obj.getValue("value").jsonPrimitive.content,
        label = obj.getValue("label").jsonPrimitive.content,
        selected = obj.getValue("selected").jsonPrimitive.boolean,
        disabled = obj.getValue("disabled").jsonPrimitive.boolean,
    )
}

fun dataTableDataFromJson(value: JsonElement): DataTableData {
    val obj = value.jsonObject
    return DataTableData(
        title = obj.getValue("title").jsonPrimitive.content,
        description = obj.getValue("description").jsonPrimitive.content,
        searchQuery = obj["searchQuery"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        activeFilters = obj.getValue("activeFilters").jsonObject.mapValues { it.value.jsonPrimitive.content },
        selectedRowCount = obj.getValue("selectedRowCount").jsonPrimitive.int,
        totalRowCount = obj.getValue("totalRowCount").jsonPrimitive.int,
        headers = obj.getValue("headers").jsonArray.map { tableHeaderFromJson(it) },
        sortColumnId = obj.getValue("sortColumnId").jsonPrimitive.content,
        sortDirection = obj.getValue("sortDirection").jsonPrimitive.content,
        rows = obj.getValue("rows").jsonArray.map { dataRowFromJson(it) },
        pages = obj.getValue("pages").jsonArray.map { pageItemFromJson(it) },
    )
}

fun tableHeaderFromJson(value: JsonElement): TableHeader {
    val obj = value.jsonObject
    return TableHeader(
        id = obj.getValue("id").jsonPrimitive.content,
        label = obj.getValue("label").jsonPrimitive.content,
        sortable = obj.getValue("sortable").jsonPrimitive.boolean,
    )
}

fun dataRowFromJson(value: JsonElement): DataRow {
    val obj = value.jsonObject
    return DataRow(
        id = obj.getValue("id").jsonPrimitive.content,
        status = obj.getValue("status").jsonPrimitive.content,
        selected = obj.getValue("selected").jsonPrimitive.boolean,
        cells = obj.getValue("cells").jsonArray.map { dataCellFromJson(it) },
    )
}

fun dataCellFromJson(value: JsonElement): DataCell {
    val obj = value.jsonObject
    return DataCell(
        columnId = obj.getValue("columnId").jsonPrimitive.content,
        rawValue = obj.getValue("rawValue").jsonPrimitive.content,
        displayValue = obj.getValue("displayValue").jsonPrimitive.content,
        tooltip = obj["tooltip"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        isStatus = obj.getValue("isStatus").jsonPrimitive.boolean,
    )
}

fun pageItemFromJson(value: JsonElement): PageItem {
    val obj = value.jsonObject
    return PageItem(
        number = obj.getValue("number").jsonPrimitive.int,
        isCurrent = obj.getValue("isCurrent").jsonPrimitive.boolean,
    )
}

fun svgDashboardDataFromJson(value: JsonElement): SvgDashboardData {
    val obj = value.jsonObject
    return SvgDashboardData(
        title = obj.getValue("title").jsonPrimitive.content,
        lineChart = lineChartFromJson(obj.getValue("lineChart")),
        barChart = barChartFromJson(obj.getValue("barChart")),
        pieChart = pieChartFromJson(obj.getValue("pieChart")),
        gauge = gaugeChartFromJson(obj.getValue("gauge")),
    )
}

fun lineChartFromJson(value: JsonElement): LineChart {
    val obj = value.jsonObject
    return LineChart(
        title = obj.getValue("title").jsonPrimitive.content,
        pathData = obj.getValue("pathData").jsonPrimitive.content,
        points = obj.getValue("points").jsonArray.map { svgPointFromJson(it) },
        xLabels = (obj["xLabels"] ?: obj.getValue("xlabels")).jsonArray.map { svgLabelFromJson(it) },
    )
}

fun svgPointFromJson(value: JsonElement): SvgPoint {
    val obj = value.jsonObject
    return SvgPoint(
        x = obj.getValue("x").jsonPrimitive.double,
        y = obj.getValue("y").jsonPrimitive.double,
    )
}

fun svgLabelFromJson(value: JsonElement): SvgLabel {
    val obj = value.jsonObject
    return SvgLabel(
        x = obj.getValue("x").jsonPrimitive.double,
        text = obj.getValue("text").jsonPrimitive.content,
    )
}

fun barChartFromJson(value: JsonElement): BarChart {
    val obj = value.jsonObject
    return BarChart(
        title = obj.getValue("title").jsonPrimitive.content,
        bars = obj.getValue("bars").jsonArray.map { barFromJson(it) },
    )
}

fun barFromJson(value: JsonElement): Bar {
    val obj = value.jsonObject
    return Bar(
        x = obj.getValue("x").jsonPrimitive.double,
        y = obj.getValue("y").jsonPrimitive.double,
        width = obj.getValue("width").jsonPrimitive.double,
        height = obj.getValue("height").jsonPrimitive.double,
        value = obj.getValue("value").jsonPrimitive.double,
    )
}

fun pieChartFromJson(value: JsonElement): PieChart {
    val obj = value.jsonObject
    return PieChart(
        title = obj.getValue("title").jsonPrimitive.content,
        slices = obj.getValue("slices").jsonArray.map { pieSliceDataFromJson(it) },
    )
}

fun pieSliceDataFromJson(value: JsonElement): PieSliceData {
    val obj = value.jsonObject
    return PieSliceData(
        pathData = obj.getValue("pathData").jsonPrimitive.content,
        color = obj.getValue("color").jsonPrimitive.content,
    )
}

fun gaugeChartFromJson(value: JsonElement): GaugeChart {
    val obj = value.jsonObject
    return GaugeChart(
        title = obj.getValue("title").jsonPrimitive.content,
        valuePathData = obj.getValue("valuePathData").jsonPrimitive.content,
        needleX = obj.getValue("needleX").jsonPrimitive.double,
        needleY = obj.getValue("needleY").jsonPrimitive.double,
        valueLabel = obj.getValue("valueLabel").jsonPrimitive.content,
    )
}

fun articleDataFromJson(value: JsonElement): ArticleData {
    val obj = value.jsonObject
    return ArticleData(
        title = obj.getValue("title").jsonPrimitive.content,
        author = obj.getValue("author").jsonPrimitive.content,
        excerpt = obj.getValue("excerpt").jsonPrimitive.content,
        publishDateIso = obj.getValue("publishDateIso").jsonPrimitive.content,
        publishDateFormatted = obj.getValue("publishDateFormatted").jsonPrimitive.content,
        readingTimeMin = obj.getValue("readingTimeMin").jsonPrimitive.int,
        heroImage = heroImageFromJson(obj.getValue("heroImage")),
        breadcrumbs = obj.getValue("breadcrumbs").jsonArray.map { breadcrumbFromJson(it) },
        toc = obj.getValue("toc").jsonArray.map { tocItemFromJson(it) },
        sections = obj.getValue("sections").jsonArray.map { contentSectionFromJson(it) },
        tags = obj.getValue("tags").jsonArray.map { it.jsonPrimitive.content },
        relatedArticles = obj.getValue("relatedArticles").jsonArray.map { relatedArticleFromJson(it) },
    )
}

fun heroImageFromJson(value: JsonElement): HeroImage {
    val obj = value.jsonObject
    return HeroImage(
        url = obj.getValue("url").jsonPrimitive.content,
        alt = obj.getValue("alt").jsonPrimitive.content,
        width = obj.getValue("width").jsonPrimitive.int,
        height = obj.getValue("height").jsonPrimitive.int,
    )
}

fun breadcrumbFromJson(value: JsonElement): Breadcrumb {
    val obj = value.jsonObject
    return Breadcrumb(
        label = obj.getValue("label").jsonPrimitive.content,
        url = obj.getValue("url").jsonPrimitive.content,
        isCurrent = obj.getValue("isCurrent").jsonPrimitive.boolean,
    )
}

fun tocItemFromJson(value: JsonElement): TocItem {
    val obj = value.jsonObject
    return TocItem(
        targetId = obj.getValue("targetId").jsonPrimitive.content,
        title = obj.getValue("title").jsonPrimitive.content,
    )
}

fun contentSectionFromJson(value: JsonElement): ContentSection {
    val obj = value.jsonObject
    return ContentSection(
        id = obj.getValue("id").jsonPrimitive.content,
        title = obj.getValue("title").jsonPrimitive.content,
        blocks = obj.getValue("blocks").jsonArray.map { contentBlockFromJson(it) },
    )
}

fun contentBlockFromJson(value: JsonElement): ContentBlock {
    val obj = value.jsonObject
    return ContentBlock(
        type = BlockType.valueOf(obj.getValue("type").jsonPrimitive.content),
        content = obj.getValue("content").jsonPrimitive.content,
        language = obj["language"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
        url = obj["url"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
    )
}

fun relatedArticleFromJson(value: JsonElement): RelatedArticle {
    val obj = value.jsonObject
    return RelatedArticle(
        url = obj.getValue("url").jsonPrimitive.content,
        title = obj.getValue("title").jsonPrimitive.content,
    )
}

fun preactSearchResultsDataFromJson(value: JsonElement): PreactSearchResultsData {
    val obj = value.jsonObject
    return PreactSearchResultsData(
        items = obj.getValue("items").jsonArray.map { preactSearchResultItemFromJson(it) },
        footerSections = obj.getValue("footerSections").jsonArray.map { preactFooterSectionFromJson(it) },
    )
}

fun preactSearchResultItemFromJson(value: JsonElement): PreactSearchResultItem {
    val obj = value.jsonObject
    return PreactSearchResultItem(
        id = obj.getValue("id").jsonPrimitive.int,
        title = obj.getValue("title").jsonPrimitive.content,
        price = obj.getValue("price").jsonPrimitive.content,
        image = obj.getValue("image").jsonPrimitive.content,
    )
}

fun preactFooterSectionFromJson(value: JsonElement): PreactFooterSection {
    val obj = value.jsonObject
    return PreactFooterSection(
        title = obj.getValue("title").jsonPrimitive.content,
        links = obj.getValue("links").jsonArray.map { preactFooterLinkFromJson(it) },
        headingHref = obj["headingHref"]?.takeUnless { it is JsonNull }?.let { it.jsonPrimitive.content },
    )
}

fun preactFooterLinkFromJson(value: JsonElement): PreactFooterLink {
    val obj = value.jsonObject
    return PreactFooterLink(
        label = obj.getValue("label").jsonPrimitive.content,
        href = obj.getValue("href").jsonPrimitive.content,
    )
}
