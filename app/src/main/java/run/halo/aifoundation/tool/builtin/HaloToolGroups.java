package run.halo.aifoundation.tool.builtin;

import java.util.List;
import run.halo.aifoundation.tool.ToolGroup;
import run.halo.aifoundation.tool.ToolPreset;

/**
 * Catalog groups shared by related built-in tool providers.
 *
 * <p>A group describes selection presets only. For example, the posts draft preset selects article queries and draft
 * writes, while publication tools retain their own permissions. The web research preset combines internet search with
 * page reading without coupling either provider to the other.
 */
final class HaloToolGroups {
    static final ToolGroup POSTS = contentGroup("posts", "文章", "文章查询、草稿编辑与发布管理", "post");
    static final ToolGroup PAGES = contentGroup("pages", "页面", "独立页面查询、草稿编辑与发布管理", "page");
    static final ToolGroup TAXONOMY = ToolGroup.builder()
            .name("taxonomy")
            .displayName("分类标签")
            .description("查询、创建和编辑文章分类与标签")
            .presets(List.of(
                    ToolPreset.builder()
                            .name("read")
                            .displayName("只读查询")
                            .description("查询可关联的分类与标签")
                            .toolNames(List.of("list_categories", "list_tags"))
                            .build(),
                    ToolPreset.builder()
                            .name("edit")
                            .displayName("分类标签编辑")
                            .description("查询并创建或修改分类与标签")
                            .toolNames(List.of(
                                    "list_categories",
                                    "list_tags",
                                    "create_category",
                                    "update_category",
                                    "create_tag",
                                    "update_tag"))
                            .build()))
            .build();

    /** Search and page-reading selection presets shared by independent web providers. */
    static final ToolGroup WEB = ToolGroup.builder()
            .name("web")
            .displayName("网页与联网搜索")
            .description("搜索互联网并读取网页或文本资源")
            .presets(List.of(ToolPreset.builder()
                    .name("research")
                    .displayName("联网研究")
                    .description("搭配使用联网搜索与网页读取")
                    .toolNames(List.of("read_web_page", "web_search"))
                    .build()))
            .build();

    private HaloToolGroups() {}

    private static ToolGroup contentGroup(String name, String display, String description, String singular) {
        var list = "list_" + name;
        var get = "get_" + singular;
        var compare = "compare_" + singular + "_versions";
        return ToolGroup.builder()
                .name(name)
                .displayName(display)
                .description(description)
                .presets(List.of(
                        ToolPreset.builder()
                                .name("read")
                                .displayName("只读查询")
                                .description("查询列表、读取正文并对比版本")
                                .toolNames(List.of(list, get, compare))
                                .build(),
                        ToolPreset.builder()
                                .name("draft")
                                .displayName("草稿编辑")
                                .description("查询正文并创建或修改草稿，不发布")
                                .toolNames(List.of(
                                        list,
                                        get,
                                        compare,
                                        "create_" + singular + "_draft",
                                        "update_" + singular + "_draft"))
                                .build(),
                        ToolPreset.builder()
                                .name("publish")
                                .displayName("发布管理")
                                .description("对比并确认发布，或取消发布")
                                .toolNames(List.of(list, get, compare, "publish_" + singular, "unpublish_" + singular))
                                .build()))
                .build();
    }
}
