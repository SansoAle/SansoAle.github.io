# Diario Uscite — app Android

App Android nativa per archiviare gite in montagna, viaggi in van e giri in bici.
Funziona **completamente offline**: dati, tracce GPX e foto restano nella memoria del telefono.
Internet serve solo per scaricare le tile della mappa; quelle già viste restano in cache (300 MB).

## Funzionalità

- **Tre tipi di uscita** con campi comuni (titolo, date, luogo/regione, note, compagni, meteo e
  temperatura, voto 1–5, tag) e campi specifici:
  - *Montagna*: tipo (escursione, ferrata, scialpinismo, ciaspole), difficoltà CAI (T/E/EE/EEA),
    vetta/meta con quota, rifugi.
  - *Van*: km su strada, tappe con pernottamento (area sosta, campeggio, libero), costi
    (carburante, sosta, pedaggi, altro) legati al viaggio o alla singola tappa.
  - *Bici*: tipo di bici (strada, gravel, MTB, e-bike) e fondo (asfalto, sterrato, misto).
- **GPX**: importazione da file o con *Condividi con* / *Apri con* da Strava, Komoot, Garmin e file
  manager. Più tracce per uscita. Il file originale viene conservato e si può esportare.
- **Statistiche automatiche** dal GPX: distanza, dislivello ±, quota min/max, durata totale, tempo
  in movimento, velocità media e massima. Il dislivello usa un filtro mediano, una media mobile sulla
  distanza e un'isteresi di 3 m. Ogni valore è **modificabile a mano**: il valore corretto resta anche
  se si aggiungono altre tracce, e si può ripristinare quello calcolato. Le uscite senza traccia si
  compilano interamente a mano.
- **Dettaglio**: mappa MapLibre (OpenTopoMap o OpenStreetMap), **profilo altimetrico interattivo**
  (toccando o trascinando, il punto viene evidenziato sulla mappa), statistiche, galleria e note.
- **Foto**: Photo Picker di Android, copia nella memoria interna con miniature. Le foto con GPS nell'EXIF
  diventano marker sulla mappa. Le altre vengono posizionate confrontando l'orario di scatto con i
  timestamp del GPX. Galleria a schermo intero con zoom, didascalie e foto di copertina.
- **Home** con card, filtri per tipo, anno e tag, e ricerca testuale (senza distinzione di accenti).
- **Mappa globale** con tutte le tracce colorate per tipo (verde montagna, arancio van, blu bici).
- **Statistiche**: totali per anno e per tipo, grafici mensili o annuali (Vico), record personali.
- **Impostazioni**: unità metriche o imperiali, tema chiaro/scuro/sistema, colori dinamici, stile mappa.
- **Backup**: esportazione e ripristino di tutto l'archivio (dati, GPX e foto) in un file ZIP salvato
  dove vuoi tramite Storage Access Framework.

## Struttura del progetto

```
outdoor-app/
├─ core/track/            Modulo Kotlin puro (JVM), testato senza Android
│  ├─ GpxParser / GpxWriter      GPX 1.0/1.1, trk/rte/wpt, estensioni ignorate
│  ├─ ElevationSmoother          filtro mediano + media mobile + isteresi
│  ├─ TrackStatsCalculator       statistiche (parametri diversi per tipo di uscita)
│  ├─ ElevationProfile           profilo per il grafico
│  ├─ TrackSimplifier            Douglas-Peucker
│  ├─ PolylineCodec              polilinee compatte per la mappa globale
│  ├─ PhotoLocator               posizione delle foto per orario
│  └─ TrackPointsCodec           cache binaria dei punti
│  └─ src/test/…                 test + GPX di esempio (escursione, Garmin, Komoot)
└─ app/
   ├─ data/db         Room: entità, relazioni, DAO (salvataggio in un'unica transazione)
   ├─ data/files      copia di GPX e foto nella memoria interna, EXIF, miniature
   ├─ data/settings   DataStore
   ├─ data/backup     ZIP (archivio.json + tracks/ photos/ thumbs/)
   ├─ data/repo       TripRepository
   ├─ domain          statistiche automatiche/manuali, statistiche d'archivio
   ├─ ui/…            home, detail, edit, gallery, globalmap, stats, settings, map, chart
   └─ recording       Fase 2: interfaccia del registratore di tracce (non implementato)
```

### Schema del database

| Tabella | Contenuto |
|---|---|
| `trip` | dati comuni, statistiche (SI), `overriddenStats` (campi modificati a mano), bounding box, copertina |
| `mountain_detail` / `hut` | dettagli montagna, rifugi |
| `van_detail` / `van_stage` / `van_cost` | km su strada, tappe, costi (in centesimi, tappa facoltativa) |
| `bike_detail` | tipo di bici, fondo |
| `track` | file GPX originale, cache punti, polilinea semplificata, statistiche della traccia |
| `photo` | file, miniatura, orario di scatto, posizione e origine (EXIF / orario traccia), didascalia |
| `tag` / `trip_tag`, `companion` / `trip_companion` | relazioni molti-a-molti |

Lo schema esportato è in `app/schemas/` (generato alla compilazione). Le future modifiche richiederanno
una migrazione in `di/AppModule.kt`.

## Compilazione

Servono JDK 17+ e l'Android SDK (API 36). Con Android Studio basta aprire la cartella `outdoor-app`.
Da riga di comando:

```bash
cd outdoor-app
./gradlew :core:track:test :app:testDebugUnitTest   # test
./gradlew assembleDebug                             # APK di debug
./gradlew assembleRelease                           # APK "release" (più fluido)
```

Gli APK si trovano in `app/build/outputs/apk/debug/` e `app/build/outputs/apk/release/`: uno per
architettura (`arm64-v8a`, `armeabi-v7a`, `x86_64`) più uno `universal` che funziona ovunque.

A ogni push che tocca `outdoor-app/`, il workflow GitHub Actions `.github/workflows/android.yml`
esegue i test e compila entrambi gli APK. Li trovi in **Actions → ultima esecuzione → Artifacts →
diario-uscite-apk**.

### Firma

Entrambe le varianti sono firmate con la chiave `keystore/uscite.keystore` presente nel repository.
Così ogni APK, compilato in locale o su GitHub, si installa **sopra** il precedente senza perdere i dati.
La versione debug ha un id diverso (`it.sansoale.uscite.debug`) e convive con la release.
È una chiave pensata per un'app personale: per pubblicare l'app sul Play Store servirebbe una chiave
privata, non presente nel repository.

## Installare l'APK sul telefono

1. Dal browser del telefono apri
   **https://github.com/SansoAle/SansoAle.github.io/raw/apk/diario-uscite.apk**
   (sempre l'ultima versione compilata, per telefoni arm64 — praticamente tutti quelli degli ultimi anni).
   Se l'installazione dice che l'app non è compatibile usa
   `https://github.com/SansoAle/SansoAle.github.io/raw/apk/diario-uscite-universale.apk`.
2. In alternativa scaricalo dal computer e trasferiscilo via USB, Google Drive o Telegram.
3. Aprilo dal file manager (o dalla notifica di download). Android chiederà di **consentire
   l'installazione da questa origine** per l'app da cui lo apri (Chrome, Files…): attiva l'opzione e
   torna indietro.
4. Tocca **Installa**. Play Protect potrebbe avvisare che l'app non è nota: scegli *Installa comunque*
   (l'app è tua e non ha permessi sensibili).
5. Per aggiornare in futuro ripeti la procedura con il nuovo APK: i dati restano.

In alternativa, con il telefono collegato via USB e il *Debug USB* attivo (Impostazioni → Info sul
telefono → tocca 7 volte "Numero build" → Opzioni sviluppatore):

```bash
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

## Note

- **Coordinate delle foto**: Android nasconde il GPS dell'EXIF alle app senza il permesso "Accesso alla
  posizione dei contenuti multimediali". L'app lo chiede la prima volta che aggiungi foto. Se lo neghi,
  le foto vengono comunque posizionate in base all'orario confrontato con la traccia.
- **Orario delle foto**: se l'EXIF non contiene il fuso orario si usa quello del telefono.
- **Tile della mappa**: OpenTopoMap e OpenStreetMap sono servizi gratuiti a uso moderato. L'app li
  usa solo per visualizzare, con cache locale.

## Fase 2 (predisposta)

- **Registrazione GPS dal vivo**: `recording/TrackRecorder.kt` descrive il foreground service previsto
  (tipo `location`, funzionante a schermo spento) e i permessi da aggiungere. Le uscite registrate
  useranno `TripSource.RECORDED` e lo stesso calcolo statistiche delle tracce importate.
- **Mappe offline**: gli stili sono centralizzati in `ui/map/MapStyles.kt`. Le regioni si potranno
  scaricare con l'`OfflineManager` di MapLibre, già usato oggi per la cache.
