package com.BreadIQ.myapp.core

/**
 * `IngredientCategory` — mirrors `ImportModal.tsx`'s inlined type (line
 * 26), which has 10 cases. The canonical shared module this file was
 * inlined from — `lib/ingredient-densities/src/index.ts` — has only 9:
 * no `egg_yolk`. See [IngredientClassifier.classifyName] for the drift
 * this produces.
 */
enum class IngredientCategory(val rawValue: String) {
    FLOUR("flour"), WATER("water"), YEAST("yeast"), SALT("salt"), FAT("fat"), SUGAR("sugar"), DAIRY("dairy"), EGG("egg"),
    EGG_YOLK("egg_yolk"),
    UNKNOWN("unknown"),
}

enum class YeastType(val rawValue: String) {
    INSTANT("instant"),
    ACTIVE_DRY("active_dry"),
    FRESH("fresh"),
}

enum class FatSource(val rawValue: String) {
    BUTTER("butter"), LARD("lard"),
    OLIVE_OIL("olive_oil"),
    VEGETABLE_OIL("vegetable_oil"),
}

enum class SugarSource(val rawValue: String) {
    GRANULATED_SUGAR("granulated_sugar"),
    HONEY("honey"), MOLASSES("molasses"),
    BARLEY_MALT("barley_malt"),
    BROWN_SUGAR("brown_sugar"),
    POWDERED_SUGAR("powdered_sugar"),
}

enum class DairySource(val rawValue: String) {
    WHOLE_MILK("whole_milk"),
    BUTTERMILK("buttermilk"),
    HEAVY_CREAM("heavy_cream"),
}

enum class FlourType(val rawValue: String) {
    BREAD("bread"),
    DOUBLE_ZERO("00"),
    WHOLE_WHEAT("whole_wheat"),
    RYE("rye"), SEMOLINA("semolina"), SPELT("spelt"), EINKORN("einkorn"),
}

data class ClassifiedIngredient(
    val category: IngredientCategory,
    val flourType: FlourType? = null,
    val yeastType: YeastType? = null,
    val fatSource: FatSource? = null,
    val sugarSource: SugarSource? = null,
    val dairySource: DairySource? = null,
)

/**
 * Port of `ImportModal.tsx`'s keyword-based ingredient classification
 * (`breadiq-mobile/components/ImportModal.tsx`, lines 24-98, plus the
 * `classifyIngredient` combiner at 198-208).
 *
 * Deliberately excludes `FLOUR_G_PER_CUP`/`FAT_G`/`SUGAR_G`/`DAIRY_G`/
 * `YEAST_G`/`SALT_G`, `normalizeUnit()`, and `convertToGrams()` (lines
 * 100-196) — those belong to [IngredientDensityConverter]. Also excludes
 * `CATEGORY_DEFAULT_UNIT` and `parseFraction()` (lines 210-218),
 * consumed by [IngredientLineParser], not this file.
 *
 * **Mobile/web drift found while checking the canonical source**:
 * `ImportModal.tsx`'s header comment says this block is "Inlined from
 * lib/ingredient-densities (Metro can't resolve workspace symlinks)" —
 * implying it should be a byte-for-byte copy of
 * `lib/ingredient-densities/src/index.ts`, the actual shared module used
 * by the web app. It is NOT: the mobile copy adds a 10th
 * `IngredientCategory` case, `"egg_yolk"`, via an extra
 * `if (n.includes("yolk")) return "egg_yolk";` line that doesn't exist in
 * the canonical module at all — there, "egg yolk" simply matches
 * `EGG_KW`'s own `"egg yolk"` keyword and classifies as plain `"egg"`.
 * Every other keyword list and the 5 `detect*` functions are otherwise
 * identical between the two copies (diffed line by line). Since this
 * item's job is porting `ImportModal.tsx` specifically (the mobile
 * client this whole project replaces), the mobile behavior — including
 * `egg_yolk` — is what's ported here, not the canonical module's.
 * Flagging rather than silently reconciling: a recipe imported via the
 * web app and the same recipe imported via this app could classify an
 * "egg yolks" line differently today.
 *
 * **Two more genuine bugs found via unit testing, confirmed against the
 * real JS logic directly (not porting mistakes — reproduced with Node
 * running the actual keyword lists)**: because `classifyName` checks
 * categories in a fixed order and matches on plain substrings, a later
 * category's keyword embedded inside an earlier category's checked word
 * wins the earlier category:
 * - `"Unsalted Butter"` / `"Salted Butter"` contain the substring
 *   `"salt"`, and `salt` is checked before `fat`/`butter` in the
 *   original source — **fixed, not preserved, per direct instruction.**
 *   `fat` (butter + oil keywords) is now checked before `salt`, so both
 *   correctly classify as [IngredientCategory.FAT] and reach
 *   [detectFatSource]. A one-off, explicitly-approved exception to this
 *   project's general preserve-don't-fix default.
 * - `"Buttermilk"` contains the substring `"butter"`, and `fat` is
 *   checked before `dairy` in the original source — **fixed (Step 1
 *   classification only), per direct instruction.** It classified as
 *   `.fat` (`fatSource: .vegetableOil`), never reaching `.dairy`,
 *   meaning [detectDairySource]'s own `"buttermilk"` branch was
 *   unreachable through the normal `classify()` flow for a plain
 *   "Buttermilk" ingredient line. Fixed with a `"buttermilk"`
 *   special-case checked BEFORE the `fat` (butter/oil) block — same
 *   pattern as the existing `"yolk"`-before-egg-keywords special-case
 *   below, not a full category reorder. **Scope note**: this only
 *   corrects classification itself — it does NOT make buttermilk (or any
 *   dairy ingredient) reach the actual generated formula. No dairy
 *   ingredient of any kind reaches `FormulaResult`/a saved `Recipe` via
 *   the import pipeline today, milk included — the Safari-extension
 *   deep-link path that actually writes into the calculator
 *   ([CalculatorImportMapping]) never calls `IngredientClassifier` at
 *   all, since it trusts a `category` the Safari extension already
 *   decided upstream.
 */
object IngredientClassifier {

    private val flourKeywords = listOf(
        "flour", "bread flour", "00 flour", "double zero", "semolina", "whole wheat",
        "wholemeal", "rye", "spelt", "einkorn", "wheat", "all-purpose", "all purpose", "ap flour",
    )
    private val waterKeywords = listOf("water", "warm water", "cold water", "ice water")
    private val yeastKeywords = listOf(
        "yeast", "instant yeast", "active dry", "fresh yeast", "rapid rise",
        "bread machine yeast", "quick rise", "packet yeast",
    )
    private val saltKeywords = listOf("salt", "kosher salt", "sea salt", "table salt", "fine salt", "flaky salt")
    private val butterKeywords = listOf("butter", "unsalted butter", "salted butter", "softened butter", "melted butter")
    private val oilKeywords = listOf(
        "olive oil", "vegetable oil", "oil", "canola oil", "sunflower oil", "lard", "shortening", "crisco",
    )
    private val sugarKeywords = listOf(
        "sugar", "granulated sugar", "honey", "molasses", "barley malt", "malt syrup",
        "brown sugar", "powdered sugar", "maple syrup", "agave", "corn syrup", "cane sugar", "confectioners",
    )
    private val milkKeywords = listOf("milk", "whole milk", "skim milk", "2% milk", "buttermilk", "nonfat milk")
    private val creamKeywords = listOf("heavy cream", "cream", "whipping cream", "double cream")
    private val eggKeywords = listOf("egg", "eggs", "large egg", "large eggs", "egg white", "egg yolk", "whole egg")

    /**
     * `classifyIngredientName()` — lines 44-58. Order is significant: the
     * FIRST matching category wins (e.g. "flour" is checked before
     * "sugar", so a hypothetical name matching both would classify as
     * flour), and the `"yolk"` special-case is checked BEFORE the general
     * [eggKeywords] list, so any name containing "yolk" preempts the
     * plain "egg" classification.
     *
     * **`fat` (butter + oil) is checked before `salt`** — a deliberate
     * fix, not the source's original order. See this object's own doc
     * comment for the fix write-up.
     *
     * **`"buttermilk"` is special-cased BEFORE the `fat` block** — same
     * deliberate-fix category as the salt/fat reorder above, and the
     * same "single substring special-case ahead of a broader category
     * check" shape already used for `"yolk"` below.
     */
    fun classifyName(name: String): IngredientCategory {
        val n = name.lowercase().trim()
        if (flourKeywords.any { n.contains(it) }) return IngredientCategory.FLOUR
        if (waterKeywords.any { n.contains(it) }) return IngredientCategory.WATER
        if (yeastKeywords.any { n.contains(it) }) return IngredientCategory.YEAST
        if (n.contains("buttermilk")) return IngredientCategory.DAIRY
        if (butterKeywords.any { n.contains(it) }) return IngredientCategory.FAT
        if (oilKeywords.any { n.contains(it) }) return IngredientCategory.FAT
        if (saltKeywords.any { n.contains(it) }) return IngredientCategory.SALT
        if (n.contains("yolk")) return IngredientCategory.EGG_YOLK
        if (eggKeywords.any { n.contains(it) }) return IngredientCategory.EGG
        if (creamKeywords.any { n.contains(it) }) return IngredientCategory.DAIRY
        if (milkKeywords.any { n.contains(it) }) return IngredientCategory.DAIRY
        if (sugarKeywords.any { n.contains(it) }) return IngredientCategory.SUGAR
        return IngredientCategory.UNKNOWN
    }

    /**
     * `detectYeastType()` — lines 60-65. Note: unlike [classifyName],
     * this (and the other four `detect*` functions below) does NOT trim
     * the lowercased name in the source — harmless in practice since
     * substring matching is unaffected by extra leading/trailing
     * whitespace, but preserved literally rather than "cleaned up."
     */
    fun detectYeastType(name: String): YeastType {
        val n = name.lowercase()
        if (n.contains("fresh") || n.contains("cake")) return YeastType.FRESH
        if (n.contains("active dry")) return YeastType.ACTIVE_DRY
        return YeastType.INSTANT
    }

    /** `detectFatSource()` — lines 66-72. */
    fun detectFatSource(name: String): FatSource {
        val n = name.lowercase()
        if (n.contains("butter")) return FatSource.BUTTER
        if (n.contains("lard") || n.contains("shortening")) return FatSource.LARD
        if (n.contains("olive")) return FatSource.OLIVE_OIL
        return FatSource.VEGETABLE_OIL
    }

    /** `detectDairySource()` — lines 73-78. */
    fun detectDairySource(name: String): DairySource {
        val n = name.lowercase()
        if (n.contains("buttermilk")) return DairySource.BUTTERMILK
        if (n.contains("cream")) return DairySource.HEAVY_CREAM
        return DairySource.WHOLE_MILK
    }

    /**
     * `detectFlourType()` — lines 79-89. The all-purpose/ap-flour check
     * and the final fallback both resolve to `.bread` — NOT dead code
     * despite the duplicate outcome: it still runs before the `00` check
     * below it, so a name matching both patterns resolves to `.bread`,
     * not `.doubleZero`. Order is load-bearing here.
     */
    fun detectFlourType(name: String): FlourType {
        val n = name.lowercase()
        if (n.contains("whole wheat") || n.contains("wholemeal")) return FlourType.WHOLE_WHEAT
        if (n.contains("rye")) return FlourType.RYE
        if (n.contains("semolina")) return FlourType.SEMOLINA
        if (n.contains("spelt")) return FlourType.SPELT
        if (n.contains("einkorn")) return FlourType.EINKORN
        if (n.contains("all-purpose") || n.contains("all purpose") || n.contains("ap flour")) return FlourType.BREAD
        if (Regex("\\b00\\b").containsMatchIn(n) || n.contains("double zero") || n.contains("doppio zero")) {
            return FlourType.DOUBLE_ZERO
        }
        return FlourType.BREAD
    }

    /** `detectSugarSource()` — lines 90-98. */
    fun detectSugarSource(name: String): SugarSource {
        val n = name.lowercase()
        if (n.contains("honey")) return SugarSource.HONEY
        if (n.contains("molasses")) return SugarSource.MOLASSES
        if (n.contains("barley malt") || n.contains("malt syrup")) return SugarSource.BARLEY_MALT
        if (n.contains("brown sugar")) return SugarSource.BROWN_SUGAR
        if (n.contains("powdered") || n.contains("confectioners")) return SugarSource.POWDERED_SUGAR
        return SugarSource.GRANULATED_SUGAR
    }

    /**
     * `classifyIngredient()` — lines 198-208. The natural composition of
     * [classifyName] with the relevant `detect*` sub-classifier; included
     * here (rather than deferred to a later item) since it uses only
     * pieces already in this file, with no density/parsing logic of its
     * own.
     */
    fun classify(name: String): ClassifiedIngredient {
        val category = classifyName(name)
        return when (category) {
            IngredientCategory.FLOUR -> ClassifiedIngredient(category = category, flourType = detectFlourType(name))
            IngredientCategory.YEAST -> ClassifiedIngredient(category = category, yeastType = detectYeastType(name))
            IngredientCategory.FAT -> ClassifiedIngredient(category = category, fatSource = detectFatSource(name))
            IngredientCategory.SUGAR -> ClassifiedIngredient(category = category, sugarSource = detectSugarSource(name))
            IngredientCategory.DAIRY -> ClassifiedIngredient(category = category, dairySource = detectDairySource(name))
            else -> ClassifiedIngredient(category = category)
        }
    }
}
