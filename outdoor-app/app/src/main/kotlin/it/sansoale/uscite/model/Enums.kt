package it.sansoale.uscite.model

import kotlinx.serialization.Serializable

@Serializable
enum class TripType(val label: String) {
    MOUNTAIN("Montagna"),
    VAN("Van"),
    BIKE("Bici"),
}

/** Scala CAI delle difficoltà escursionistiche. */
@Serializable
enum class MountainDifficulty(val code: String, val label: String) {
    T("T", "Turistico"),
    E("E", "Escursionistico"),
    EE("EE", "Escursionisti esperti"),
    EEA("EEA", "Esperti con attrezzatura"),
}

@Serializable
enum class MountainActivity(val label: String) {
    HIKE("Escursione"),
    FERRATA("Via ferrata"),
    SKI_TOURING("Scialpinismo"),
    SNOWSHOE("Ciaspole"),
}

@Serializable
enum class OvernightType(val label: String) {
    AREA_SOSTA("Area sosta"),
    CAMPING("Campeggio"),
    FREE("Libero"),
}

@Serializable
enum class CostCategory(val label: String) {
    FUEL("Carburante"),
    PARKING("Sosta"),
    TOLL("Pedaggi"),
    OTHER("Altro"),
}

@Serializable
enum class BikeType(val label: String) {
    ROAD("Strada"),
    GRAVEL("Gravel"),
    MTB("MTB"),
    EBIKE("E-bike"),
}

@Serializable
enum class Surface(val label: String) {
    ASPHALT("Asfalto"),
    DIRT("Sterrato"),
    MIXED("Misto"),
}

@Serializable
enum class Weather(val label: String, val emoji: String) {
    SUNNY("Sereno", "☀️"),
    PARTLY_CLOUDY("Variabile", "⛅"),
    CLOUDY("Nuvoloso", "☁️"),
    FOG("Nebbia", "🌫️"),
    RAIN("Pioggia", "🌧️"),
    STORM("Temporale", "⛈️"),
    SNOW("Neve", "❄️"),
    WIND("Vento", "💨"),
}

@Serializable
enum class TripSource { MANUAL, IMPORTED, RECORDED }

@Serializable
enum class PhotoPositionSource { EXIF, TRACK_TIME, NONE }

enum class StatKind { DISTANCE, ELEVATION, DURATION, SPEED }

/** Statistiche di un'uscita: calcolate dal GPX ma modificabili a mano una per una. */
@Serializable
enum class StatField(val label: String, val kind: StatKind) {
    DISTANCE("Distanza", StatKind.DISTANCE),
    ELEVATION_GAIN("Dislivello +", StatKind.ELEVATION),
    ELEVATION_LOSS("Dislivello −", StatKind.ELEVATION),
    MIN_ELEVATION("Quota minima", StatKind.ELEVATION),
    MAX_ELEVATION("Quota massima", StatKind.ELEVATION),
    TOTAL_DURATION("Durata totale", StatKind.DURATION),
    MOVING_DURATION("Tempo in movimento", StatKind.DURATION),
    AVG_SPEED("Velocità media", StatKind.SPEED),
    MAX_SPEED("Velocità massima", StatKind.SPEED),
}
