# Documento di Analisi: Client Gmail Cleanup & Archive (Windows Desktop) - Versione 3.0

## 1. Obiettivo del Progetto
Realizzare un client desktop in Kotlin che si interfacci con le API di Gmail per processare un archivio storico di circa 41.000 email. Il sistema adotterà un approccio in 3 step: estrazione sicura dei dati, analisi esterna tramite LLM per definire le regole, e infine l'esecuzione di un motore deterministico per etichettare, archiviare in locale gli allegati ed eliminare lo spam/inutile.

## 2. Stack Tecnologico e Architettura
*   **Piattaforma e UI:** Kotlin JVM con Compose for Desktop (ecosistema Jetpack).
*   **Build System:** Gradle DSL (`build.gradle.kts`). Focus sull'eliminazione del codice boilerplate e utilizzo delle librerie più recenti.
*   **Integrazione API Google:** Utilizzo delle API ufficiali (`google-api-services-gmail`) per la lettura, la gestione delle etichette e lo scaricamento degli allegati.
*   **Concorrenza e Asincronia:** Kotlin Coroutines e Flows per gestire la paginazione, l'I/O su disco e mantenere la UI fluida ed efficiente.
*   **Serializzazione:** `kotlinx.serialization` per la generazione e lettura dei file JSON (senza ricorrere a librerie obsolete).

## 3. Gestione dell'Autenticazione (OAuth 2.0)
Il sistema utilizzerà il **Loopback IP address flow** per l'autenticazione su Windows:
1.  Il client apre il browser predefinito per il login Google.
2.  Un server HTTP locale temporaneo intercetta il token di autorizzazione al termine del redirect.
3.  Il token viene salvato in modo sicuro per evitare di richiedere il login ai successivi avvii.

## 4. Gestione della Mole di Dati e Resilienza
Per elaborare l'enorme mole di dati senza blocchi o perdita di progressi:
*   **Paginazione e Rate Limiting:** Implementazione di un sistema a loop asincrono con logica di *Exponential Backoff* per rispettare i limiti delle API di Google (evitando l'errore HTTP 429).
*   **Stato di Avanzamento:** Utilizzo della libreria **Room** (compatibile con Kotlin Multiplatform/Desktop) e un database SQLite locale. Questo permetterà di tracciare in modo affidabile quali ID email sono già stati processati, consentendo di interrompere e riprendere il lavoro in qualsiasi momento senza ricominciare da capo, sia in fase di estrazione che in fase di pulizia. Soluzione affidabile e non complessa.

## 5. Architettura del Flusso Operativo in 3 Fasi

### Fase 1: Acquisizione ed Estrazione Dati (JSON Export)
Il client interroga Gmail e, senza alterare la casella di posta, costruisce un dataset analitico in sola lettura.
*   Estrae per ogni email: `id`, `titolo` (oggetto), `da` (mittente), `a` (destinatario), `testo` (corpo del messaggio o snippet), e un flag booleano per la presenza di allegati con i relativi `nomi_allegati`.
*   Salva questi dati in locale strutturandoli in file JSON.

### Fase 2: Analisi Statistica e Creazione Regole (Offline)
Fase assistita fuori dal software:
*   I file JSON generati nella Fase 1 vengono forniti a un LLM.
*   L'LLM effettua un'analisi statistica (identifica ricorrenze di mittenti, pattern degli oggetti per email bancarie, bollette o newsletter).
*   L'LLM produce un set di filtri e regole di business chiare, esportate preferibilmente in un file di configurazione JSON.

### Fase 3: Esecuzione del Motore di Triage (Il Software Vero e Proprio)
Il client Kotlin carica le regole fisse e deterministiche generate nella Fase 2. Per ogni email (sfruttando il DB Room per tenere traccia):
1.  **Valutazione:** Confronta i metadati dell'email con le regole deterministiche.
2.  **Azione su Allegati:** Se l'email è classificata come utile e ha allegati, questi vengono scaricati sul File System di Windows in una struttura a directory organizzata per categorie.
3.  **Azione su Gmail:** 
    *   *Se utile:* Crea (se non esiste) l'etichetta corrispondente alla categoria (es. `Banca`, `Bollette`) e la applica.
    *   *Se inutile/spam:* Invia la richiesta API per spostare il messaggio nel Cestino.

## 6. Architettura Definitiva Implementata (Aggiornamento)
L'applicazione è stata sviluppata coprendo integralmente i requisiti, introducendo ulteriori miglioramenti:
*   **Fase 1 (Estrazione):** Partizionamento automatico ogni 1000 email (`emails_part_N.json`). Aggiunta la possibilità di Pausare e Riprendere il download in tempo reale senza perdere l'ID di paginazione di Google (salvato in un thread-safe `StateFlow`).
*   **Fase 2 (Automazione IA):** Superata l'idea dell'analisi manuale offline. L'app integra nativamente l'SDK `generativeai` per collegarsi all'API di **Google Gemini 1.5 Flash**. Gemini riceve un campione rappresentativo, genera il file `rules.json` strutturato e lo salva in automatico.
*   **Fase 3 (Triage Locale):** Il motore (implementato e funzionante) legge i JSON bypassando le API di fetch, valuta i pattern (RegEx) offline, esegue le cancellazioni in batch e scarica gli allegati creando le label su Gmail.
*   **Fase 4 (Monitoraggio ed Esportazione):** Sviluppata un'interfaccia a doppia colonna con controlli di esecuzione. Integrato un **Grafico dinamico (Canvas)** per monitorare la velocità di download e la libreria **Apache POI** per esportare tutti i dati acquisiti in un comodo file Excel (`.xlsx`).
