package ir.scarpin.m.twa.constants

/**
 * @author AMostafa
 * |
 * Constants of ECU Params and OBD-II Device
 **/
object CEcu {

    /**
     * Status of Process
     **/
    const val STATE_LOG = 1000
    const val STATE_NONE = 0
    const val STATE_LISTEN = 1
    const val STATE_READ = 6
    const val STATE_CONNECTING = 2
    const val STATE_CONNECTED = 3
    const val STATE_CONNECT_LOST = 4
    const val STATE_CONNECT_FAILED = 5

    /**
     * ECU Comments
     * 
     * --> ATZ       : reset all
     * --> ATDP      : Describe the current Protocol
     * --> ATAT0-1-2 : Adaptive Timing Off - Adaptive Timing Auto1 - Adaptive Timing Auto2
     * --> ATE0-1    : Echo Off - Echo On
     * --> ATSP0     : Set Protocol to Auto and save it
     * --> ATMA      : Monitor All
     * --> ATL1-0    : LineFeeds On - LineFeeds Off
     * --> ATH1-0    : Headers On - Headers Off
     * --> ATS1-0    : printing of Spaces On - printing of Spaces Off
     * --> ATAL      : Allow Long (>7 byte) messages
     * --> ATI       : Read ID
     * --> ATRD      : Read the stored data
     * --> ATSTFF    : Set time out to maximum
     * --> ATSTHH    : Set timeout to 4ms
     **/
    const val ECU_RESET = "ATZ"

    enum class BluetoothDeviceType(val value: Int, name: String) {
        DEVICE_BT(50, "BluetoothDevice"),
        AUDIO_VIDEO_CAMCORDER(1076, "AvCamcorder"),
        AUDIO_VIDEO_CAR_AUDIO(1056, "CarAudio"),
        AUDIO_VIDEO_HANDSFREE(1032, "HandsFree"),
        AUDIO_VIDEO_HEADPHONES(1048, "HeadPhones"),
        AUDIO_VIDEO_HIFI_AUDIO(1064, "HiFiAudio"),
        AUDIO_VIDEO_LOUDSPEAKER(1044, "LoudSpeaker"),
        AUDIO_VIDEO_MICROPHONE(1040, "Microphone"),
        AUDIO_VIDEO_PORTABLE_AUDIO(1052, "PortableAudio"),
        AUDIO_VIDEO_SET_TOP_BOX(1060, "SetTopBox"),
        AUDIO_VIDEO_UNCATEGORIZED(1024, "Uncategorized_Av"),
        AUDIO_VIDEO_VCR(1068, "VCR"),
        AUDIO_VIDEO_VIDEO_CAMERA(1072, "VideoCamera"),
        AUDIO_VIDEO_VIDEO_CONFERENCING(1088, "VideoConferencing"),
        AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER(1084, "Display_LoudSpeaker"),
        AUDIO_VIDEO_VIDEO_GAMING_TOY(1096, "GamingToy"),
        AUDIO_VIDEO_VIDEO_MONITOR(1080, "VideoMonitor"),
        AUDIO_VIDEO_WEARABLE_HEADSET(1028, "WearableHeadset"),
        COMPUTER_DESKTOP(260, "Desktop"),
        COMPUTER_HANDHELD_PC_PDA(272, "HandheldPcPDA"),
        COMPUTER_LAPTOP(268, "Laptop"),
        COMPUTER_PALM_SIZE_PC_PDA(276, "PalmSizePcPDA"),
        COMPUTER_SERVER(264, "Server"),
        COMPUTER_UNCATEGORIZED(256, "Uncategorized_Pc"),
        COMPUTER_WEARABLE(280, "WearablePc"),
        HEALTH_BLOOD_PRESSURE(2308, "BloodPressure"),
        HEALTH_DATA_DISPLAY(2332, "DataDisplay"),
        HEALTH_GLUCOSE(2320, "Glucose"),
        HEALTH_PULSE_OXIMETER(2324, "PulseOxiMeter"),
        HEALTH_PULSE_RATE(2328, "PulseRate"),
        HEALTH_THERMOMETER(2312, "Thermometer"),
        HEALTH_UNCATEGORIZED(2304, "Uncategorized_Health"),
        HEALTH_WEIGHING(2316, "Weighing"),
        PERIPHERAL_KEYBOARD(1344, "Keyboard"),
        PERIPHERAL_KEYBOARD_POINTING(1472, "KeyboardPointing"),
        PERIPHERAL_NON_KEYBOARD_NON_POINTING(1280, "NonKeyboardPointing"),
        PERIPHERAL_POINTING(1408, "Pointing"),
        PHONE_CELLULAR(516, "PhoneCellular"),
        PHONE_CORDLESS(520, "PhoneCordless"),
        PHONE_ISDN(532, "PhoneISDN"),
        PHONE_MODEM_OR_GATEWAY(528, "PhoneModemOrGateway"),
        PHONE_SMART(524, "SmartPhone"),
        PHONE_UNCATEGORIZED(512, "Uncategorized_Phone"),
        TOY_CONTROLLER(2064, "Controller"),
        TOY_DOLL_ACTION_FIGURE(2060, "DollActionFigure"),
        TOY_GAME(2068, "Game"),
        TOY_ROBOT(2052, "Robot"),
        TOY_UNCATEGORIZED(2048, "Uncategorized_Toy"),
        TOY_VEHICLE(2056, "Vehicle"),
        WEARABLE_GLASSES(1812, "Glasses"),
        WEARABLE_HELMET(1808, "Helmet"),
        WEARABLE_JACKET(1804, "Jacket"),
        WEARABLE_PAGER(1800, "Pager"),
        WEARABLE_UNCATEGORIZED(1792, "Uncategorized_Wearable"),
        WEARABLE_WRIST_WATCH(1796, "WristWatch");

        companion object {
            fun findByValue(value: Int): BluetoothDeviceType {
                return values().find { it.value == value } ?: DEVICE_BT
            }
        }
    }
}