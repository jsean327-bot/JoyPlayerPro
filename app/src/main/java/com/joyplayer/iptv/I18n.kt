package com.joyplayer.iptv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * App language. Every Text() in the app goes through [Text] below (it hides the Material one),
 * which translates the English label into the chosen language. Strings without a translation stay English.
 */
object Lang {
    val codes = listOf("ar", "en", "fr", "es", "de", "it", "pt", "ru", "tr")
    val names = listOf("العربية", "English", "Français", "Español", "Deutsch", "Italiano", "Português", "Русский", "Türkçe")

    // column of each language inside the table rows (English is the key itself)
    private val col = mapOf("ar" to 0, "fr" to 1, "es" to 2, "de" to 3, "it" to 4, "pt" to 5, "ru" to 6, "tr" to 7)

    private var prefs: SharedPreferences? = null

    var code by mutableStateOf("en")
        private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("joyplayer_lang", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString("code", null)
        val device = Locale.getDefault().language
        code = when {
            saved != null && saved in codes -> saved
            device in codes -> device
            else -> "en"
        }
    }

    /** Re-read the saved language (after a backup restore). */
    fun reload() {
        val saved = prefs?.getString("code", null) ?: return
        if (saved in codes) code = saved
    }

    fun set(c: String) {
        if (c !in codes) return
        code = c
        prefs?.edit()?.putString("code", c)?.apply()
    }

    private class Table(val exact: HashMap<String, List<String>>, val prefix: List<Pair<String, List<String>>>)

    private val table: Table by lazy {
        val ex = HashMap<String, List<String>>()
        val pre = ArrayList<Pair<String, List<String>>>()
        val all = T_MENU + "\n" + T_VIDEO + "\n" + T_ACCOUNT + "\n" + T_SETTINGS
        for (line in all.lines()) {
            if (line.isBlank()) continue
            val p = line.split("|")
            if (p.size != 9) continue
            val key = p[0]
            val vals = p.subList(1, 9).map { it.trim() }
            if (key.endsWith("~")) pre.add(key.dropLast(1) to vals) else ex[key] = vals
        }
        pre.sortByDescending { it.first.length }
        Table(ex, pre)
    }

    fun tr(s: String): String {
        val k = col[code] ?: return s
        if (s.isEmpty()) return s
        val i = s.indexOfFirst { it.isLetterOrDigit() }
        if (i < 0) return s
        var j = s.length
        while (j > i && !s[j - 1].isLetterOrDigit() && s[j - 1] !in ".?!:()") j--
        val lead = s.substring(0, i)
        val core = s.substring(i, j)
        val tail = s.substring(j)
        val t = table
        t.exact[core]?.let { v -> return lead + (v.getOrNull(k) ?: core) + tail }
        for ((p, v) in t.prefix) {
            if (core.startsWith(p)) {
                val head = v.getOrNull(k) ?: return s
                return lead + head + (if (p.endsWith(" ")) " " else "") + core.substring(p.length) + tail
            }
        }
        return s
    }
}

/** Same as the Material Text, but the label is translated to the app language first. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = Lang.tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

// Rows: English|ar|fr|es|de|it|pt|ru|tr   (a key ending in ~ is a prefix: the rest of the text is kept)

private val T_MENU = """
Account|الحساب|Compte|Cuenta|Konto|Account|Conta|Аккаунт|Hesap
Change Playlist|تغيير القائمة|Changer de playlist|Cambiar lista|Playlist wechseln|Cambia playlist|Trocar lista|Сменить плейлист|Listeyi değiştir
Settings|الإعدادات|Paramètres|Ajustes|Einstellungen|Impostazioni|Configurações|Настройки|Ayarlar
Reload|إعادة تحميل|Recharger|Recargar|Neu laden|Ricarica|Recarregar|Обновить|Yenile
Exit|خروج|Quitter|Salir|Beenden|Esci|Sair|Выход|Çıkış
Live|مباشر|En direct|En vivo|Live|Diretta|Ao vivo|Эфир|Canlı
Movies|الأفلام|Films|Películas|Filme|Film|Filmes|Фильмы|Filmler
Series|المسلسلات|Séries|Series|Serien|Serie|Séries|Сериалы|Diziler
Movie|فيلم|Film|Película|Film|Film|Filme|Фильм|Film
Home|الرئيسية|Accueil|Inicio|Start|Home|Início|Главная|Ana sayfa
Search|بحث|Rechercher|Buscar|Suchen|Cerca|Buscar|Поиск|Ara
Search...|بحث...|Rechercher...|Buscar...|Suchen...|Cerca...|Buscar...|Поиск...|Ara...
Filter...|تصفية...|Filtrer...|Filtrar...|Filtern...|Filtra...|Filtrar...|Фильтр...|Filtrele...
All|الكل|Tout|Todo|Alle|Tutti|Todos|Все|Tümü
Favorite|المفضلة|Favoris|Favoritos|Favoriten|Preferiti|Favoritos|Избранное|Favoriler
Add to Favorite|إضافة للمفضلة|Ajouter aux favoris|Añadir a favoritos|Zu Favoriten hinzufügen|Aggiungi ai preferiti|Adicionar aos favoritos|В избранное|Favorilere ekle
Remove Favorite|إزالة من المفضلة|Retirer des favoris|Quitar de favoritos|Aus Favoriten entfernen|Rimuovi dai preferiti|Remover dos favoritos|Убрать из избранного|Favorilerden çıkar
Fullscreen|ملء الشاشة|Plein écran|Pantalla completa|Vollbild|Schermo intero|Tela cheia|Во весь экран|Tam ekran
Recently watched|شوهد مؤخراً|Vus récemment|Vistos recientemente|Zuletzt gesehen|Guardati di recente|Assistidos recentemente|Недавно смотрели|Son izlenenler
Resume to Watch|تابع المشاهدة|Reprendre la lecture|Continuar viendo|Weiterschauen|Riprendi|Continuar assistindo|Продолжить просмотр|İzlemeye devam et
Continue Watching|متابعة المشاهدة|Reprendre la lecture|Continuar viendo|Weiterschauen|Continua a guardare|Continuar assistindo|Продолжить просмотр|İzlemeye devam et
Position|الترتيب|Position|Posición|Position|Posizione|Posição|Позиция|Sıra
Move to top|نقل إلى الأعلى|Placer en premier|Mover al principio|Nach oben|Sposta in alto|Mover para o topo|В начало|En üste taşı
Move up|نقل للأعلى|Monter|Subir|Nach oben schieben|Sposta su|Mover para cima|Выше|Yukarı taşı
Move down|نقل للأسفل|Descendre|Bajar|Nach unten schieben|Sposta giù|Mover para baixo|Ниже|Aşağı taşı
Move to bottom|نقل إلى الأسفل|Placer en dernier|Mover al final|Nach unten|Sposta in basso|Mover para o fim|В конец|En alta taşı
Select a channel|اختر قناة|Choisissez une chaîne|Elige un canal|Kanal auswählen|Seleziona un canale|Selecione um canal|Выберите канал|Kanal seçin
Categories|الفئات|Catégories|Categorías|Kategorien|Categorie|Categorias|Категории|Kategoriler
Back|رجوع|Retour|Atrás|Zurück|Indietro|Voltar|Назад|Geri
Connected|متصل|Connecté|Conectado|Verbunden|Connesso|Conectado|Подключено|Bağlandı
No playlist|لا توجد قائمة|Aucune playlist|Sin lista|Keine Playlist|Nessuna playlist|Sem lista|Нет плейлиста|Liste yok
Add Playlist|إضافة قائمة|Ajouter une playlist|Añadir lista|Playlist hinzufügen|Aggiungi playlist|Adicionar lista|Добавить плейлист|Liste ekle
Add list|إضافة قائمة|Ajouter une liste|Añadir lista|Liste hinzufügen|Aggiungi lista|Adicionar lista|Добавить список|Liste ekle
Connect|اتصال|Connecter|Conectar|Verbinden|Connetti|Conectar|Подключить|Bağlan
Edit|تعديل|Modifier|Editar|Bearbeiten|Modifica|Editar|Изменить|Düzenle
Delete|حذف|Supprimer|Eliminar|Löschen|Elimina|Excluir|Удалить|Sil
Edit playlist|تعديل القائمة|Modifier la playlist|Editar lista|Playlist bearbeiten|Modifica playlist|Editar lista|Изменить плейлист|Listeyi düzenle
Delete playlist?|حذف القائمة؟|Supprimer la playlist ?|¿Eliminar la lista?|Playlist löschen?|Eliminare la playlist?|Excluir a lista?|Удалить плейлист?|Liste silinsin mi?
Name|الاسم|Nom|Nombre|Name|Nome|Nome|Имя|Ad
List name|اسم القائمة|Nom de la liste|Nombre de la lista|Listenname|Nome lista|Nome da lista|Название списка|Liste adı
Server|الخادم|Serveur|Servidor|Server|Server|Servidor|Сервер|Sunucu
Username|اسم المستخدم|Nom d'utilisateur|Usuario|Benutzername|Nome utente|Usuário|Логин|Kullanıcı adı
Password|كلمة المرور|Mot de passe|Contraseña|Passwort|Password|Senha|Пароль|Şifre
Save|حفظ|Enregistrer|Guardar|Speichern|Salva|Salvar|Сохранить|Kaydet
Cancel|إلغاء|Annuler|Cancelar|Abbrechen|Annulla|Cancelar|Отмена|İptal
Loading...|جارٍ التحميل...|Chargement...|Cargando...|Wird geladen...|Caricamento...|Carregando...|Загрузка...|Yükleniyor...
Save & Load|حفظ وتحميل|Enregistrer et charger|Guardar y cargar|Speichern & Laden|Salva e carica|Salvar e carregar|Сохранить и загрузить|Kaydet ve yükle
Choose language|اختر اللغة|Choisir la langue|Elegir idioma|Sprache wählen|Scegli la lingua|Escolher idioma|Выберите язык|Dil seçin
JoyPlayer IPTV Pro doesn't sell playlists or subscriptions.|JoyPlayer IPTV Pro لا يبيع قوائم أو اشتراكات.|JoyPlayer IPTV Pro ne vend ni playlists ni abonnements.|JoyPlayer IPTV Pro no vende listas ni suscripciones.|JoyPlayer IPTV Pro verkauft keine Playlists oder Abonnements.|JoyPlayer IPTV Pro non vende playlist né abbonamenti.|O JoyPlayer IPTV Pro não vende listas nem assinaturas.|JoyPlayer IPTV Pro не продаёт плейлисты и подписки.|JoyPlayer IPTV Pro liste veya abonelik satmaz.
JoyPlayer is a general media player and it doesn't include any content or playlists.|JoyPlayer مشغّل وسائط عام ولا يتضمن أي محتوى أو قوائم.|JoyPlayer est un lecteur multimédia général et n'inclut aucun contenu ni playlist.|JoyPlayer es un reproductor multimedia general y no incluye contenido ni listas.|JoyPlayer ist ein allgemeiner Mediaplayer und enthält keine Inhalte oder Playlists.|JoyPlayer è un lettore multimediale generico e non include contenuti o playlist.|O JoyPlayer é um reprodutor de mídia geral e não inclui conteúdo nem listas.|JoyPlayer — универсальный медиаплеер, он не содержит контента и плейлистов.|JoyPlayer genel bir medya oynatıcıdır; içerik veya liste içermez.
Watch Now|شاهد الآن|Regarder|Ver ahora|Jetzt ansehen|Guarda ora|Assistir agora|Смотреть|Şimdi izle
Resume|استئناف|Reprendre|Reanudar|Fortsetzen|Riprendi|Retomar|Продолжить|Devam et
Trailer|الإعلان|Bande-annonce|Tráiler|Trailer|Trailer|Trailer|Трейлер|Fragman
Continue|متابعة|Continuer|Continuar|Weiter|Continua|Continuar|Продолжить|Devam
No episodes available|لا توجد حلقات|Aucun épisode disponible|No hay episodios disponibles|Keine Episoden verfügbar|Nessun episodio disponibile|Nenhum episódio disponível|Серий нет|Bölüm yok
Trailer not available|الإعلان غير متوفر|Bande-annonce indisponible|Tráiler no disponible|Trailer nicht verfügbar|Trailer non disponibile|Trailer indisponível|Трейлер недоступен|Fragman yok
Season ~|الموسم|Saison|Temporada|Staffel|Stagione|Temporada|Сезон|Sezon
Episode ~|الحلقة|Épisode|Episodio|Folge|Episodio|Episódio|Серия|Bölüm
Genre : ~|النوع :|Genre :|Género :|Genre :|Genere :|Gênero :|Жанр :|Tür :
Date Added : ~|تاريخ الإضافة :|Date d'ajout :|Fecha de adición :|Hinzugefügt am :|Data di aggiunta :|Adicionado em :|Дата добавления :|Eklenme tarihi :
"""

private val T_VIDEO = """
Audio|الصوت|Audio|Audio|Audio|Audio|Áudio|Звук|Ses
Subtitles|الترجمة|Sous-titres|Subtítulos|Untertitel|Sottotitoli|Legendas|Субтитры|Altyazı
Quality|الجودة|Qualité|Calidad|Qualität|Qualità|Qualidade|Качество|Kalite
Fit|ملاءمة|Ajuster|Ajustar|Anpassen|Adatta|Ajustar|По размеру|Sığdır
Zoom|تكبير|Zoom|Zoom|Zoom|Zoom|Zoom|Масштаб|Yakınlaştır
Stretch|تمديد|Étirer|Estirar|Strecken|Estendi|Esticar|Растянуть|Uzat
Next|التالي|Suivant|Siguiente|Weiter|Avanti|Próximo|Далее|Sonraki
Audio / Dubbing|الصوت / الدبلجة|Audio / Doublage|Audio / Doblaje|Audio / Synchronisation|Audio / Doppiaggio|Áudio / Dublagem|Звук / Дубляж|Ses / Dublaj
This stream has only one audio track.|هذا البث يحتوي على مسار صوت واحد فقط.|Ce flux n'a qu'une seule piste audio.|Esta emisión solo tiene una pista de audio.|Dieser Stream hat nur eine Tonspur.|Questo stream ha una sola traccia audio.|Esta transmissão tem apenas uma faixa de áudio.|В этом потоке только одна звуковая дорожка.|Bu yayında yalnızca bir ses parçası var.
Off|إيقاف|Désactivé|Desactivado|Aus|Disattivato|Desligado|Выкл.|Kapalı
Search online (SubDL)|بحث على الإنترنت (SubDL)|Rechercher en ligne (SubDL)|Buscar en línea (SubDL)|Online suchen (SubDL)|Cerca online (SubDL)|Buscar online (SubDL)|Искать онлайн (SubDL)|Çevrimiçi ara (SubDL)
Online search works for movies and series.|البحث على الإنترنت يعمل للأفلام والمسلسلات.|La recherche en ligne fonctionne pour les films et séries.|La búsqueda en línea funciona para películas y series.|Die Online-Suche funktioniert für Filme und Serien.|La ricerca online funziona per film e serie.|A busca online funciona para filmes e séries.|Онлайн-поиск работает для фильмов и сериалов.|Çevrimiçi arama film ve diziler için çalışır.
SubDL subtitles|ترجمات SubDL|Sous-titres SubDL|Subtítulos SubDL|SubDL-Untertitel|Sottotitoli SubDL|Legendas SubDL|Субтитры SubDL|SubDL altyazıları
Paste your SubDL API key (saved on this device only):|الصق مفتاح SubDL API (يُحفظ على هذا الجهاز فقط):|Collez votre clé API SubDL (enregistrée sur cet appareil uniquement) :|Pega tu clave API de SubDL (se guarda solo en este dispositivo):|Füge deinen SubDL-API-Schlüssel ein (nur auf diesem Gerät gespeichert):|Incolla la tua chiave API SubDL (salvata solo su questo dispositivo):|Cole sua chave de API do SubDL (salva apenas neste dispositivo):|Вставьте ключ API SubDL (хранится только на этом устройстве):|SubDL API anahtarınızı yapıştırın (yalnızca bu cihazda saklanır):
API key|مفتاح API|Clé API|Clave API|API-Schlüssel|Chiave API|Chave de API|Ключ API|API anahtarı
Auto-load subtitles|تحميل الترجمة تلقائياً|Charger les sous-titres automatiquement|Cargar subtítulos automáticamente|Untertitel automatisch laden|Carica sottotitoli automaticamente|Carregar legendas automaticamente|Автозагрузка субтитров|Altyazıları otomatik yükle
Change API key|تغيير مفتاح API|Changer la clé API|Cambiar clave API|API-Schlüssel ändern|Cambia chiave API|Alterar chave de API|Сменить ключ API|API anahtarını değiştir
Video quality|جودة الفيديو|Qualité vidéo|Calidad de vídeo|Videoqualität|Qualità video|Qualidade de vídeo|Качество видео|Video kalitesi
Auto|تلقائي|Auto|Auto|Auto|Auto|Auto|Авто|Otomatik
This stream offers only one quality.|هذا البث يوفر جودة واحدة فقط.|Ce flux n'offre qu'une seule qualité.|Esta emisión ofrece una sola calidad.|Dieser Stream bietet nur eine Qualität.|Questo stream offre una sola qualità.|Esta transmissão oferece apenas uma qualidade.|Этот поток доступен только в одном качестве.|Bu yayın yalnızca tek kalite sunar.
Resuming from ~|استئناف من|Reprise à|Reanudando desde|Fortsetzen ab|Ripresa da|Retomando de|Продолжение с|Devam ediliyor:
Next episode: ~|الحلقة التالية:|Épisode suivant :|Siguiente episodio:|Nächste Folge:|Prossimo episodio:|Próximo episódio:|Следующая серия:|Sonraki bölüm:
Subtitle loaded: ~|تم تحميل الترجمة:|Sous-titre chargé :|Subtítulo cargado:|Untertitel geladen:|Sottotitolo caricato:|Legenda carregada:|Субтитры загружены:|Altyazı yüklendi:
Track ~|مسار|Piste|Pista|Spur|Traccia|Faixa|Дорожка|Parça
Subtitle ~|ترجمة|Sous-titre|Subtítulo|Untertitel|Sottotitolo|Legenda|Субтитр|Altyazı
Now: ~|الآن:|Maintenant :|Ahora:|Jetzt:|Ora:|Agora:|Сейчас:|Şimdi:
Next: ~|التالي:|Suivant :|Siguiente:|Danach:|Prossimo:|Próximo:|Далее:|Sonraki:
Loading guide...|جارٍ تحميل الدليل...|Chargement du guide...|Cargando guía...|Programmführer wird geladen...|Caricamento guida...|Carregando guia...|Загрузка программы...|Rehber yükleniyor...
No program info|لا توجد معلومات البرنامج|Aucune info de programme|Sin información de programa|Keine Programminfo|Nessuna info programma|Sem informações do programa|Нет данных о программе|Program bilgisi yok
Program Guide|دليل البرامج|Guide des programmes|Guía de programación|Programmführer|Guida programmi|Guia de programação|Телепрограмма|Program rehberi
Picture-in-picture is not supported on this device|وضع صورة داخل صورة غير مدعوم على هذا الجهاز|Le mode image dans l'image n'est pas pris en charge sur cet appareil|Imagen en imagen no es compatible con este dispositivo|Bild-in-Bild wird auf diesem Gerät nicht unterstützt|Picture-in-picture non è supportato su questo dispositivo|Picture-in-picture não é compatível com este dispositivo|Режим «картинка в картинке» не поддерживается на этом устройстве|Bu cihaz pencere içinde pencereyi desteklemiyor
Keep playing in a small window when leaving the app|متابعة التشغيل في نافذة صغيرة عند مغادرة التطبيق|Continuer la lecture dans une petite fenêtre en quittant l'app|Seguir reproduciendo en una ventana pequeña al salir de la app|In einem kleinen Fenster weiterspielen, wenn die App verlassen wird|Continua la riproduzione in una piccola finestra uscendo dall'app|Continuar reproduzindo em uma janela pequena ao sair do app|Продолжать воспроизведение в маленьком окне при выходе из приложения|Uygulamadan çıkınca küçük pencerede oynatmaya devam et
Picture-in-picture is supported on this device.|وضع صورة داخل صورة مدعوم على هذا الجهاز.|Le mode image dans l'image est pris en charge sur cet appareil.|Imagen en imagen es compatible con este dispositivo.|Bild-in-Bild wird auf diesem Gerät unterstützt.|Picture-in-picture è supportato su questo dispositivo.|Picture-in-picture é compatível com este dispositivo.|Режим «картинка в картинке» поддерживается на этом устройстве.|Bu cihaz pencere içinde pencereyi destekliyor.
This device does not support picture-in-picture.|هذا الجهاز لا يدعم وضع صورة داخل صورة.|Cet appareil ne prend pas en charge l'image dans l'image.|Este dispositivo no admite imagen en imagen.|Dieses Gerät unterstützt kein Bild-in-Bild.|Questo dispositivo non supporta il picture-in-picture.|Este dispositivo não é compatível com picture-in-picture.|Это устройство не поддерживает «картинку в картинке».|Bu cihaz pencere içinde pencereyi desteklemiyor.
While a video plays, press Home or the PiP button in the player.|أثناء تشغيل الفيديو، اضغط زر الرئيسية أو زر PiP في المشغّل.|Pendant la lecture, appuyez sur Accueil ou sur le bouton PiP du lecteur.|Durante la reproducción, pulsa Inicio o el botón PiP del reproductor.|Drücke während der Wiedergabe Home oder die PiP-Taste im Player.|Durante la riproduzione, premi Home o il pulsante PiP nel player.|Durante a reprodução, pressione Início ou o botão PiP no player.|Во время воспроизведения нажмите «Домой» или кнопку PiP в плеере.|Video oynarken Ana ekran'a veya oynatıcıdaki PiP düğmesine basın.
"""

private val T_ACCOUNT = """
Playlist|القائمة|Playlist|Lista|Playlist|Playlist|Lista|Плейлист|Liste
Status|الحالة|Statut|Estado|Status|Stato|Status|Статус|Durum
Active|نشط|Actif|Activo|Aktiv|Attivo|Ativo|Активен|Aktif
Unknown|غير معروف|Inconnu|Desconocido|Unbekannt|Sconosciuto|Desconhecido|Неизвестно|Bilinmiyor
Expired|منتهي|Expiré|Caducado|Abgelaufen|Scaduto|Expirado|Истёк|Süresi doldu
Expiry date|تاريخ الانتهاء|Date d'expiration|Fecha de vencimiento|Ablaufdatum|Data di scadenza|Data de expiração|Дата окончания|Bitiş tarihi
Unlimited|غير محدود|Illimité|Ilimitado|Unbegrenzt|Illimitato|Ilimitado|Безлимит|Sınırsız
Days left|الأيام المتبقية|Jours restants|Días restantes|Verbleibende Tage|Giorni rimasti|Dias restantes|Осталось дней|Kalan gün
Max connections|أقصى عدد اتصالات|Connexions max.|Conexiones máx.|Max. Verbindungen|Connessioni max|Conexões máx.|Макс. подключений|Maks. bağlantı
Active connections|الاتصالات النشطة|Connexions actives|Conexiones activas|Aktive Verbindungen|Connessioni attive|Conexões ativas|Активных подключений|Aktif bağlantı
Trial|تجريبي|Essai|Prueba|Test|Prova|Teste|Пробный|Deneme
Yes|نعم|Oui|Sí|Ja|Sì|Sim|Да|Evet
No|لا|Non|No|Nein|No|Não|Нет|Hayır
Created|تاريخ الإنشاء|Créé|Creado|Erstellt|Creato|Criado|Создан|Oluşturuldu
Refresh|تحديث|Actualiser|Actualizar|Aktualisieren|Aggiorna|Atualizar|Обновить|Yenile
No playlist connected. Add one from Change Playlist.|لا توجد قائمة متصلة. أضف واحدة من «تغيير القائمة».|Aucune playlist connectée. Ajoutez-en une via « Changer de playlist ».|Ninguna lista conectada. Añade una desde «Cambiar lista».|Keine Playlist verbunden. Füge eine unter „Playlist wechseln“ hinzu.|Nessuna playlist connessa. Aggiungine una da «Cambia playlist».|Nenhuma lista conectada. Adicione uma em «Trocar lista».|Плейлист не подключён. Добавьте его в разделе «Сменить плейлист».|Bağlı liste yok. «Listeyi değiştir» bölümünden ekleyin.
Installed: ~|المثبّت:|Installé :|Instalado:|Installiert:|Installato:|Instalado:|Установлено:|Yüklü:
Checking for updates...|جارٍ البحث عن تحديثات...|Recherche de mises à jour...|Buscando actualizaciones...|Suche nach Updates...|Controllo aggiornamenti...|Verificando atualizações...|Проверка обновлений...|Güncellemeler denetleniyor...
You are up to date.|أنت تستخدم آخر إصدار.|Vous êtes à jour.|Estás al día.|Du bist auf dem neuesten Stand.|Sei aggiornato.|Você está atualizado.|У вас последняя версия.|Güncelsiniz.
Check again|تحقق مرة أخرى|Vérifier à nouveau|Comprobar de nuevo|Erneut prüfen|Controlla di nuovo|Verificar novamente|Проверить снова|Tekrar kontrol et
New version: ~|إصدار جديد:|Nouvelle version :|Nueva versión:|Neue Version:|Nuova versione:|Nova versão:|Новая версия:|Yeni sürüm:
Download & install|تحميل وتثبيت|Télécharger et installer|Descargar e instalar|Herunterladen & installieren|Scarica e installa|Baixar e instalar|Скачать и установить|İndir ve yükle
Downloading... ~|جارٍ التحميل...|Téléchargement...|Descargando...|Wird heruntergeladen...|Download in corso...|Baixando...|Загрузка...|İndiriliyor...
Install now|ثبّت الآن|Installer maintenant|Instalar ahora|Jetzt installieren|Installa ora|Instalar agora|Установить сейчас|Şimdi yükle
Retry|إعادة المحاولة|Réessayer|Reintentar|Erneut versuchen|Riprova|Tentar novamente|Повторить|Tekrar dene
Allow installs from JoyPlayer, come back here and press Install.|اسمح بالتثبيت من JoyPlayer ثم ارجع واضغط تثبيت.|Autorisez les installations depuis JoyPlayer, revenez ici et appuyez sur Installer.|Permite instalar desde JoyPlayer, vuelve aquí y pulsa Instalar.|Erlaube Installationen aus JoyPlayer, komm zurück und tippe auf Installieren.|Consenti le installazioni da JoyPlayer, torna qui e premi Installa.|Permita instalações do JoyPlayer, volte aqui e toque em Instalar.|Разрешите установку из JoyPlayer, вернитесь сюда и нажмите «Установить».|JoyPlayer'dan yüklemeye izin verin, buraya dönün ve Yükle'ye basın.
"""

private val T_SETTINGS = """
Default|افتراضي|Par défaut|Predeterminado|Standard|Predefinito|Padrão|По умолчанию|Varsayılan
Auto-Update Live, Movie & Series everytime|تحديث المباشر والأفلام والمسلسلات في كل مرة|Mise à jour Live, films et séries à chaque fois|Actualizar En vivo, películas y series cada vez|Live, Filme & Serien jedes Mal aktualisieren|Aggiorna Live, film e serie ogni volta|Atualizar ao vivo, filmes e séries toda vez|Обновлять эфир, фильмы и сериалы каждый раз|Canlı, film ve dizileri her seferinde güncelle
Auto-Update Live, Movie & Series everyday|تحديث المباشر والأفلام والمسلسلات كل يوم|Mise à jour Live, films et séries chaque jour|Actualizar En vivo, películas y series cada día|Live, Filme & Serien täglich aktualisieren|Aggiorna Live, film e serie ogni giorno|Atualizar ao vivo, filmes e séries todo dia|Обновлять эфир, фильмы и сериалы каждый день|Canlı, film ve dizileri her gün güncelle
Auto-Update Live, Movie & Series after 2 days|تحديث المباشر والأفلام والمسلسلات بعد يومين|Mise à jour Live, films et séries après 2 jours|Actualizar En vivo, películas y series tras 2 días|Live, Filme & Serien nach 2 Tagen aktualisieren|Aggiorna Live, film e serie dopo 2 giorni|Atualizar ao vivo, filmes e séries após 2 dias|Обновлять эфир, фильмы и сериалы через 2 дня|Canlı, film ve dizileri 2 gün sonra güncelle
Enable Subtitle|تفعيل الترجمة|Activer les sous-titres|Activar subtítulos|Untertitel aktivieren|Attiva sottotitoli|Ativar legendas|Включить субтитры|Altyazıyı etkinleştir
Subtitle Size|حجم الترجمة|Taille des sous-titres|Tamaño de subtítulos|Untertitelgröße|Dimensione sottotitoli|Tamanho da legenda|Размер субтитров|Altyazı boyutu
Subtitle Color|لون الترجمة|Couleur des sous-titres|Color de subtítulos|Untertitelfarbe|Colore sottotitoli|Cor da legenda|Цвет субтитров|Altyazı rengi
Subtitle Background|خلفية الترجمة|Fond des sous-titres|Fondo de subtítulos|Untertitelhintergrund|Sfondo sottotitoli|Fundo da legenda|Фон субтитров|Altyazı arka planı
Subtitle Test|اختبار الترجمة|Test de sous-titre|Prueba de subtítulo|Untertitel-Test|Test sottotitolo|Teste de legenda|Проверка субтитров|Altyazı testi
Get a free key from subdl.com|احصل على مفتاح مجاني من subdl.com|Obtenez une clé gratuite sur subdl.com|Obtén una clave gratis en subdl.com|Hol dir einen kostenlosen Schlüssel auf subdl.com|Ottieni una chiave gratuita su subdl.com|Obtenha uma chave grátis em subdl.com|Получите бесплатный ключ на subdl.com|subdl.com'dan ücretsiz anahtar alın
Access Restricted|الوصول مقيّد|Accès restreint|Acceso restringido|Zugriff beschränkt|Accesso limitato|Acesso restrito|Доступ ограничен|Erişim kısıtlı
Backup & Restore|نسخة احتياطية واستعادة|Sauvegarde et restauration|Copia de seguridad y restauración|Sicherung & Wiederherstellung|Backup e ripristino|Backup e restauração|Резервная копия и восстановление|Yedekle ve geri yükle
The backup contains your playlists (with passwords), favorites, history and settings. Keep it private.|النسخة تحتوي قوائمك (مع كلمات المرور) والمفضلة والسجل والإعدادات. احتفظ بها بسرية.|La sauvegarde contient vos playlists (avec mots de passe), favoris, historique et réglages. Gardez-la privée.|La copia contiene tus listas (con contraseñas), favoritos, historial y ajustes. Mantenla privada.|Die Sicherung enthält deine Playlists (mit Passwörtern), Favoriten, Verlauf und Einstellungen. Halte sie privat.|Il backup contiene le tue playlist (con password), preferiti, cronologia e impostazioni. Tienilo privato.|O backup contém suas listas (com senhas), favoritos, histórico e configurações. Mantenha-o privado.|Копия содержит ваши плейлисты (с паролями), избранное, историю и настройки. Храните её в тайне.|Yedek listelerinizi (şifrelerle), favorileri, geçmişi ve ayarları içerir. Gizli tutun.
Save backup to a file|حفظ نسخة احتياطية في ملف|Enregistrer la sauvegarde dans un fichier|Guardar copia en un archivo|Sicherung in Datei speichern|Salva backup in un file|Salvar backup em arquivo|Сохранить копию в файл|Yedeği dosyaya kaydet
Restore from a file|استعادة من ملف|Restaurer depuis un fichier|Restaurar desde un archivo|Aus Datei wiederherstellen|Ripristina da file|Restaurar de arquivo|Восстановить из файла|Dosyadan geri yükle
Copy backup to clipboard|نسخ النسخة الاحتياطية للحافظة|Copier la sauvegarde dans le presse-papiers|Copiar copia al portapapeles|Sicherung in Zwischenablage kopieren|Copia backup negli appunti|Copiar backup para a área de transferência|Копировать копию в буфер|Yedeği panoya kopyala
Restore from clipboard|استعادة من الحافظة|Restaurer depuis le presse-papiers|Restaurar desde el portapapeles|Aus Zwischenablage wiederherstellen|Ripristina dagli appunti|Restaurar da área de transferência|Восстановить из буфера|Panodan geri yükle
Backup link (https://...)|رابط النسخة (https://...)|Lien de sauvegarde (https://...)|Enlace de la copia (https://...)|Sicherungslink (https://...)|Link del backup (https://...)|Link do backup (https://...)|Ссылка на копию (https://...)|Yedek bağlantısı (https://...)
Restore from link|استعادة من رابط|Restaurer depuis un lien|Restaurar desde enlace|Von Link wiederherstellen|Ripristina da link|Restaurar de link|Восстановить по ссылке|Bağlantıdan geri yükle
Restore backup?|استعادة النسخة؟|Restaurer la sauvegarde ?|¿Restaurar la copia?|Sicherung wiederherstellen?|Ripristinare il backup?|Restaurar o backup?|Восстановить копию?|Yedek geri yüklensin mi?
This replaces your playlists, favorites, history and settings with the backup.|سيتم استبدال قوائمك والمفضلة والسجل والإعدادات بما في النسخة.|Ceci remplace vos playlists, favoris, historique et réglages par la sauvegarde.|Esto reemplaza tus listas, favoritos, historial y ajustes por la copia.|Dadurch werden Playlists, Favoriten, Verlauf und Einstellungen durch die Sicherung ersetzt.|Questo sostituisce playlist, preferiti, cronologia e impostazioni con il backup.|Isso substitui suas listas, favoritos, histórico e configurações pelo backup.|Плейлисты, избранное, история и настройки будут заменены копией.|Listeleriniz, favorileriniz, geçmişiniz ve ayarlarınız yedekle değiştirilecek.
Backup saved|تم حفظ النسخة الاحتياطية|Sauvegarde enregistrée|Copia guardada|Sicherung gespeichert|Backup salvato|Backup salvo|Копия сохранена|Yedek kaydedildi
This is not a JoyPlayer backup|هذا ليس ملف نسخة JoyPlayer|Ce n'est pas une sauvegarde JoyPlayer|No es una copia de JoyPlayer|Das ist keine JoyPlayer-Sicherung|Non è un backup di JoyPlayer|Não é um backup do JoyPlayer|Это не копия JoyPlayer|Bu bir JoyPlayer yedeği değil
Subtitle delay|تأخير الترجمة|Décalage des sous-titres|Retraso de subtítulos|Untertitelversatz|Ritardo sottotitoli|Atraso da legenda|Задержка субтитров|Altyazı gecikmesi
Delay works with subtitles downloaded from SubDL. Load one first.|التأخير يعمل مع الترجمات المحمّلة من SubDL. حمّل ترجمة أولاً.|Le décalage fonctionne avec les sous-titres SubDL. Chargez-en un d'abord.|El retraso funciona con subtítulos de SubDL. Carga uno primero.|Der Versatz funktioniert mit SubDL-Untertiteln. Lade zuerst einen.|Il ritardo funziona con i sottotitoli SubDL. Caricane uno prima.|O atraso funciona com legendas do SubDL. Carregue uma primeiro.|Задержка работает с субтитрами SubDL. Сначала загрузите субтитры.|Gecikme SubDL altyazılarıyla çalışır. Önce bir altyazı yükleyin.
Positive values show the subtitle later, negative earlier.|القيم الموجبة تؤخر الترجمة والسالبة تقدّمها.|Les valeurs positives retardent le sous-titre, les négatives l'avancent.|Los valores positivos retrasan el subtítulo y los negativos lo adelantan.|Positive Werte zeigen den Untertitel später, negative früher.|I valori positivi ritardano il sottotitolo, quelli negativi lo anticipano.|Valores positivos atrasam a legenda e negativos a adiantam.|Положительные значения задерживают субтитры, отрицательные — ускоряют.|Pozitif değerler altyazıyı geciktirir, negatif değerler öne alır.
Reset|إعادة ضبط|Réinitialiser|Restablecer|Zurücksetzen|Reimposta|Redefinir|Сбросить|Sıfırla
Playback speed|سرعة التشغيل|Vitesse de lecture|Velocidad de reproducción|Wiedergabegeschwindigkeit|Velocità di riproduzione|Velocidade de reprodução|Скорость воспроизведения|Oynatma hızı
Normal (1x)|عادية (1x)|Normale (1x)|Normal (1x)|Normal (1x)|Normale (1x)|Normal (1x)|Обычная (1x)|Normal (1x)
Sleep timer|مؤقت النوم|Minuterie de sommeil|Temporizador de sueño|Einschlaftimer|Timer di spegnimento|Temporizador de sono|Таймер сна|Uyku zamanlayıcı
Sleep|نوم|Veille|Dormir|Schlaf|Sleep|Dormir|Сон|Uyku
Cast to|بث إلى|Diffuser sur|Transmitir a|Streamen auf|Trasmetti a|Transmitir para|Транслировать на|Şuraya yayınla
Searching for devices on your Wi-Fi...|جارٍ البحث عن أجهزة على شبكة Wi-Fi...|Recherche d'appareils sur votre Wi-Fi...|Buscando dispositivos en tu Wi-Fi...|Suche nach Geräten im WLAN...|Ricerca di dispositivi sul Wi-Fi...|Procurando dispositivos no Wi-Fi...|Поиск устройств в Wi-Fi...|Wi-Fi ağında cihazlar aranıyor...
Stop casting|إيقاف البث|Arrêter la diffusion|Detener transmisión|Streaming beenden|Interrompi trasmissione|Parar transmissão|Остановить трансляцию|Yayını durdur
Casting to ~|البث إلى|Diffusion sur|Transmitiendo a|Streamt auf|Trasmissione a|Transmitindo para|Трансляция на|Yayınlanıyor:
Cast|بث|Cast|Cast|Cast|Cast|Cast|Cast|Cast
Stop|إيقاف|Arrêter|Detener|Stopp|Ferma|Parar|Стоп|Durdur
Last|السابقة|Précédente|Anterior|Zurück|Precedente|Anterior|Предыдущий|Önceki
EPG (XMLTV)|دليل البرامج (XMLTV)|Guide TV (XMLTV)|Guía TV (XMLTV)|TV-Programm (XMLTV)|Guida TV (XMLTV)|Guia de TV (XMLTV)|Телепрограмма (XMLTV)|TV rehberi (XMLTV)
Program guide for M3U playlists. Leave empty to use the link found inside the playlist.|دليل البرامج لقوائم M3U. اتركه فارغاً لاستخدام الرابط الموجود داخل القائمة.|Guide des programmes pour les playlists M3U. Laissez vide pour utiliser le lien de la playlist.|Guía de programación para listas M3U. Déjalo vacío para usar el enlace de la lista.|Programmführer für M3U-Playlists. Leer lassen, um den Link aus der Playlist zu nutzen.|Guida programmi per playlist M3U. Lascia vuoto per usare il link della playlist.|Guia de programação para listas M3U. Deixe vazio para usar o link da lista.|Телепрограмма для M3U-плейлистов. Оставьте пустым, чтобы использовать ссылку из плейлиста.|M3U listeleri için program rehberi. Listedeki bağlantıyı kullanmak için boş bırakın.
XMLTV link (.xml or .xml.gz)|رابط XMLTV (.xml أو .xml.gz)|Lien XMLTV (.xml ou .xml.gz)|Enlace XMLTV (.xml o .xml.gz)|XMLTV-Link (.xml oder .xml.gz)|Link XMLTV (.xml o .xml.gz)|Link XMLTV (.xml ou .xml.gz)|Ссылка XMLTV (.xml или .xml.gz)|XMLTV bağlantısı (.xml veya .xml.gz)
Do you want to exit the app?|هل تريد الخروج من التطبيق؟|Voulez-vous quitter l'application ?|¿Quieres salir de la aplicación?|Möchten Sie die App beenden?|Vuoi uscire dall'app?|Deseja sair do aplicativo?|Выйти из приложения?|Uygulamadan çıkmak istiyor musunuz?
Main Screen Background|خلفية الشاشة الرئيسية|Arrière-plan de l'écran principal|Fondo de la pantalla principal|Hintergrund des Hauptbildschirms|Sfondo della schermata principale|Plano de fundo da tela principal|Фон главного экрана|Ana ekran arka planı
Choose Main Screen Background|اختر خلفية الشاشة الرئيسية|Choisir l'arrière-plan de l'écran principal|Elegir el fondo de la pantalla principal|Hintergrund des Hauptbildschirms wählen|Scegli lo sfondo della schermata principale|Escolher plano de fundo da tela principal|Выберите фон главного экрана|Ana ekran arka planını seçin
Main Buttons Style|شكل الأزرار الرئيسية|Style des boutons principaux|Estilo de botones principales|Stil der Hauptschaltflächen|Stile dei pulsanti principali|Estilo dos botões principais|Стиль главных кнопок|Ana düğme stili
Button Color|لون الزر|Couleur du bouton|Color del botón|Schaltflächenfarbe|Colore del pulsante|Cor do botão|Цвет кнопки|Düğme rengi
Border Color|لون الإطار|Couleur de la bordure|Color del borde|Rahmenfarbe|Colore del bordo|Cor da borda|Цвет рамки|Kenarlık rengi
Text Color|لون الخط|Couleur du texte|Color del texto|Textfarbe|Colore del testo|Cor do texto|Цвет текста|Metin rengi
Font|نوع الخط|Police|Fuente|Schriftart|Carattere|Fonte|Шрифт|Yazı tipi
Text size|حجم الخط|Taille du texte|Tamaño del texto|Textgröße|Dimensione del testo|Tamanho do texto|Размер текста|Metin boyutu
Icon size|حجم الأيقونة|Taille de l'icône|Tamaño del icono|Symbolgröße|Dimensione icona|Tamanho do ícone|Размер значка|Simge boyutu
Reset to default|إعادة الضبط|Rétablir par défaut|Restablecer|Zurücksetzen|Ripristina|Redefinir|Сбросить|Varsayılana sıfırla
Extra large|كبير جداً|Très grand|Muy grande|Sehr groß|Molto grande|Muito grande|Очень большой|Çok büyük
Category added to favorites|تمت إضافة التصنيف إلى المفضلة|Catégorie ajoutée aux favoris|Categoría añadida a favoritos|Kategorie zu Favoriten hinzugefügt|Categoria aggiunta ai preferiti|Categoria adicionada aos favoritos|Категория добавлена в избранное|Kategori favorilere eklendi
Category removed from favorites|تمت إزالة التصنيف من المفضلة|Catégorie retirée des favoris|Categoría quitada de favoritos|Kategorie aus Favoriten entfernt|Categoria rimossa dai preferiti|Categoria removida dos favoritos|Категория удалена из избранного|Kategori favorilerden çıkarıldı
Added to favorites|تمت الإضافة إلى المفضلة|Ajouté aux favoris|Añadido a favoritos|Zu Favoriten hinzugefügt|Aggiunto ai preferiti|Adicionado aos favoritos|Добавлено в избранное|Favorilere eklendi
Removed from favorites|تمت الإزالة من المفضلة|Retiré des favoris|Quitado de favoritos|Aus Favoriten entfernt|Rimosso dai preferiti|Removido dos favoritos|Удалено из избранного|Favorilerden çıkarıldı
Reorder|ترتيب|Réorganiser|Reordenar|Sortieren|Riordina|Reordenar|Порядок|Sırala
List Layout|عرض قائمة|Disposition liste|Diseño de lista|Listenansicht|Layout elenco|Layout de lista|Список|Liste görünümü
Grid Layout|عرض شبكة|Disposition grille|Diseño de cuadrícula|Rasteransicht|Layout griglia|Layout de grade|Сетка|Izgara görünümü
OK|موافق|OK|Aceptar|OK|OK|OK|ОК|Tamam
Poster size|حجم الملصق|Taille des affiches|Tamaño de carteles|Postergröße|Dimensione locandine|Tamanho dos pôsteres|Размер постеров|Poster boyutu
Parental Control|الرقابة الأبوية|Contrôle parental|Control parental|Kindersicherung|Controllo parentale|Controle parental|Родительский контроль|Ebeveyn denetimi
Change Language|تغيير اللغة|Changer de langue|Cambiar idioma|Sprache ändern|Cambia lingua|Alterar idioma|Сменить язык|Dili değiştir
Change Layout|تغيير التخطيط|Changer la disposition|Cambiar diseño|Layout ändern|Cambia layout|Alterar layout|Сменить макет|Düzeni değiştir
Hide Live Categories|إخفاء فئات المباشر|Masquer les catégories Live|Ocultar categorías en vivo|Live-Kategorien ausblenden|Nascondi categorie Live|Ocultar categorias ao vivo|Скрыть категории эфира|Canlı kategorileri gizle
Hide Vod Categories|إخفاء فئات الأفلام|Masquer les catégories Films|Ocultar categorías de películas|Film-Kategorien ausblenden|Nascondi categorie Film|Ocultar categorias de filmes|Скрыть категории фильмов|Film kategorilerini gizle
Hide Series Categories|إخفاء فئات المسلسلات|Masquer les catégories Séries|Ocultar categorías de series|Serien-Kategorien ausblenden|Nascondi categorie Serie|Ocultar categorias de séries|Скрыть категории сериалов|Dizi kategorilerini gizle
Clear History Channels|مسح سجل القنوات|Effacer l'historique des chaînes|Borrar historial de canales|Kanalverlauf löschen|Cancella cronologia canali|Limpar histórico de canais|Очистить историю каналов|Kanal geçmişini temizle
Clear History Movies|مسح سجل الأفلام|Effacer l'historique des films|Borrar historial de películas|Filmverlauf löschen|Cancella cronologia film|Limpar histórico de filmes|Очистить историю фильмов|Film geçmişini temizle
Clear History Series|مسح سجل المسلسلات|Effacer l'historique des séries|Borrar historial de series|Serienverlauf löschen|Cancella cronologia serie|Limpar histórico de séries|Очистить историю сериалов|Dizi geçmişini temizle
Live Channel Sort|ترتيب القنوات المباشرة|Tri des chaînes Live|Orden de canales en vivo|Live-Kanalsortierung|Ordinamento canali Live|Ordem dos canais ao vivo|Сортировка каналов|Canlı kanal sıralaması
Live channel sort|ترتيب القنوات المباشرة|Tri des chaînes Live|Orden de canales en vivo|Live-Kanalsortierung|Ordinamento canali Live|Ordem dos canais ao vivo|Сортировка каналов|Canlı kanal sıralaması
Live Stream Format|صيغة البث المباشر|Format du flux Live|Formato de emisión en vivo|Live-Stream-Format|Formato stream Live|Formato da transmissão ao vivo|Формат потока|Canlı yayın biçimi
Live stream format|صيغة البث المباشر|Format du flux Live|Formato de emisión en vivo|Live-Stream-Format|Formato stream Live|Formato da transmissão ao vivo|Формат потока|Canlı yayın biçimi
Automatic|تلقائي|Automatique|Automático|Automatisch|Automatico|Automático|Автоматически|Otomatik
Time Format|صيغة الوقت|Format de l'heure|Formato de hora|Zeitformat|Formato ora|Formato de hora|Формат времени|Saat biçimi
Time format|صيغة الوقت|Format de l'heure|Formato de hora|Zeitformat|Formato ora|Formato de hora|Формат времени|Saat biçimi
Subtitle Settings|إعدادات الترجمة|Paramètres des sous-titres|Ajustes de subtítulos|Untertitel-Einstellungen|Impostazioni sottotitoli|Configurações de legendas|Настройки субтитров|Altyazı ayarları
PIP Settings|إعدادات صورة داخل صورة|Paramètres PiP|Ajustes de PiP|PiP-Einstellungen|Impostazioni PiP|Configurações de PiP|Настройки PiP|PiP ayarları
Update Now|حدّث الآن|Mettre à jour|Actualizar ahora|Jetzt aktualisieren|Aggiorna ora|Atualizar agora|Обновить сейчас|Şimdi güncelle
Poster size (Movies & Series)|حجم الملصق (أفلام ومسلسلات)|Taille des affiches (films et séries)|Tamaño de carteles (películas y series)|Postergröße (Filme & Serien)|Dimensione locandine (film e serie)|Tamanho dos pôsteres (filmes e séries)|Размер постеров (фильмы и сериалы)|Poster boyutu (film ve diziler)
Small|صغير|Petit|Pequeño|Klein|Piccolo|Pequeno|Маленький|Küçük
Medium|متوسط|Moyen|Mediano|Mittel|Medio|Médio|Средний|Orta
Large|كبير|Grand|Grande|Groß|Grande|Grande|Большой|Büyük
Default (as provided)|افتراضي (كما هو)|Par défaut (comme fourni)|Predeterminado (como se proporciona)|Standard (wie geliefert)|Predefinito (come fornito)|Padrão (como fornecido)|По умолчанию (как в списке)|Varsayılan (sağlandığı gibi)
24-hour|24 ساعة|24 h|24 horas|24 Stunden|24 ore|24 horas|24 часа|24 saat
12-hour (AM/PM)|12 ساعة (ص/م)|12 h (AM/PM)|12 horas (AM/PM)|12 Stunden (AM/PM)|12 ore (AM/PM)|12 horas (AM/PM)|12 часов (AM/PM)|12 saat (ÖÖ/ÖS)
Auto-connect to the last playlist on launch|الاتصال تلقائياً بآخر قائمة عند التشغيل|Connexion automatique à la dernière playlist au lancement|Conectar automáticamente a la última lista al iniciar|Beim Start automatisch mit der letzten Playlist verbinden|Connetti automaticamente all'ultima playlist all'avvio|Conectar automaticamente à última lista ao abrir|Автоподключение к последнему плейлисту при запуске|Açılışta son listeye otomatik bağlan
Auto-load subtitles (movies & series)|تحميل الترجمة تلقائياً (أفلام ومسلسلات)|Chargement auto des sous-titres (films et séries)|Cargar subtítulos automáticamente (películas y series)|Untertitel automatisch laden (Filme & Serien)|Carica sottotitoli automaticamente (film e serie)|Carregar legendas automaticamente (filmes e séries)|Автозагрузка субтитров (фильмы и сериалы)|Altyazıları otomatik yükle (film ve diziler)
Size|الحجم|Taille|Tamaño|Größe|Dimensione|Tamanho|Размер|Boyut
Normal|عادي|Normal|Normal|Normal|Normale|Normal|Обычный|Normal
Color|اللون|Couleur|Color|Farbe|Colore|Cor|Цвет|Renk
White|أبيض|Blanc|Blanco|Weiß|Bianco|Branco|Белый|Beyaz
Yellow|أصفر|Jaune|Amarillo|Gelb|Giallo|Amarelo|Жёлтый|Sarı
Cyan|سماوي|Cyan|Cian|Cyan|Ciano|Ciano|Голубой|Camgöbeği
Dark background box|خلفية داكنة|Fond sombre|Fondo oscuro|Dunkler Hintergrund|Sfondo scuro|Fundo escuro|Тёмный фон|Koyu arka plan
Preferred online language|لغة البحث المفضلة|Langue en ligne préférée|Idioma en línea preferido|Bevorzugte Online-Sprache|Lingua online preferita|Idioma online preferido|Предпочитаемый язык поиска|Tercih edilen çevrimiçi dil
SubDL API key|مفتاح SubDL API|Clé API SubDL|Clave API de SubDL|SubDL-API-Schlüssel|Chiave API SubDL|Chave de API do SubDL|Ключ API SubDL|SubDL API anahtarı
Saved|تم الحفظ|Enregistré|Guardado|Gespeichert|Salvato|Salvo|Сохранено|Kaydedildi
Save key|حفظ المفتاح|Enregistrer la clé|Guardar clave|Schlüssel speichern|Salva chiave|Salvar chave|Сохранить ключ|Anahtarı kaydet
Clear history?|مسح السجل؟|Effacer l'historique ?|¿Borrar historial?|Verlauf löschen?|Cancellare la cronologia?|Limpar histórico?|Очистить историю?|Geçmiş temizlensin mi?
This clears the recently watched channels list.|سيتم مسح قائمة القنوات المشاهدة مؤخراً.|Ceci efface la liste des chaînes récemment regardées.|Esto borra la lista de canales vistos recientemente.|Dadurch wird die Liste der zuletzt gesehenen Kanäle gelöscht.|Questo cancella l'elenco dei canali guardati di recente.|Isso limpa a lista de canais assistidos recentemente.|Список недавно просмотренных каналов будет очищен.|Son izlenen kanallar listesini temizler.
Done|تم|Terminé|Hecho|Fertig|Fatto|Concluído|Готово|Tamam
Clear|مسح|Effacer|Borrar|Löschen|Cancella|Limpar|Очистить|Temizle
Order by number|ترتيب حسب الرقم|Trier par numéro|Ordenar por número|Nach Nummer|Per numero|Por número|По номеру|Numaraya göre
Order by added|ترتيب حسب الإضافة|Trier par ajout|Ordenar por añadido|Nach Hinzufügung|Per aggiunta|Por adição|По добавлению|Eklenmeye göre
Order by rating|ترتيب حسب التقييم|Trier par note|Ordenar por valoración|Nach Bewertung|Per valutazione|Por avaliação|По рейтингу|Puana göre
Order by A-Z|ترتيب أ-ي|Trier A-Z|Ordenar A-Z|Nach A-Z|Per A-Z|Por A-Z|По А-Я|A-Z'ye göre
Order by Z-A|ترتيب ي-أ|Trier Z-A|Ordenar Z-A|Nach Z-A|Per Z-A|Por Z-A|По Я-А|Z-A'ya göre
Enter PIN|أدخل الرمز|Entrez le code PIN|Introduce el PIN|PIN eingeben|Inserisci il PIN|Digite o PIN|Введите PIN|PIN girin
Wrong PIN|رمز خاطئ|Code PIN incorrect|PIN incorrecto|Falscher PIN|PIN errato|PIN incorreto|Неверный PIN|Yanlış PIN
Create PIN|إنشاء رمز|Créer un code PIN|Crear PIN|PIN erstellen|Crea PIN|Criar PIN|Создать PIN|PIN oluştur
New PIN|رمز جديد|Nouveau PIN|Nuevo PIN|Neuer PIN|Nuovo PIN|Novo PIN|Новый PIN|Yeni PIN
New PIN (4 digits)|رمز جديد (4 أرقام)|Nouveau PIN (4 chiffres)|Nuevo PIN (4 dígitos)|Neuer PIN (4 Ziffern)|Nuovo PIN (4 cifre)|Novo PIN (4 dígitos)|Новый PIN (4 цифры)|Yeni PIN (4 haneli)
Confirm PIN|تأكيد الرمز|Confirmer le PIN|Confirmar PIN|PIN bestätigen|Conferma PIN|Confirmar PIN|Подтвердите PIN|PIN'i onayla
PIN must be 4 digits|يجب أن يكون الرمز 4 أرقام|Le PIN doit comporter 4 chiffres|El PIN debe tener 4 dígitos|Der PIN muss 4 Ziffern haben|Il PIN deve avere 4 cifre|O PIN deve ter 4 dígitos|PIN должен состоять из 4 цифр|PIN 4 haneli olmalı
PINs do not match|الرمزان غير متطابقين|Les codes PIN ne correspondent pas|Los PIN no coinciden|PINs stimmen nicht überein|I PIN non coincidono|Os PINs não coincidem|PIN-коды не совпадают|PIN'ler eşleşmiyor
Save PIN|حفظ الرمز|Enregistrer le PIN|Guardar PIN|PIN speichern|Salva PIN|Salvar PIN|Сохранить PIN|PIN'i kaydet
Locked Live categories|فئات المباشر المقفلة|Catégories Live verrouillées|Categorías en vivo bloqueadas|Gesperrte Live-Kategorien|Categorie Live bloccate|Categorias ao vivo bloqueadas|Заблокированные категории эфира|Kilitli canlı kategoriler
Locked Movie categories|فئات الأفلام المقفلة|Catégories Films verrouillées|Categorías de películas bloqueadas|Gesperrte Film-Kategorien|Categorie Film bloccate|Categorias de filmes bloqueadas|Заблокированные категории фильмов|Kilitli film kategorileri
Locked Series categories|فئات المسلسلات المقفلة|Catégories Séries verrouillées|Categorías de series bloqueadas|Gesperrte Serien-Kategorien|Categorie Serie bloccate|Categorias de séries bloqueadas|Заблокированные категории сериалов|Kilitli dizi kategorileri
Show locked content now|إظهار المحتوى المقفل الآن|Afficher le contenu verrouillé maintenant|Mostrar contenido bloqueado ahora|Gesperrte Inhalte jetzt anzeigen|Mostra contenuti bloccati ora|Mostrar conteúdo bloqueado agora|Показать заблокированное сейчас|Kilitli içeriği şimdi göster
Change PIN|تغيير الرمز|Changer le PIN|Cambiar PIN|PIN ändern|Cambia PIN|Alterar PIN|Сменить PIN|PIN'i değiştir
Remove PIN|حذف الرمز|Supprimer le PIN|Quitar PIN|PIN entfernen|Rimuovi PIN|Remover PIN|Удалить PIN|PIN'i kaldır
Select shown|تحديد المعروض|Sélectionner l'affichage|Seleccionar mostrados|Angezeigte auswählen|Seleziona visibili|Selecionar exibidos|Выбрать показанные|Görüneni seç
Clear shown|إلغاء تحديد المعروض|Désélectionner l'affichage|Quitar mostrados|Angezeigte abwählen|Deseleziona visibili|Limpar exibidos|Снять выбор|Görüneni temizle
Save (~|حفظ (|Enregistrer (|Guardar (|Speichern (|Salva (|Salvar (|Сохранить (|Kaydet (
Connect to a playlist first|اتصل بقائمة أولاً|Connectez-vous d'abord à une playlist|Conéctate primero a una lista|Verbinde dich zuerst mit einer Playlist|Connettiti prima a una playlist|Conecte-se primeiro a uma lista|Сначала подключите плейлист|Önce bir listeye bağlanın
Movies and Series need an Xtream list|الأفلام والمسلسلات تحتاج قائمة Xtream|Les films et séries nécessitent une liste Xtream|Películas y series requieren una lista Xtream|Filme und Serien benötigen eine Xtream-Liste|Film e serie richiedono una lista Xtream|Filmes e séries exigem uma lista Xtream|Фильмам и сериалам нужен список Xtream|Film ve diziler için Xtream listesi gerekir
"""
