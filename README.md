# Zapp

Telefonun TV'nin kumandası olur. **Tek uygulama**, iki mod: ilk açılışta "Android TV" mi "Telefon" mu diye sorar.

## Nasıl çalışır

1. TV'de uygulamayı aç, **Android TV**'yi seç. Zapp'a **erişilebilirlik izni** ver (aşağıda). TV ekranında QR kod çıkar.
2. Telefonda uygulamayı aç, **Telefon**'u seç, kamerayla QR'ı tara. Telefon kumandaya dönüşür.
3. Eşleşme hem TV'de hem telefonda kayıtlıdır. Sonra:
   - Telefonda uygulamadan çıksan ya da yukarıdan kapatsan, tekrar açınca telefon kayıtlı TV'ye **kendiliğinden bağlanır**. (Uygulamayı telefondan silersen kayıt da silinir; QR'ı yeniden taraman gerekir.)
   - TV'nin interneti gitse ya da elektrik kesilip gelse, TV'deki servis **kendiliğinden yeniden başlar** (erişilebilirlik servisini Android sistemi açılışta başlatır). Telefon da bağlantı kopunca yeniden dener; TV'nin IP'si değişmişse mDNS ile TV'yi ağda yeniden bulur.

Telefon ve TV **aynı Wi-Fi/yerel ağda** olmalı. Ağ tamamen yokken kumanda çalışmaz; ağ gelince bağlantı kendiliğinden yenilenir.

## Neler var (v2)

- Açılış animasyonu, TV/telefon seçimi, QR eşleştirme
- Yön tuşları, OK, Geri, **Ana ekran**, **Son uygulama** (yedek yollu, aşağıya bak)
- Ses +/−, sessiz, oynat/duraklat/ileri/geri sarma tuşları, Ayarlar ve Wi-Fi kısayolu
- **Güç:** Uyut, güç menüsü, uyku zamanlayıcı, Uyandır (Wake-on-LAN)
- Dokunmatik yüzey, TV'de yazı yazma, sesle yazma
- TV'deki uygulamaları listeleme/açma, son kullanılanlar
- **Dosya gönderme:** telefondan TV'ye ve TV'den telefona, APK'yı TV'ye kurma. İnternet gerekmez.
- **TV ana ekranı düzenleyici:** şablon (Sinema = Apple TV tarzı, Alt çubuk = Samsung tarzı, Şerit = LG tarzı, Izgara), arka plan, kısayol ekle/sil/sırala (Ayarlar'ı istediğin yere koy). Telefondan kaydet, TV'de anında değişir.
- **Yılan oyunu:** TV'de açılır, telefon gamepad olur.
- **İnternetsiz çalışma:** yalnızca aynı yerel ağ (modem/hotspot) yeterli; telefon Wi-Fi'ye sabitlenir.
- Hızlı yeniden bağlanma: uygulamaya dönünce bağlantı beklemeden yenilenir.
- **TV hata kaydı:** TV'de "Kayıt" düğmesi ya da telefonda Cihazlar > "TV hata kaydını göster".

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
