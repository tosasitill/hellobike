package dev.local.ridecompact

class RideState {
    enum class Stage { IDLE, PREPARED, OPENING, RIDING, CLOSING, UNKNOWN, ENDED }

    @JvmField var stage: Stage = Stage.IDLE
    @JvmField var bike: String = ""
    @JvmField var order: String = ""
    @JvmField var specialBike: Boolean = false
    @JvmField var preparedAt: Long = 0L

    fun canPrepare() = stage == Stage.IDLE || stage == Stage.ENDED
    fun canOpen(now: Long) = stage == Stage.PREPARED && order.isNotEmpty() && now >= preparedAt && now - preparedAt < 60_000L
    fun canClose() = stage == Stage.RIDING && order.isNotEmpty() && bike.isNotEmpty()

    fun prepared(bike: String, order: String, now: Long) = prepared(bike, order, now, false)

    fun prepared(bike: String, order: String, now: Long, specialBike: Boolean) {
        this.bike = bike
        this.order = order
        this.preparedAt = now
        this.specialBike = specialBike
        stage = Stage.PREPARED
    }

    fun opening(now: Long) {
        check(canOpen(now)) { "预校验已失效，请同步订单" }
        stage = Stage.OPENING
    }

    fun closing() {
        check(canClose()) { "当前订单状态不允许还车" }
        stage = Stage.CLOSING
    }

    fun observed(status: Int) {
        stage = when {
            status == 2 -> Stage.ENDED
            status == 0 && stage != Stage.CLOSING -> Stage.RIDING
            status != 0 -> Stage.UNKNOWN
            else -> stage
        }
    }

    fun clear() {
        stage = Stage.IDLE
        bike = ""
        order = ""
        specialBike = false
        preparedAt = 0L
    }
}
