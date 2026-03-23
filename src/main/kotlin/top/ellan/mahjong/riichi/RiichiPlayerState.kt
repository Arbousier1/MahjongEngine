package top.ellan.mahjong.riichi

import top.ellan.mahjong.riichi.model.ClaimTarget
import top.ellan.mahjong.riichi.model.DoubleYakuman
import top.ellan.mahjong.riichi.model.Fuuro
import top.ellan.mahjong.riichi.model.GeneralSituation
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.MeldType
import top.ellan.mahjong.riichi.model.PersonalSituation
import top.ellan.mahjong.riichi.model.ScoringStick
import top.ellan.mahjong.riichi.model.TileInstance
import top.ellan.mahjong.riichi.model.Wind
import top.ellan.mahjong.riichi.model.YakuSettlement
import top.ellan.mahjong.riichi.model.toMahjongTileList
import mahjongutils.CalcContext
import mahjongutils.hora.HoraOptions
import mahjongutils.hora.hora
import mahjongutils.models.Tile
import mahjongutils.models.Tatsu
import mahjongutils.models.isYaochu
import mahjongutils.shanten.ShantenWithGot
import mahjongutils.shanten.ShantenWithoutGot
import mahjongutils.shanten.UnionShantenResult
import mahjongutils.shanten.furoChanceShanten
import mahjongutils.shanten.shanten
import mahjongutils.yaku.Yaku
import mahjongutils.yaku.Yakus
import kotlin.math.absoluteValue
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger

data class RiichiDiscardSuggestion(
    val tile: MahjongTile,
    val shantenNum: Int,
    val advanceTiles: List<MahjongTile>,
    val advanceCount: Int,
    val goodShapeAdvanceCount: Int,
    val improvementCount: Int,
    val goodShapeImprovementCount: Int
)

open class RiichiPlayerState(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean = true
) {
    companion object {
        private val LOGGER: Logger = Logger.getLogger(RiichiPlayerState::class.java.name)

        private data class StableUtilShantenInvoker(
            val internalArgsConstructor: Constructor<*>,
            val shantenMethod: Method
        )

        private val stableUtilShantenInvoker: StableUtilShantenInvoker? = runCatching {
            val internalArgsClass = Class.forName("mahjongutils.shanten.InternalShantenArgs")
            val calcContextClass = Class.forName("mahjongutils.CalcContext")
            val shantenKtClass = Class.forName("mahjongutils.shanten.ShantenKt")
            val internalArgsConstructor = internalArgsClass.getDeclaredConstructor(
                List::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val shantenMethod = shantenKtClass.getDeclaredMethod(
                "shanten",
                calcContextClass,
                internalArgsClass
            ).apply { isAccessible = true }
            StableUtilShantenInvoker(
                internalArgsConstructor = internalArgsConstructor,
                shantenMethod = shantenMethod
            )
        }.getOrElse { error ->
            LOGGER.log(Level.WARNING, "Unable to initialize util stable shanten invoker", error)
            null
        }

        internal var shantenCalculator: (
            tiles: List<Tile>,
            furo: List<mahjongutils.models.Furo>,
            bestShantenOnly: Boolean
        ) -> UnionShantenResult = { tiles, furo, bestOnly ->
            shanten(
                tiles = tiles,
                furo = furo,
                bestShantenOnly = bestOnly
            )
        }
    }

    val hands: MutableList<TileInstance> = mutableListOf()
    var lastDrawnTile: TileInstance? = null
    var autoArrangeHands: Boolean = true
    val fuuroList: MutableList<Fuuro> = mutableListOf()
    var riichiSengenTile: TileInstance? = null
    val discardedTiles: MutableList<TileInstance> = mutableListOf()
    val discardedTilesForDisplay: MutableList<TileInstance> = mutableListOf()
    var ready: Boolean = false
    var riichi: Boolean = false
    var doubleRiichi: Boolean = false
    val sticks: MutableList<ScoringStick> = mutableListOf()
    var points: Int = 0
    var basicThinkingTime: Int = 0
    var extraThinkingTime: Int = 0
    private var analysisStateVersion: Long = 0
    private var cachedTilePairsForRiichiVersion: Long = -1
    private var cachedTilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>> = emptyList()
    private var cachedMachiVersion: Long = -1
    private var cachedMachi: List<MahjongTile> = emptyList()
    private var cachedCurrentShantenVersion: Long = -1
    private var cachedCurrentShantenResult: UnionShantenResult? = null
    private var cachedDiscardSuggestionsVersion: Long = -1
    private var cachedDiscardSuggestions: List<RiichiDiscardSuggestion> = emptyList()
    private var cachedFuroReactionVersion: Long = -1
    private val cachedFuroReactions: MutableMap<Pair<MahjongTile, Boolean>, FuroReactionAnalysis?> = mutableMapOf()

    val riichiStickAmount: Int
        get() = sticks.count { it == ScoringStick.P1000 }

    val isMenzenchin: Boolean
        get() = fuuroList.isEmpty() || fuuroList.all { it.isKan && !it.isOpen }

    val isRiichiable: Boolean
        get() = isMenzenchin && !(riichi || doubleRiichi) && tilePairsForRiichi.isNotEmpty() && points >= 1000

    val numbersOfYaochuuhaiTypes: Int
        get() = hands.map { it.scoringTile }.distinct().count { it.isYaochu }

    fun chii(
        tile: TileInstance,
        tilePair: Pair<MahjongTile, MahjongTile>,
        target: RiichiPlayerState
    ) {
        lastDrawnTile = null
        val tileShuntsu = mutableListOf(
            tile,
            hands.first { it.mahjongTile == tilePair.first },
            hands.first { it.mahjongTile == tilePair.second }
        ).also { it.sortBy { candidate -> candidate.mahjongTile.sortOrder } }
        val fuuro = Fuuro(
            type = MeldType.CHII,
            tileInstances = tileShuntsu,
            claimTarget = ClaimTarget.LEFT,
            claimTile = tile
        )
        hands -= tileShuntsu.filter { it != tile }.toSet()
        target.discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun pon(tile: TileInstance, claimTarget: ClaimTarget, target: RiichiPlayerState) {
        lastDrawnTile = null
        val tilesForPon = tilesForPon(tile)
        val fuuro = Fuuro(MeldType.PON, tilesForPon, claimTarget, tile)
        hands -= tilesForPon.filter { it != tile }.toSet()
        target.discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun minkan(tile: TileInstance, claimTarget: ClaimTarget, target: RiichiPlayerState) {
        lastDrawnTile = null
        val tilesForMinkan = tilesForMinkan(tile)
        val fuuro = Fuuro(MeldType.MINKAN, tilesForMinkan, claimTarget, tile)
        hands -= tilesForMinkan.filter { it != tile }.toSet()
        target.discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun ankan(tile: TileInstance) {
        lastDrawnTile = null
        val tilesForAnkan = tilesForAnkan(tile)
        val fuuro = Fuuro(MeldType.ANKAN, tilesForAnkan, ClaimTarget.SELF, tile)
        hands -= tilesForAnkan.toSet()
        discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun kakan(tile: TileInstance) {
        lastDrawnTile = null
        val minPon = fuuroList.find { it.isPon && it.tileInstances.any { existing -> existing.mahjongTile.sameKind(tile.mahjongTile) } } ?: return
        fuuroList -= minPon
        val tiles = minPon.tileInstances.toMutableList().also { it += tile }
        val fuuro = Fuuro(MeldType.KAKAN, tiles, minPon.claimTarget, minPon.claimTile)
        hands -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun canPon(tile: TileInstance): Boolean =
        !(riichi || doubleRiichi) && (reactionOptionsFor(tile, allowChii = false, canRon = false)?.canPon == true)

    fun canMinkan(tile: TileInstance): Boolean =
        !(riichi || doubleRiichi) && (reactionOptionsFor(tile, allowChii = false, canRon = false)?.canMinkan == true)

    val canKakan: Boolean
        get() = tilesCanKakan.isNotEmpty()

    val canAnkan: Boolean
        get() = tilesCanAnkan.isNotEmpty()

    fun canChii(tile: TileInstance): Boolean = !(riichi || doubleRiichi) && availableChiiPairs(tile).isNotEmpty()

    private fun tilesForPon(tile: TileInstance): List<TileInstance> =
        sameTilesInHands(tile).apply {
            if (size > 2) {
                remove(first { !it.mahjongTile.isRed })
                sortBy { it.mahjongTile.isRed }
            }
            add(tile)
        }

    private fun tilesForMinkan(tile: TileInstance): List<TileInstance> =
        sameTilesInHands(tile).also { it += tile }

    private fun tilesForAnkan(tile: TileInstance): List<TileInstance> =
        sameTilesInHands(tile)

    val tilesCanAnkan: Set<TileInstance>
        get() {
            val candidates = hands.distinctBy { it.scoringTile.code }.filter { distinct ->
                hands.count { it.mahjongTile.sameKind(distinct.mahjongTile) } == 4
            }.toMutableSet()
            if (!riichi && !doubleRiichi) {
                return candidates
            }
            val drawnTile = lastDrawnTile?.mahjongTile ?: return emptySet()
            candidates.removeIf { candidate -> !candidate.mahjongTile.sameKind(drawnTile) }
            if (candidates.isEmpty()) {
                return emptySet()
            }

            val machiBefore = machi
            for (candidate in candidates.toList()) {
                val handsCopy = hands.toMutableList()
                val anKanTilesInHands = hands.filter { tile -> tile.mahjongTile.sameKind(candidate.mahjongTile) }.toMutableList()
                handsCopy -= anKanTilesInHands.toSet()
                val fuuroListCopy = fuuroList.toMutableList().apply {
                    add(Fuuro(MeldType.ANKAN, anKanTilesInHands, ClaimTarget.SELF, candidate))
                }
                val calculatedMachi = calculateMachi(handsCopy.toMahjongTileList(), fuuroListCopy)
                if (calculatedMachi != machiBefore) {
                    candidates -= candidate
                }
            }
            return candidates
        }

    private val tilesCanKakan: MutableSet<Pair<TileInstance, ClaimTarget>>
        get() = mutableSetOf<Pair<TileInstance, ClaimTarget>>().apply {
            fuuroList.filter { it.isPon }.forEach { fuuro ->
                val tile = hands.find { it.mahjongTile.sameKind(fuuro.claimTile.mahjongTile) }
                if (tile != null) {
                    add(tile to fuuro.claimTarget)
                }
            }
        }

    fun availableChiiPairs(tile: TileInstance): List<Pair<MahjongTile, MahjongTile>> =
        analyzeFuroReaction(tile, allowChii = true)?.chiChoices?.map { it.first } ?: emptyList()

    fun reactionOptionsFor(tile: TileInstance, allowChii: Boolean, canRon: Boolean): ReactionOptions? {
        if (riichi || doubleRiichi) {
            return if (canRon) {
                ReactionOptions(
                    canRon = true,
                    canPon = false,
                    canMinkan = false,
                    chiiPairs = emptyList(),
                    suggestedResponse = ReactionResponse(ReactionType.RON, null)
                )
            } else {
                null
            }
        }

        val analysis = analyzeFuroReaction(tile, allowChii)
        val canPon = analysis?.pon != null
        val canMinkan = analysis?.minkan != null
        val chiiPairs = analysis?.chiChoices?.map { it.first } ?: emptyList()
        if (!canRon && !canPon && !canMinkan && chiiPairs.isEmpty()) {
            return null
        }

        val suggestion = when {
            canRon -> ReactionResponse(ReactionType.RON, null)
            analysis == null -> ReactionResponse(ReactionType.SKIP, null)
            else -> suggestedReactionResponse(analysis)
        }
        return ReactionOptions(
            canRon = canRon,
            canPon = canPon,
            canMinkan = canMinkan,
            chiiPairs = chiiPairs,
            suggestedResponse = suggestion
        )
    }

    private fun findChiiTileVariant(baseTile: MahjongTile, exclude: MahjongTile? = null): MahjongTile? =
        hands.firstOrNull { it.mahjongTile.baseTile == baseTile && it.mahjongTile != exclude }?.mahjongTile

    fun tilePairForPon(tile: TileInstance): Pair<MahjongTile, MahjongTile> {
        val tiles = tilesForPon(tile)
        return tiles[0].mahjongTile to tiles[1].mahjongTile
    }

    val tilePairsForRiichi: List<Pair<MahjongTile, List<MahjongTile>>>
        get() {
            if (cachedTilePairsForRiichiVersion == analysisStateVersion) {
                return cachedTilePairsForRiichi
            }
            cachedTilePairsForRiichi = buildList {
                if (hands.size != 14) return@buildList
                val shantenWithGot = currentShantenWithGot() ?: return@buildList
                shantenWithGot.discardToAdvance.entries
                    .filter { it.value.shantenNum == 0 }
                    .map { MahjongTile.fromUtilsTile(it.key) to it.value.advance.map(MahjongTile::fromUtilsTile).distinct() }
                    .distinct()
                    .forEach(::add)
            }
            cachedTilePairsForRiichiVersion = analysisStateVersion
            return cachedTilePairsForRiichi
        }

    private fun sameTilesInHands(tile: TileInstance): MutableList<TileInstance> =
        hands.filter { it.mahjongTile.sameKind(tile.mahjongTile) }.toMutableList()

    val isTenpai: Boolean
        get() = machi.isNotEmpty()

    private val machi: List<MahjongTile>
        get() {
            if (cachedMachiVersion != analysisStateVersion) {
                cachedMachi = currentShantenWithoutGot()
                    ?.takeIf { it.shantenNum == 0 }
                    ?.advance
                    ?.map(MahjongTile::fromUtilsTile)
                    ?.distinct()
                    ?: emptyList()
                cachedMachiVersion = analysisStateVersion
            }
            return cachedMachi
        }

    private fun calculateMachi(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList
    ): List<MahjongTile> = shantenWithoutGot(hands, fuuroList)
        ?.takeIf { it.shantenNum == 0 }
        ?.advance
        ?.map(MahjongTile::fromUtilsTile)
        ?.distinct()
        ?: emptyList()

    fun discardSuggestions(): List<RiichiDiscardSuggestion> {
        if (cachedDiscardSuggestionsVersion == analysisStateVersion) {
            return cachedDiscardSuggestions
        }
        val shantenWithGot = currentShantenWithGot()
        cachedDiscardSuggestions = if (shantenWithGot == null) {
            emptyList()
        } else {
            shantenWithGot.discardToAdvance.entries
                .sortedWith(
                    compareBy<Map.Entry<Tile, ShantenWithoutGot>> { it.value.shantenNum }
                        .thenByDescending { it.value.goodShapeAdvanceNum ?: -1 }
                        .thenByDescending { it.value.advanceNum }
                        .thenByDescending { it.value.goodShapeImprovementNum ?: -1 }
                        .thenByDescending { it.value.improvementNum ?: -1 }
                        .thenBy { it.key }
                )
                .map { (tile, shanten) ->
                    RiichiDiscardSuggestion(
                        tile = MahjongTile.fromUtilsTile(tile),
                        shantenNum = shanten.shantenNum,
                        advanceTiles = shanten.advance.map(MahjongTile::fromUtilsTile).distinct(),
                        advanceCount = shanten.advanceNum,
                        goodShapeAdvanceCount = shanten.goodShapeAdvanceNum ?: 0,
                        improvementCount = shanten.improvementNum ?: 0,
                        goodShapeImprovementCount = shanten.goodShapeImprovementNum ?: 0
                    )
                }
                .distinctBy { it.tile }
        }
        cachedDiscardSuggestionsVersion = analysisStateVersion
        return cachedDiscardSuggestions
    }

    fun bestDiscardSuggestions(): List<MahjongTile> {
        return discardSuggestions().map { it.tile }
    }

    fun calculateMachiAndHan(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation
    ): Map<MahjongTile, Int> {
        val allMachi = calculateMachi(hands, fuuroList)
        return allMachi.associateWith { machiTile ->
            val yakuSettlement = calculateYakuSettlement(
                winningTile = machiTile,
                isWinningTileInHands = false,
                hands = hands,
                fuuroList = fuuroList,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation,
                doraIndicators = emptyList(),
                uraDoraIndicators = emptyList()
            )
            if (yakuSettlement.yakuList.isNotEmpty() || yakuSettlement.yakumanList.isNotEmpty()) -1 else yakuSettlement.han
        }
    }

    fun isFuriten(tile: TileInstance, discards: List<TileInstance>): Boolean {
        val baseMachi = machi.map { it.baseTile }
        val target = tile.mahjongTile.baseTile
        if (target in discardedTiles.map { it.mahjongTile.baseTile }) {
            return true
        }
        val lastOwnDiscard = discardedTiles.lastOrNull()
        if (lastOwnDiscard != null) {
            val sameTurnStartIndex = discards.indexOf(lastOwnDiscard)
            if (sameTurnStartIndex >= 0) {
                for (index in sameTurnStartIndex until discards.lastIndex) {
                    if (discards[index].mahjongTile.baseTile in baseMachi) {
                        return true
                    }
                }
            }
        }
        val riichiDiscard = riichiSengenTile
        if ((riichi || doubleRiichi) && riichiDiscard != null) {
            val riichiStartIndex = discards.indexOf(riichiDiscard)
            if (riichiStartIndex >= 0) {
                for (index in riichiStartIndex until discards.lastIndex) {
                    if (discards[index].mahjongTile.baseTile in baseMachi) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun isFuriten(
        tile: MahjongTile,
        discards: List<MahjongTile>,
        machi: List<MahjongTile> = this.machi
    ): Boolean {
        val discardedTiles = discardedTiles.map { it.mahjongTile.baseTile }
        val target = tile.baseTile
        val baseMachi = machi.map { it.baseTile }
        if (target in discardedTiles) return true
        if (discardedTiles.isNotEmpty()) {
            val lastDiscard = discardedTiles.last()
            val sameTurnStartIndex = discards.map { it.baseTile }.indexOf(lastDiscard)
            for (index in sameTurnStartIndex until discards.lastIndex) {
                if (discards[index].baseTile in baseMachi) return true
            }
        }
        val riichiSengenTile = riichiSengenTile?.mahjongTile?.baseTile ?: return false
        if (riichi || doubleRiichi) {
            val riichiStartIndex = discards.map { it.baseTile }.indexOf(riichiSengenTile)
            for (index in riichiStartIndex until discards.lastIndex) {
                if (discards[index].baseTile in baseMachi) return true
            }
        }
        return false
    }

    fun isIppatsu(players: List<RiichiPlayerState>, discards: List<TileInstance>): Boolean {
        if (riichi) {
            val riichiSengenIndex = discards.indexOf(riichiSengenTile!!)
            if (discards.lastIndex - riichiSengenIndex > 4) return false
            val someoneCalls = discards.slice(riichiSengenIndex..discards.lastIndex).any { tile ->
                players.any { player -> player.fuuroList.any { fuuro -> tile in fuuro.tileInstances } }
            }
            return !someoneCalls
        }
        return false
    }

    fun isKokushimuso(tile: MahjongTile): Boolean {
        val result = runCatching {
            hora(
                tiles = (hands.toMahjongTileList() + tile).toUtilsTiles(),
                furo = fuuroList.map { it.utilsFuro },
                agari = tile.utilsTile,
                tsumo = false,
                options = HoraOptions.Default
            )
        }.getOrElse { error ->
            LOGGER.log(
                shantenFailureLogLevel(error),
                "Kokushi check failed (hands=${hands.size}, fuuro=${fuuroList.size}, tile=$tile)",
                error
            )
            return false
        }
        return result.yaku.any { it.name == "Kokushi" || it.name == "KokushiThirteenWaiting" }
    }

    fun canWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation
    ): Boolean {
        val yakuSettlement = calculateYakuSettlement(
            winningTile = winningTile,
            isWinningTileInHands = isWinningTileInHands,
            hands = hands,
            fuuroList = fuuroList,
            rule = rule,
            generalSituation = generalSituation,
            personalSituation = personalSituation
        )
        return yakuSettlement.yakumanList.isNotEmpty() ||
            yakuSettlement.doubleYakumanList.isNotEmpty() ||
            yakuSettlement.han >= rule.minimumHan.han
    }

    private fun canFormWinningHand(hands: List<MahjongTile>, fuuroList: List<Fuuro>): Boolean =
        runCatching {
            shanten(
                tiles = hands.toUtilsTiles(),
                furo = fuuroList.map { it.utilsFuro },
                bestShantenOnly = true
            ).shantenInfo.shantenNum == -1
        }.getOrElse { error ->
            LOGGER.log(
                shantenFailureLogLevel(error),
                "Winning hand pre-check failed (hands=${hands.size}, fuuro=${fuuroList.size})",
                error
            )
            false
        }

    private fun calculateYakuSettlement(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        hands: List<MahjongTile>,
        fuuroList: List<Fuuro>,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile> = listOf(),
        uraDoraIndicators: List<MahjongTile> = listOf()
    ): YakuSettlement {
        val fullHands = hands.toMutableList().also { if (!isWinningTileInHands) it += winningTile }
        if (!canFormWinningHand(fullHands, fuuroList)) {
            return YakuSettlement.NO_YAKU
        }

        val horaOptions = toHoraOptions(rule)
        val yakus = Yakus(horaOptions)
        val extraYaku = buildExtraYaku(yakus, generalSituation, personalSituation)
        val doraCount = countDora(fullHands, fuuroList, generalSituation.doraIndicators)
        val uraDoraCount = if (personalSituation.isRiichi || personalSituation.isDoubleRiichi) {
            countDora(fullHands, fuuroList, generalSituation.uraDoraIndicators)
        } else {
            0
        }
        val redFiveCount = if (rule.redFive == MahjongRule.RedFive.NONE) {
            0
        } else {
            fullHands.count { it.isRed } + fuuroList.sumOf { fuuro -> fuuro.tileInstances.count { it.mahjongTile.isRed } }
        }

        val hora = runCatching {
            hora(
                tiles = fullHands.toUtilsTiles(),
                furo = fuuroList.map { it.utilsFuro },
                agari = winningTile.utilsTile,
                tsumo = personalSituation.isTsumo,
                dora = doraCount + uraDoraCount,
                selfWind = personalSituation.jikaze.utilsWind,
                roundWind = generalSituation.bakaze.utilsWind,
                extraYaku = extraYaku,
                options = horaOptions
            )
        }.getOrElse { error ->
            LOGGER.log(
                shantenFailureLogLevel(error),
                "Yaku settlement hora failed (hands=${fullHands.size}, fuuro=${fuuroList.size}, winningTile=$winningTile, tsumo=${personalSituation.isTsumo})",
                error
            )
            return YakuSettlement.NO_YAKU
        }

        val finalNormalYakuList = mutableListOf<String>()
        val finalYakumanList = mutableListOf<String>()
        val finalDoubleYakumanList = mutableListOf<DoubleYakuman>()
        hora.yaku.sortedBy { it.name }.forEach { yaku ->
            when (val doubleYakuman = toDoubleYakuman(yaku.name)) {
                null -> if (yaku.isYakuman) {
                    finalYakumanList += canonicalYakuName(yaku.name)
                } else {
                    finalNormalYakuList += canonicalYakuName(yaku.name)
                }

                else -> finalDoubleYakumanList += doubleYakuman
            }
        }
        if (finalNormalYakuList.isEmpty() && finalYakumanList.isEmpty() && finalDoubleYakumanList.isEmpty()) {
            return YakuSettlement.NO_YAKU
        }
        repeat(doraCount) {
            finalNormalYakuList += "DORA"
        }
        repeat(uraDoraCount) {
            finalNormalYakuList += "URADORA"
        }

        val fuuroListForSettlement = fuuroList.map { fuuro ->
            (!fuuro.isOpen && fuuro.isKan) to fuuro.tileInstances.toMahjongTileList()
        }
        val isParent = personalSituation.jikaze == Wind.EAST
        val score = if (personalSituation.isTsumo) {
            if (isParent) {
                hora.parentPoint.tsumoTotal.toInt()
            } else {
                hora.childPoint.tsumoTotal.toInt()
            }
        } else {
            if (isParent) {
                hora.parentPoint.ron.toInt()
            } else {
                hora.childPoint.ron.toInt()
            }
        }

        return YakuSettlement(
            displayName = displayName,
            uuid = uuid,
            yakuList = finalNormalYakuList,
            yakumanList = finalYakumanList,
            doubleYakumanList = finalDoubleYakumanList,
            redFiveCount = redFiveCount,
            riichi = riichi || doubleRiichi,
            winningTile = winningTile,
            hands = this.hands.toMahjongTileList(),
            fuuroList = fuuroListForSettlement,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = if (finalYakumanList.isEmpty() && finalDoubleYakumanList.isEmpty() && finalNormalYakuList.isEmpty()) 0 else hora.hu,
            han = hora.han + redFiveCount,
            score = score
        )
    }

    fun calcYakuSettlementForWin(
        winningTile: MahjongTile,
        isWinningTileInHands: Boolean,
        rule: MahjongRule,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation,
        doraIndicators: List<MahjongTile>,
        uraDoraIndicators: List<MahjongTile>
    ): YakuSettlement = calculateYakuSettlement(
        winningTile = winningTile,
        isWinningTileInHands = isWinningTileInHands,
        hands = hands.toMahjongTileList(),
        fuuroList = fuuroList,
        rule = rule,
        generalSituation = generalSituation,
        personalSituation = personalSituation,
        doraIndicators = doraIndicators,
        uraDoraIndicators = uraDoraIndicators
    )

    fun drawTile(tile: TileInstance) {
        hands += tile
        lastDrawnTile = tile
        invalidateHandAnalysis()
    }

    fun declareRiichi(riichiSengenTile: TileInstance, isFirstRound: Boolean) {
        this.riichiSengenTile = riichiSengenTile
        if (isFirstRound) doubleRiichi = true else riichi = true
        invalidateHandAnalysis()
    }

    fun discardTile(tile: TileInstance): TileInstance? =
        hands.find { it.id == tile.id }?.also { discardTileInstance(it) }

    fun discardTile(tile: MahjongTile): TileInstance? =
        hands.findLast { it.mahjongTile == tile }?.also { discardTileInstance(it) }

    fun resetRoundState() {
        hands.clear()
        fuuroList.clear()
        discardedTiles.clear()
        discardedTilesForDisplay.clear()
        lastDrawnTile = null
        riichiSengenTile = null
        riichi = false
        doubleRiichi = false
        invalidateHandAnalysis()
    }

    private fun invalidateHandAnalysis() {
        analysisStateVersion++
    }

    private fun currentShantenResult(): UnionShantenResult? {
        if (cachedCurrentShantenVersion != analysisStateVersion) {
            cachedCurrentShantenResult = analyzeShanten()
            cachedCurrentShantenVersion = analysisStateVersion
        }
        return cachedCurrentShantenResult
    }

    private fun currentShantenWithGot(): ShantenWithGot? =
        currentShantenResult()?.shantenInfo as? ShantenWithGot

    private fun currentShantenWithoutGot(): ShantenWithoutGot? =
        currentShantenResult()?.shantenInfo as? ShantenWithoutGot

    private fun analyzeShanten(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList,
        bestShantenOnly: Boolean = true
    ): UnionShantenResult? {
        val tiles = hands.toUtilsTiles()
        val furo = fuuroList.map { it.utilsFuro }
        val primary = runCatching {
            shantenCalculator(tiles, furo, bestShantenOnly)
        }
        if (primary.isSuccess) {
            return primary.getOrNull()
        }
        val primaryError = primary.exceptionOrNull() ?: return null
        if (bestShantenOnly && primaryError.isNoSuchElementFailure()) {
            val fallback = runCatching {
                shantenCalculator(tiles, furo, false)
            }
            if (fallback.isSuccess) {
                LOGGER.log(
                    Level.FINE,
                    "Shanten best-only analysis failed; fallback succeeded (hands=${hands.size}, fuuro=${fuuroList.size})",
                    primaryError
                )
                return fallback.getOrNull()
            }
            val fallbackError = fallback.exceptionOrNull() ?: return null
            if (fallbackError.isNoSuchElementFailure()) {
                val stableFallback = runCatching {
                    shantenWithUtilStableArgs(tiles, furo, false)
                }
                if (stableFallback.isSuccess) {
                    LOGGER.log(
                        Level.FINE,
                        "Shanten fallback recovered via util stable args (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=false)",
                        fallbackError
                    )
                    return stableFallback.getOrNull()
                }
                val stableFallbackError = stableFallback.exceptionOrNull() ?: return null
                LOGGER.log(
                    shantenFailureLogLevel(stableFallbackError),
                    "Shanten stable fallback failed (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=false)",
                    stableFallbackError
                )
                return null
            }
            val fallbackLevel = shantenFailureLogLevel(fallbackError)
            LOGGER.log(
                fallbackLevel,
                "Shanten analysis fallback failed (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=false)",
                fallbackError
            )
            return null
        }
        if (!bestShantenOnly && primaryError.isNoSuchElementFailure()) {
            val stableFallback = runCatching {
                shantenWithUtilStableArgs(tiles, furo, false)
            }
            if (stableFallback.isSuccess) {
                LOGGER.log(
                    Level.FINE,
                    "Shanten recovered via util stable args (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=false)",
                    primaryError
                )
                return stableFallback.getOrNull()
            }
            val stableFallbackError = stableFallback.exceptionOrNull() ?: return null
            LOGGER.log(
                shantenFailureLogLevel(stableFallbackError),
                "Shanten stable fallback failed (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=false)",
                stableFallbackError
            )
            return null
        }
        val level = shantenFailureLogLevel(primaryError)
        LOGGER.log(
            level,
            "Shanten analysis failed (hands=${hands.size}, fuuro=${fuuroList.size}, bestOnly=$bestShantenOnly)",
            primaryError
        )
        return null
    }

    private fun shantenWithUtilStableArgs(
        tiles: List<Tile>,
        furo: List<mahjongutils.models.Furo>,
        bestShantenOnly: Boolean
    ): UnionShantenResult {
        val invoker = stableUtilShantenInvoker
            ?: throw IllegalStateException("mahjongutils internal shanten API is unavailable")
        val args = invoker.internalArgsConstructor.newInstance(
            tiles,
            furo,
            true,
            false,
            bestShantenOnly,
            true,
            true
        )
        val result = invoker.shantenMethod.invoke(null, CalcContext(), args)
        return result as? UnionShantenResult
            ?: throw IllegalStateException(
                "mahjongutils internal shanten returned unexpected result type: ${result?.javaClass?.name}"
            )
    }

    private fun shantenFailureLogLevel(error: Throwable): Level =
        if (error is IllegalArgumentException) Level.FINE else Level.WARNING

    private fun Throwable.isNoSuchElementFailure(): Boolean {
        var current: Throwable? = this
        repeat(8) {
            if (current == null) {
                return false
            }
            if (current is kotlin.NoSuchElementException || current is java.util.NoSuchElementException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun shantenWithoutGot(
        hands: List<MahjongTile>,
        fuuroList: List<Fuuro>
    ): ShantenWithoutGot? = analyzeShanten(hands, fuuroList)?.shantenInfo as? ShantenWithoutGot

    private fun discardTileInstance(tile: TileInstance) {
        hands -= tile
        if (lastDrawnTile?.id == tile.id) {
            lastDrawnTile = null
        }
        discardedTiles += tile
        discardedTilesForDisplay += tile
        invalidateHandAnalysis()
    }

    private fun List<MahjongTile>.toUtilsTiles(): List<Tile> =
        map { it.utilsTile }

    private fun analyzeFuroReaction(tile: TileInstance, allowChii: Boolean): FuroReactionAnalysis? {
        if (cachedFuroReactionVersion != analysisStateVersion) {
            cachedFuroReactions.clear()
            cachedFuroReactionVersion = analysisStateVersion
        }
        val cacheKey = tile.mahjongTile.baseTile to allowChii
        return cachedFuroReactions.getOrPut(cacheKey) {
            val result = runCatching {
                furoChanceShanten(
                    tiles = hands.toMahjongTileList().toUtilsTiles(),
                    chanceTile = tile.scoringTile,
                    allowChi = allowChii,
                    bestShantenOnly = false,
                    allowKuikae = false
                )
            }.getOrElse { error ->
                LOGGER.log(
                    shantenFailureLogLevel(error),
                    "Furo reaction analysis failed (hands=${hands.size}, fuuro=${fuuroList.size}, tile=${tile.mahjongTile}, allowChii=$allowChii)",
                    error
                )
                return@getOrPut null
            }
            val shanten = result.shantenInfo
            val chiChoices = if (allowChii) {
                shanten.chi.entries.mapNotNull { entry ->
                    toChiiChoice(entry.key, entry.value)
                }
                    .distinctBy { it.first }
                    .sortedWith { left, right ->
                        val evaluationCompare = ActionEvaluation.comparator.compare(right.second, left.second)
                        if (evaluationCompare != 0) {
                            evaluationCompare
                        } else {
                            compareValuesBy(left, right, { it.first.first.sortOrder }, { it.first.second.sortOrder })
                        }
                    }
            } else {
                emptyList()
            }
            FuroReactionAnalysis(
                pass = shanten.pass?.let(::evaluateAction) ?: ActionEvaluation.worst(),
                chiChoices = chiChoices,
                pon = shanten.pon?.let(::evaluateAction),
                minkan = shanten.minkan?.let(::evaluateAction)
            )
        }
    }

    private fun toChiiChoice(tatsu: Tatsu, shanten: ShantenWithGot): Pair<Pair<MahjongTile, MahjongTile>, ActionEvaluation>? {
        val firstBase = MahjongTile.fromUtilsTile(tatsu.first)
        val secondBase = MahjongTile.fromUtilsTile(tatsu.second)
        val firstTile = findChiiTileVariant(firstBase) ?: return null
        val secondTile = findChiiTileVariant(secondBase, exclude = firstTile) ?: return null
        return (firstTile to secondTile) to evaluateAction(shanten)
    }

    private fun evaluateAction(shanten: ShantenWithoutGot): ActionEvaluation =
        ActionEvaluation(
            shantenNum = shanten.shantenNum,
            goodShapeAdvanceNum = shanten.goodShapeAdvanceNum ?: -1,
            advanceNum = shanten.advanceNum,
            goodShapeImprovementNum = shanten.goodShapeImprovementNum ?: -1,
            improvementNum = shanten.improvementNum ?: -1
        )

    private fun evaluateAction(shanten: ShantenWithGot): ActionEvaluation {
        val bestDiscard = shanten.discardToAdvance.values
            .map(::evaluateAction)
            .maxWithOrNull(ActionEvaluation.comparator)
        return if (bestDiscard == null) {
            ActionEvaluation(
                shantenNum = shanten.shantenNum,
                goodShapeAdvanceNum = -1,
                advanceNum = 0,
                goodShapeImprovementNum = -1,
                improvementNum = -1
            )
        } else {
            bestDiscard
        }
    }

    private fun suggestedReactionResponse(analysis: FuroReactionAnalysis): ReactionResponse {
        var best = ReactionChoice(ReactionResponse(ReactionType.SKIP, null), analysis.pass)
        analysis.pon?.let {
            val candidate = ReactionChoice(ReactionResponse(ReactionType.PON, null), it)
            if (ReactionChoice.comparator.compare(candidate, best) > 0) {
                best = candidate
            }
        }
        analysis.minkan?.let {
            val candidate = ReactionChoice(ReactionResponse(ReactionType.MINKAN, null), it)
            if (ReactionChoice.comparator.compare(candidate, best) > 0) {
                best = candidate
            }
        }
        analysis.chiChoices.forEach { (pair, evaluation) ->
            val candidate = ReactionChoice(ReactionResponse(ReactionType.CHII, pair), evaluation)
            if (ReactionChoice.comparator.compare(candidate, best) > 0) {
                best = candidate
            }
        }
        return best.response
    }

    private fun countDora(
        hands: List<MahjongTile>,
        fuuroList: List<Fuuro>,
        indicators: List<MahjongTile>
    ): Int {
        val doraTiles = indicators.map { it.nextTile }
        return hands.count { handTile -> doraTiles.any { dora -> handTile.sameKind(dora) } } +
            fuuroList.sumOf { fuuro -> fuuro.tileInstances.count { tile -> doraTiles.any { dora -> tile.mahjongTile.sameKind(dora) } } }
    }

    private fun buildExtraYaku(
        yakus: Yakus,
        generalSituation: GeneralSituation,
        personalSituation: PersonalSituation
    ): Set<Yaku> = buildSet {
        when {
            personalSituation.isDoubleRiichi -> add(yakus.WRichi)
            personalSituation.isRiichi -> add(yakus.Richi)
        }
        if (personalSituation.isIppatsu) {
            add(yakus.Ippatsu)
        }
        if (personalSituation.isRinshanKaihoh) {
            add(yakus.Rinshan)
        }
        if (personalSituation.isChankan) {
            add(yakus.Chankan)
        }
        if (generalSituation.isHoutei) {
            add(if (personalSituation.isTsumo) yakus.Haitei else yakus.Houtei)
        }
        if (generalSituation.isFirstRound && personalSituation.isTsumo) {
            add(if (personalSituation.jikaze == Wind.EAST) yakus.Tenhou else yakus.Chihou)
        }
    }

    private fun toHoraOptions(rule: MahjongRule): HoraOptions =
        HoraOptions(
            aotenjou = false,
            allowKuitan = rule.openTanyao,
            hasRenpuuJyantouHu = true,
            hasKiriageMangan = false,
            hasKazoeYakuman = true,
            hasMultipleYakuman = true,
            hasComplexYakuman = true
        )

    private fun toDoubleYakuman(name: String): DoubleYakuman? = when (name) {
        "Daisushi" -> DoubleYakuman.DAISUSHI
        "SuankoTanki" -> DoubleYakuman.SUANKO_TANKI
        "ChurenNineWaiting" -> DoubleYakuman.JUNSEI_CHURENPOHTO
        "KokushiThirteenWaiting" -> DoubleYakuman.KOKUSHIMUSO_JUSANMENMACHI
        else -> null
    }

    private fun canonicalYakuName(name: String): String = when (name) {
        "Tsumo" -> "TSUMO"
        "Pinhu" -> "PINFU"
        "Tanyao" -> "TANYAO"
        "Ipe" -> "IIPEIKOU"
        "SelfWind" -> "SELF_WIND"
        "RoundWind" -> "ROUND_WIND"
        "Haku" -> "HAKU"
        "Hatsu" -> "HATSU"
        "Chun" -> "CHUN"
        "Sanshoku" -> "SANSHOKU"
        "Ittsu" -> "ITTSU"
        "Chanta" -> "CHANTA"
        "Chitoi" -> "CHITOITSU"
        "Toitoi" -> "TOITOI"
        "Sananko" -> "SANANKOU"
        "Honroto" -> "HONROUTOU"
        "Sandoko" -> "SANDOKOU"
        "Sankantsu" -> "SANKANTSU"
        "Shosangen" -> "SHOUSANGEN"
        "Honitsu" -> "HONITSU"
        "Junchan" -> "JUNCHAN"
        "Ryanpe" -> "RYANPEIKOU"
        "Chinitsu" -> "CHINITSU"
        "Kokushi" -> "KOKUSHIMUSO"
        "Suanko" -> "SUANKO"
        "Daisangen" -> "DAISANGEN"
        "Tsuiso" -> "TSUUIISOU"
        "Shousushi" -> "SHOUSUUSHII"
        "Lyuiso" -> "RYUUIISOU"
        "Chinroto" -> "CHINROUTOU"
        "Sukantsu" -> "SUUKANTSU"
        "Churen" -> "CHUURENPOUTOU"
        "Richi" -> "REACH"
        "Ippatsu" -> "IPPATSU"
        "Rinshan" -> "RINSHAN_KAIHOU"
        "Chankan" -> "CHANKAN"
        "Haitei" -> "HAITEI"
        "Houtei" -> "HOUTEI"
        "WRichi" -> "DOUBLE_REACH"
        "Tenhou" -> "TENHOU"
        "Chihou" -> "CHIIHOU"
        else -> name.uppercase()
    }

    private data class FuroReactionAnalysis(
        val pass: ActionEvaluation,
        val chiChoices: List<Pair<Pair<MahjongTile, MahjongTile>, ActionEvaluation>>,
        val pon: ActionEvaluation?,
        val minkan: ActionEvaluation?
    )

    private data class ActionEvaluation(
        val shantenNum: Int,
        val goodShapeAdvanceNum: Int,
        val advanceNum: Int,
        val goodShapeImprovementNum: Int,
        val improvementNum: Int
    ) {
        companion object {
            fun worst(): ActionEvaluation =
                ActionEvaluation(
                    shantenNum = Int.MAX_VALUE,
                    goodShapeAdvanceNum = -1,
                    advanceNum = -1,
                    goodShapeImprovementNum = -1,
                    improvementNum = -1
                )

            val comparator: Comparator<ActionEvaluation> =
                compareBy<ActionEvaluation> { -it.shantenNum }
                    .thenBy { it.goodShapeAdvanceNum }
                    .thenBy { it.advanceNum }
                    .thenBy { it.goodShapeImprovementNum }
                    .thenBy { it.improvementNum }
        }
    }

    private data class ReactionChoice(
        val response: ReactionResponse,
        val evaluation: ActionEvaluation
    ) {
        companion object {
            val comparator: Comparator<ReactionChoice> = Comparator { left, right ->
                val evaluationCompare = ActionEvaluation.comparator.compare(left.evaluation, right.evaluation)
                if (evaluationCompare != 0) {
                    evaluationCompare
                } else {
                    reactionPriority(left.response.type).compareTo(reactionPriority(right.response.type))
                }
            }

            private fun reactionPriority(type: ReactionType): Int = when (type) {
                ReactionType.SKIP -> 0
                ReactionType.MINKAN -> 1
                ReactionType.CHII -> 2
                ReactionType.PON -> 3
                ReactionType.RON -> 4
            }
        }
    }
}

