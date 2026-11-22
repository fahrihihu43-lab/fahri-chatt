# Fahri Chat APK (izin mic, kamera, lokasi, notifikasi, galeri jalan)

Masalah izin di APK terjadi karena pembungkus WebView biasa tidak meneruskan izin ke Android. Project ini sudah memperbaikinya.

## Cara bikin APK (tanpa Android Studio)
1. Buat repo baru di github.com, lalu upload SEMUA isi folder ini (termasuk folder .github) lewat Add file > Upload files (mode desktop di browser HP).
2. Buka tab Actions > Build APK > Run workflow. Tunggu 3-5 menit.
3. Buka run yang selesai, unduh artifact FahriChat-APK, ekstrak, lalu pasang app-debug.apk.

## Cara lain
Buka folder ini di Android Studio, lalu Build > Build APK(s).

Ganti file app/src/main/assets/fahri-chat.html bila ada versi baru, lalu bangun ulang.
Uninstall APK lama dulu supaya izin mulai dari awal.
