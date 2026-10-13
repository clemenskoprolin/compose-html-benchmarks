package benchmarks.jstachio;

import benchmarks.model.*;
import io.jstach.jstache.JStache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed presentation helpers for Mustache's logic-free templates. Created during rendering. */
public final class Views {
    private Views() {}

    @JStache(name = "CatalogTemplate", path = "jstachio/tailwind-catalog.mustache")
    public record Catalog(CatalogData data, List<Platform> platforms) {
        public Catalog(CatalogData data) {
            this(data, data.getPlatforms().stream().map(Platform::new).toList());
        }
    }
    public record Platform(PlatformFilter platform) {
        public String cssClass() {
            return platform.getActive()
                ? "px-3 py-2 font-medium text-sm rounded-md bg-indigo-100 text-indigo-700"
                : "px-3 py-2 font-medium text-sm rounded-md text-slate-500 hover:text-slate-700 bg-slate-100 hover:bg-slate-200";
        }
    }

    @JStache(name = "FormTemplate", path = "jstachio/form-app.mustache")
    public record Form(FormData data, List<FormSectionView> sections) {
        public Form(FormData data) {
            this(data, data.getSections().stream().map(FormSectionView::new).toList());
        }
    }
    public record FormSectionView(FormSection section, List<Field> fields) {
        public FormSectionView(FormSection section) {
            this(section, section.getFields().stream().map(Field::new).toList());
        }
    }
    public record Field(FormField field, List<Option> options) {
        public Field(FormField field) {
            this(field, radioOptions(field));
        }

        private static List<Option> radioOptions(FormField field) {
            if (field.getType() != FormFieldType.RADIO || field.getOptions() == null) return List.of();
            var options = field.getOptions();
            var result = new ArrayList<Option>(options.size());
            for (int i = 0; i < options.size(); i++) {
                result.add(new Option(options.get(i), field.getId() + "-opt-" + i));
            }
            return result;
        }

        public boolean select() { return field.getType() == FormFieldType.SELECT; }
        public boolean textarea() { return field.getType() == FormFieldType.TEXTAREA; }
        public boolean radio() { return field.getType() == FormFieldType.RADIO; }
        public boolean checkbox() { return field.getType() == FormFieldType.CHECKBOX; }
        public boolean input() { return !select() && !textarea() && !radio() && !checkbox(); }
        public String stepValue() { return field.getStep() == null ? "" : field.getStep().toString(); }
        public String inputType() { return field.getType().name().toLowerCase(java.util.Locale.ROOT); }
        public String described() {
            return ((field.getHelpText() != null ? "help-" + field.getId() + " " : "") +
                (field.getErrorMessage() != null ? "error-" + field.getId() : "")).trim();
        }
        public boolean hasDescription() { return hasHelp() || hasError(); }
        public boolean hasHelp() { return field.getHelpText() != null; }
        public boolean hasError() { return field.getErrorMessage() != null; }
    }
    public record Option(SelectOption opt, String optionId) {}

    @JStache(name = "TableTemplate", path = "jstachio/data-table.mustache")
    public record Table(DataTableData data, List<Header> headers, List<Row> rows) {
        public Table(DataTableData data) {
            this(data, data.getHeaders().stream().map(h -> new Header(h, data)).toList(),
                data.getRows().stream().map(Row::new).toList());
        }
        public Set<Map.Entry<String, String>> filters() { return data.getActiveFilters().entrySet(); }
        public int columnCount() { return data.getHeaders().size(); }
    }
    public record Header(TableHeader header, String direction) {
        public Header(TableHeader header, DataTableData data) {
            this(header, header.getId().equals(data.getSortColumnId()) ? data.getSortDirection() : "none");
        }
        public String cssClass() { return header.getSortable() ? "sortable" +
            (!direction.equals("none") ? " sorted-" + direction : "") : null; }
    }
    public record Row(DataRow row, List<Cell> cells) {
        public Row(DataRow row) {
            this(row, row.getCells().stream().map(c -> Cell.of(c, row.getId())).toList());
        }
    }
    public record Cell(DataCell cell, String tooltipId) {
        public static Cell of(DataCell cell, String rowId) {
            return new Cell(cell, cell.getTooltip() == null ? null : "tt-" + rowId + "-" + cell.getColumnId());
        }
        public boolean hasTooltip() { return cell.getTooltip() != null; }
    }

    @JStache(name = "SvgTemplate", path = "jstachio/svg-dashboard.mustache")
    public record Svg(SvgDashboardData data, List<BarView> bars) {
        public Svg(SvgDashboardData data) {
            this(data, data.getBarChart().getBars().stream().map(BarView::new).toList());
        }
    }
    public record BarView(Bar bar) {
        public double labelX() { return bar.getX() + bar.getWidth() / 2; }
        public double labelY() { return bar.getY() - 10; }
        public String label() {
            String value = Double.toString(bar.getValue());
            return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
        }
    }

    @JStache(name = "ArticleTemplate", path = "jstachio/content-article.mustache")
    public record Article(ArticleData data, List<Section> sections) {
        public Article(ArticleData data) {
            this(data, data.getSections().stream().map(Section::new).toList());
        }
    }
    public record Section(ContentSection section, List<Block> blocks) {
        public Section(ContentSection section) {
            this(section, section.getBlocks().stream().map(Block::new).toList());
        }
    }
    public record Block(ContentBlock block) {
        public boolean paragraph() { return block.getType() == BlockType.PARAGRAPH; }
        public boolean code() { return block.getType() == BlockType.CODE; }
        public boolean image() { return block.getType() == BlockType.IMAGE; }
        public boolean link() { return block.getType() == BlockType.LINK; }
        public String language() { return block.getLanguage() == null ? "text" : block.getLanguage(); }
        public String url() { return block.getUrl() == null ? "" : block.getUrl(); }
    }

    @JStache(name = "SearchTemplate", path = "jstachio/preact-search-results.mustache")
    public record Search(PreactSearchResultsData data) {}

    @JStache(name = "TextTemplate", path = "jstachio/preact-text.mustache")
    public record Text() {
        private static final List<Boolean> REPETITIONS = Collections.nCopies(1000, Boolean.TRUE);
        public List<Boolean> repetitions() { return REPETITIONS; }
    }

    @JStache(name = "DynamicTextTemplate", path = "jstachio/dynamic-text.mustache")
    public record DynamicText(TextData data) {}
}
