# Manuale Utente - Gmail Filter Advanced (GFA)

Benvenuto in GFA, l'assistente basato su Intelligenza Artificiale per ripulire e organizzare la tua casella Gmail!

---

## 🚨 AVVISI DI SICUREZZA CRITICI (LEGGERE ATTENTAMENTE)

GFA manipola direttamente i dati della tua casella email e interagisce con le API a pagamento di Google Cloud. Pertanto, **LA SICUREZZA È FONDAMENTALE**:

1. **NON CONDIVIDERE MAI `credentials.json`**: Questo file, scaricato da Google Cloud, contiene le chiavi di accesso per interagire con i server di Google. Se qualcuno lo ottiene, potrebbe impersonare la tua applicazione.
2. **NON CONDIVIDERE MAI `gemini_api_key.txt`**: Questo file (che si trova in `C:\Users\TuoNome\.gfa\`) contiene la password segreta per usare i modelli di Intelligenza Artificiale a pagamento. **Se lo condividi o lo pubblichi online (es. su GitHub), potresti ricevere addebiti altissimi e inaspettati sulla tua carta di credito!**
3. **Occhio al codice sorgente**: Se decidi di pubblicare questo programma su GitHub o altre piattaforme, assicurati di **non committare mai** la cartella `.gfa` né alcun file di credenziali.

---

## 1. Configurazione Google Cloud (GCP)
Per far funzionare l'app, devi autorizzare GFA a leggere le tue email tramite Google Cloud.
Ecco come fare:

1. Vai su Google Cloud Console.
2. Crea un nuovo progetto chiamato "Gmail Filter Advanced".
3. Abilita le API "Gmail API".
4. Configura la schermata di consenso OAuth inserendo la tua email tra i Tester.
5. Crea delle credenziali di tipo "ID client OAuth 2.0 (Desktop)".
6. Scarica il file e rinominalo in `credentials.json`, poi posizionalo nella cartella base del programma.

> [INSERISCI SCREENSHOT CONFIGURAZIONE GCP QUI]

---

## 2. Configurazione Gemini AI
Il cervello del programma si basa su Gemini. Per attivarlo:
1. Vai su Google AI Studio (aistudio.google.com).
2. Genera una API Key.
3. Copiala e incollala all'interno del file `C:\Users\TuoNome\.gfa\gemini_api_key.txt`.

> [INSERISCI SCREENSHOT GENERAZIONE CHIAVE GEMINI QUI]

---

## 3. Le 5 Fasi dell'Applicazione

L'applicazione è strutturata in 5 fasi sequenziali:

### 1. Mission Control (Estrazione)
In questa fase il programma scarica velocemente gli identificatori, i mittenti e l'oggetto delle email. I dati sono salvati in pacchetti JSON locali per non sovraccaricare le richieste.

### 2. Pulizia (Generazione Regole e Trash)
L'Intelligenza Artificiale entra in gioco: analizzerà i mittenti più molesti (spam, newsletter, notifiche superflue) e genererà in automatico delle Regole di pulizia "sicure". 
**Nota Bene**: L'IA è istruita per NON cestinare MAI documenti personali, bancari, o sanitari. 
Una volta generate le regole, potrai calcolare l'anteprima e spostare la spazzatura nel Cestino.

### 3. Backup (Salvataggio Gemme)
Tutte le email "importanti" (che non sono finite nel Cestino) e che contengono **allegati** (come bollette, referti medici, contratti) vengono considerate "Gemme". In questa schermata potrai scaricare questi allegati. Saranno organizzati automaticamente in cartelle con il nome del mittente!

### 4. Editor Regole Manuali
Se l'IA blocca per sbaglio un mittente utile, o se vuoi bloccare manualmente un indirizzo fastidioso, usa l'Editor. Potrai inserire domini (`sito.com`), indirizzi esatti, o parole chiave dell'oggetto. Puoi anche cancellare al volo le vecchie regole se non ti servono più.

### 5. Guida (Questo Documento)
Per ricordarti i processi e come configurare l'ambiente di lavoro.

---

Buona pulizia!
