package dev.local.ridecompact

import org.json.JSONObject

/**
 * Blocking state machine for the one-screen flow: 预校验 → 开锁轮询 → 还车校验 → 关锁轮询 → 结算确认.
 * Every method must be called from a background thread; the UI only renders [state] and [priceRule].
 * Dependencies are injected so the flow stays free of Android APIs and testable without a device.
 */
class RideFlow(
    private val state: RideState,
    private val api: () -> RideApi,
    private val locate: () -> DoubleArray,
    private val persist: () -> Unit,
    private val log: (String) -> Unit,
    private val sleep: (Long) -> Unit,
    private val wallClock: () -> Long,
    private val uptime: () -> Long,
) {
    private companion object {
        const val PREPARED_TTL_MS = 60_000L
        const val CLOSE_CHECK_TTL_MS = 30_000L
        const val POLL_TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 1_800L
        const val CLOSE_POLL_INTERVAL_MS = 1_500L
    }

    /** Price rule returned by the last successful pre-check; empty until then. */
    var priceRule: String = ""
        private set
    private var closeCheckedAt: Long = 0L
    private var closeChecked = false

    fun canPrepare() = state.canPrepare()
    fun canOpen() = state.canOpen(wallClock())
    fun canClose() = state.canClose()
    fun closeCheckFresh() = closeChecked && uptime() - closeCheckedAt <= CLOSE_CHECK_TTL_MS

    /** Adopts a scanned or typed bike number without touching the network. */
    fun selectBike(raw: String) {
        val selected = ScanParser.parse(raw)
        check(state.canPrepare()) { "当前订单尚未处理完成" }
        state.clear()
        state.bike = selected.bikeNo
        priceRule = ""
        persist()
        log("车辆已识别 · ${selected.source} · ${mask(selected.bikeNo)}")
    }

    fun prepare(rawBike: String) {
        selectBike(rawBike)
        val position = locate()
        val prepared = api().prepare(state.bike, position)
        priceRule = prepared.getString("priceRule")
        state.prepared(state.bike, prepared.getString("orderGuid"), wallClock(), prepared.optBoolean("specialBike"))
        persist()
        log("预校验通过，尚未创建骑行" + if (state.specialBike) " · 服务端已识别特殊车型" else "")
    }

    /** Creates the real order; call only after the user confirmed the price rule. */
    fun open() {
        check(state.canOpen(wallClock())) { "预校验已过期，请先刷新订单" }
        val position = locate()
        state.opening(wallClock())
        persist()
        val result = api().create(state.bike, state.order, position)
        check(result.optBoolean("result") && state.order == result.optString("rideId")) { "创建结果不明确，请刷新订单或返回官方 App" }
        log("创建请求已受理；等待服务器确认")
        poll(state.order)
    }

    /** Location check that must pass before the user is offered the return confirmation. */
    fun checkClose() {
        check(state.canClose()) { "当前订单状态不允许还车" }
        val client = api()
        var result = client.preClose(state.order, state.bike, locate(), 0, 0)
        var cause = result.optInt("causeType", -1)
        if (cause == 1102 || cause == 1104) throw ApiResponse.closeFailure(result)
        val deadline = uptime() + POLL_TIMEOUT_MS
        while (result.optInt("status", -1) == 2 && cause == 1101 && uptime() < deadline) {
            sleep(CLOSE_POLL_INTERVAL_MS)
            result = client.preClose(state.order, state.bike, locate(), 1, 0)
            cause = result.optInt("causeType", -1)
        }
        if (result.optInt("status", -1) != 3 || cause != 1103 || result.optInt("penaltyFree", 0) != 1) throw ApiResponse.closeFailure(result)
        closeCheckedAt = uptime()
        closeChecked = true
        log("还车位置校验通过，等待确认关锁")
    }

    fun commitClose() {
        check(state.canClose()) { "当前订单状态不允许还车" }
        check(closeCheckFresh()) { "位置校验已过期，请重新还车校验" }
        val position = locate()
        val order = state.order
        state.closing()
        closeChecked = false
        closeCheckedAt = 0L
        persist()
        val sent = api().preClose(order, state.bike, position, 1, 2)
        log("关锁已提交，阶段 ${sent.optInt("status", -1)}；不自动重发关锁请求")
        poll(order)
    }

    fun refresh() {
        val data = api().checkRide()
        val info = data.optJSONObject("rideInfo")
        if (data.optBoolean("_noRide")) {
            noRide()
            return
        }
        check(info != null && rawOrder(info).isNotEmpty()) { "user.tw.ride.check 响应缺少订单标识，未修改本地状态" }
        applyRide(info)
        if (info.optInt("rideStatus", -1) == 2) showEnd(rawOrder(info))
    }

    private fun noRide() {
        val rest = state.stage == RideState.Stage.IDLE || state.stage == RideState.Stage.ENDED || state.stage == RideState.Stage.PREPARED ||
            (state.stage == RideState.Stage.UNKNOWN && state.order.isEmpty())
        if (rest) {
            state.clear()
            priceRule = ""
            persist()
            log("301：无骑行中订单，可以扫码")
            return
        }
        val result = api().orderStatus(state.order)
        val main = result.optInt("rideStatus", -1)
        log("无骑行中订单；已提交订单状态 $main / ${result.optInt("subStatus", -1)}")
        if (main == 30 && showEnd(state.order)) {
            state.stage = RideState.Stage.ENDED
            persist()
        }
    }

    private fun applyRide(info: JSONObject) {
        val order = rawOrder(info)
        check(state.order.isEmpty() || state.order == order || state.stage == RideState.Stage.IDLE || state.stage == RideState.Stage.ENDED) {
            "服务器订单与本地操作不一致，请在官方 App 确认"
        }
        state.order = order
        state.bike = info.optString("bikeNo")
        state.observed(info.optInt("rideStatus", -1))
        persist()
        log("服务器状态 ${info.optInt("rideStatus", -1)} · 骑行 ${info.optInt("rideTimeInSeconds", 0)} 秒")
    }

    private fun poll(expectedOrder: String) {
        val deadline = uptime() + POLL_TIMEOUT_MS
        val closing = state.stage == RideState.Stage.CLOSING
        while (uptime() < deadline) {
            sleep(POLL_INTERVAL_MS)
            val data = api().checkRide()
            val info = data.optJSONObject("rideInfo")
            if (data.optBoolean("_noRide")) {
                if (api().orderStatus(expectedOrder).optInt("rideStatus", -1) == 30 && showEnd(expectedOrder)) {
                    state.stage = RideState.Stage.ENDED
                    persist()
                    return
                }
                continue
            }
            if (info == null) continue
            check(expectedOrder == rawOrder(info)) { "返回订单不一致，请打开官方 App" }
            val current = info.optInt("rideStatus", -1)
            if (current == 2 || (current == 0 && !closing)) {
                applyRide(info)
                if (current == 2) showEnd(expectedOrder)
                return
            }
        }
        log("等待超时，结果尚未确认。请刷新订单；不要重复创建或关锁")
    }

    private fun showEnd(order: String): Boolean {
        val end = api().end(order)
        val trade = end.optJSONObject("tradeRegionInfo")
        if (trade == null || trade.optInt("orderStatus", -1) != 2) {
            log("关锁后结算尚未确认")
            return false
        }
        val details = trade.optJSONObject("orderInfo")
        val region = end.optJSONObject("rideRegionInfo")
        if (details != null) log("结算已确认 · 应付 " + details.optString("payAmount", "?") + " 元" + if (region == null) "" else " · " + region.optInt("rideDuration", 0) + " 秒 / " + region.optString("rideDistance", "?") + " 公里")
        return true
    }

    private fun rawOrder(info: JSONObject): String {
        val raw = info.optString("originGuid", info.optString("rideGuid"))
        return if (raw.startsWith("TW")) raw.substring(2) else raw
    }
}

internal fun mask(value: String) = if (value.length < 5) "****" else "****" + value.substring(value.length - 4)
