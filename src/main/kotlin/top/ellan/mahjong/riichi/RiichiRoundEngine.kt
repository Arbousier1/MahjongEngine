package top.ellan.mahjong.riichi

import top.ellan.mahjong.table.core.round.OpeningDiceRoll
import top.ellan.mahjong.riichi.model.ClaimTarget
import top.ellan.mahjong.riichi.model.ExhaustiveDraw
import top.ellan.mahjong.riichi.model.GeneralSituation
import top.ellan.mahjong.riichi.model.MahjongRound
import top.ellan.mahjong.riichi.model.MahjongRule
import top.ellan.mahjong.riichi.model.MahjongTile
import top.ellan.mahjong.riichi.model.PersonalSituation
import top.ellan.mahjong.riichi.model.ScoreItem
import top.ellan.mahjong.riichi.model.ScoreSettlement
import top.ellan.mahjong.riichi.model.ScoringStick
import top.ellan.mahjong.riichi.model.SettlementPayment
import top.ellan.mahjong.riichi.model.SettlementPaymentType
import top.ellan.mahjong.riichi.model.TileInstance
import top.ellan.mahjong.riichi.model.Wind
import top.ellan.mahjong.riichi.model.YakuSettlement
import top.ellan.mahjong.riichi.scoring.RiichiPaoRules
import mahjongutils.models.isYaochu
import kotlin.random.Random

enum class ReactionType {
    RON,
    PON,
    MINKAN,
    CHII,
    SKIP
}

data class ReactionOptions @JvmOverloads constructor(
    val canRon: Boolean,
    val canPon: Boolean,
    val canMinkan: Boolean,
    val chiiPairs: List<Pair<MahjongTile, MahjongTile>>,
    val suggestedResponse: ReactionResponse? = null
)

data class ReactionResponse(
    val type: ReactionType,
    val chiiPair: Pair<MahjongTile, MahjongTile>? = null
)

data class PendingReaction(
    val discarderUuid: String,
    val tile: TileInstance,
    val options: Map<String, ReactionOptions>,
    val isChankan: Boolean = false,
    val responses: MutableMap<String, ReactionResponse> = linkedMapOf()
)

data class RoundResolution(
    val title: String,
    val yakuSettlements: List<YakuSettlement> = emptyList(),
    val scoreSettlement: ScoreSettlement? = null,
    val draw: ExhaustiveDraw? = null
)

class RiichiRoundEngine(
    players: List<RiichiPlayerState>,
    val rule: MahjongRule = MahjongRule()
) {
    val seats: MutableList<RiichiPlayerState> = players.toMutableList()
    var round: MahjongRound = rule.length.getStartingRound()
    val wall: MutableList<TileInstance> = mutableListOf()
    val deadWall: MutableList<TileInstance> = mutableListOf()
    val discards: MutableList<TileInstance> = mutableListOf()
    var kanCount: Int = 0
        private set
    var dicePoints: Int = 0
        private set
    private var openingDiceRoll: OpeningDiceRoll? = null
    var currentPlayerIndex: Int = 0
        private set
    var pendingReaction: PendingReaction? = null
        private set
    var lastResolution: RoundResolution? = null
        private set
    var started: Boolean = false
        private set
    var gameFinished: Boolean = false
        private set
    private var currentDrawIsRinshan: Boolean = false
    private var pendingAbortiveDraw: ExhaustiveDraw? = null
    private var revealedKanDoraCount: Int = 0
    private var pendingOpenKanDoraCount: Int = 0
    private val paoLiabilityByWinner: MutableMap<String, MutableMap<String, String>> = linkedMapOf()

    val currentPlayer: RiichiPlayerState
        get() = seats[currentPlayerIndex]

    val dealer: RiichiPlayerState
        get() = seatOrderFromDealer().first()

    val isFirstRound: Boolean
        get() = discards.size <= 4 && seats.none { it.fuuroList.isNotEmpty() }

    val isHoutei: Boolean
        get() = wall.size <= 4

    val isSuufonRenda: Boolean
        get() {
            if (discards.size < 4) return false
            val lastFour = discards.takeLast(4)
            val first = lastFour.first().scoringTile
            if (first.type.name != "Z" || first.realNum !in 1..4) return false
            return lastFour.all { it.code == lastFour.first().code }
        }

    val doraIndicators: List<TileInstance>
        get() {
            val visibleKanCount = minOf(revealedKanDoraCount, 4)
            if (deadWall.isEmpty()) {
                return emptyList()
            }
            return buildList {
                repeat(visibleKanCount + 1) {
                    val index = (4 - it) * 2 + visibleKanCount
                    if (index in deadWall.indices) {
                        add(deadWall[index])
                    }
                }
            }
        }

    val uraDoraIndicators: List<TileInstance>
        get() {
            val visibleKanCount = minOf(revealedKanDoraCount, 4)
            if (deadWall.isEmpty()) {
                return emptyList()
            }
            return buildList {
                repeat(visibleKanCount + 1) {
                    val index = (4 - it) * 2 + 1 + visibleKanCount
                    if (index in deadWall.indices) {
                        add(deadWall[index])
                    }
                }
            }
        }

    val generalSituation: GeneralSituation
        get() = GeneralSituation(
            isFirstRound,
            isHoutei,
            round.wind,
            doraIndicators.map { it.mahjongTile },
            uraDoraIndicators.map { it.mahjongTile }
        )

    init {
        require(players.size == 4) { "Riichi round engine requires exactly 4 players" }
        seats.forEach {
            it.points = rule.startingPoints
            it.basicThinkingTime = rule.thinkingTime.base
            it.extraThinkingTime = rule.thinkingTime.extra
        }
    }

    fun startRound() {
        if (gameFinished) {
            return
        }
        clearRoundState()
        buildWall()
        assignDeadWall()
        dealHands()
        currentPlayerIndex = round.round
        started = true
        lastResolution = null
        pendingReaction = null
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
    }

    fun setPendingDiceRoll(diceRoll: OpeningDiceRoll?) {
        openingDiceRoll = diceRoll
    }

    fun discard(playerUuid: String, tileIndex: Int): Boolean {
        if (!started || pendingReaction != null) return false
        if (currentPlayer.uuid != playerUuid) return false
        if (pendingAbortiveDraw != null) {
            resolveDraw(pendingAbortiveDraw!!)
            return false
        }
        val player = currentPlayer
        if (tileIndex !in player.hands.indices) return false
        val selectedTile = player.hands[tileIndex]
        val discarded = player.discardTile(selectedTile) ?: return false
        currentDrawIsRinshan = false
        discards += discarded
        revealPendingOpenKanDoraIfNeeded()
        pendingReaction = computePendingReaction(player, discarded)
        if (pendingReaction == null) {
            advanceAfterDiscard()
        }
        return true
    }

    fun declareRiichi(playerUuid: String, tileIndex: Int): Boolean {
        if (!started || pendingReaction != null) return false
        val player = currentPlayer
        if (player.uuid != playerUuid || tileIndex !in player.hands.indices) return false
        if (!player.isMenzenchin || player.riichi || player.doubleRiichi || player.points < ScoringStick.P1000.point) return false
        val discardTile = player.hands[tileIndex].mahjongTile
        if (player.tilePairsForRiichi.none { it.first == discardTile }) return false
        val tile = player.hands[tileIndex]
        player.declareRiichi(tile, isFirstRound)
        player.points -= ScoringStick.P1000.point
        player.sticks += ScoringStick.P1000
        return discard(playerUuid, tileIndex)
    }

    fun tryTsumo(playerUuid: String): Boolean {
        if (!canDeclareTsumo(playerUuid)) {
            return false
        }
        val player = currentPlayer
        val winningTileInstance = player.lastDrawnTile ?: return false
        resolveTsumo(player, winningTileInstance, isRinshanKaihoh = currentDrawIsRinshan)
        return true
    }

    fun canDeclareTsumo(playerUuid: String): Boolean {
        if (!started || pendingReaction != null || currentPlayer.uuid != playerUuid) return false
        val player = currentPlayer
        val winningTileInstance = player.lastDrawnTile ?: return false
        return player.canWin(
            winningTileInstance.mahjongTile,
            true,
            rule = rule,
            generalSituation = generalSituation,
            personalSituation = personalSituation(player, isTsumo = true, isRinshanKaihoh = currentDrawIsRinshan)
        )
    }

    fun tryAnkanOrKakan(playerUuid: String, tile: MahjongTile): Boolean {
        if (!started || pendingReaction != null || currentPlayer.uuid != playerUuid) return false
        if (kanCount >= 4) return false
        val player = currentPlayer
        val ankanTile = player.tilesCanAnkan.find { it.mahjongTile == tile }
        if (ankanTile != null) {
            if (!canDrawRinshanTile()) return false
            player.ankan(ankanTile)
            currentDrawIsRinshan = false
            pendingReaction = computeChankanReaction(player, ankanTile, allowOnlyKokushi = true)
            if (pendingReaction != null) {
                pendingAbortiveDraw = null
                return true
            }
            registerClosedKan()
            drawRinshanAndContinue(player)
            return true
        }
        val kakanTile = player.hands.find { it.mahjongTile == tile && player.canKakan }
        if (kakanTile != null) {
            if (!canDrawRinshanTile()) return false
            player.kakan(kakanTile)
            currentDrawIsRinshan = false
            pendingReaction = computeChankanReaction(player, kakanTile, allowOnlyKokushi = false)
            if (pendingReaction != null) {
                pendingAbortiveDraw = null
                return true
            }
            registerOpenKan()
            drawRinshanAndContinue(player)
            return true
        }
        return false
    }

    fun react(playerUuid: String, response: ReactionResponse): Boolean {
        val pending = pendingReaction ?: return false
        val options = pending.options[playerUuid] ?: return false
        when (response.type) {
            ReactionType.RON -> if (!options.canRon) return false
            ReactionType.PON -> if (!options.canPon) return false
            ReactionType.MINKAN -> if (!options.canMinkan) return false
            ReactionType.CHII -> if (response.chiiPair !in options.chiiPairs) return false
            ReactionType.SKIP -> {}
        }
        pending.responses[playerUuid] = response
        resolvePendingReactionsIfReady()
        return true
    }

    fun availableReactions(playerUuid: String): ReactionOptions? = pendingReaction?.options?.get(playerUuid)

    fun canKyuushuKyuuhai(playerUuid: String): Boolean =
        started && pendingReaction == null && currentPlayer.uuid == playerUuid && isFirstRound && currentPlayer.numbersOfYaochuuhaiTypes >= 9

    fun declareKyuushuKyuuhai(playerUuid: String): Boolean {
        if (!canKyuushuKyuuhai(playerUuid)) return false
        resolveDraw(ExhaustiveDraw.KYUUSHU_KYUUHAI)
        return true
    }

    fun seatPlayer(uuid: String): RiichiPlayerState? = seats.find { it.uuid == uuid }

    fun placementOrder(): List<RiichiPlayerState> {
        val seatOrder = seatOrderFromDealer()
        return seats.sortedWith(
            compareByDescending<RiichiPlayerState> { it.points }
                .thenBy { seatOrder.indexOf(it) }
        )
    }

    fun nagashiManganCandidates(): List<RiichiPlayerState> =
        seats.filter { player ->
            player.discardedTiles.isNotEmpty() &&
                player.discardedTiles.size == player.discardedTilesForDisplay.size &&
                player.discardedTiles.all { it.scoringTile.isYaochu }
        }

    private fun clearRoundState() {
        wall.clear()
        deadWall.clear()
        discards.clear()
        kanCount = 0
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        revealedKanDoraCount = 0
        pendingOpenKanDoraCount = 0
        paoLiabilityByWinner.clear()
        seats.forEach {
            it.resetRoundState()
        }
    }

    private fun buildWall() {
        val tiles = when (rule.redFive) {
            MahjongRule.RedFive.NONE -> MahjongTile.normalWall
            MahjongRule.RedFive.THREE -> MahjongTile.redFive3Wall
            MahjongRule.RedFive.FOUR -> MahjongTile.redFive4Wall
        }.shuffled(Random.Default).map { TileInstance(mahjongTile = it) }
        wall += tiles
        val diceRoll = openingDiceRoll ?: OpeningDiceRoll(Random.nextInt(1, 7), Random.nextInt(1, 7))
        openingDiceRoll = null
        dicePoints = diceRoll.total()
        val directionIndex = (4 - ((dicePoints % 4 - 1) + round.round) % 4)
        val startingStackIndex = 2 * dicePoints
        val reordered = MutableList(wall.size) {
            val tileIndex = (directionIndex * 34 + startingStackIndex + it) % wall.size
            wall[tileIndex]
        }
        wall.clear()
        wall += reordered
    }

    private fun assignDeadWall() {
        repeat(14) {
            deadWall += wall.removeLast()
        }
        deadWall.reverse()
    }

    private fun dealHands() {
        val dealer = dealer
        repeat(3) {
            seats.forEach { player ->
                repeat(4) {
                    player.drawTile(wall.removeFirst())
                }
            }
        }
        seats.forEach { it.drawTile(wall.removeFirst()) }
        dealer.drawTile(wall.removeFirst())
        seats.forEach { it.hands.sortBy { tile -> tile.mahjongTile.sortOrder } }
    }

    private fun drawRinshanTile(player: RiichiPlayerState): TileInstance {
        val tile = if (kanCount % 2 == 0) deadWall[deadWall.size - 2] else deadWall[deadWall.size - 1]
        val lastWallTile = wall.removeLast()
        deadWall.add(0, lastWallTile)
        deadWall.remove(tile)
        player.drawTile(tile)
        return tile
    }

    private fun drawRinshanAndContinue(player: RiichiPlayerState) {
        val rinshan = drawRinshanTile(player)
        player.hands.sortBy { it.mahjongTile.sortOrder }
        player.hands.remove(rinshan)
        player.hands.add(rinshan)
        currentDrawIsRinshan = true
        pendingAbortiveDraw = if (isSuukaikanAbort()) ExhaustiveDraw.SUUKAIKAN else null
    }

    private fun canDrawRinshanTile(): Boolean =
        deadWall.size >= 2 && wall.isNotEmpty()

    private fun computePendingReaction(discarder: RiichiPlayerState, tile: TileInstance): PendingReaction? {
        val options = linkedMapOf<String, ReactionOptions>()
        seatOrderFrom(discarder).drop(1).forEach { candidate ->
            val target = claimTarget(candidate, discarder)
            val canRon = candidate.canWin(
                tile.mahjongTile,
                false,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation(candidate, isTsumo = false)
            ) && !candidate.isFuriten(tile, discards)
            val reactionOptions = candidate.reactionOptionsFor(tile, allowChii = target == ClaimTarget.LEFT, canRon = canRon)
            if (reactionOptions != null) {
                options[candidate.uuid] = reactionOptions
            }
        }
        return options.takeIf { it.isNotEmpty() }?.let { PendingReaction(discarder.uuid, tile, it) }
    }

    private fun computeChankanReaction(discarder: RiichiPlayerState, tile: TileInstance, allowOnlyKokushi: Boolean): PendingReaction? {
        val options = linkedMapOf<String, ReactionOptions>()
        seatOrderFrom(discarder).drop(1).forEach { candidate ->
            val canRon = candidate.canWin(
                tile.mahjongTile,
                false,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation(candidate, isTsumo = false, isChankan = true)
            ) && !candidate.isFuriten(tile, discards) && (!allowOnlyKokushi || candidate.isKokushimuso(tile.mahjongTile))
            if (canRon) {
                options[candidate.uuid] = ReactionOptions(
                    canRon = true,
                    canPon = false,
                    canMinkan = false,
                    chiiPairs = emptyList(),
                    suggestedResponse = ReactionResponse(ReactionType.RON, null)
                )
            }
        }
        return options.takeIf { it.isNotEmpty() }?.let {
            PendingReaction(discarder.uuid, tile, it, isChankan = true)
        }
    }

    private fun resolvePendingReactionsIfReady() {
        val pending = pendingReaction ?: return
        val discarder = seatPlayer(pending.discarderUuid)!!
        val orderedClaimers = seatOrderFrom(discarder).drop(1)
        val ronCandidates = pending.options.filterValues { it.canRon }.keys
        if (pending.responses.keys.containsAll(ronCandidates).not()) {
            return
        }
        val ronPlayers = orderedClaimers.filter { pending.responses[it.uuid]?.type == ReactionType.RON }
        if (ronPlayers.isNotEmpty()) {
            val winners = when (rule.ronMode) {
                MahjongRule.RonMode.HEAD_BUMP -> ronPlayers.take(1)
                MahjongRule.RonMode.MULTI_RON -> ronPlayers
            }
            resolveRon(winners, discarder, pending.tile, isChankan = pending.isChankan)
            pendingReaction = null
            return
        }

        if (pending.isChankan) {
            pendingReaction = null
            drawRinshanAndContinue(discarder)
            return
        }

        val ponKanCandidates = pending.options.filterValues { it.canPon || it.canMinkan }.keys
        if (pending.responses.keys.containsAll(ponKanCandidates).not()) {
            return
        }
        val ponKanWinner = orderedClaimers.firstOrNull { player ->
            val type = pending.responses[player.uuid]?.type
            type == ReactionType.PON || type == ReactionType.MINKAN
        }
        if (ponKanWinner != null) {
            val response = pending.responses[ponKanWinner.uuid]!!
            val winner = ponKanWinner
            val target = claimTarget(winner, discarder)
            if (response.type == ReactionType.PON) {
                winner.pon(pending.tile, target, discarder)
                RiichiPaoRules.registerLiability(paoLiabilityByWinner, winner, discarder, pending.tile)
                currentDrawIsRinshan = false
                pendingAbortiveDraw = null
            } else {
                winner.minkan(pending.tile, target, discarder)
                RiichiPaoRules.registerLiability(paoLiabilityByWinner, winner, discarder, pending.tile)
                currentPlayerIndex = seats.indexOf(winner)
                registerOpenKan()
                drawRinshanAndContinue(winner)
            }
            currentPlayerIndex = seats.indexOf(winner)
            pendingReaction = null
            return
        }

        val chiiCandidates = pending.options.filterValues { it.chiiPairs.isNotEmpty() }.keys
        if (pending.responses.keys.containsAll(chiiCandidates).not()) {
            return
        }
        val chiiResponse = orderedClaimers.firstNotNullOfOrNull { player ->
            pending.responses[player.uuid]
                ?.takeIf { it.type == ReactionType.CHII }
                ?.let { player to it }
        }
        if (chiiResponse != null) {
            val winner = chiiResponse.first
            val response = chiiResponse.second
            winner.chii(pending.tile, response.chiiPair!!, claimTarget(winner, discarder), discarder)
            currentPlayerIndex = seats.indexOf(winner)
            currentDrawIsRinshan = false
            pendingAbortiveDraw = null
            pendingReaction = null
            return
        }

        pendingReaction = null
        advanceAfterDiscard()
    }

    private fun advanceAfterDiscard() {
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        if (isSuufonRenda) {
            resolveDraw(ExhaustiveDraw.SUUFON_RENDA)
            return
        }
        if (seats.count { it.riichi || it.doubleRiichi } == 4) {
            resolveDraw(ExhaustiveDraw.SUUCHA_RIICHI)
            return
        }
        if (wall.isEmpty()) {
            resolveDraw(ExhaustiveDraw.NORMAL)
            return
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % seats.size
        currentPlayer.drawTile(wall.removeFirst())
        currentPlayer.hands.sortBy { it.mahjongTile.sortOrder }
    }

    private fun registerClosedKan() {
        kanCount++
        if (revealedKanDoraCount < 4) {
            revealedKanDoraCount++
        }
    }

    private fun registerOpenKan() {
        kanCount++
        if (revealedKanDoraCount + pendingOpenKanDoraCount < 4) {
            pendingOpenKanDoraCount++
        }
    }

    private fun revealPendingOpenKanDoraIfNeeded() {
        if (pendingOpenKanDoraCount <= 0) {
            return
        }
        val revealCount = minOf(4 - revealedKanDoraCount, pendingOpenKanDoraCount)
        if (revealCount > 0) {
            revealedKanDoraCount += revealCount
        }
        pendingOpenKanDoraCount = 0
    }

    private fun resolveRon(winners: List<RiichiPlayerState>, target: RiichiPlayerState, tile: TileInstance, isChankan: Boolean = false) {
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        val yakuSettlements = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val seatOrderFromTarget = seatOrderFrom(target)
        val atamahanePlayer = seatOrderFromTarget.firstOrNull { it in winners }
        val allRiichiStickQuantity = seats.sumOf { it.riichiStickAmount }
        val honbaScore = round.honba * 300
        val riichiPoolScore = allRiichiStickQuantity * ScoringStick.P1000.point
        winners.forEach {
            val settlement = it.calcYakuSettlementForWin(
                winningTile = tile.mahjongTile,
                isWinningTileInHands = false,
                rule = rule,
                generalSituation = generalSituation,
                personalSituation = personalSituation(it, isChankan = isChankan),
                doraIndicators = doraIndicators.map { indicator -> indicator.mahjongTile },
                uraDoraIndicators = uraDoraIndicators.map { indicator -> indicator.mahjongTile }
            )
            val liabilityEntries = RiichiPaoRules.liabilityEntries(paoLiabilityByWinner, it, settlement, ::seatPlayer)
            val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            val basicScore = settlement.score
            val payments = mutableListOf<SettlementPayment>()
            val paoBreakdown = RiichiPaoRules.ronBreakdown(liabilityEntries, it == dealer, basicScore, target, seatOrderFromDealer())
            val targetBasePayment = paoBreakdown.targetBase + if (target == paoBreakdown.honbaPayer) honbaScore else 0
            if (targetBasePayment > 0) {
                payments += SettlementPayment(target.uuid, targetBasePayment, SettlementPaymentType.RON)
            }
            paoBreakdown.liabilityPayments.forEach { (liablePlayer, amount) ->
                val totalPayment = amount + if (liablePlayer == paoBreakdown.honbaPayer) honbaScore else 0
                if (totalPayment > 0) {
                    payments += SettlementPayment(liablePlayer.uuid, totalPayment, SettlementPaymentType.PAO, paoBreakdown.liabilityNotes[liablePlayer.uuid].orEmpty())
                }
            }
            if (it == atamahanePlayer && riichiPoolScore > 0) {
                payments += SettlementPayment("", riichiPoolScore, SettlementPaymentType.RIICHI_POOL)
            }
            val score = basicScore - riichiStickPoints + honbaScore + if (it == atamahanePlayer) riichiPoolScore else 0
            scoreList += ScoreItem(it.displayName, it.uuid, it.points, score)
            it.points += score
            yakuSettlements += settlement.copy(paymentBreakdown = payments)
        }
        val targetRiichiStick = if (target.riichi || target.doubleRiichi) ScoringStick.P1000.point else 0
        val targetPaoShare = yakuSettlements.sumOf { settlement ->
            settlement.paymentBreakdown
                .filter { payment -> payment.payerUuid == target.uuid && payment.type != SettlementPaymentType.RIICHI_POOL }
                .sumOf(SettlementPayment::amount)
        }
        scoreList += ScoreItem(target.displayName, target.uuid, target.points, -(targetPaoShare + targetRiichiStick))
        target.points -= (targetPaoShare + targetRiichiStick)
        val liabilityTotals = linkedMapOf<String, Int>()
        yakuSettlements.forEach { settlement ->
            settlement.paymentBreakdown
                .filter { payment ->
                    payment.payerUuid.isNotBlank()
                        && payment.payerUuid != target.uuid
                        && payment.type == SettlementPaymentType.PAO
                }
                .forEach { payment -> liabilityTotals.merge(payment.payerUuid, payment.amount, Int::plus) }
        }
        liabilityTotals.forEach { (liableUuid, amount) ->
            val liablePlayer = seatPlayer(liableUuid) ?: return@forEach
            val liableRiichiStick = if (liablePlayer.riichi || liablePlayer.doubleRiichi) ScoringStick.P1000.point else 0
            scoreList += ScoreItem(liablePlayer.displayName, liablePlayer.uuid, liablePlayer.points, -(amount + liableRiichiStick))
            liablePlayer.points -= (amount + liableRiichiStick)
        }
        seats.filter { it !in winners && it != target }.forEach {
            if (it.uuid in liabilityTotals.keys) {
                return@forEach
            }
            val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            scoreList += ScoreItem(it.displayName, it.uuid, it.points, -riichiStickPoints)
            it.points -= riichiStickPoints
        }
        lastResolution = RoundResolution("Ron", yakuSettlements, ScoreSettlement("Ron", scoreList))
        finishRound(winners.contains(dealer), true)
    }

    private fun resolveTsumo(player: RiichiPlayerState, tile: TileInstance, isRinshanKaihoh: Boolean = false) {
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        val yakuSettlements = mutableListOf<YakuSettlement>()
        val scoreList = mutableListOf<ScoreItem>()
        val allRiichiStickQuantity = seats.sumOf { it.riichiStickAmount }
        val honbaScore = round.honba * 300
        val playerRiichiStickPoints = if (player.riichi || player.doubleRiichi) ScoringStick.P1000.point else 0
        val riichiPoolScore = allRiichiStickQuantity * ScoringStick.P1000.point
        val tsumoPlayerIsDealer = player == dealer
        val settlement = player.calcYakuSettlementForWin(
            winningTile = tile.mahjongTile,
            isWinningTileInHands = true,
            rule = rule,
            generalSituation = generalSituation,
            personalSituation = personalSituation(player, isTsumo = true, isRinshanKaihoh = isRinshanKaihoh),
            doraIndicators = doraIndicators.map { it.mahjongTile },
            uraDoraIndicators = uraDoraIndicators.map { it.mahjongTile }
        )
        val liabilityEntries = RiichiPaoRules.liabilityEntries(paoLiabilityByWinner, player, settlement, ::seatPlayer)
        val basicScore = settlement.score
        val paoBreakdown = RiichiPaoRules.tsumoBreakdown(
            winner = player,
            liabilityEntries = liabilityEntries,
            winnerIsDealer = tsumoPlayerIsDealer,
            basicScore = basicScore,
            others = seats.filter { it != player },
            dealer = dealer
        )
        val payments = paoBreakdown.payments.toMutableList()
        val honbaPayer = RiichiPaoRules.honbaPayer(liabilityEntries, seatOrderFromDealer())
        if (honbaPayer != null && honbaScore > 0) {
            payments += SettlementPayment(honbaPayer.uuid, honbaScore, SettlementPaymentType.HONBA)
        }
        if (riichiPoolScore > 0) {
            payments += SettlementPayment("", riichiPoolScore, SettlementPaymentType.RIICHI_POOL)
        }
        val score = basicScore - playerRiichiStickPoints + honbaScore + riichiPoolScore
        scoreList += ScoreItem(player.displayName, player.uuid, player.points, score)
        player.points += score
        yakuSettlements += settlement.copy(paymentBreakdown = payments)
        seats.filter { it != player }.forEach {
            val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
            val basePayment = paoBreakdown.paymentTotals[it.uuid] ?: 0
            val totalPayment = basePayment + if (it == honbaPayer) honbaScore else 0 + riichiStickPoints
            scoreList += ScoreItem(it.displayName, it.uuid, it.points, -totalPayment)
            it.points -= totalPayment
        }
        lastResolution = RoundResolution("Tsumo", yakuSettlements, ScoreSettlement("Tsumo", scoreList))
        finishRound(player == dealer, true)
    }

    private fun resolveDraw(draw: ExhaustiveDraw) {
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        if (draw == ExhaustiveDraw.NORMAL) {
            val nagashiPlayers = nagashiManganCandidates()
            if (nagashiPlayers.isNotEmpty()) {
                resolveNagashiMangan(nagashiPlayers)
                return
            }
        }
        val scoreList = buildList {
            if (draw != ExhaustiveDraw.NORMAL) {
                seats.forEach {
                    val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
                    add(ScoreItem(it.displayName, it.uuid, it.points, -riichiStickPoints))
                    it.points -= riichiStickPoints
                }
            } else {
                val tenpaiCount = seats.count { it.isTenpai }
                if (tenpaiCount == 0 || tenpaiCount == seats.size) {
                    seats.forEach { add(ScoreItem(it.displayName, it.uuid, it.points, 0)) }
                } else {
                    val notenCount = seats.size - tenpaiCount
                    val notenBappu = 3000 / notenCount
                    val bappuGet = 3000 / tenpaiCount
                    seats.forEach {
                        if (it.isTenpai) {
                            val riichiStickPoints = if (it.riichi || it.doubleRiichi) ScoringStick.P1000.point else 0
                            add(ScoreItem(it.displayName, it.uuid, it.points, bappuGet - riichiStickPoints))
                            it.points += bappuGet
                            it.points -= riichiStickPoints
                        } else {
                            add(ScoreItem(it.displayName, it.uuid, it.points, -notenBappu))
                            it.points -= notenBappu
                        }
                    }
                }
            }
        }
        lastResolution = RoundResolution(draw.name, scoreSettlement = ScoreSettlement(draw.name, scoreList), draw = draw)
        val dealerRemaining = if (draw == ExhaustiveDraw.NORMAL) dealer.isTenpai else true
        finishRound(dealerRemaining, false)
    }

    private fun resolveNagashiMangan(winners: List<RiichiPlayerState>) {
        val yakuSettlements = mutableListOf<YakuSettlement>()
        val originalScores = seats.associateWith { it.points }
        val allRiichiStickQuantity = seats.sumOf { it.riichiStickAmount }
        val honbaScore = round.honba * 300
        val extraScore = allRiichiStickQuantity * ScoringStick.P1000.point + honbaScore
        val atamahanePlayer = seatOrderFromDealer().firstOrNull { it in winners }

        winners.forEach { winner ->
            val settlement = YakuSettlement.nagashiMangan(
                displayName = winner.displayName,
                uuid = winner.uuid,
                doraIndicators = doraIndicators.map { it.mahjongTile },
                uraDoraIndicators = uraDoraIndicators.map { it.mahjongTile },
                isDealer = winner == dealer
            )
            yakuSettlements += settlement
            val score = settlement.score + if (winner == atamahanePlayer) extraScore else 0
            winner.points += score
            seats.filter { it != winner }.forEach { other ->
                other.points -= settlement.score / 3
                if (winner == atamahanePlayer) {
                    other.points -= honbaScore / 3
                }
            }
        }

        val scoreList = seats.map { player ->
            val original = originalScores.getValue(player)
            ScoreItem(player.displayName, player.uuid, original, player.points - original)
        }
        lastResolution = RoundResolution(
            title = "NagashiMangan",
            yakuSettlements = yakuSettlements,
            scoreSettlement = ScoreSettlement("NagashiMangan", scoreList)
        )
        finishRound(dealer.isTenpai, false)
    }

    private fun personalSituation(
        player: RiichiPlayerState,
        isTsumo: Boolean = false,
        isChankan: Boolean = false,
        isRinshanKaihoh: Boolean = false
    ): PersonalSituation {
        val selfWindNumber = seatOrderFromDealer().indexOf(player)
        val jikaze = Wind.entries[selfWindNumber]
        val isIppatsu = player.isIppatsu(seats, discards)
        return PersonalSituation(
            isTsumo,
            isIppatsu,
            player.riichi,
            player.doubleRiichi,
            isChankan,
            isRinshanKaihoh,
            jikaze
        )
    }

    private fun seatOrderFromDealer(): List<RiichiPlayerState> =
        List(4) { seats[(round.round + it) % 4] }

    private fun seatOrderFrom(target: RiichiPlayerState): List<RiichiPlayerState> {
        val index = seats.indexOf(target)
        return List(4) { seats[(index + it) % 4] }
    }

    private fun claimTarget(claimer: RiichiPlayerState, discarder: RiichiPlayerState): ClaimTarget {
        val diff = (seats.indexOf(claimer) - seats.indexOf(discarder) + 4) % 4
        return when (diff) {
            1 -> ClaimTarget.LEFT
            2 -> ClaimTarget.ACROSS
            3 -> ClaimTarget.RIGHT
            else -> ClaimTarget.SELF
        }
    }

    private fun isSuukaikanAbort(): Boolean {
        if (kanCount < 4) {
            return false
        }
        return seats.count { player -> player.fuuroList.count { it.isKan } > 0 } > 1
    }

    private fun finishRound(dealerRemaining: Boolean, clearRiichiSticks: Boolean) {
        started = false
        pendingReaction = null
        currentDrawIsRinshan = false
        pendingAbortiveDraw = null
        if (clearRiichiSticks) {
            seats.forEach { it.sticks.removeIf { stick -> stick == ScoringStick.P1000 } }
        }
        if (seats.any { it.points < 0 }) {
            gameFinished = true
            awardRemainingRiichiDepositsToFirstPlace()
            return
        }

        if (!round.isAllLast(rule)) {
            if (dealerRemaining) {
                round.honba++
            } else {
                round.nextRound()
            }
            return
        }

        val firstPlace = placementOrder().first()
        if (dealerRemaining) {
            if (firstPlace == dealer && dealer.points >= rule.minPointsToWin) {
                gameFinished = true
            } else {
                round.honba++
            }
        } else {
            if (firstPlace.points >= rule.minPointsToWin) {
                gameFinished = true
            } else {
                val finalRound = rule.length.finalRound
                if (round.wind == finalRound.first && round.round == finalRound.second) {
                    gameFinished = true
                } else {
                    round.nextRound()
                }
            }
        }
        if (gameFinished) {
            awardRemainingRiichiDepositsToFirstPlace()
        }
    }

    private fun awardRemainingRiichiDepositsToFirstPlace() {
        val riichiPoints = seats.sumOf { it.riichiStickAmount } * ScoringStick.P1000.point
        if (riichiPoints > 0) {
            placementOrder().first().points += riichiPoints
        }
        seats.forEach { it.sticks.removeIf { stick -> stick == ScoringStick.P1000 } }
    }
}

