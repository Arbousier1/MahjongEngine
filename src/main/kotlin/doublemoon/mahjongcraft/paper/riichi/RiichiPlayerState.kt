package doublemoon.mahjongcraft.paper.riichi

import doublemoon.mahjongcraft.paper.riichi.model.ClaimTarget
import doublemoon.mahjongcraft.paper.riichi.model.DoubleYakuman
import doublemoon.mahjongcraft.paper.riichi.model.Fuuro
import doublemoon.mahjongcraft.paper.riichi.model.GeneralSituation
import doublemoon.mahjongcraft.paper.riichi.model.MahjongRule
import doublemoon.mahjongcraft.paper.riichi.model.MahjongTile
import doublemoon.mahjongcraft.paper.riichi.model.MeldType
import doublemoon.mahjongcraft.paper.riichi.model.PersonalSituation
import doublemoon.mahjongcraft.paper.riichi.model.ScoringStick
import doublemoon.mahjongcraft.paper.riichi.model.TileInstance
import doublemoon.mahjongcraft.paper.riichi.model.Wind
import doublemoon.mahjongcraft.paper.riichi.model.YakuSettlement
import doublemoon.mahjongcraft.paper.riichi.model.toMahjongTileList
import mahjongutils.hora.HoraOptions
import mahjongutils.hora.hora
import mahjongutils.models.Tile
import mahjongutils.models.TileType
import mahjongutils.models.isYaochu
import mahjongutils.shanten.shanten
import mahjongutils.yaku.Yaku
import mahjongutils.yaku.Yakus
import kotlin.math.absoluteValue

open class RiichiPlayerState(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean = true
) {
    val hands: MutableList<TileInstance> = mutableListOf()
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
        val tilesForPon = tilesForPon(tile)
        val fuuro = Fuuro(MeldType.PON, tilesForPon, claimTarget, tile)
        hands -= tilesForPon.filter { it != tile }.toSet()
        target.discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun minkan(tile: TileInstance, claimTarget: ClaimTarget, target: RiichiPlayerState) {
        val tilesForMinkan = tilesForMinkan(tile)
        val fuuro = Fuuro(MeldType.MINKAN, tilesForMinkan, claimTarget, tile)
        hands -= tilesForMinkan.filter { it != tile }.toSet()
        target.discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun ankan(tile: TileInstance) {
        val tilesForAnkan = tilesForAnkan(tile)
        val fuuro = Fuuro(MeldType.ANKAN, tilesForAnkan, ClaimTarget.SELF, tile)
        hands -= tilesForAnkan.toSet()
        discardedTilesForDisplay -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun kakan(tile: TileInstance) {
        val minPon = fuuroList.find { it.isPon && it.tileInstances.any { existing -> existing.mahjongTile.sameKind(tile.mahjongTile) } } ?: return
        fuuroList -= minPon
        val tiles = minPon.tileInstances.toMutableList().also { it += tile }
        val fuuro = Fuuro(MeldType.KAKAN, tiles, minPon.claimTarget, minPon.claimTile)
        hands -= tile
        fuuroList += fuuro
        invalidateHandAnalysis()
    }

    fun canPon(tile: TileInstance): Boolean = !(riichi || doubleRiichi) && sameTilesInHands(tile).size >= 2

    fun canMinkan(tile: TileInstance): Boolean = !(riichi || doubleRiichi) && sameTilesInHands(tile).size == 3

    val canKakan: Boolean
        get() = tilesCanKakan.isNotEmpty()

    val canAnkan: Boolean
        get() = tilesCanAnkan.isNotEmpty()

    fun canChii(tile: TileInstance): Boolean = !(riichi || doubleRiichi) && tilePairsForChii(tile).isNotEmpty()

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

    fun availableChiiPairs(tile: TileInstance): List<Pair<MahjongTile, MahjongTile>> = tilePairsForChii(tile)

    private fun tilePairsForChii(tile: TileInstance): List<Pair<MahjongTile, MahjongTile>> {
        val scoringTile = tile.scoringTile
        if (scoringTile.type == TileType.Z) return emptyList()

        val number = scoringTile.realNum
        val type = scoringTile.type
        val pairBases = mutableListOf<Pair<MahjongTile, MahjongTile>>()
        if (number <= 7) {
            pairBases += MahjongTile.fromUtilsTile(Tile.get(type, number + 1)) to MahjongTile.fromUtilsTile(Tile.get(type, number + 2))
        }
        if (number in 2..8) {
            pairBases += MahjongTile.fromUtilsTile(Tile.get(type, number - 1)) to MahjongTile.fromUtilsTile(Tile.get(type, number + 1))
        }
        if (number >= 3) {
            pairBases += MahjongTile.fromUtilsTile(Tile.get(type, number - 2)) to MahjongTile.fromUtilsTile(Tile.get(type, number - 1))
        }

        return buildList {
            pairBases.distinct().forEach { (firstBase, secondBase) ->
                val firstTile = findChiiTileVariant(firstBase)
                val secondTile = findChiiTileVariant(secondBase, exclude = firstTile)
                if (firstTile != null && secondTile != null) {
                    add(firstTile to secondTile)
                }
            }
        }.distinct()
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
                val results = buildList {
                    hands.forEach { tile ->
                        val nowHands = hands.toMutableList().also { it -= tile }.toMahjongTileList()
                        val nowMachi = calculateMachi(hands = nowHands)
                        if (nowMachi.isNotEmpty()) {
                            add(tile.mahjongTile to nowMachi)
                        }
                    }
                }
                addAll(results.distinct())
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
                cachedMachi = calculateMachi()
                cachedMachiVersion = analysisStateVersion
            }
            return cachedMachi
        }

    private fun calculateMachi(
        hands: List<MahjongTile> = this.hands.toMahjongTileList(),
        fuuroList: List<Fuuro> = this.fuuroList
    ): List<MahjongTile> = MahjongTile.entries.filter { tile ->
        tile != MahjongTile.UNKNOWN &&
            (hands.count { it.sameKind(tile) } + fuuroList.sumOf { fuuro -> fuuro.tileInstances.count { it.mahjongTile.sameKind(tile) } }) < 4 &&
            canFormWinningHand(hands + tile, fuuroList)
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

    fun isFuriten(tile: TileInstance, discards: List<TileInstance>): Boolean =
        isFuriten(tile.mahjongTile, discards.map { it.mahjongTile })

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

    fun isKokushimuso(tile: MahjongTile): Boolean =
        runCatching {
            hora(
                tiles = (hands.toMahjongTileList() + tile).toUtilsTiles(),
                furo = fuuroList.map { it.utilsFuro },
                agari = tile.utilsTile,
                tsumo = false,
                options = HoraOptions.Default
            )
        }.getOrNull()?.yaku?.any { it.name == "Kokushi" || it.name == "KokushiThirteenWaiting" } == true

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
        }.getOrDefault(false)

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
        }.getOrElse {
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
    }

    fun declareRiichi(riichiSengenTile: TileInstance, isFirstRound: Boolean) {
        this.riichiSengenTile = riichiSengenTile
        if (isFirstRound) doubleRiichi = true else riichi = true
        invalidateHandAnalysis()
    }

    fun discardTile(tile: MahjongTile): TileInstance? =
        hands.findLast { it.mahjongTile == tile }?.also {
            hands -= it
            discardedTiles += it
            discardedTilesForDisplay += it
            invalidateHandAnalysis()
        }

    fun resetRoundState() {
        hands.clear()
        fuuroList.clear()
        discardedTiles.clear()
        discardedTilesForDisplay.clear()
        riichiSengenTile = null
        riichi = false
        doubleRiichi = false
        invalidateHandAnalysis()
    }

    private fun invalidateHandAnalysis() {
        analysisStateVersion++
    }

    private fun List<MahjongTile>.toUtilsTiles(): List<Tile> =
        map { it.utilsTile }

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
}
