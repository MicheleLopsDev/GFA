# Guida per Sviluppatori Kotlin (GFA)

Questo documento fornisce una panoramica dell'architettura e delle classi principali del progetto **Gmail Filter Advanced (GFA)**. Il progetto è un'applicazione desktop scritta in Kotlin e basata sul framework **Compose Multiplatform** per l'interfaccia grafica.

## Architettura Generale

Il progetto segue un'architettura a livelli abbastanza delineata:
1. **UI Layer (Compose)**: Gestito principalmente in `Main.kt` e in componenti dedicati (es. `PreviewDashboard`, `RulesEditorScreen`, `AiRequestBanner`).
2. **Domain/Service Layer**: Contiene la logica di business, l'interazione con le API di Google (Gmail e Gemini) e la generazione di file (Excel).
3. **Data Layer**: Gestisce il database locale (Room) e la serializzazione/deserializzazione di JSON (kotlinx.serialization).

## Classi e Metodi Principali

### 1. `Main.kt` (Entry Point e UI State)
È il cuore dell'applicazione grafica. Inizializza la `Window` di Compose e gestisce la navigazione tra i vari tab (definiti nell'enum `Screen`: `MISSION_CONTROL`, `CLEANUP`, `EDITOR`, `BACKUP`, `HELP`).
- **Gestione dello Stato**: Mantiene le variabili di stato globale (`remember { mutableStateOf(...) }`) come la lista delle email, la gestione dei caricamenti e i messaggi di notifica.
- **Coroutines**: Utilizza un `rememberCoroutineScope()` per lanciare operazioni I/O intensive delegando le chiamate ai service in `Dispatchers.IO`.

### 2. `GmailAuthManager.kt`
Gestisce il flusso di autenticazione OAuth2 con Google.
- **`getGmailService(): Gmail`**: Restituisce un'istanza autenticata del client Gmail, occupandosi di rinfrescare il token se necessario o di aprire il browser locale per il primo login. I token vengono salvati in `.gfa/tokens`.

### 3. `GmailTriageService.kt`
È il service più corposo, responsabile delle operazioni dirette sulle email (lettura, cestinamento, ripristino, download allegati).
- **`simulateTriage(): List<EmailData>`**: Applica le regole locali (`rules.json`) allo storico scaricato, restituendo la lista delle email destinate al cestino senza effettuare operazioni distruttive.
- **`executeTrash(emailsToTrash)`**: Sposta le email selezionate nel cestino di Gmail (`trash`) e ne tiene traccia nel DB.
- **`executeRestoreTrash()`**: Recupera dal database le email cestinate dall'app e usa il metodo `untrash` di Gmail per ripristinarle, cancellando lo storico locale.
- **`simulateGems()` / `executeDownloadGems()`**: Logica analoga per le email "Importanti" (Fase 4), che si occupa di scaricare gli allegati in cartelle locali suddivise per mittente.

### 4. `GeminiAnalyzerService.kt`
Si occupa dell'integrazione con i modelli LLM (Gemini) tramite chiamate HTTP REST dirette.
- **`generateCleanupRules()`**: Legge un campione di dati scaricati, aggrega i domini, costruisce il prompt e interroga i modelli Gemini specificati (es. `gemini-3.6-flash`, `gemini-2.0-flash`). Se la chiamata ha successo, genera e sovrascrive il file locale `rules.json` e notifica la UI per aggiornare l' `AiRequestBanner` salvando il log in `.gfa/last_gemini_run.json`.

### 5. `RuleEvaluator.kt`
Motore di valutazione locale che interpreta le regole JSON.
- **`evaluate(email: EmailData): Rule?`**: Controlla tramite `Regex` se l'oggetto o il mittente dell'email corrispondono a una regola attiva. 
- *Sicurezza*: Contiene un check hardcoded iniziale (Regex) che intercetta i Codici Fiscali italiani. Se ne trova uno nell'oggetto o nello snippet, impedisce qualsiasi match di cancellazione (ritorna `null`).

### 6. `ExcelExporterService.kt`
Service di utilità per l'esportazione dei report.
- **`exportSpecificEmailsToExcel(emails, fileName)`**: Sfrutta la libreria Apache POI (`XSSFWorkbook`) per generare file `.xlsx` formattati contenenti ID, Mittente, Data, Oggetto e stato degli allegati.

### 7. Data Layer: `EmailDao.kt` e `DatabaseFactory.kt`
Utilizzano **Room** per Kotlin per persistere lo stato delle operazioni ed evitare elaborazioni duplicate.
- **`EmailDao`**: Contiene le query SQL (es. `@Query("SELECT emailId FROM triaged_emails WHERE actionTaken = 'TRASH'")`) utilizzate dal `GmailTriageService`.
- Traccia quali email sono state "analizzate", quali "cestinate" e quali "scaricate".

## Dipendenze Chiave (Gradle)
- `org.jetbrains.compose`: UI Framework.
- `com.google.api-client:google-api-client`: Interazione con API Google.
- `com.google.apis:google-api-services-gmail`: Chiamate dirette a Gmail (Trash/Untrash, Download).
- `org.apache.poi:poi-ooxml`: Creazione file Excel.
- `androidx.room`: ORM per database SQLite locale (`gfa_database.db`).
- `org.jetbrains.kotlinx:kotlinx-serialization-json`: Parsing nativo JSON (necessario flag `ignoreUnknownKeys = true` per gestire discrepanze nei log).

---
*Documento autogenerato per il setup e la manutenzione di GFA.*
