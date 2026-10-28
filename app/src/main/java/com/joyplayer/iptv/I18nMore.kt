package com.joyplayer.iptv

/**
 * More app languages: Polish, Greek, Dutch, Romanian, Swedish, Ukrainian.
 * Rows: English|pl|el|nl|ro|sv|uk   (a key ending in ~ is a prefix, as in I18n.kt). Text without a row stays English.
 */
object LangMore {
    val codes = listOf("pl", "el", "nl", "ro", "sv", "uk")
    val names = listOf("Polski", "Ελληνικά", "Nederlands", "Română", "Svenska", "Українська")

    /** column of each language in the rows */
    val col = mapOf("pl" to 0, "el" to 1, "nl" to 2, "ro" to 3, "sv" to 4, "uk" to 5)

    val table: Lang.Table by lazy {
        val ex = HashMap<String, List<String>>()
        val pre = ArrayList<Pair<String, List<String>>>()
        for (line in T_MORE.lines()) {
            if (line.isBlank()) continue
            val p = line.split("|")
            if (p.size != 7) continue
            val vals = p.subList(1, 7).map { it.trim() }
            if (p[0].endsWith("~")) pre.add(p[0].dropLast(1) to vals) else ex[p[0]] = vals
        }
        pre.sortByDescending { it.first.length }
        Lang.Table(ex, pre)
    }
}

private val T_MORE = """
Account|Konto|Λογαριασμός|Account|Cont|Konto|Обліковий запис
Change Playlist|Zmień playlistę|Αλλαγή λίστας|Playlist wijzigen|Schimbă lista|Byt spellista|Змінити плейлист
Settings|Ustawienia|Ρυθμίσεις|Instellingen|Setări|Inställningar|Налаштування
Reload|Odśwież|Επαναφόρτωση|Opnieuw laden|Reîncarcă|Ladda om|Оновити
Exit|Wyjdź|Έξοδος|Afsluiten|Ieșire|Avsluta|Вихід
Live|Na żywo|Ζωντανά|Live|Live|Live|Наживо
Movies|Filmy|Ταινίες|Films|Filme|Filmer|Фільми
Series|Seriale|Σειρές|Series|Seriale|Serier|Серіали
Movie|Film|Ταινία|Film|Film|Film|Фільм
Home|Strona główna|Αρχική|Start|Acasă|Hem|Головна
Search|Szukaj|Αναζήτηση|Zoeken|Caută|Sök|Пошук
Search...|Szukaj...|Αναζήτηση...|Zoeken...|Caută...|Sök...|Пошук...
Filter...|Filtruj...|Φίλτρο...|Filteren...|Filtrează...|Filtrera...|Фільтр...
All|Wszystkie|Όλα|Alles|Toate|Alla|Усі
Favorite|Ulubione|Αγαπημένα|Favorieten|Favorite|Favoriter|Обране
Add to Favorite|Dodaj do ulubionych|Προσθήκη στα αγαπημένα|Toevoegen aan favorieten|Adaugă la favorite|Lägg till i favoriter|Додати до обраного
Remove Favorite|Usuń z ulubionych|Αφαίρεση από τα αγαπημένα|Verwijderen uit favorieten|Elimină din favorite|Ta bort från favoriter|Видалити з обраного
Fullscreen|Pełny ekran|Πλήρης οθόνη|Volledig scherm|Ecran complet|Helskärm|На весь екран
Recently watched|Ostatnio oglądane|Πρόσφατα προβληθέντα|Recent bekeken|Vizionate recent|Nyligen tittat|Нещодавно переглянуті
Resume to Watch|Kontynuuj oglądanie|Συνέχεια προβολής|Verder kijken|Continuă vizionarea|Fortsätt titta|Продовжити перегляд
Continue Watching|Kontynuuj oglądanie|Συνέχεια προβολής|Verder kijken|Continuă vizionarea|Fortsätt titta|Продовжити перегляд
Position|Pozycja|Θέση|Positie|Poziție|Position|Позиція
Move to top|Przenieś na górę|Μετακίνηση στην κορυφή|Naar boven verplaatsen|Mută sus|Flytta överst|Перемістити нагору
Move up|Przenieś wyżej|Μετακίνηση επάνω|Omhoog verplaatsen|Mută mai sus|Flytta upp|Перемістити вище
Move down|Przenieś niżej|Μετακίνηση κάτω|Omlaag verplaatsen|Mută mai jos|Flytta ned|Перемістити нижче
Move to bottom|Przenieś na dół|Μετακίνηση στο τέλος|Naar beneden verplaatsen|Mută jos|Flytta nederst|Перемістити вниз
Select a channel|Wybierz kanał|Επιλέξτε κανάλι|Kies een zender|Alege un canal|Välj en kanal|Виберіть канал
Categories|Kategorie|Κατηγορίες|Categorieën|Categorii|Kategorier|Категорії
Back|Wstecz|Πίσω|Terug|Înapoi|Tillbaka|Назад
Connected|Połączono|Συνδεδεμένο|Verbonden|Conectat|Ansluten|Підключено
No playlist|Brak playlisty|Καμία λίστα|Geen playlist|Fără listă|Ingen spellista|Немає плейлиста
Add Playlist|Dodaj playlistę|Προσθήκη λίστας|Playlist toevoegen|Adaugă listă|Lägg till spellista|Додати плейлист
Add list|Dodaj listę|Προσθήκη λίστας|Lijst toevoegen|Adaugă listă|Lägg till lista|Додати список
Connect|Połącz|Σύνδεση|Verbinden|Conectează|Anslut|Підключити
Edit|Edytuj|Επεξεργασία|Bewerken|Editează|Redigera|Редагувати
Delete|Usuń|Διαγραφή|Verwijderen|Șterge|Ta bort|Видалити
Edit playlist|Edytuj playlistę|Επεξεργασία λίστας|Playlist bewerken|Editează lista|Redigera spellista|Редагувати плейлист
Delete playlist?|Usunąć playlistę?|Διαγραφή λίστας;|Playlist verwijderen?|Ștergi lista?|Ta bort spellistan?|Видалити плейлист?
Name|Nazwa|Όνομα|Naam|Nume|Namn|Назва
List name|Nazwa listy|Όνομα λίστας|Naam van lijst|Numele listei|Listans namn|Назва списку
Server|Serwer|Διακομιστής|Server|Server|Server|Сервер
Username|Nazwa użytkownika|Όνομα χρήστη|Gebruikersnaam|Nume utilizator|Användarnamn|Ім'я користувача
Password|Hasło|Κωδικός|Wachtwoord|Parolă|Lösenord|Пароль
Save|Zapisz|Αποθήκευση|Opslaan|Salvează|Spara|Зберегти
Cancel|Anuluj|Ακύρωση|Annuleren|Anulează|Avbryt|Скасувати
Loading...|Ładowanie...|Φόρτωση...|Laden...|Se încarcă...|Laddar...|Завантаження...
Save & Load|Zapisz i wczytaj|Αποθήκευση και φόρτωση|Opslaan en laden|Salvează și încarcă|Spara och ladda|Зберегти та завантажити
Choose language|Wybierz język|Επιλογή γλώσσας|Taal kiezen|Alege limba|Välj språk|Виберіть мову
JoyPlayer IPTV Pro doesn't sell playlists or subscriptions.|JoyPlayer IPTV Pro nie sprzedaje playlist ani subskrypcji.|Το JoyPlayer IPTV Pro δεν πουλά λίστες ή συνδρομές.|JoyPlayer IPTV Pro verkoopt geen playlists of abonnementen.|JoyPlayer IPTV Pro nu vinde liste sau abonamente.|JoyPlayer IPTV Pro säljer inga spellistor eller prenumerationer.|JoyPlayer IPTV Pro не продає плейлисти чи підписки.
JoyPlayer is a general media player and it doesn't include any content or playlists.|JoyPlayer to ogólny odtwarzacz multimediów i nie zawiera żadnych treści ani playlist.|Το JoyPlayer είναι γενικό πρόγραμμα αναπαραγωγής πολυμέσων και δεν περιλαμβάνει περιεχόμενο ή λίστες.|JoyPlayer is een algemene mediaspeler en bevat geen content of playlists.|JoyPlayer este un player media general și nu include conținut sau liste.|JoyPlayer är en allmän mediaspelare och innehåller inget innehåll eller några spellistor.|JoyPlayer — це універсальний медіаплеєр, який не містить жодного контенту чи плейлистів.
Watch Now|Oglądaj teraz|Δείτε τώρα|Nu kijken|Vizionează acum|Titta nu|Дивитися зараз
Resume|Wznów|Συνέχεια|Hervatten|Reia|Fortsätt|Продовжити
Trailer|Zwiastun|Τρέιλερ|Trailer|Trailer|Trailer|Трейлер
Continue|Kontynuuj|Συνέχεια|Doorgaan|Continuă|Fortsätt|Продовжити
No episodes available|Brak dostępnych odcinków|Δεν υπάρχουν διαθέσιμα επεισόδια|Geen afleveringen beschikbaar|Niciun episod disponibil|Inga avsnitt tillgängliga|Немає доступних серій
Trailer not available|Zwiastun niedostępny|Το τρέιλερ δεν είναι διαθέσιμο|Trailer niet beschikbaar|Trailerul nu este disponibil|Trailern är inte tillgänglig|Трейлер недоступний
Season ~|Sezon|Κύκλος|Seizoen|Sezonul|Säsong|Сезон
Episode ~|Odcinek|Επεισόδιο|Aflevering|Episodul|Avsnitt|Серія
Genre : ~|Gatunek :|Είδος :|Genre :|Gen :|Genre :|Жанр :
Date Added : ~|Data dodania :|Ημερομηνία προσθήκης :|Toegevoegd op :|Data adăugării :|Tillagd :|Дата додавання :
Audio|Dźwięk|Ήχος|Audio|Audio|Ljud|Звук
Subtitles|Napisy|Υπότιτλοι|Ondertitels|Subtitrări|Undertexter|Субтитри
Quality|Jakość|Ποιότητα|Kwaliteit|Calitate|Kvalitet|Якість
Fit|Dopasuj|Προσαρμογή|Passend|Potrivire|Anpassa|За розміром
Zoom|Powiększ|Ζουμ|Zoom|Mărire|Zoom|Масштаб
Stretch|Rozciągnij|Έκταση|Uitrekken|Întinde|Sträck ut|Розтягнути
Next|Dalej|Επόμενο|Volgende|Următorul|Nästa|Далі
Audio / Dubbing|Dźwięk / Dubbing|Ήχος / Μεταγλώττιση|Audio / Nasynchronisatie|Audio / Dublaj|Ljud / Dubbning|Звук / Дубляж
This stream has only one audio track.|Ten strumień ma tylko jedną ścieżkę dźwiękową.|Αυτή η ροή έχει μόνο ένα κανάλι ήχου.|Deze stream heeft maar één audiospoor.|Acest flux are o singură pistă audio.|Den här strömmen har bara ett ljudspår.|Цей потік має лише одну аудіодоріжку.
Off|Wył.|Ανενεργό|Uit|Oprit|Av|Вимк.
Search online (SubDL)|Szukaj online (SubDL)|Αναζήτηση online (SubDL)|Online zoeken (SubDL)|Caută online (SubDL)|Sök online (SubDL)|Пошук онлайн (SubDL)
Online search works for movies and series.|Wyszukiwanie online działa dla filmów i seriali.|Η αναζήτηση online λειτουργεί για ταινίες και σειρές.|Online zoeken werkt voor films en series.|Căutarea online funcționează pentru filme și seriale.|Onlinesökning fungerar för filmer och serier.|Пошук онлайн працює для фільмів і серіалів.
SubDL subtitles|Napisy SubDL|Υπότιτλοι SubDL|SubDL-ondertitels|Subtitrări SubDL|SubDL-undertexter|Субтитри SubDL
Paste your SubDL API key (saved on this device only):|Wklej swój klucz API SubDL (zapisany tylko na tym urządzeniu):|Επικολλήστε το κλειδί API του SubDL (αποθηκεύεται μόνο σε αυτήν τη συσκευή):|Plak je SubDL API-sleutel (alleen op dit apparaat opgeslagen):|Lipește cheia API SubDL (salvată doar pe acest dispozitiv):|Klistra in din SubDL API-nyckel (sparas bara på den här enheten):|Вставте ключ API SubDL (зберігається лише на цьому пристрої):
API key|Klucz API|Κλειδί API|API-sleutel|Cheie API|API-nyckel|Ключ API
Auto-load subtitles|Automatycznie wczytuj napisy|Αυτόματη φόρτωση υποτίτλων|Ondertitels automatisch laden|Încarcă automat subtitrările|Ladda undertexter automatiskt|Автозавантаження субтитрів
Change API key|Zmień klucz API|Αλλαγή κλειδιού API|API-sleutel wijzigen|Schimbă cheia API|Byt API-nyckel|Змінити ключ API
Video quality|Jakość wideo|Ποιότητα βίντεο|Videokwaliteit|Calitate video|Videokvalitet|Якість відео
Auto|Auto|Αυτόματα|Auto|Auto|Auto|Авто
This stream offers only one quality.|Ten strumień oferuje tylko jedną jakość.|Αυτή η ροή προσφέρει μόνο μία ποιότητα.|Deze stream biedt maar één kwaliteit.|Acest flux oferă o singură calitate.|Den här strömmen erbjuder bara en kvalitet.|Цей потік пропонує лише одну якість.
Resuming from ~|Wznawianie od|Συνέχεια από|Hervatten vanaf|Se reia de la|Fortsätter från|Продовження з
Next episode: ~|Następny odcinek:|Επόμενο επεισόδιο:|Volgende aflevering:|Următorul episod:|Nästa avsnitt:|Наступна серія:
Subtitle loaded: ~|Wczytano napisy:|Οι υπότιτλοι φορτώθηκαν:|Ondertitel geladen:|Subtitrare încărcată:|Undertext laddad:|Субтитри завантажено:
Track ~|Ścieżka|Κομμάτι|Spoor|Pista|Spår|Доріжка
Subtitle ~|Napisy|Υπότιτλος|Ondertitel|Subtitrare|Undertext|Субтитри
Now: ~|Teraz:|Τώρα:|Nu:|Acum:|Nu:|Зараз:
Next: ~|Następnie:|Επόμενο:|Straks:|Urmează:|Nästa:|Далі:
Loading guide...|Ładowanie programu...|Φόρτωση οδηγού...|Gids laden...|Se încarcă ghidul...|Laddar guiden...|Завантаження програми...
No program info|Brak informacji o programie|Χωρίς πληροφορίες προγράμματος|Geen programma-informatie|Fără informații despre program|Ingen programinformation|Немає інформації про програму
Program Guide|Przewodnik programowy|Οδηγός προγράμματος|Programmagids|Ghid TV|Programguide|Телепрограма
Picture-in-picture is not supported on this device|Obraz w obrazie nie jest obsługiwany na tym urządzeniu|Η λειτουργία εικόνα σε εικόνα δεν υποστηρίζεται σε αυτήν τη συσκευή|Beeld-in-beeld wordt op dit apparaat niet ondersteund|Imagine în imagine nu este acceptată pe acest dispozitiv|Bild-i-bild stöds inte på den här enheten|Режим «картинка в картинці» не підтримується на цьому пристрої
Keep playing in a small window when leaving the app|Kontynuuj odtwarzanie w małym oknie po opuszczeniu aplikacji|Συνέχεια αναπαραγωγής σε μικρό παράθυρο κατά την έξοδο από την εφαρμογή|Blijf afspelen in een klein venster als je de app verlaat|Continuă redarea într-o fereastră mică la ieșirea din aplicație|Fortsätt spela i ett litet fönster när du lämnar appen|Продовжувати відтворення в маленькому вікні після виходу з програми
Picture-in-picture is supported on this device.|Obraz w obrazie jest obsługiwany na tym urządzeniu.|Η λειτουργία εικόνα σε εικόνα υποστηρίζεται σε αυτήν τη συσκευή.|Beeld-in-beeld wordt op dit apparaat ondersteund.|Imaginea în imagine este acceptată pe acest dispozitiv.|Bild-i-bild stöds på den här enheten.|Режим «картинка в картинці» підтримується на цьому пристрої.
This device does not support picture-in-picture.|To urządzenie nie obsługuje obrazu w obrazie.|Αυτή η συσκευή δεν υποστηρίζει εικόνα σε εικόνα.|Dit apparaat ondersteunt geen beeld-in-beeld.|Acest dispozitiv nu acceptă imaginea în imagine.|Den här enheten stöder inte bild-i-bild.|Цей пристрій не підтримує режим «картинка в картинці».
While a video plays, press Home or the PiP button in the player.|Podczas odtwarzania wideo naciśnij Home lub przycisk PiP w odtwarzaczu.|Κατά την αναπαραγωγή βίντεο, πατήστε Home ή το κουμπί PiP στο πρόγραμμα αναπαραγωγής.|Druk tijdens het afspelen op Home of de PiP-knop in de speler.|În timpul redării, apasă Home sau butonul PiP din player.|Tryck på Hem eller PiP-knappen i spelaren medan en video spelas.|Під час відтворення відео натисніть Home або кнопку PiP у плеєрі.
Playlist|Playlista|Λίστα|Playlist|Listă|Spellista|Плейлист
Status|Status|Κατάσταση|Status|Stare|Status|Статус
Active|Aktywne|Ενεργό|Actief|Activ|Aktiv|Активний
Unknown|Nieznane|Άγνωστο|Onbekend|Necunoscut|Okänt|Невідомо
Expired|Wygasło|Έληξε|Verlopen|Expirat|Utgånget|Закінчився
Expiry date|Data wygaśnięcia|Ημερομηνία λήξης|Vervaldatum|Data expirării|Utgångsdatum|Дата закінчення
Unlimited|Bez limitu|Απεριόριστο|Onbeperkt|Nelimitat|Obegränsat|Необмежено
Days left|Pozostało dni|Ημέρες που απομένουν|Resterende dagen|Zile rămase|Dagar kvar|Залишилось днів
Max connections|Maks. połączeń|Μέγ. συνδέσεις|Max. verbindingen|Conexiuni max.|Max anslutningar|Макс. підключень
Active connections|Aktywne połączenia|Ενεργές συνδέσεις|Actieve verbindingen|Conexiuni active|Aktiva anslutningar|Активні підключення
Trial|Wersja próbna|Δοκιμαστική|Proefperiode|Probă|Provperiod|Пробний
Yes|Tak|Ναι|Ja|Da|Ja|Так
No|Nie|Όχι|Nee|Nu|Nej|Ні
Created|Utworzono|Δημιουργήθηκε|Aangemaakt|Creat|Skapad|Створено
Refresh|Odśwież|Ανανέωση|Vernieuwen|Reîmprospătează|Uppdatera|Оновити
No playlist connected. Add one from Change Playlist.|Brak połączonej playlisty. Dodaj ją w sekcji Zmień playlistę.|Δεν υπάρχει συνδεδεμένη λίστα. Προσθέστε μία από την Αλλαγή λίστας.|Geen playlist verbonden. Voeg er een toe via Playlist wijzigen.|Nicio listă conectată. Adaugă una din Schimbă lista.|Ingen spellista ansluten. Lägg till en under Byt spellista.|Плейлист не підключено. Додайте його в розділі «Змінити плейлист».
Installed: ~|Zainstalowano:|Εγκατεστημένο:|Geïnstalleerd:|Instalat:|Installerad:|Встановлено:
Checking for updates...|Sprawdzanie aktualizacji...|Έλεγχος για ενημερώσεις...|Controleren op updates...|Se caută actualizări...|Söker efter uppdateringar...|Перевірка оновлень...
You are up to date.|Masz najnowszą wersję.|Είστε ενημερωμένοι.|Je bent up-to-date.|Ești la zi.|Du har den senaste versionen.|У вас остання версія.
Check again|Sprawdź ponownie|Έλεγχος ξανά|Opnieuw controleren|Verifică din nou|Kontrollera igen|Перевірити знову
New version: ~|Nowa wersja:|Νέα έκδοση:|Nieuwe versie:|Versiune nouă:|Ny version:|Нова версія:
Download & install|Pobierz i zainstaluj|Λήψη και εγκατάσταση|Downloaden en installeren|Descarcă și instalează|Ladda ner och installera|Завантажити та встановити
Downloading... ~|Pobieranie...|Λήψη...|Downloaden...|Se descarcă...|Laddar ner...|Завантаження...
Install now|Zainstaluj teraz|Εγκατάσταση τώρα|Nu installeren|Instalează acum|Installera nu|Встановити зараз
Retry|Ponów|Επανάληψη|Opnieuw proberen|Reîncearcă|Försök igen|Повторити
Allow installs from JoyPlayer, come back here and press Install.|Zezwól na instalacje z JoyPlayer, wróć tutaj i naciśnij Zainstaluj.|Επιτρέψτε εγκαταστάσεις από το JoyPlayer, επιστρέψτε εδώ και πατήστε Εγκατάσταση.|Sta installaties vanuit JoyPlayer toe, kom terug en druk op Installeren.|Permite instalările din JoyPlayer, revino aici și apasă Instalează.|Tillåt installationer från JoyPlayer, kom tillbaka hit och tryck på Installera.|Дозвольте встановлення з JoyPlayer, поверніться сюди й натисніть «Встановити».
Default|Domyślne|Προεπιλογή|Standaard|Implicit|Standard|За замовчуванням
Auto-Update Live, Movie & Series everytime|Aktualizuj Live, Filmy i Seriale za każdym razem|Αυτόματη ενημέρωση Live, Ταινιών και Σειρών κάθε φορά|Live, films en series elke keer automatisch bijwerken|Actualizează automat Live, Filme și Seriale de fiecare dată|Uppdatera Live, filmer och serier automatiskt varje gång|Автооновлення Live, фільмів і серіалів щоразу
Auto-Update Live, Movie & Series everyday|Aktualizuj Live, Filmy i Seriale codziennie|Αυτόματη ενημέρωση Live, Ταινιών και Σειρών καθημερινά|Live, films en series elke dag automatisch bijwerken|Actualizează automat Live, Filme și Seriale zilnic|Uppdatera Live, filmer och serier automatiskt varje dag|Автооновлення Live, фільмів і серіалів щодня
Auto-Update Live, Movie & Series after 2 days|Aktualizuj Live, Filmy i Seriale co 2 dni|Αυτόματη ενημέρωση Live, Ταινιών και Σειρών ανά 2 ημέρες|Live, films en series na 2 dagen automatisch bijwerken|Actualizează automat Live, Filme și Seriale după 2 zile|Uppdatera Live, filmer och serier automatiskt efter 2 dagar|Автооновлення Live, фільмів і серіалів через 2 дні
Enable Subtitle|Włącz napisy|Ενεργοποίηση υποτίτλων|Ondertitels inschakelen|Activează subtitrările|Aktivera undertexter|Увімкнути субтитри
Subtitle Size|Rozmiar napisów|Μέγεθος υποτίτλων|Ondertitelgrootte|Dimensiune subtitrări|Undertextstorlek|Розмір субтитрів
Subtitle Color|Kolor napisów|Χρώμα υποτίτλων|Ondertitelkleur|Culoare subtitrări|Undertextfärg|Колір субтитрів
Subtitle Background|Tło napisów|Φόντο υποτίτλων|Ondertitelachtergrond|Fundal subtitrări|Undertextbakgrund|Фон субтитрів
Subtitle Test|Test napisów|Δοκιμή υποτίτλων|Ondertiteltest|Test subtitrări|Undertexttest|Тест субтитрів
Get a free key from subdl.com|Pobierz darmowy klucz z subdl.com|Αποκτήστε δωρεάν κλειδί από το subdl.com|Haal een gratis sleutel op subdl.com|Obține o cheie gratuită de pe subdl.com|Hämta en gratis nyckel på subdl.com|Отримайте безкоштовний ключ на subdl.com
Access Restricted|Dostęp ograniczony|Περιορισμένη πρόσβαση|Toegang beperkt|Acces restricționat|Åtkomst begränsad|Доступ обмежено
Backup & Restore|Kopia zapasowa i przywracanie|Αντίγραφο ασφαλείας και επαναφορά|Back-up en herstel|Backup și restaurare|Säkerhetskopiering och återställning|Резервна копія та відновлення
The backup contains your playlists (with passwords), favorites, history and settings. Keep it private.|Kopia zawiera Twoje playlisty (z hasłami), ulubione, historię i ustawienia. Zachowaj ją w tajemnicy.|Το αντίγραφο περιέχει τις λίστες σας (με κωδικούς), τα αγαπημένα, το ιστορικό και τις ρυθμίσεις. Κρατήστε το ιδιωτικό.|De back-up bevat je playlists (met wachtwoorden), favorieten, geschiedenis en instellingen. Houd hem privé.|Backup-ul conține listele tale (cu parole), favoritele, istoricul și setările. Păstrează-l privat.|Säkerhetskopian innehåller dina spellistor (med lösenord), favoriter, historik och inställningar. Håll den privat.|Резервна копія містить ваші плейлисти (з паролями), обране, історію та налаштування. Зберігайте її в таємниці.
Save backup to a file|Zapisz kopię do pliku|Αποθήκευση αντιγράφου σε αρχείο|Back-up opslaan in een bestand|Salvează backup într-un fișier|Spara säkerhetskopia till en fil|Зберегти копію у файл
Restore from a file|Przywróć z pliku|Επαναφορά από αρχείο|Herstellen uit een bestand|Restaurează dintr-un fișier|Återställ från en fil|Відновити з файлу
Copy backup to clipboard|Kopiuj kopię do schowka|Αντιγραφή αντιγράφου στο πρόχειρο|Back-up naar klembord kopiëren|Copiază backup în clipboard|Kopiera säkerhetskopia till urklipp|Скопіювати копію в буфер обміну
Restore from clipboard|Przywróć ze schowka|Επαναφορά από το πρόχειρο|Herstellen vanaf klembord|Restaurează din clipboard|Återställ från urklipp|Відновити з буфера обміну
Backup link (https://...)|Link do kopii (https://...)|Σύνδεσμος αντιγράφου (https://...)|Back-uplink (https://...)|Link backup (https://...)|Länk till säkerhetskopia (https://...)|Посилання на копію (https://...)
Restore from link|Przywróć z linku|Επαναφορά από σύνδεσμο|Herstellen via link|Restaurează din link|Återställ från länk|Відновити за посиланням
Restore backup?|Przywrócić kopię zapasową?|Επαναφορά αντιγράφου;|Back-up herstellen?|Restaurezi backup-ul?|Återställa säkerhetskopian?|Відновити копію?
This replaces your playlists, favorites, history and settings with the backup.|To zastąpi Twoje playlisty, ulubione, historię i ustawienia kopią zapasową.|Αυτό αντικαθιστά τις λίστες, τα αγαπημένα, το ιστορικό και τις ρυθμίσεις σας με το αντίγραφο.|Hiermee worden je playlists, favorieten, geschiedenis en instellingen vervangen door de back-up.|Aceasta înlocuiește listele, favoritele, istoricul și setările cu backup-ul.|Detta ersätter dina spellistor, favoriter, historik och inställningar med säkerhetskopian.|Це замінить ваші плейлисти, обране, історію та налаштування даними з копії.
Backup saved|Kopia zapisana|Το αντίγραφο αποθηκεύτηκε|Back-up opgeslagen|Backup salvat|Säkerhetskopian sparad|Копію збережено
This is not a JoyPlayer backup|To nie jest kopia JoyPlayer|Αυτό δεν είναι αντίγραφο του JoyPlayer|Dit is geen JoyPlayer-back-up|Aceasta nu este o copie JoyPlayer|Detta är inte en JoyPlayer-säkerhetskopia|Це не копія JoyPlayer
Subtitle delay|Opóźnienie napisów|Καθυστέρηση υποτίτλων|Ondertitelvertraging|Întârziere subtitrări|Undertextfördröjning|Затримка субтитрів
Delay works with subtitles downloaded from SubDL. Load one first.|Opóźnienie działa z napisami pobranymi z SubDL. Najpierw wczytaj napisy.|Η καθυστέρηση λειτουργεί με υπότιτλους από το SubDL. Φορτώστε πρώτα έναν.|Vertraging werkt met ondertitels van SubDL. Laad er eerst een.|Întârzierea funcționează cu subtitrări descărcate de pe SubDL. Încarcă una mai întâi.|Fördröjning fungerar med undertexter från SubDL. Ladda en först.|Затримка працює з субтитрами, завантаженими з SubDL. Спершу завантажте їх.
Positive values show the subtitle later, negative earlier.|Wartości dodatnie pokazują napisy później, ujemne wcześniej.|Οι θετικές τιμές εμφανίζουν τον υπότιτλο αργότερα, οι αρνητικές νωρίτερα.|Positieve waarden tonen de ondertitel later, negatieve eerder.|Valorile pozitive afișează subtitrarea mai târziu, cele negative mai devreme.|Positiva värden visar undertexten senare, negativa tidigare.|Додатні значення показують субтитри пізніше, від'ємні — раніше.
Reset|Resetuj|Επαναφορά|Resetten|Resetează|Återställ|Скинути
Playback speed|Prędkość odtwarzania|Ταχύτητα αναπαραγωγής|Afspeelsnelheid|Viteză de redare|Uppspelningshastighet|Швидкість відтворення
Normal (1x)|Normalna (1x)|Κανονική (1x)|Normaal (1x)|Normală (1x)|Normal (1x)|Звичайна (1x)
Sleep timer|Wyłącznik czasowy|Χρονοδιακόπτης ύπνου|Slaaptimer|Cronometru de somn|Sovtimer|Таймер сну
Sleep|Uśpienie|Ύπνος|Slaap|Somn|Sömn|Сон
Cast to|Prześlij do|Προβολή σε|Casten naar|Transmite la|Casta till|Транслювати на
Searching for devices on your Wi-Fi...|Wyszukiwanie urządzeń w sieci Wi-Fi...|Αναζήτηση συσκευών στο Wi-Fi σας...|Zoeken naar apparaten op je wifi...|Se caută dispozitive în rețeaua Wi-Fi...|Söker efter enheter på ditt Wi-Fi...|Пошук пристроїв у вашій мережі Wi-Fi...
Stop casting|Zatrzymaj przesyłanie|Διακοπή προβολής|Stoppen met casten|Oprește transmiterea|Sluta casta|Зупинити трансляцію
Casting to ~|Przesyłanie do|Προβολή σε|Casten naar|Se transmite la|Castar till|Трансляція на
Cast|Przesyłaj|Προβολή|Casten|Transmite|Casta|Транслювати
Stop|Stop|Διακοπή|Stoppen|Oprește|Stopp|Зупинити
Last|Ostatni|Τελευταίο|Laatste|Ultimul|Senaste|Останній
EPG (XMLTV)|EPG (XMLTV)|EPG (XMLTV)|EPG (XMLTV)|EPG (XMLTV)|EPG (XMLTV)|EPG (XMLTV)
Program guide for M3U playlists. Leave empty to use the link found inside the playlist.|Przewodnik programowy dla playlist M3U. Zostaw puste, aby użyć linku znalezionego w playliście.|Οδηγός προγράμματος για λίστες M3U. Αφήστε κενό για χρήση του συνδέσμου μέσα στη λίστα.|Programmagids voor M3U-playlists. Laat leeg om de link in de playlist te gebruiken.|Ghid de program pentru liste M3U. Lasă gol pentru a folosi linkul din listă.|Programguide för M3U-spellistor. Lämna tomt för att använda länken i spellistan.|Телепрограма для M3U-плейлистів. Залиште порожнім, щоб використати посилання з плейлиста.
XMLTV link (.xml or .xml.gz)|Link XMLTV (.xml lub .xml.gz)|Σύνδεσμος XMLTV (.xml ή .xml.gz)|XMLTV-link (.xml of .xml.gz)|Link XMLTV (.xml sau .xml.gz)|XMLTV-länk (.xml eller .xml.gz)|Посилання XMLTV (.xml або .xml.gz)
Do you want to exit the app?|Czy chcesz zamknąć aplikację?|Θέλετε να βγείτε από την εφαρμογή;|Wil je de app afsluiten?|Vrei să ieși din aplicație?|Vill du avsluta appen?|Вийти з програми?
Main Screen Background|Tło ekranu głównego|Φόντο αρχικής οθόνης|Achtergrond hoofdscherm|Fundal ecran principal|Bakgrund för startskärmen|Фон головного екрана
Choose Main Screen Background|Wybierz tło ekranu głównego|Επιλογή φόντου αρχικής οθόνης|Kies achtergrond hoofdscherm|Alege fundalul ecranului principal|Välj bakgrund för startskärmen|Виберіть фон головного екрана
Main Buttons Style|Styl głównych przycisków|Στυλ κύριων κουμπιών|Stijl hoofdknoppen|Stil butoane principale|Stil för huvudknappar|Стиль головних кнопок
Button Color|Kolor przycisku|Χρώμα κουμπιού|Knopkleur|Culoare buton|Knappfärg|Колір кнопки
Border Color|Kolor obramowania|Χρώμα περιγράμματος|Randkleur|Culoare chenar|Kantfärg|Колір рамки
Text Color|Kolor tekstu|Χρώμα κειμένου|Tekstkleur|Culoare text|Textfärg|Колір тексту
Font|Czcionka|Γραμματοσειρά|Lettertype|Font|Typsnitt|Шрифт
Text size|Rozmiar tekstu|Μέγεθος κειμένου|Tekstgrootte|Dimensiune text|Textstorlek|Розмір тексту
Icon size|Rozmiar ikon|Μέγεθος εικονιδίων|Pictogramgrootte|Dimensiune pictograme|Ikonstorlek|Розмір піктограм
Reset to default|Przywróć domyślne|Επαναφορά προεπιλογών|Standaardwaarden herstellen|Resetează la implicit|Återställ till standard|Скинути до типових
Extra large|Bardzo duży|Πολύ μεγάλο|Extra groot|Foarte mare|Extra stor|Дуже великий
Category added to favorites|Kategoria dodana do ulubionych|Η κατηγορία προστέθηκε στα αγαπημένα|Categorie toegevoegd aan favorieten|Categorie adăugată la favorite|Kategori tillagd i favoriter|Категорію додано до обраного
Category removed from favorites|Kategoria usunięta z ulubionych|Η κατηγορία αφαιρέθηκε από τα αγαπημένα|Categorie verwijderd uit favorieten|Categorie eliminată din favorite|Kategori borttagen från favoriter|Категорію видалено з обраного
Added to favorites|Dodano do ulubionych|Προστέθηκε στα αγαπημένα|Toegevoegd aan favorieten|Adăugat la favorite|Tillagd i favoriter|Додано до обраного
Removed from favorites|Usunięto z ulubionych|Αφαιρέθηκε από τα αγαπημένα|Verwijderd uit favorieten|Eliminat din favorite|Borttagen från favoriter|Видалено з обраного
Reorder|Zmień kolejność|Αναδιάταξη|Herordenen|Reordonează|Ändra ordning|Змінити порядок
List Layout|Układ listy|Διάταξη λίστας|Lijstweergave|Aspect listă|Listvy|Вигляд списку
Grid Layout|Układ siatki|Διάταξη πλέγματος|Rasterweergave|Aspect grilă|Rutnätsvy|Вигляд сітки
OK|OK|OK|OK|OK|OK|OK
Poster size|Rozmiar plakatów|Μέγεθος αφισών|Posterformaat|Dimensiune postere|Affischstorlek|Розмір постерів
Parental Control|Kontrola rodzicielska|Γονικός έλεγχος|Ouderlijk toezicht|Control parental|Föräldrakontroll|Батьківський контроль
Change Language|Zmień język|Αλλαγή γλώσσας|Taal wijzigen|Schimbă limba|Byt språk|Змінити мову
Change Layout|Zmień układ|Αλλαγή διάταξης|Weergave wijzigen|Schimbă aspectul|Byt layout|Змінити вигляд
Hide Live Categories|Ukryj kategorie Live|Απόκρυψη κατηγοριών Live|Live-categorieën verbergen|Ascunde categoriile Live|Dölj Live-kategorier|Приховати категорії Live
Hide Vod Categories|Ukryj kategorie VOD|Απόκρυψη κατηγοριών VOD|VOD-categorieën verbergen|Ascunde categoriile VOD|Dölj VOD-kategorier|Приховати категорії VOD
Hide Series Categories|Ukryj kategorie seriali|Απόκρυψη κατηγοριών σειρών|Seriecategorieën verbergen|Ascunde categoriile de seriale|Dölj serikategorier|Приховати категорії серіалів
Clear History Channels|Wyczyść historię kanałów|Διαγραφή ιστορικού καναλιών|Zenderhistorie wissen|Șterge istoricul canalelor|Rensa kanalhistorik|Очистити історію каналів
Clear History Movies|Wyczyść historię filmów|Διαγραφή ιστορικού ταινιών|Filmgeschiedenis wissen|Șterge istoricul filmelor|Rensa filmhistorik|Очистити історію фільмів
Clear History Series|Wyczyść historię seriali|Διαγραφή ιστορικού σειρών|Seriegeschiedenis wissen|Șterge istoricul serialelor|Rensa serihistorik|Очистити історію серіалів
Live Channel Sort|Sortowanie kanałów Live|Ταξινόμηση καναλιών Live|Sortering live-zenders|Sortare canale Live|Sortering av livekanaler|Сортування каналів Live
Live channel sort|Sortowanie kanałów Live|Ταξινόμηση καναλιών Live|Sortering live-zenders|Sortare canale Live|Sortering av livekanaler|Сортування каналів Live
Live Stream Format|Format strumienia Live|Μορφή ροής Live|Live-streamformaat|Format flux Live|Format för livestream|Формат потоку Live
Live stream format|Format strumienia Live|Μορφή ροής Live|Live-streamformaat|Format flux Live|Format för livestream|Формат потоку Live
Automatic|Automatycznie|Αυτόματα|Automatisch|Automat|Automatiskt|Автоматично
Time Format|Format czasu|Μορφή ώρας|Tijdnotatie|Format oră|Tidsformat|Формат часу
Time format|Format czasu|Μορφή ώρας|Tijdnotatie|Format oră|Tidsformat|Формат часу
Subtitle Settings|Ustawienia napisów|Ρυθμίσεις υποτίτλων|Ondertitelinstellingen|Setări subtitrări|Undertextinställningar|Налаштування субтитрів
PIP Settings|Ustawienia PiP|Ρυθμίσεις PiP|PiP-instellingen|Setări PiP|PiP-inställningar|Налаштування PiP
Update Now|Aktualizuj teraz|Ενημέρωση τώρα|Nu bijwerken|Actualizează acum|Uppdatera nu|Оновити зараз
Poster size (Movies & Series)|Rozmiar plakatów (Filmy i Seriale)|Μέγεθος αφισών (Ταινίες και Σειρές)|Posterformaat (Films en series)|Dimensiune postere (Filme și Seriale)|Affischstorlek (Filmer och serier)|Розмір постерів (Фільми та серіали)
Small|Mały|Μικρό|Klein|Mic|Liten|Малий
Medium|Średni|Μεσαίο|Gemiddeld|Mediu|Medel|Середній
Large|Duży|Μεγάλο|Groot|Mare|Stor|Великий
Default (as provided)|Domyślny (jak w źródle)|Προεπιλογή (όπως παρέχεται)|Standaard (zoals aangeboden)|Implicit (așa cum este furnizat)|Standard (som tillhandahålls)|Типово (як надано)
24-hour|24-godzinny|24ωρο|24-uurs|24 de ore|24-timmars|24-годинний
12-hour (AM/PM)|12-godzinny (AM/PM)|12ωρο (AM/PM)|12-uurs (AM/PM)|12 ore (AM/PM)|12-timmars (AM/PM)|12-годинний (AM/PM)
Auto-connect to the last playlist on launch|Automatycznie łącz z ostatnią playlistą przy uruchomieniu|Αυτόματη σύνδεση στην τελευταία λίστα κατά την εκκίνηση|Bij het starten automatisch verbinden met de laatste playlist|Conectare automată la ultima listă la pornire|Anslut automatiskt till senaste spellistan vid start|Автопідключення до останнього плейлиста під час запуску
Auto-load subtitles (movies & series)|Automatycznie wczytuj napisy (filmy i seriale)|Αυτόματη φόρτωση υποτίτλων (ταινίες και σειρές)|Ondertitels automatisch laden (films en series)|Încarcă automat subtitrările (filme și seriale)|Ladda undertexter automatiskt (filmer och serier)|Автозавантаження субтитрів (фільми та серіали)
Size|Rozmiar|Μέγεθος|Grootte|Dimensiune|Storlek|Розмір
Normal|Normalny|Κανονικό|Normaal|Normal|Normal|Звичайний
Color|Kolor|Χρώμα|Kleur|Culoare|Färg|Колір
White|Biały|Λευκό|Wit|Alb|Vit|Білий
Yellow|Żółty|Κίτρινο|Geel|Galben|Gul|Жовтий
Cyan|Cyjan|Κυανό|Cyaan|Cian|Cyan|Блакитний
Dark background box|Ciemne pole tła|Σκούρο πλαίσιο φόντου|Donker achtergrondvak|Casetă de fundal închisă|Mörk bakgrundsruta|Темне тло
Preferred online language|Preferowany język online|Προτιμώμενη γλώσσα online|Voorkeurstaal online|Limba online preferată|Önskat språk online|Бажана мова онлайн
SubDL API key|Klucz API SubDL|Κλειδί API SubDL|SubDL API-sleutel|Cheie API SubDL|SubDL API-nyckel|Ключ API SubDL
Saved|Zapisano|Αποθηκεύτηκε|Opgeslagen|Salvat|Sparad|Збережено
Save key|Zapisz klucz|Αποθήκευση κλειδιού|Sleutel opslaan|Salvează cheia|Spara nyckel|Зберегти ключ
Clear history?|Wyczyścić historię?|Διαγραφή ιστορικού;|Geschiedenis wissen?|Ștergi istoricul?|Rensa historiken?|Очистити історію?
This clears the recently watched channels list.|To wyczyści listę ostatnio oglądanych kanałów.|Αυτό διαγράφει τη λίστα των καναλιών που προβλήθηκαν πρόσφατα.|Hiermee wordt de lijst met recent bekeken zenders gewist.|Aceasta șterge lista canalelor vizionate recent.|Detta rensar listan över nyligen sedda kanaler.|Це очистить список нещодавно переглянутих каналів.
Done|Gotowe|Τέλος|Klaar|Gata|Klart|Готово
Clear|Wyczyść|Διαγραφή|Wissen|Șterge|Rensa|Очистити
Order by number|Sortuj wg numeru|Ταξινόμηση κατά αριθμό|Sorteren op nummer|Ordonare după număr|Sortera efter nummer|За номером
Order by added|Sortuj wg daty dodania|Ταξινόμηση κατά προσθήκη|Sorteren op toegevoegd|Ordonare după adăugare|Sortera efter tillagd|За датою додавання
Order by rating|Sortuj wg oceny|Ταξινόμηση κατά βαθμολογία|Sorteren op beoordeling|Ordonare după rating|Sortera efter betyg|За рейтингом
Order by A-Z|Sortuj A-Z|Ταξινόμηση Α-Ω|Sorteren A-Z|Ordonare A-Z|Sortera A-Ö|За алфавітом А-Я
Order by Z-A|Sortuj Z-A|Ταξινόμηση Ω-Α|Sorteren Z-A|Ordonare Z-A|Sortera Ö-A|За алфавітом Я-А
Enter PIN|Wpisz PIN|Εισάγετε PIN|Voer pincode in|Introdu PIN|Ange PIN|Введіть PIN
Wrong PIN|Zły PIN|Λάθος PIN|Onjuiste pincode|PIN greșit|Fel PIN|Невірний PIN
Create PIN|Utwórz PIN|Δημιουργία PIN|Pincode maken|Creează PIN|Skapa PIN|Створити PIN
New PIN|Nowy PIN|Νέο PIN|Nieuwe pincode|PIN nou|Ny PIN|Новий PIN
New PIN (4 digits)|Nowy PIN (4 cyfry)|Νέο PIN (4 ψηφία)|Nieuwe pincode (4 cijfers)|PIN nou (4 cifre)|Ny PIN (4 siffror)|Новий PIN (4 цифри)
Confirm PIN|Potwierdź PIN|Επιβεβαίωση PIN|Pincode bevestigen|Confirmă PIN|Bekräfta PIN|Підтвердіть PIN
PIN must be 4 digits|PIN musi mieć 4 cyfry|Το PIN πρέπει να έχει 4 ψηφία|De pincode moet 4 cijfers hebben|PIN-ul trebuie să aibă 4 cifre|PIN måste vara 4 siffror|PIN має містити 4 цифри
PINs do not match|PIN-y nie są takie same|Τα PIN δεν ταιριάζουν|Pincodes komen niet overeen|PIN-urile nu se potrivesc|PIN-koderna matchar inte|PIN-коди не збігаються
Save PIN|Zapisz PIN|Αποθήκευση PIN|Pincode opslaan|Salvează PIN|Spara PIN|Зберегти PIN
Locked Live categories|Zablokowane kategorie Live|Κλειδωμένες κατηγορίες Live|Vergrendelde live-categorieën|Categorii Live blocate|Låsta Live-kategorier|Заблоковані категорії Live
Locked Movie categories|Zablokowane kategorie filmów|Κλειδωμένες κατηγορίες ταινιών|Vergrendelde filmcategorieën|Categorii de filme blocate|Låsta filmkategorier|Заблоковані категорії фільмів
Locked Series categories|Zablokowane kategorie seriali|Κλειδωμένες κατηγορίες σειρών|Vergrendelde seriecategorieën|Categorii de seriale blocate|Låsta serikategorier|Заблоковані категорії серіалів
Show locked content now|Pokaż zablokowaną zawartość teraz|Εμφάνιση κλειδωμένου περιεχομένου τώρα|Vergrendelde inhoud nu tonen|Afișează acum conținutul blocat|Visa låst innehåll nu|Показати заблокований вміст зараз
Change PIN|Zmień PIN|Αλλαγή PIN|Pincode wijzigen|Schimbă PIN|Byt PIN|Змінити PIN
Remove PIN|Usuń PIN|Αφαίρεση PIN|Pincode verwijderen|Elimină PIN|Ta bort PIN|Видалити PIN
Select shown|Zaznacz widoczne|Επιλογή εμφανιζόμενων|Getoonde selecteren|Selectează afișatele|Markera visade|Вибрати показані
Clear shown|Odznacz widoczne|Απεπιλογή εμφανιζόμενων|Getoonde deselecteren|Deselectează afișatele|Avmarkera visade|Зняти вибір з показаних
Save (~|Zapisz (|Αποθήκευση (|Opslaan (|Salvează (|Spara (|Зберегти (
Connect to a playlist first|Najpierw połącz się z playlistą|Συνδεθείτε πρώτα σε μια λίστα|Verbind eerst met een playlist|Conectează-te mai întâi la o listă|Anslut till en spellista först|Спершу підключіться до плейлиста
Movies and Series need an Xtream list|Filmy i Seriale wymagają listy Xtream|Οι Ταινίες και οι Σειρές χρειάζονται λίστα Xtream|Films en series hebben een Xtream-lijst nodig|Filmele și Serialele necesită o listă Xtream|Filmer och serier kräver en Xtream-lista|Фільми та серіали потребують списку Xtream
"""
