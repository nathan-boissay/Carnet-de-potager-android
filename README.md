# 🌱 Carnet de Potager — Application Android Native (Kotlin + Jetpack Compose)

> **Projet Android M2 Informatique — Università di Corsica**  
> Portage natif complet de la Progressive Web App **[Carnet de Potager](https://carnetdepotager.vercel.app/)** avec capacité matérielle exclusive (**Luxmètre Potager** via le capteur physique `Sensor.TYPE_LIGHT`).

---

## 📦 Téléchargement de l'APK (Release GitHub)

- **Dernière Release GitHub (APK prêt à installer)** : [**📥 Télécharger `Carnet-de-potager.apk` dans les Releases GitHub**](../../releases/latest)
- **Identifiant du package (`applicationId`)** : `corsica.universita.carnet`
- **SDK Android** : `minSdk = 26` (Android 8.0+) · `targetSdk = 35` · `compileSdk = 35`
- **PWA associée** : [https://carnetdepotager.vercel.app/](https://carnetdepotager.vercel.app/)

---

## 🌿 1. Présentation de l'Application

**Carnet de Potager** est un compagnon de jardinage conçu pour fonctionner **100 % hors-ligne au fond du jardin** tout en se synchronisant dès que la connexion revient :

1. **Gestion complète des fiches de plantes** :
   - Création, consultation et suppression de plantes (*Légume-fruit*, *Aromatique*, *Feuille*, *Racine*).
   - Calcul automatique de l'urgence d'arrosage (`En retard`, `À arroser aujourd'hui`, `OK`) et arrosage en un geste (`💧 Arrosé`).
   - Épinglage des plantes prioritaires en tête de liste (`📌`) et filtrage dynamique par recherche ou catégorie.
   - Hydratation initiale des 3 plantes repères de la PWA (*Tomates Cerises*, *Basilic Grand Vert*, *Courgettes Rondes*).
2. **Enrichissement terrain natif sur chaque fiche** :
   - **☀️ Luxmètre Potager en temps réel** (`Sensor.TYPE_LIGHT`) pour mesurer l'ensoleillement de la parcelle en lux (`lx`) et diagnostiquer l'exposition (*Ombre*, *Mi-ombre*, *Plein soleil*).
   - **📸 Prise de photo native** via **CameraX** stockée dans l'espace privé de l'application (`filesDir/photos/`) et partageable via `FileProvider`.
   - **📍 Géolocalisation GPS de la parcelle** (`FusedLocationProviderClient`) avec latitude, longitude et précision en mètres.
3. **Synchronisation Outbox Offline-First & Notifications** :
   - Chaque modification hors-ligne marque la fiche `synced = 0` et programme un `SyncWorker` (**WorkManager**) sous contrainte réseau (`NetworkType.CONNECTED`).
   - Envoi vers le serveur d'écho HTTP via **Retrofit + OkHttp**, passage à `synced = 1` et notification locale permettant d'ouvrir directement la fiche concernée.
   - Réception de notifications push via **Firebase Cloud Messaging (`CarnetMessagingService`)**.
4. **Pont bidirectionnel Web $\leftrightarrow$ Android (App Links)** :
   - **Web $\rightarrow$ App** : Ouverture automatique de l'application Android sur `https://carnetdepotager.vercel.app/` et `https://carnetdepotager.vercel.app/plant/{id}` grâce à `autoVerify="true"` et `/.well-known/assetlinks.json`.
   - **App $\rightarrow$ Web** : Bouton explicite **`🌐 Site Web`** dans la barre supérieure et sur chaque fiche plante pour ouvrir la PWA dans le navigateur externe, ainsi que le partage système (`ACTION_SEND`).

---

## 🛠️ 2. Choix Techniques & Architecture

### A. Architecture en 4 couches (Unidirectional Data Flow)

```
[ UI Jetpack Compose (Material 3) ]
        │  ▲
        │  │ StateFlow<List<Plant>> + collectAsStateWithLifecycle()
        ▼  │
[ PotagerViewModel + SavedStateHandle ]
        │  ▲
        ▼  │ Flow<List<Plant>> / suspend functions
[ PlantsRepository (Single Source of Truth) ]
   ├── Room Database v4 (SQLite : carnet.db + Migrations 1→2→3→4)
   ├── PhotoStore (filesDir/photos/*.jpg + FileProvider)
   ├── Preferences DataStore (jeton FCM & préférences)
   ├── Retrofit + OkHttp (cache HTTP 10 Mo + kotlinx.serialization)
   └── WorkManager (SyncWorker Outbox Offline-First)
```

| Couche | Fichiers clés | Rôle et justification technique |
| :--- | :--- | :--- |
| **UI & Navigation** | `MainActivity.kt`, `CarnetNavigation.kt`, `PotagerListScreen.kt`, `PlantDetailScreen.kt` | Interface 100 % déclarative **Jetpack Compose (Material 3)**. Collecte les flux avec `collectAsStateWithLifecycle()` pour stopper automatiquement les capteurs et requêtes lorsque l'application passe en arrière-plan. |
| **ViewModel & État** | `PotagerViewModel.kt` | Expose un `StateFlow` avec `SharingStarted.WhileSubscribed(5_000)` pour survivre aux rotations d'écran sans ré-exécuter les requêtes SQLite. Persiste le brouillon de création dans **`SavedStateHandle`** pour survivre à la mort du processus (`Process Death`). |
| **Repository** | `PlantsRepository.kt` | Point d'entrée unique des données. Sépare strictement le modèle SQLite (`PlantEntity`), le modèle métier UI (`Plant`) et le modèle réseau (`PlantDto`). |
| **Persistance Room (`v1` $\rightarrow$ `v4`)** | `CarnetDatabase.kt`, `PlantDao.kt`, `PlantEntity.kt` | Base SQLite versionnée de `1` à `4` avec migrations SQL explicites (`MIGRATION_1_2` pour `photoPath`, `MIGRATION_2_3` pour `lat`/`lng`/`accuracy`, `MIGRATION_3_4` pour `lastLux`) et schémas JSON exportés dans `app/schemas/`. |
| **Stockage Fichiers & DataStore** | `PhotoStore.kt`, `Settings.kt` | Les photos JPEG ne sont jamais stockées en BLOB dans SQLite (évite les erreurs `CursorWindow` de 2 Mo) mais dans `filesDir/photos/`, Room ne conservant que le chemin. `Preferences DataStore` stocke le token FCM de manière asynchrone. |
| **Réseau & Outbox WorkManager** | `Network.kt`, `SyncApi.kt`, `SyncWorker.kt` | Client unique `OkHttpClient` avec cache de 10 Mo (`cacheDir/http`) et `Retrofit`. `SyncWorker` (`CoroutineWorker`) utilise `ExistingWorkPolicy.KEEP` et `NetworkType.CONNECTED` pour vider l'outbox même si l'application a été fermée. |
| **Permissions Runtime** | `PermissionFlow.kt` | Machine à 4 états réutilisable (`Granted`, `FirstAsk`, `Rationale`, `PermanentlyDenied` avec redirection vers les paramètres système) appliquée à `POST_NOTIFICATIONS`, `CAMERA` et `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`. |

### B. Justification de la Capacité Native Exclusive : Le Luxmètre (`Sensor.TYPE_LIGHT`)

- **Implémentation** : `data/sensor/LightSensorSource.kt`.
- **Pourquoi c'est impossible en PWA** : Sur les navigateurs mobiles (Chrome Android / Safari iOS), l'API Web `AmbientLightSensor` est désactivée par défaut pour des raisons de confidentialité et de lutte contre le *fingerprinting* matériel.
- **Plus-value native Android** : L'application accède directement au photodétecteur matériel via `SensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)` encapsulé dans un `callbackFlow` Kotlin. Dès que la fiche plante est affichée, la jauge en lux s'actualise en direct et se désabonne automatiquement dans le bloc `awaitClose` lorsque l'utilisateur quitte l'écran.
- **Seuils agronomiques intégrés** :
  - `< 1 000 lx` : **Ombre** (adapté aux feuillages tolérants, insuffisant pour les légumes-fruits).
  - `1 000 – 10 000 lx` : **Mi-ombre** (idéal pour le *Basilic Grand Vert*, la menthe ou les salades).
  - `> 10 000 lx` : **Plein soleil** (idéal pour les *Tomates Cerises* et les *Courgettes Rondes*).

---

## 🚀 3. Comment lancer et compiler l'application

### Prérequis
- **Android Studio** (Ladybug / Meerkat ou plus récent) ou **JDK 17 / 21** en ligne de commande.
- **Android SDK Platform 35** (`compileSdk = 35`).
- Un **téléphone Android physique** (Android 8.0+, débogage USB activé) ou un **émulateur Android**.

### Méthode 1 : Installer directement l'APK pré-compilé
1. Téléchargez le fichier `Carnet-de-potager.apk` depuis la section [**Releases GitHub**](../../releases/latest) (ou utilisez `app/build/outputs/apk/debug/app-debug.apk` après compilation).
2. Avec un téléphone ou émulateur connecté en ADB :
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n corsica.universita.carnet/.MainActivity
   ```

### Méthode 2 : Ouvrir et lancer depuis Android Studio
1. Ouvrez **Android Studio** $\rightarrow$ **Open** $\rightarrow$ sélectionnez la racine de ce dépôt.
2. Laissez Gradle synchroniser le projet (le fichier `debug.keystore` est inclus pour que la signature et la vérification des App Links `assetlinks.json` fonctionnent immédiatement sur n'importe quelle machine).
3. Cliquez sur **Run ▶ (`Shift + F10`)** sur votre émulateur ou téléphone.

### Méthode 3 : Compiler en ligne de commande avec Gradle Wrapper
```bash
# Sous Windows (PowerShell / CMD)
.\gradlew.bat assembleDebug

# Sous macOS / Linux
./gradlew assembleDebug
```
L'APK signé est généré dans : `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧪 4. Commandes ADB utiles pour tester toutes les fonctionnalités

```bash
# 1. Tester le Luxmètre sur l'émulateur :
# Ouvrez une fiche plante puis dans l'émulateur : "..." (Extended Controls) -> Virtual sensors -> Additional sensors -> Light (lux)

# 2. Vérifier la survie du brouillon à la mort du processus (SavedStateHandle - TD A2)
# Saisissez un nom de plante sans valider, appuyez sur le bouton Home, puis :
adb shell am kill corsica.universita.carnet

# 3. Tester l'ouverture directe par App Link (Web -> App - TD A3)
adb shell am start -a android.intent.action.VIEW -d "https://carnetdepotager.vercel.app/plant/1"

# 4. Tester l'Outbox hors-ligne WorkManager (TD A5)
adb shell cmd connectivity airplane-mode enable
# -> Ajoutez une plante dans l'app (badge "1 fiche en attente de synchro"), puis réactivez le réseau :
adb shell cmd connectivity airplane-mode disable

# 5. Tester l'ouverture directe d'une fiche via l'extra PendingIntent d'une notification (TD A6)
adb shell am start -n corsica.universita.carnet/.MainActivity --el noteId 1

# 6. Simuler une coordonnée GPS sur l'émulateur (Corte - Università di Corsica - TD A7)
adb emu geo fix 9.1500 42.3000
```

---

## 📂 5. Structure du projet

```text
├── app/
│   ├── schemas/corsica.universita.carnet.data.local.CarnetDatabase/
│   │   ├── 1.json, 2.json, 3.json, 4.json       # Schémas Room exportés (v1 -> v4)
│   ├── src/main/
│   │   ├── AndroidManifest.xml                  # Permissions, FileProvider, FCM Service & App Links
│   │   ├── java/corsica/universita/carnet/
│   │   │   ├── CarnetApp.kt                     # Application & conteneur d'injection (CarnetContainer)
│   │   │   ├── MainActivity.kt                  # Cycle de vie, réception ACTION_SEND / ACTION_VIEW / noteId
│   │   │   ├── data/
│   │   │   │   ├── Plant.kt                     # Modèle métier UI & calcul du statut d'arrosage
│   │   │   │   ├── PhotoStore.kt                # Stockage interne filesDir/photos/ + FileProvider
│   │   │   │   ├── PlantsRepository.kt          # Source de vérité unique, hydratation & synchro Outbox
│   │   │   │   ├── local/                       # Room (CarnetDatabase, PlantDao, PlantEntity) & DataStore
│   │   │   │   ├── remote/                      # OkHttp (cache 10 Mo) + Retrofit (SyncApi)
│   │   │   │   ├── location/                    # FusedLocationProviderClient (LocationSource)
│   │   │   │   └── sensor/                      # Capteur matériel Sensor.TYPE_LIGHT (LightSensorSource)
│   │   │   ├── work/SyncWorker.kt               # CoroutineWorker WorkManager (NetworkType.CONNECTED)
│   │   │   ├── notif/                           # Notifications (canaux sync & hub) + CarnetMessagingService
│   │   │   └── ui/
│   │   │       ├── CarnetNavigation.kt          # Navigation Compose entre Liste et Détail
│   │   │       ├── permissions/PermissionFlow.kt# Machine à 4 états pour permissions runtime
│   │   │       └── notes/                       # PotagerViewModel, PotagerListScreen, PlantDetailScreen, PlantIntents
│   │   └── res/                                 # Thème Material 3, chaînes, icônes et xml/file_paths.xml
│   └── build.gradle.kts
├── gradle/libs.versions.toml                    # Catalogue des versions (Kotlin, Compose, Room, WorkManager, CameraX…)
├── debug.keystore                               # Keystore partagé pour la vérification SHA-256 des App Links
└── README.md
```
