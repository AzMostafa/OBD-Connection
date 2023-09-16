package ir.device.obd

object Constants {

    const val STATE_LOG = 1000
    const val STATE_NONE = 0
    const val STATE_LISTEN = 1
    const val STATE_CONNECTING = 2
    const val STATE_CONNECTED = 3

    const val REQUEST_BT_ENABLE: Int = 1004
    const val REQUEST_BT_PERMISSION: Int = 1005

    const val ECU_RESET = "ATZ"
}