# Gmail Filter Advanced (GFA)

**GFA (Gmail Filter Advanced)** è un'applicazione desktop open-source sviluppata in **Kotlin** e **Compose for Desktop**. Progettata per power-user e professionisti che gestiscono caselle Gmail intasate, GFA ti aiuta a triagiare, pulire e fare il backup delle tue email sfruttando regole intelligenti generate e gestite tramite l'intelligenza artificiale di Google Gemini.

## 🚀 Caratteristiche Principali

*   **Sincronizzazione Incrementale**: Scarica e salva localmente (in un database SQLite) l'intestazione e i metadati delle tue email, permettendoti di operare offline molto più velocemente.
*   **Analisi AI con Google Gemini**: Definisci regole di pulizia in linguaggio naturale e lascia che Gemini decida quali email sono sacrificabili (Spam, Newsletter) e quali sono importanti (Fatture, Contratti).
*   **Pulizia (Trash)**: Sposta massivamente nel cestino le email identificate come "da cancellare", fornendoti prima un'interfaccia intuitiva per rivedere e approvare le selezioni.
*   **Backup Allegati**: GFA individua le email classificate come "importanti" (Gems) e ne scarica automaticamente gli allegati, organizzandoli in cartelle per mittente direttamente sul tuo PC, generando inoltre un report Excel riassuntivo.
*   **UI Moderna e Reattiva**: Sviluppata con Jetpack Compose, offre temi Chiaro/Scuro, paginazione fluida, ordinamento dinamico e dashboard statistiche (es. *Top 10 Spammer*).
*   **Sicurezza Privacy-First**: L'app opera localmente. Le credenziali OAuth2 e il database risiedono sul tuo PC (nella cartella `.gfa` della tua home utente).

*   **Salvataggio e Anteprima Intelligente**: Le email già elaborate o scaricate restano visibili in dashboard (evidenziate in blu) per avere sempre sotto controllo lo storico.
*   **Selezione Rapida per Mittente**: Un singolo clic sul mittente nella dashboard seleziona o deseleziona istantaneamente tutte le email di quel contatto.
*   **Filtro "Codice Fiscale" integrato**: Per massima sicurezza, le email contenenti un codice fiscale (nel corpo o nell'oggetto) vengono automaticamente escluse dalla pulizia massiva, anche se contrassegnate come spam dall'IA.
*   **Ripristino Intelligente**: Possibilità di ripristinare email finite nel cestino spostandole direttamente nell'Archivio (rimuovendo l'etichetta `INBOX`), senza intasare la posta in arrivo.

## 🛠️ Tecnologie Utilizzate

*   **Linguaggio**: Kotlin
*   **Interfaccia Grafica**: Jetpack Compose per Desktop (Multiplatform)
*   **Database Locale**: SQLite gestito tramite Room Database
*   **Autenticazione & API**: Google OAuth2, API Gmail v1
*   **Intelligenza Artificiale**: Google Gemini Pro (generazione ed esecuzione regole)
*   **Esportazione Dati**: Apache POI (per i report in formato `.xlsx`)
*   **Build & Packaging**: Gradle Kotlin DSL, pacchettizzazione per Windows tramite JPackage

## 📦 Installazione

Essendo un progetto Compose Desktop, GFA supporta la creazione di pacchetti nativi.
Per generare l'installer per Windows:

1. Assicurati di avere il JDK (es. JDK 17 o superiore) installato e le variabili d'ambiente correttamente configurate.
2. Clona questa repository:
   ```bash
   git clone https://github.com/MicheleLopsDev/GFA.git
   ```
3. Esegui lo script di build per creare l'installer (MSI/EXE):
   ```bash
   .\build_installer.bat
   ```
   *(Oppure tramite gradle: `./gradlew packageMsi` / `./gradlew packageExe`)*
4. Troverai l'installer generato nella cartella `build/compose/binaries/main/msi/`. 

Una volta installato, GFA sarà disponibile dal menu Start di Windows!

## ⚙️ Configurazione Iniziale

Al primo avvio, GFA necessiterà di due file fondamentali per funzionare. Verranno generati o salvati nella cartella utente `C:\Users\NomeUtente\.gfa`:

1.  **Credenziali Gmail OAuth2**: Avrai bisogno di un file `credentials.json` generato da Google Cloud Console (abilitando le API di Gmail). Mettilo nella cartella `.gfa` e al primo avvio autorizza l'app dal browser.
2.  **Chiave API Gemini**: Inserisci la tua API Key di Google Gemini nella sezione *Editor Regole* dell'app o salva direttamente il file `gemini_api_key.txt` nella cartella `.gfa`.

## 🤝 Contribuire

I contributi sono sempre i benvenuti! 
Sentiti libero di aprire Issue, proporre nuove feature o inviare Pull Request. Poiché il progetto si appoggia sulle ultime versioni di Android/Jetpack Compose, assicurati di utilizzare pattern dichiarativi moderni e logiche pulite.

## 📝 Licenza

Questo progetto è rilasciato come software **Open Source**. (Aggiungi qui i dettagli della licenza desiderata, es. MIT License).