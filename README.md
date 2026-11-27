# Zapp

Telefonun TV'nin kumandası olur. **Tek uygulama**, iki mod: ilk açılışta "Android TV" mi "Telefon" mu diye sorar.

## Nasıl çalışır

1. TV'de uygulamayı aç, **Android TV**'yi seç. Zapp'a **erişilebilirlik izni** ver (aşağıda). TV ekranında QR kod çıkar.
2. Telefonda uygulamayı aç, **Telefon**'u seç, kamerayla QR'ı tara. Telefon kumandaya dönüşür.
3. Eşleşme hem TV'de hem telefonda kayıtlıdır. Sonra:
   - Telefonda uygulamadan çıksan ya da yukarıdan kapatsan, tekrar açınca telefon kayıtlı TV'ye **kendiliğinden bağlanır**. (Uygulamayı telefondan silersen kayıt da silinir; QR'ı yeniden taraman gerekir.)
   - TV'nin interneti gitse ya da elektrik kesilip gelse, TV'deki servis **kendiliğinden yeniden başlar** (erişilebilirlik servisini Android sistemi açılışta başlatır). Telefon da bağlantı kopunca yeniden dener; TV'nin IP'si değişmişse mDNS ile TV'yi ağda yeniden bulur.

Telefon ve TV **aynı Wi-Fi/yerel ağda** olmalı. Ağ tamamen yokken kumanda çalışmaz; ağ gelince bağlantı kendiliğinden yenilenir.

## Neler var (v3)
- QR ile eşleşme, kumanda, dokunmatik, klavye (sesle yazma dahil), uygulama listesi
- **Dosya gönderme** (telefon → TV, TV → telefon, APK kurma). Alınan dosya TV'de ayrıca Download/Zapp klasörüne kopyalanır
- **Duvar kâğıdı**: hazır 8 tasarım ya da telefondan seçtiğin fotoğraf TV'ye gider
- **Admin paneli**: TV'ye bağlı cihazları gör, bağlantısını kes, engelle, yeni bağlantıya kilitle, eşleşmeleri sıfırla, yönetici kodunu değiştir
- Animasyonlu açılış + açılış sesi
- Yılan oyunu: 3 mod (Klasik/Sınırsız/Engelli), altın yem, güçlendirici, seviye, kombo, ses
- Güç/uyku/medya tuşları, Wake-on-LAN, Wi‑Fi'ye bağlanma

(v3'te "Son uygulamalar" ve "TV Ekranı düzenleme" kaldırıldı.)

## v3.2
- Admin paneli router arayüzü gibi: üstte Ağ—TV—Müşteriler şeması, 4 kutu (Cihazlar, Güvenlik, Ağ, Sistem), sunucuyu yeniden başlat
- Yeni açılış: beyaz nokta düşer, havada donar, mor ışık, internet simgesine dönüşür (sesli)
- YouTube gibi uygulamalarda yön tuşu çalışmazsa: sağ/sol = ileri/geri sar, diğerleri fare imleciyle

## v3.1 düzeltmeleri
- Dokunmatik sekmesinde gerçek **fare imleci** (kaydır, dokun = tıkla, basılı tut = uzun tık, kaydır düğmeleri)
- Yazı yazma: önce klavye gibi doğrudan giriş (Android 13+), olmazsa SET_TEXT, olmazsa yapıştırma; YouTube gibi uygulamalarda dener
- Yılan menüsü ve ekranı her karede tazeleniyor (mod seçimi artık hareket eder)
- Silinen dosya Download/Zapp kopyasıyla birlikte silinir
- Admin gizli kodu bağlıyken de çalışır

## Admin
- Telefonu TV'ye bağlı değilken kumandada sırayla: **OK OK OK, ▲ ▲, ▼ ▼, OK** → yönetici isteği gönderilir; TV'ye bağlanınca Admin sekmesi animasyonla açılır.
- İlk yönetici olan telefonun cihaz kimliği ve IP'si TV'ye kaydedilir; sonraki bağlantılarda otomatik yöneticidir.
- Varsayılan kod herkesin okuyabildiği bu depoda yazıyor. **Admin → "Yönetici kodunu değiştir" ile hemen değiştir.**
- Yönetici gücü yalnızca Zapp bağlantılarını kapsar (TV'nin geri kalanını yönetmez). Zaten bağlanabilmek için TV'deki QR'a sahip olmak gerekir.

## TV'ye APK'yı indirmek
Dosya yöneticisi/Chrome ile: GitHub Releases → "latest" → Zapp.apk. TV'de klavye bozuksa telefondaki Klavye sekmesiyle adresi yaz. **Dosya/duvar kâğıdı/admin için TV'deki Zapp da v3 olmalı.**

## Dürüst notlar

- Ana ekran / Son uygulama: bazı Android TV'ler sistem eylemini "yapıldı" deyip yapmaz. Bu durumda Zapp 1 sn içinde yedek yola geçer (Ana ekran: ana ekran uygulamasını açar; Son uygulama: bir önceki uygulamaya geçer, tekrar basınca geri döner).
- "Ana ekran yap": Zapp ana ekranını Ana ekran tuşuna atar. İlk basışta TV "Her zaman" diye sorabilir. Bazı TV'ler üçüncü taraf ana ekranı kabul etmez; "Eskisine dön" ile geri alınır.
- Uyut: TV modeline göre uykuya alır ya da almaz; olmazsa Güç menüsünü kullan. Uyandır: TV'nin MAC adresi ve TV ayarlarında "Wi-Fi/LAN ile uyandır" açık olmalı (MAC'i bazen TV vermez, telefondan elle girilir).
- Şablonlar Apple/Samsung/LG'nin kendi arayüzünü değil, TV'ne kurulan Android TV ana ekranının **görünümünü** taklit eder. Samsung/LG TV'lerde Zapp'ın TV modu çalışmaz (Android TV değiller).
- Kanal +/− ve TV'yi tamamen kapatma normal bir uygulamayla yapılamaz.
- Erişilebilirlik izni kendiliğinden kapanıyorsa TV'deki "Kayıt" ekranı nedenini gösterir; bana gönder.

## Güncelleme (Termux)

    cd ~/Zapp
    git add -A && git commit -m "v2" && git push
    gh run watch
    gh run download -n Zapp-apk

## Derleme (APK üretme)

Bu proje **henüz derlenip test edilmedi**; yazıldığı ortamda Android SDK yoktu. İlk derlemede küçük hatalar çıkabilir.

### Yol 1: Android Studio (en kolayı)
1. Android Studio'yu kur, `Zapp` klasörünü **Open** ile aç, Gradle eşitlemesini bekle.
2. **Build > Build APK(s)**. APK `app/build/outputs/apk/debug/app-debug.apk` içinde çıkar.

### Yol 2: Bilgisayar yok, GitHub var
1. Klasörü bir GitHub deposuna yükle.
2. **Actions > APK derle > Run workflow**.
3. Bitince **Zapp-apk** dosyasını indir.

### Yol 3: Komut satırı
Android SDK ve JDK 17 kuruluysa: `gradle assembleDebug`

## Kurulum

- **Telefon:** APK'yı aç, "bilinmeyen kaynaklardan yükle" izni ver.
- **TV:** APK'yı USB bellek ya da "Send Files to TV" gibi bir uygulamayla aktar ve bir dosya yöneticisiyle aç. Ya da bilgisayardan: `adb connect <tv-ip>` sonra `adb install app-debug.apk`.

## TV'de erişilebilirlik izni

Ayarlar > Cihaz tercihleri > Erişilebilirlik > **Zapp** > Açık. (Uygulama "Ayarları aç" düğmesiyle buraya götürür.)

Android 13+ cihazlarda yan yüklenen uygulamalar için izin gri görünürse: Ayarlar > Uygulamalar > Zapp > ⋮ > **Kısıtlı ayarlara izin ver**, sonra tekrar dene.

Yön tuşları Android 13+ TV'lerde doğrudan tuş olayı olarak gider. Daha eski TV'lerde odak, ekrandaki öğeler arasında elle taşınır; bazı uygulamalarda (özellikle oyun ve video oynatıcıları) bu daha az güvenilirdir.

## Güvenlik

- TV'deki QR, TV'ye özel rastgele bir anahtar içerir. Yalnızca bu anahtarı bilen telefon komut gönderebilir.
- Bağlantı yerel ağda şifrelenmemiş WebSocket (`ws://`) ile yapılır. Ağındaki herkes güvenilir değilse (kafe, otel Wi-Fi'si) kullanma.
- Erişilebilirlik izni güçlü bir izindir; Zapp bunu yalnızca gelen kumanda komutlarını uygulamak için kullanır.

## Proje yapısı

- `ZappAccessibilityService.kt`: TV'de kalıcı çalışan servis, WebSocket sunucusu ve komutları uygulayan kod
- `ZappServer.kt`: TV'deki WebSocket sunucusu ve anahtar doğrulama
- `RemoteClient.kt`: telefondaki bağlantı, otomatik yeniden bağlanma ve mDNS ile TV bulma
- `FileXfer.kt`: dosya aktarımı · `Launcher.kt`: TV ana ekranı · `Game.kt`: Yılan · `Diag.kt`: kayıt
- `TvScreens.kt`, `PhoneScreens.kt`, `PhoneMore.kt`, `Splash.kt`, `Ui.kt`: ekranlar ve animasyonlar
