package com.agentassist.ai.support;

/**
 * Language-tag normalization and Chinese script detection. Moved VERBATIM from
 * BaseAiProvider in the Part 2 split. The old describeLanguage() lives on as
 * data in the aa_language registry (§4.11, LanguageRegistryService.describe).
 */
public final class LanguageSupport {

    private LanguageSupport() {
    }

    // Characters that exist in only one Chinese script. Used to decide Traditional vs
    // Simplified from the customer's own text, which is deterministic - unlike asking
    // the model, which answers "zh", "zh-TW" or "zh-Hant" for the same input.
    private static final String TRADITIONAL_ONLY =
            "繁體灣東車買觀們個來應這時說對開關費務點電話廣鐵頭問題實現當經濟權證單價營業機構樣兒學國讀寫語譯聽見產屬醫藥銀錢長門雞魚鳥馬龍鳳從將軍隊島龜"
            + "軒裝麼樓處號間過還發為與動樂兩邊廳選進預訂麗舊歡團導會"
            // Traditional-only, deliberately with no Simplified counterpart below:
            // 着 and 几 are both valid in Traditional writing, so counting them as
            // Simplified evidence would misread Hong Kong / Macau text.
            + "著幾";
    private static final String SIMPLIFIED_ONLY =
            "简体湾东车买观们个来应这时说对开关费务点电话广铁头问题实现当经济权证单价营业机构样儿学国读写语译听见产属医药银钱长门鸡鱼鸟马龙凤从将军队岛龟"
            + "轩装么楼处号间过还发为与动乐两边厅选进预订丽旧欢团导会";

    /**
     * Normalize whatever the model returns into a usable language tag.
     * <p>
     * Models answer "zh-TW", "zh-Hant" or "Chinese (Traditional)" for Traditional Chinese.
     * The previous {@code length() == 2} check turned every one of those into "und", and
     * LanguageService skips translation entirely for "und" - so the agent was handed raw
     * English where Chinese was expected. For Chinese the script is decided from the
     * customer's own characters; the model's region tag is only a fallback.
     */
    public static String normalizeLanguageTag(String raw, String sourceText) {
        String tag = raw.trim().toLowerCase().replaceAll("[^a-z-]", "");
        String primary = tag.isEmpty() ? "" : tag.split("-")[0];
        boolean tagUnusable = primary.length() != 2;

        // Han characters alone do NOT mean Chinese - Japanese kanji live in the same
        // Unicode block, and an English message can quote a Chinese venue name. So the
        // text is only consulted when the model's own answer is unusable.
        boolean modelSaysChinese = tag.startsWith("zh") || tag.contains("chinese");
        if (modelSaysChinese || (tagUnusable && hasHanCharacters(sourceText))) {
            String scriptFromText = detectChineseScript(sourceText);
            if (scriptFromText != null) {
                return scriptFromText;
            }
            if (tag.contains("hant") || tag.contains("traditional")
                    || tag.contains("tw") || tag.contains("hk") || tag.contains("mo")) {
                return "zh-Hant";
            }
            if (tag.contains("hans") || tag.contains("simplified")
                    || tag.contains("cn") || tag.contains("sg")) {
                return "zh-Hans";
            }
            return "zh";
        }

        // Primary subtag only, e.g. "pt-br" -> "pt"
        return tagUnusable ? "und" : primary;
    }

    private static boolean hasHanCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF));
    }

    /**
     * Decide Traditional vs Simplified by counting script-exclusive characters.
     * Returns null when the text has no distinguishing characters.
     */
    private static String detectChineseScript(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int traditional = 0;
        int simplified = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (TRADITIONAL_ONLY.indexOf(c) >= 0) {
                traditional++;
            } else if (SIMPLIFIED_ONLY.indexOf(c) >= 0) {
                simplified++;
            }
        }
        if (traditional == 0 && simplified == 0) {
            return null;
        }
        return traditional >= simplified ? "zh-Hant" : "zh-Hans";
    }
}
