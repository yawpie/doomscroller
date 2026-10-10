# Rencana fitur pointer melalui finger tracking

Tanggal: 10 Oktober 2026. Status: rencana, belum diimplementasikan atau diuji di perangkat.

## 1. Tujuan dan batas versi pertama

Menambahkan mode Pointer untuk menggerakkan kursor virtual menggunakan ujung telunjuk satu tangan. Cubitan ibu jari–telunjuk yang dikonfirmasi memulai sentuhan pada posisi kursor; sentuhan tetap ditekan selama kedua jari bersentuhan, lalu dilepas ketika cubitan dilepas. Cubitan singkat menjadi tap, sedangkan cubitan yang dipertahankan menjadi touch and hold. Targetnya layar Android, bukan kontrol mouse PC atau hover mouse native.

Mode Scroll yang ada tetap menjadi default. Pengguna memilih Scroll atau Pointer secara eksplisit; keduanya tidak menghasilkan aksi bersamaan. Mode gesture HYBRID/POSE/FLICK/CONTINUOUS tetap merupakan pilihan di dalam Scroll. Pointer tidak memakai gesture telunjuk/open hand/thumbs-up lama.

Versi pertama mencakup gerakan kursor, tap, touch and hold, latihan lokal, kalibrasi area gerak, dan penanganan kehilangan tracking. Hold tetap pada titik awal sentuhan; drag, scroll dua jari, double click, kontrol PC, dan dua tangan masuk backlog masa depan. Aplikasi target menentukan respons hold, misalnya menu konteks atau aksi tekan lama; tidak menjanjikan semua aplikasi merespons sama.

## 2. Kondisi project yang sudah diperiksa

| Fondasi saat ini | Implikasi untuk rencana |
| --- | --- |
| Android Kotlin, modul `:gesture` murni Kotlin dan `:app` untuk platform | Logika pointer dapat diuji tanpa kamera; integrasi Android tetap di app. |
| `HandLandmarkerEngine.kt`: model lokal, VIDEO, `setNumHands(1)`, 21 landmark | Model dan pipeline kamera dipakai ulang; tidak perlu model atau dependency baru. |
| Engine sudah mencerminkan x dengan `1 - x`; `HandPose` memvalidasi landmark | Pointer memakai koordinat domain itu, jangan melakukan mirror kedua kali. |
| `TrackingConfig`: default 15 FPS, dimensi inference maksimum 320 | Jadikan baseline pengukuran, bukan jaminan pointer mulus. |
| `GestureCameraService` mengalirkan hasil ke `GestureCoordinator` lalu `ScrollActionCoordinator` | Cabangkan berdasarkan mode kontrol pada pemilik sesi; jangan menjalankan dua alur aksi. |
| Tap di `swipeCoordinates` selalu memakai tengah content bounds | Klik pointer perlu membawa koordinat eksplisit, bukan mengubah semua tap lama. |
| `avoidOverlay` dapat menggeser kolom tap/swipe agar menghindari widget | Klik pointer harus ditolak jika tertutup widget, bukan dipindahkan ke lokasi lain. |
| Overlay sekarang merupakan widget kontrol yang menerima sentuhan | Kursor memerlukan view kecil terpisah yang tidak menerima sentuhan atau fokus. |
| Session generation, watchdog, target policy, lock guard, satu dispatch tanpa backlog | Semua guard tetap berlaku untuk pointer. Hold memerlukan kepemilikan satu rangkaian sentuhan, bukan hanya satu callback dispatch. |
| Practice milik Activity, tanpa adapter accessibility; kamera dihandoff secara terserialisasi | Pointer practice harus memakai alur lokal yang sama dan tidak mengirim tap eksternal. |
| Settings memiliki draft/Apply/Discard; Apply mem-pause sesi | Tambahkan mode dan konfigurasi pointer ke alur ini, pertahankan preference lama. |

Sumber lokal: `README.md`, `AGENTS.md`, `docs/architecture.md`, `docs/development.md`, serta kelas yang disebutkan di tabel. Temuan merupakan inspeksi kode, bukan bukti performa kamera langsung.

## 3. Perilaku pengguna yang direncanakan

1. Pilih Pointer lalu buka Practice. Kamera hanya berjalan setelah Start practice.
2. Ikuti kalibrasi dua sudut area gerak yang nyaman, menggunakan tombol layar untuk mengambil sampel yang stabil. Validasi luas minimum, urutan sudut, dan kestabilan; jangan memakai cubitan untuk kalibrasi awal.
3. Gerakkan telunjuk untuk mengarahkan kursor pada target lokal. Sentuhkan telunjuk ke ibu jari: cubitan singkat lalu lepas untuk tap, pertahankan cubitan untuk touch and hold, kemudian pisahkan kedua jari untuk melepas sentuhan. Feedback kursor membedakan tracking, konfirmasi cubitan, sedang menekan, dan pelepasan.
4. Setelah latihan, pengguna bisa mengaktifkan sesi eksternal. Kamera, persetujuan accessibility, layanan accessibility, dan izin tampil di atas aplikasi lain harus siap. Kursor membutuhkan izin overlay walaupun widget kontrol opsional dimatikan.
5. Pause mengakhiri sentuhan aktif, melepas kamera, dan menyembunyikan kursor. Stop juga menghapus resource overlay. Resume selalu eksplisit dan memerlukan kedua jari terpisah sebelum cubitan baru.

Kalibrasi MVP berlaku selama sesi/practice yang berjalan. Pause, perubahan orientasi, pergantian mode, atau perubahan area layar membatalkannya. Untuk sesi eksternal, lakukan kalibrasi lokal sebelum mengaktifkan aksi eksternal; kirim hasil valid bersama permintaan Start yang baru melalui handoff yang ada. Kalibrasi tidak perlu disimpan permanen pada versi pertama. Penolakan izin tidak memulai kamera atau menghasilkan tap.

## 4. Logika tracking, tap, dan hold

- Sumber pointer adalah landmark 8 (ujung telunjuk); cubitan memakai jarak landmark 4 dan 8. Normalisasikan jarak cubitan terhadap ukuran telapak, bukan pixel kamera. Jangan memakai handedness confidence sebagai confidence setiap jari.
- Mapping absolut: `u = clamp((x - left) / (right - left), 0, 1)` dan rumus serupa untuk y, lalu petakan ke content bounds layar. Gunakan batas pixel terakhir yang valid; hit-test memakai titik pusat kursor, bukan pojok view.
- Ukur jarak geometri dalam koordinat dengan rasio aspek gambar upright agar jarak x/y tidak terdistorsi; jika rasio ini belum tersedia pada hasil tracking, teruskan metadata kecil dari pipeline. Tipe Android/MediaPipe tetap di luar `:gesture`.
- Pakai pola smoothing scalar yang sudah tersedia, dengan state x dan y terpisah. Reset saat tracking hilang atau kalibrasi berubah. Tambahkan dead zone kecil hanya bila pengukuran menunjukkan jitter masih mengganggu.
- Gerakan memerlukan tangan valid dan telunjuk cukup terentang; tentukan secara geometri sendi, bukan label POINTING_UP yang bergantung arah. Saat cubitan dimulai, aturan telunjuk tidak boleh membatalkan klik yang sedang dikonfirmasi.
- State minimal: belum dikalibrasi, tracking, konfirmasi cubitan, sentuhan aktif, sedang melepas, menunggu jari terpisah, tracking hilang. Memulai sesi dengan jari sudah mencubit tidak menghasilkan sentuhan.
- Saat ambang cubitan mulai terlewati, bekukan kandidat titik sentuhan. Setelah cubitan stabil selama durasi dan jumlah sampel minimum, kirim satu awal sentuhan jika sampel segar dan guard lolos. Pertahankan kontak pada titik itu selama cubitan bertahan, termasuk jika tangan bergerak. Pointer visual tetap pada titik kontak sampai sentuhan berakhir; drag belum termasuk MVP.
- Pelepasan yang stabil mengakhiri sentuhan yang sama. Jangan mengirim SINGLE_TAP tambahan saat release: down/up singkat sudah menjadi tap, dan kontak yang lebih lama memungkinkan aplikasi target mengenali long press. Tidak perlu menunggu ambang hold buatan sebelum touch-down; durasi konfirmasi cubitan tetap menambah latency awal dan perlu dituning.
- Gunakan ambang masuk/keluar berbeda (hysteresis), konfirmasi berbasis timestamp, dan maksimum gap sampel. Menahan cubitan tidak mengulang touch-down atau tap. Cubitan dibatalkan oleh kehilangan tangan, gap panjang, perubahan target aplikasi, pause, lock, rotasi, mode, atau interaksi widget. Bila kontak sudah aktif, pembatalan wajib menjalankan terminasi sentuhan sebelum state direset.
- Awal sentuhan ditolak ketika busy atau target tidak aman; jangan antrekan, ulangi otomatis, atau pindahkan koordinatnya. Selama hold, rangkaian itu memiliki jalur dispatch secara eksklusif; aksi manual/scroll/tap lain tidak boleh menyela. Guard yang gagal menghentikan hold, tetapi terminasi kontak lama tidak boleh tertolak hanya karena session sudah inactive atau executor masih dianggap busy.
- Ketika tangan hilang, segera larang klik dan sembunyikan kursor. Watchdog juga menangani berhentinya callback. Reacquire membutuhkan sampel stabil dan pelepasan sebelum cubitan baru; perpindahan besar membatalkan kandidat klik. Satu tangan yang terdeteksi bukan jaminan identitas tangan tetap jika tangan lain masuk frame.

Nilai smoothing, ambang cubitan, konfirmasi, dan stabilitas adalah parameter eksperimen terarah. Tetapkan default setelah uji perangkat, jangan membuka banyak slider sebelum kebutuhan terbukti.

### Kontrak sentuhan berkelanjutan di Android

- Gunakan `GestureDescription.StrokeDescription(..., willContinue = true)` untuk awal kontak stasioner, kemudian `continueStroke` untuk mempertahankan kontak yang sama. Akhiri dengan continuation `willContinue = false` pada titik akhir yang sama. API continuation tersedia mulai API 26, sesuai minimum Android project.
- Callback completion sebuah segmen tidak berarti jari sudah terangkat ketika `willContinue = true`. Simpan stroke terakhir dan status kontak dalam pemilik dispatch, dengan hanya satu segmen berjalan. Jadwalkan continuation terikat watchdog/freshness, bukan satu dispatch per frame kamera; tidak ada backlog atau tap berulang.
- Pelepasan selama segmen berjalan ditandai sebagai permintaan terminal prioritas. Setelah callback segmen itu, kirim akhir kontak, bukan continuation hold berikutnya. Jangan menghapus referensi stroke, melepas pemilik dispatch, atau mengizinkan sesi baru sebelum terminasi selesai atau jalur kegagalan tercatat.
- Kehilangan tracking/permission, watchdog timeout, dispatch rejection/cancellation, service disconnect, dan teardown harus memiliki jalur terminasi eksplisit. API tidak menyediakan operasi cancel langsung; detail fallback ketika continuation gagal harus dibuktikan di instrumentasi, bukan diasumsikan selesai karena state lokal direset. Jangan mengirim tap pengganti untuk memaksa pelepasan karena dapat mengaktifkan target lain.
- Batasi panjang setiap segmen dan uji batas durasi gesture platform. Tidak memakai satu stroke sangat panjang yang mengabaikan pelepasan jari. Durasi hold mengikuti cubitan selama tracking segar, bukan timer long press tetap; watchdog membatasi kegagalan tracking, bukan durasi penggunaan yang normal.

## 5. Lokasi perubahan dan tanggung jawab

| Area | Perubahan yang direncanakan |
| --- | --- |
| `gesture/.../gesture` | Tambahkan model keputusan pointer, mapper area kalibrasi, dan detector cubitan kecil dengan timestamp eksplisit. Reuse smoothing yang cocok; jangan ubah kontrak HandPose menjadi multi-hand. |
| `settings/NativeSettings.kt`, `SettingsStore.kt`, draft settings | Tambahkan pilihan kontrol Scroll/Pointer, default Scroll, validasi dan persistence konfigurasi yang perlu. Periksa reset group dan seluruh konsumen enum/settings. |
| `session/GestureCameraService.kt`, `SessionStore.kt` | Pilih satu alur kontrol, publikasikan feedback pointer yang diperlukan, reset/cancel pada setiap jalur teardown dan perubahan target. Jaga generation token, watchdog, dan handoff kamera. |
| `scrolling` dan `accessibility` | Tambahkan kontrak kecil begin/continue/end untuk sentuhan pointer pada koordinat tervalidasi, memakai kepemilikan executor bersama. Center tap lama tetap. Jangan mengubah hold menjadi deretan tap atau menerapkan ulang seluruh dispatcher. |
| `GestureAccessibilityService.kt`, `SwipeConfig.kt` | Pakai sumber bounds yang sama untuk tampilan dan dispatch; validasi posisi sebelum begin. Pertahankan target/lock/interaction/in-flight guard, tambahkan pemilik rangkaian hold dan terminasi prioritas. Pointer tidak memakai pemindahan kolom `avoidOverlay`. |
| `overlay` | Kursor view kecil terpisah, tidak fokus dan tidak menerima touch; kontras baik, anchor pusat konsisten, update main thread, cleanup idempoten. Jangan jadikan kursor sendiri sebagai area klik terlarang. |
| `MainActivity.kt`, `presentation` | Pilihan mode, status readiness, instruksi cubitan, kalibrasi, dan feedback. Izin/persistence tetap di entry point. Pertahankan Apply/Discard, rotasi, teks besar, light/dark. |
| `practice/PracticeController.kt`, feedback/screen | Reuse kamera practice dan detector pointer; tampilkan grid target lokal, hit/miss, kalibrasi, dan target latihan hold dengan feedback pressed/released serta durasi. Simulasikan begin/end lokal tanpa adapter aksi eksternal. |
| Unit/instrumentation tests, fixtures, docs | Tambahkan kasus pointer sendiri; fixture parity lama tetap. Perbarui README, architecture, permissions, dan matriks device validation sesuai perilaku final. |

Sebelum implementasi, telusuri semua caller simbol yang diubah, termasuk dispatch manual, practice, settings reset/draft, diagnostic, exhaustive enum branches, dan test fakes. Graph kelas service tidak menampilkan incoming caller saat inspeksi ini; wiring intent/manifest tetap perlu diperiksa langsung. Tidak perlu modul, framework DI, atau dependency baru.

## 6. Urutan implementasi dengan gerbang penerimaan

| Tahap | Hasil | Syarat lanjut |
| --- | --- | --- |
| P1 — Logika dan latihan lokal | Mapper, cubitan, smoothing, kalibrasi, grid practice; belum ada tap eksternal | Unit test edge case lolos; latihan langsung menunjukkan arah dan anchor benar. |
| P2 — Kursor eksternal tanpa klik | Routing mode, readiness overlay, view kursor dan lifecycle | Kursor mengikuti jari; pause/stop/lock/permission loss membersihkan tampilan; gesture Scroll tidak berjalan di Pointer. |
| P3 — Tap dan touch and hold | Koordinat eksplisit, stroke berkelanjutan, dan terminasi melalui executor bersama | Tap dan hold dikenali pada posisi yang ditampilkan; release tidak menambah tap; seluruh jalur stop/failure mengakhiri kontak; target terlarang diblokir tanpa pemindahan atau backlog. |
| P4 — Integrasi dan penerimaan | Settings lengkap, disclosure/instruksi, regression, uji perangkat | Kriteria di bawah terpenuhi; dokumentasi membedakan pengujian yang dijalankan dan yang skip. |

Kerjakan sebagai perubahan kecil terpisah. Jangan mengoptimasi kamera, menaikkan FPS, atau mengganti algoritma smoothing sebelum P1/P2 menghasilkan bukti hambatan.

## 7. Validasi

**Unit test:** mapping sudut/tengah/batas, area invalid, metadata aspek dan mirror; jitter/reacquire; timestamp lama/future/duplikat; pinch tahan/lepas/release awal; tangan hilang saat pinch; target berubah sebelum release; busy tanpa antrean; pointer mode tidak menghasilkan scroll; center tap lama tetap sama; settings lama tanpa key baru memuat default Scroll.

**Instrumentasi:** view kursor tidak menangkap tap; koordinat render sama dengan titik tap pada test activity; titik di widget ditolak; content insets dan rotasi; pause/stop/disconnect; kehilangan izin; practice tidak mengirim aksi eksternal. Periksa conditional skips dan gunakan perangkat uji eksplisit, tanpa mengubah izin perangkat pribadi secara otomatis.

**Khusus hold:** unit test begin sekali, release sekali, hold lama tanpa repeated down, hysteresis/gap, startup sudah pinch, dan release saat busy; instrumentasi merekam satu ACTION_DOWN, tidak ada ACTION_UP di antara segmen, lalu satu ACTION_UP setelah release normal. Verifikasi long-press callback, titik tetap saat tangan bergerak, tidak ada tap tambahan setelah hold, aksi manual tertolak selama kontak aktif, dan kontak berakhir saat pause/stop/loss/target change/lock/disconnect. Uji cancellation/rejection/failure continuation secara terpisah; terminasi abnormal boleh berupa cancellation platform, tidak harus normal ACTION_UP, tetapi tidak boleh meninggalkan kontak aktif atau men-trigger tap baru.

**Regresi/build:** jalankan rangkaian di `docs/development.md` sebelum commit implementasi: unit test kedua modul, lintDebug, assembleDebug, assembleRelease, assembleDebugAndroidTest, lalu `git diff --check`. Kompilasi instrumentasi tidak dianggap eksekusi di perangkat. Fixture golden Scroll lama tidak diganti untuk menyembunyikan regresi.

**Penerimaan fisik yang diusulkan, belum merupakan hasil:**

- Grid sembilan target berukuran 48dp, tiga percobaan per target setelah kalibrasi: minimal 25 dari 27 klik mengenai target dan tidak ada klik ganda untuk satu cubitan.
- Tangan diam tanpa cubitan selama 30 detik: tidak ada sentuhan tidak sengaja. Cubitan ditahan selama 10 detik: satu kontak berkelanjutan, tidak ada tap/down berulang, lalu kontak berakhir saat jari dipisahkan. Respons berulang yang memang dibuat aplikasi target saat tombol ditahan dibedakan dari pengulangan dispatch aplikasi ini.
- Pada target latihan long press, hold melewati timeout target memicu respons tekan lama; cubitan singkat hanya tap. Setelah hold dilepas tidak ada klik tambahan. Catat latency release-ke-touch-up dan tetapkan batas penerimaan dari panjang segmen serta konfirmasi pelepasan yang dipilih.
- Lepas tangan dari frame, tutup jari, pindah aplikasi, kunci layar, tekan Pause/Stop, dan ubah orientasi saat cubitan: tidak ada tap tambahan atau kontak tertahan; kursor tidak tertinggal. Long press yang sudah dipicu aplikasi target tidak dapat dijanjikan dibatalkan oleh pelepasan.
- Catat FPS aktual, latency hasil-ke-render, jitter saat diam, error posisi, serta jumlah kehilangan tracking. Target sementara latency hasil-ke-render p95 maksimal 100ms; ini bukan latency kamera-ke-layar. Ukur latency ujung-ke-ujung terpisah dengan observasi/video eksternal bila diperlukan.
- Uji penggunaan 15 menit pada perangkat fisik: catat panas, konsumsi baterai, drop FPS, dan rasa lelah tangan. Angka batas performa final disepakati dari baseline perangkat uji.
- Uji portrait/landscape, navigation gesture/tiga tombol, cutout, pencahayaan baik/redup, tangan kiri/kanan, dan sekurangnya dua aplikasi target. Prioritaskan versi Android/OEM perangkat pengguna.

## 8. Risiko dan keputusan teknis yang perlu dibuktikan

- 15 FPS dan inference 320 mungkin cukup untuk pointer dasar tetapi belum terbukti untuk target kecil. Smoothing berlebihan mengurangi jitter sekaligus menambah keterlambatan.
- Cubitan menggerakkan ujung telunjuk dan bisa menutupi landmark. Posisi beku mencegah pergeseran titik klik, tetapi ambang dan konfirmasi tetap memerlukan kalibrasi perangkat nyata.
- Mapping ke seluruh layar dari seluruh frame menyulitkan jangkauan; area kalibrasi yang nyaman merupakan bagian MVP, bukan tambahan kosmetik.
- Application overlay dapat memengaruhi penyaluran sentuhan pada Android modern. Mulai dengan view kursor kecil non-touchable, atur transparansi sesuai aturan platform, dan verifikasi sentuhan menembus overlay pada perangkat; jika gagal, evaluasi accessibility overlay sebelum memperluas fitur.
- Sistem dispatch mengirim sentuhan, bukan hover mouse. Completion callback tidak membuktikan target menerima klik atau kontak berakhir. Segmen yang sudah diserahkan ke Android mungkin selesai setelah Pause; terminasi hold menunggu jalur platform yang tersedia sehingga UI tidak boleh menjanjikan pembatalan instan. Long press yang sudah memicu aksi target tidak bisa diurungkan secara umum.
- Tidak membaca konten layar atau mengawasi seluruh physical touch. Klik accessibility dapat mengganggu sentuhan manual; pertahankan guard interaksi yang ada dan jelaskan keterbatasan ini dalam instruksi penggunaan.

## 9. Backlog masa depan

Setelah pointer satu tangan dengan touch and hold lulus penerimaan: pertimbangkan scroll dua jari, double click, drag saat cubitan tetap ditahan, dan kalibrasi tersimpan bila diperlukan. Dukungan dua tangan adalah rencana terpisah: satu tangan sebagai pointer dan lainnya sebagai perintah, memerlukan multi-hand domain output, identitas antarsampel, arbitration, dan pengukuran performa ulang. Tidak menaikkan `numHands` atau membuat abstraksi multi-hand dalam implementasi MVP.

## Referensi platform

- [MediaPipe Hand Landmarker Android](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android): landmark, VIDEO tracking, dan konfigurasi jumlah tangan.
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): dispatchGesture dan layanan accessibility.
- [StrokeDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription): `willContinue` dan `continueStroke` untuk kontak berkelanjutan serta pelepasan.
- [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE): aturan penyaluran touch dan overlay non-touchable.

Rencana ini tidak menyatakan fitur telah tersedia. Bukti build/test terdahulu tidak dipakai sebagai bukti kelayakan pointer.
