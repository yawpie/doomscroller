# Rencana pengaturan aksi untuk setiap pose tangan

Tanggal: 1 November 2026 (Asia/Makassar).
Status: usulan implementasi, belum dikerjakan atau diuji.

Keputusan pengguna, 1 November 2026: profil arah dan kombinasi jari tidak perlu aktif bersamaan untuk sekarang. Profil jumlah jari 1-5 diganti dengan tiga pose: telunjuk saja, jari tengah saja, dan telunjuk + tengah bersamaan, masing-masing memiliki pilihan aksi sendiri. Katalog yang diperlukan adalah scroll, tap, Back, dan continuous scroll selama pose dipertahankan. Pause/Stop tetap tersedia melalui kontrol sesi existing, bukan sebagai pilihan mapping pose versi ini.

## 1. Tujuan

Pengguna dapat memilih apa yang dilakukan sebuah pose melalui Settings, mencoba hasilnya di Practice, lalu menerapkan pengaturan untuk mengontrol aplikasi lain. Pengaturan mencakup menunjuk atas/bawah/kiri/kanan, tangan terbuka, tangan dikepal, serta tiga kombinasi telunjuk dan jari tengah. Fitur tidak lagi menyediakan hitungan umum satu sampai lima jari.

Fitur berlaku pada Control mode **Scroll**, untuk recognition mode **POSE** dan **HYBRID**. Mode POINTER tetap khusus aim/pinch/hold/drag. CONTINUOUS dan FLICK tetap memakai detektor gerakan masing-masing; pilihan mapping disimpan tetapi tidak aktif pada kedua mode tersebut. Settings dan Gestures wajib menjelaskan kapan mapping aktif tanpa mengganti mode pengguna secara diam-diam.

Dokumen ini merencanakan perubahan, bukan menyatakan bahwa pengenalan pose baru atau akurasinya sudah tersedia.

## 2. Kondisi implementasi saat ini

| Bagian | Temuan dari kode | Konsekuensi |
| --- | --- | --- |
| `Models.kt` | `RecognizedHandPose` berisi POINTING_UP, POINTING_DOWN, NEUTRAL, THUMBS_UP, CLOSED_HAND, UNKNOWN | Belum ada arah horizontal atau identitas kombinasi telunjuk/tengah. |
| `HandPoseClassifier.kt` | Mengklasifikasi landmark secara geometri; NEUTRAL memeriksa empat jari panjang, bukan lima jari lengkap | Label tangan terbuka saat ini tidak boleh dianggap deteksi lima jari yang sudah akurat. |
| `HandPoseClassifier.kt` | CLOSED_HAND saat ini ditentukan oleh empat jari panjang yang melengkung | Pose kepalan baru memerlukan definisi ibu jari yang jelas, bukan sekadar memakai label lama. |
| `HandPoseDetector.kt` | Confirmation, cooldown, latch dan rearm; open hand satu kali per penampilan | Mapping baru harus memisahkan identitas pose dari kebijakan pemicu aksi. |
| `ScrollActionCoordinator.kt` | Mapping pose ke aksi masih berupa `when` tetap | Ganti dengan resolver bersama, tanpa menggandakan aturan di service dan Practice. |
| `ScrollAction.kt` / `SwipeConfig.kt` | FORWARD, BACKWARD, SINGLE_TAP, DOUBLE_TAP; swipe vertikal | Pose kiri/kanan dapat dipetakan ke aksi yang ada tanpa membuat swipe horizontal. |
| `PracticeFeedback.kt` | Mapping pose ditulis ulang untuk sampel lokal | Practice wajib memakai resolver yang sama dengan sesi eksternal. |
| `NativeSettings.kt`, `SettingsStore.kt`, `SettingsDraft.kt` | Snapshot immutable, SharedPreferences `gesture_settings`, draft Apply/Discard | Tambahkan field dan key baru, pertahankan preference, nilai lama dan lifecycle. |
| `GestureCameraService.kt` | Cabang Scroll memanggil `gestures.process` lalu `actions.process` secara langsung | Wiring tidak cukup hanya mengubah `SessionRecognition`; telusuri jalur aktual di service. |

## 3. Keputusan tentang pose yang bertumpang tindih

### 3.1 Profil arah dan profil kombinasi jari

Telunjuk menunjuk ke atas juga merupakan satu jari terbuka. Menaruh dua aksi independen pada bentuk tangan yang sama membuat hasil tidak pasti. Sesuai keputusan pengguna, versi pertama menggunakan **dua profil pengenalan yang saling eksklusif**:

| Profil | Pose yang dapat memicu aksi |
| --- | --- |
| Arah telunjuk | Menunjuk atas, bawah, kiri, kanan; tangan terbuka; kepalan; thumbs-up lama |
| Kombinasi jari | Telunjuk saja, jari tengah saja, telunjuk + tengah; tangan terbuka; kepalan; thumbs-up lama |

Semua pilihan mapping tersedia di Settings dalam kelompok masing-masing. Pengguna memilih satu profil aktif; kelompok lain diberi penanda tidak aktif dan tetap dapat diedit untuk pemakaian berikutnya. Tidak ada fallback silang profil: pose arah yang dinonaktifkan tidak berubah menjadi aksi telunjuk saja.

Profil ini berbeda dari Control mode Scroll/Pointer dan recognition mode POSE/HYBRID. UI memakai istilah berbeda dan penjelasan singkat agar ketiganya tidak tertukar.

### 3.2 Pose bersama antarprofil

Tangan terbuka, kepalan dan thumbs-up tersedia di kedua profil dengan identitas dan binding bersama per pose. Tampilkan kelompok **Pose umum** satu kali di Settings, terpisah dari kelompok arah dan kombinasi. Mengganti profil tidak menggandakan atau mengubah mapping pose umum. Tidak ada lagi baris hitungan 5 jari; tangan terbuka tetap didefinisikan sebagai kelima jari terbuka.

Membedakan lima jari rapat dan telapak dengan jari direnggangkan merupakan fitur lain yang memerlukan bukti pengenalan. Tidak dimasukkan ke versi pertama.

### 3.3 Definisi kombinasi jari yang didukung

Versi pertama memakai bentuk eksplisit yang ditampilkan dalam ilustrasi dan teks:

| Pose | ID usulan | Bentuk yang didukung |
| --- | --- | --- |
| Telunjuk saja | INDEX_ONLY | Telunjuk terbuka; ibu jari, tengah, manis dan kelingking terlipat |
| Jari tengah saja | MIDDLE_ONLY | Tengah terbuka; ibu jari, telunjuk, manis dan kelingking terlipat |
| Telunjuk + tengah | INDEX_MIDDLE | Telunjuk dan tengah terbuka pada tangan yang sama; ibu jari, manis dan kelingking terlipat |
| Tangan terbuka | OPEN_HAND | Semua lima jari terbuka; pose umum, bukan event kombinasi |
| Kepalan | CLOSED_HAND | Empat jari panjang terlipat; ibu jari menutup/berdekatan dengan kepalan, tidak memanjang seperti thumbs-up |

INDEX_ONLY, MIDDLE_ONLY dan INDEX_MIDDLE adalah tiga binding independen, bukan dua boolean pemicu. Contoh: telunjuk saja = scroll berikutnya sekali; tengah saja = Back; keduanya = continuous scroll berikutnya. Contoh ini bukan default. Saat keduanya terbuka, hanya aksi INDEX_MIDDLE yang dipilih, tidak mengirim aksi INDEX_ONLY atau MIDDLE_ONLY dan tidak menggabungkan ketiga aksi.

Orientasi jari tidak mengubah binding pada profil kombinasi. Posisi telunjuk dan tengah yang rapat maupun membentuk V boleh diterima selama masing-masing jari memenuhi geometri terbuka. Bukan sekadar menghitung dua jari: pasangan tengah + manis tidak boleh dianggap INDEX_MIDDLE. Pose lain, misalnya tiga/empat jari terbuka atau hanya kelingking, belum didukung dan tidak boleh ditebak sebagai pose terdekat.

Evaluasi identitas jari sebelum mengeluarkan satu hasil kanonis: kombinasi harus persis memenuhi keadaan terbuka/terlipat pada tabel. Tangan terbuka tidak cocok dengan kombinasi karena jari lain tidak terlipat. Bila salah satu jari berada di antara ambang terbuka dan terlipat, keluarkan UNKNOWN atau pertahankan pose aktif hanya selama masih memenuhi batas keluar hysteresis; jangan mengeksekusi binding jari tunggal secara parsial.

Thumbs-up tetap menjadi pose khusus dengan mapping double tap bawaan. Prioritasnya di atas kepalan/kombinasi jari. Bentuk ambigu atau geometri tidak valid menghasilkan UNKNOWN dan tidak memicu aksi.

## 4. Pilihan aksi dan default

### 4.1 Katalog aksi versi pertama

| ID usulan | Label pengguna | Perilaku |
| --- | --- | --- |
| NONE | Tidak ada aksi | Tidak mengirim aksi. |
| NEXT_ITEM | Scroll berikutnya sekali | Satu swipe vertikal FORWARD per penampilan pose. Respons bergantung aplikasi target. |
| PREVIOUS_ITEM | Scroll sebelumnya sekali | Satu swipe vertikal BACKWARD per penampilan pose. |
| SINGLE_TAP | Tap sekali | Tap di tengah area konten dengan aturan overlay yang ada; bukan tap di posisi telunjuk. |
| DOUBLE_TAP | Tap dua kali | Menggunakan double tap yang sudah ada, tanpa menjanjikan fungsi Like. |
| BACK | Kembali (Back) | Satu perintah Back Android per penampilan pose, bukan scroll ke item sebelumnya. |
| CONTINUOUS_NEXT | Scroll terus ke berikutnya | Scroll vertikal selama pose yang sudah dikonfirmasi tetap terdeteksi dan segar. |
| CONTINUOUS_PREVIOUS | Scroll terus ke sebelumnya | Sama, dengan arah berlawanan. |

Tap sekali bukan perintah pasti untuk pause video. DOUBLE_TAP dipertahankan agar kemampuan thumbs-up lama tetap tersedia. Back merupakan aksi sistem melalui accessibility, bukan tap pada koordinat tombol Back. Tidak ada aksi Home Android, Resume otomatis, toggle tracking, macro, membuka aplikasi, long press atau drag dalam katalog ini.

Scroll sekali, tap dan Back selalu sekali per penampilan pose. Pengguna memilih aksi continuous secara eksplisit jika ingin scroll selama pose ditahan. Tidak perlu dropdown repeat terpisah; perilaku pengulangan merupakan bagian dari jenis aksi. Jalur lama saat custom off tetap mempertahankan timing dan repeat existing.

Pose menunjuk kiri/kanan adalah **input**, bukan janji swipe kiri/kanan. Swipe horizontal dapat direncanakan terpisah setelah kebutuhan terbukti; adapter sekarang hanya memiliki jalur vertikal.

### 4.2 Mapping bawaan ketika kustomisasi diaktifkan

| Pose | Aksi default | Pemicu |
| --- | --- | --- |
| Menunjuk atas | Scroll sebelumnya sekali | Sekali per penampilan |
| Menunjuk bawah | Scroll berikutnya sekali | Sekali per penampilan |
| Menunjuk kiri | Tidak ada aksi | Tidak aktif |
| Menunjuk kanan | Tidak ada aksi | Tidak aktif |
| Tangan terbuka | Tap sekali | Sekali per penampilan |
| Kepalan | Tidak ada aksi | Tidak aktif |
| Telunjuk saja | Tidak ada aksi | Tidak aktif |
| Jari tengah saja | Tidak ada aksi | Tidak aktif |
| Telunjuk + tengah | Tidak ada aksi | Tidak aktif |
| Thumbs-up | Tap dua kali | Sekali per penampilan pada custom; jalur lama tetap memiliki repeat existing |

Satu aksi boleh dipilih untuk beberapa pose. Seluruh binding boleh NONE; tampilkan informasi bahwa tidak ada aksi pose aktif, bukan melarang penyimpanan. Mode HYBRID masih dapat memakai gerakan/flick sesuai aturan di bawah.

### 4.3 Reverse scroll direction

Pertahankan perilaku lama ketika kustomisasi tidak aktif. Ketika kustomisasi aktif, NEXT_ITEM, PREVIOUS_ITEM, CONTINUOUS_NEXT dan CONTINUOUS_PREVIOUS adalah arah final dan **tidak dibalik lagi** oleh `inverted`. Reverse scroll direction tetap berlaku pada flick dan continuous movement berbasis gerak tangan lama. UI memperbarui penjelasan toggle: mapping pose kustom mengikuti aksi yang dipilih.

Resolver harus membedakan sumber aksi: pose kustom, pose lama, flick, dan continuous. Jangan membalik arah dua kali atau mengubah semantik FORWARD/BACKWARD secara global. Practice wajib menghasilkan arah yang sama dengan sesi eksternal.

### 4.4 Continuous scroll selama pose ada

- Berbeda dari recognition mode CONTINUOUS lama yang mengikuti perpindahan tangan. Aksi continuous berbasis pose tersedia dalam POSE/HYBRID; pengguna cukup mempertahankan bentuk tangan tanpa menggerakkannya.
- Mulai hanya setelah pose dikonfirmasi. Keluaran recognition perlu membawa status hold yang segar setiap sampel, bukan hanya satu `PoseEvent` awal atau timer cooldown berulang.
- Gunakan kecepatan tetap awal yang konservatif dan tervalidasi, satu nilai bersama untuk dua arah. Nilai numeriknya ditentukan lewat uji perangkat sebelum fitur dinyatakan diterima; jangan menebaknya dari FPS. Tidak ada slider kecepatan per pose pada versi pertama.
- Pakai kembali mekanisme short swipe vertikal existing dengan durasi bounded (baseline 100ms) dan satu dispatch berjalan. Jika target pixel/durasi terpilih di bawah touch slop target, catat dan tuning berdasarkan uji nyata; jangan mengklaim kontak jari kontinu atau menjamin feed mulus.
- Hanya sampel hold terbaru yang valid boleh menghasilkan segmen berikutnya ketika jalur bebas. BUSY membuang permintaan pada sampel tersebut; tidak ada antrean, akumulasi jarak, burst susulan atau coroutine baru per frame. Bila ada scheduler, ia hanya memiliki satu desired velocity dan memeriksa freshness/generation sebelum setiap submit.
- Hentikan desired velocity segera pada sampel pertama yang bukan pose pemilik, UNKNOWN, no-hand, gap berlebihan, perubahan profil/mode/target, Apply, Pause/Stop, lock, izin hilang atau interaksi overlay. Watchdog juga menghentikan scroll jika callback kamera macet. Jangan memakai grace period latch untuk tetap men-scroll ketika pose sudah hilang.
- Hold yang sedang aktif diperiksa dengan hysteresis geometri untuk menahan jitter kecil; setelah benar-benar dihentikan, pose yang kembali wajib menjalani confirmation baru. Pergantian pose, termasuk ke pose lain dengan arah continuous yang sama, tidak membawa bukti lama.
- Jika target berganti, hentikan scroll dan wajibkan pelepasan pose lama yang valid lalu konfirmasi baru; jangan meneruskan hold ke aplikasi lain. Target diblokir/unavailable tidak menghasilkan aksi maupun retry tertunda.
- Segmen yang sudah disubmit dapat selesai setelah pose dilepas. Target penghentian: tidak ada segmen baru setelah invalidasi diamati; sisa gerakan dibatasi segmen berjalan. Ukur juga batas result freshness/watchdog agar keterlambatan kamera tidak disamarkan sebagai respons seketika.

## 5. Pengalaman Settings dan Practice

1. Tambahkan bagian **Aksi pose tangan** di Settings utama, di luar Advanced.
2. Switch **Gunakan aksi pose kustom** berada pada draft; default off menjaga perilaku instalasi lama.
3. Saat aktif, tampilkan pilihan profil Arah telunjuk/Kombinasi jari, kelompok pose, serta aksi saat ini per baris. Profil kombinasi memiliki tiga baris independen: Telunjuk saja, Jari tengah saja, Telunjuk + tengah. Setiap baris memiliki pemilih action sendiri dengan katalog yang sama. Thumb-up dan kepalan diberi bentuk contoh yang jelas.
4. Ketuk baris membuka pemilih aksi dengan nama, deskripsi, dan opsi NONE. Gunakan radio list dalam dialog/sheet yang dapat di-scroll. Pilihan tidak langsung mengeksekusi aksi.
5. Baris yang bertumpang tindih menjelaskan profil aktif. Tampilkan Pose umum satu kali untuk tangan terbuka, kepalan dan thumbs-up; hapus seluruh baris hitungan 1-5 jari. Beri keterangan bahwa pose telunjuk + tengah memiliki aksi sendiri dan tidak menjalankan dua aksi jari tunggal.
6. Sediakan Reset aksi pose pada kelompok sendiri. Reset hanya menyentuh konfigurasi kustom: switch kembali off, profil kembali Arah, binding kembali default. Recognition mode, tuning, overlay, consent dan konfigurasi Pointer tidak direset.
7. Apply/Discard memakai mekanisme existing. Apply yang mengubah nilai mem-pause sesi aktif/starting; Home Start/Resume membuat snapshot baru. Draft bertahan saat pindah tab dan rotasi. Process death boleh membuang draft sebagaimana perilaku saat ini.
8. Saat Pointer, CONTINUOUS atau FLICK dipilih, tampilkan alasan mapping tidak aktif. Jangan menghapus binding atau menyimpan mode baru otomatis.
9. Gestures menampilkan petunjuk berdasarkan **saved settings**, bukan mapping draft yang belum diterapkan. Ringkasan meliputi pose, aksi, profil serta beda sekali per penampilan dan scroll selama ditahan; kartu instruksi lama tidak boleh tetap mengatakan open hand selalu tap. Pemilih aksi menjelaskan bahwa Back kembali ke layar sebelumnya, bukan ke item feed sebelumnya.
10. Tautan **Coba di Practice** tidak menerapkan draft diam-diam. Jika ada perubahan belum tersimpan, tampilkan pesan untuk Apply atau Discard terlebih dahulu; kembali dari Practice mempertahankan draft.

Practice menggunakan snapshot saved settings, classifier, trigger policy dan resolver yang sama. Tampilkan pose terdeteksi, status konfirmasi, aksi terpilih, aksi terakhir, serta alasan tidak aktif/UNKNOWN. NEXT/PREVIOUS, tap dan double tap hanya mengubah sampel lokal. Continuous menggerakkan sampel lokal sesuai elapsed time dengan freshness dan batas gap yang sama, serta berhenti saat pose tidak lagi valid.

Untuk Back di Practice, tambahkan permukaan navigasi contoh lokal (halaman daftar/detail sederhana) dan indikator Back terdeteksi. Aksi hanya mengembalikan halaman sampel atau mencatat sudah di root; tidak menutup layar Practice, memanggil Back Activity, memanggil service eksternal atau membangun adapter accessibility. Tombol Back fisik/sistem tetap memakai lifecycle Practice existing. NONE tidak menambah hitungan aksi. Jumlah segmen continuous tidak dilabeli sebagai jumlah pose baru; hitung aktivasi hold terpisah dari progres geraknya.

UI mengikuti bahasa aplikasi yang ada; label Indonesia dalam rencana ini menjelaskan makna. Simpan label melalui resource sesuai konvensi implementasi, jangan menambahkan sistem lokalisasi baru hanya untuk fitur ini.

## 6. Pengakuan pose, confirmation, dan arbitration

### 6.1 Geometri dan identitas

- Tetap gunakan 21 landmark dan model lokal yang ada, satu tangan, tanpa dependency/model baru.
- Tambahkan classifier kustom atau jalur opt-in pada classifier existing. Pertahankan jalur lama saat kustomisasi off dan pada fixture kompatibilitas. Pilih bentuk terkecil yang menjaga kontrak tersebut; tidak perlu framework classifier.
- Klasifikasi setiap jari memakai sudut sendi, panjang tulang relatif telapak, jarak tip ke telapak dan hysteresis. Thumb memerlukan geometri sendiri; jangan menghitung ibu jari dengan aturan empat jari panjang secara membabi buta.
- Gunakan `uprightAspectRatio` untuk geometri jarak/sudut kustom, tidak memakai jarak x/y mentah yang terdistorsi. Metadata sudah tersedia pada `HandTrackingResult`.
- Arah telunjuk dihitung dari MCP ke tip dalam koordinat upright yang telah dimirror sekali. Atas/bawah/kiri/kanan mengikuti layar/preview pengguna, bukan sisi anatomis tangan.
- Empat sektor arah memiliki zona ambigu di diagonal. Masuk/keluar sektor memakai hysteresis; jangan menghasilkan dua pose ketika sudut berada dekat batas.
- Profil arah mensyaratkan telunjuk terbuka dan jari panjang lain terlipat; ibu jari harus dalam batas bentuk menunjuk yang diuji. Profil kombinasi menggunakan identitas landmark telunjuk dan tengah, serta keadaan jari lain sesuai tabel bagian 3.3. Tidak memakai total jari terbuka sebagai identitas aksi.
- Validitas tangan kiri/kanan tidak ditentukan hanya oleh label handedness. `handednessConfidence` bukan confidence tiap pose/jari. Landmark hilang, degenerat, terpotong atau ambigu tidak menghasilkan tebakan aksi.

### 6.2 Kebijakan pemicu

- Pakai confirmation duration, minimum observations, maximum sample gap, lost-tracking watchdog dan clock monoton yang sudah ada. Jangan menentukan stabilitas hanya dari jumlah frame/FPS.
- Binding memuat jenis aksi immutable. Trigger policy diturunkan dari aksi: discrete untuk scroll sekali/tap/double tap/Back, held untuk dua continuous action, inactive untuk NONE. Tidak ada repeat preference terpisah dalam versi ini.
- Sekali per penampilan baru diarm ulang setelah pose lain yang valid bertahan selama release duration. UNKNOWN, kehilangan tangan singkat atau jitter tidak dianggap pelepasan yang sah. Tidak ada pemicu ganda ketika reacquire pose yang sama.
- Cooldown global yang ada tetap membatasi perpindahan/pemicu aksi discrete, tetapi cooldown yang habis tidak mengarm ulang pose discrete yang masih ditahan. Continuous dimulai lewat confirmation, diteruskan oleh status hold segar tanpa menunggu cooldown tiap segmen, dan berhenti sesuai bagian 4.4. Jalur lama tetap memakai repeat existing saat custom off.
- Perubahan kandidat pose membuang bukti sebelumnya. Satu sampel menghasilkan paling banyak satu event. Perubahan konfigurasi/sesi menghapus latch/candidate/timer lama.
- Peralihan INDEX_ONLY/MIDDLE_ONLY/INDEX_MIDDLE menjalani confirmation penuh untuk pose tujuan. Jika sedang continuous pada jari tunggal lalu jari kedua dibuka, hentikan continuous lama pada invalidasi dan jangan menjalankan aksi kombinasi sebelum konfirmasi selesai. Aturan yang sama berlaku ketika salah satu jari ditutup.
- Sampel transisi yang bertahan lebih singkat dari confirmation tidak boleh memicu aksi sela. Bila pengguna mempertahankan pose tunggal cukup lama sebelum menambah jari kedua, itu merupakan pose tunggal yang sah dan aksinya dapat terjadi; classifier tidak dapat mengetahui pose masa depan. Jelaskan latihan perpindahan ini dan uji waktu transisinya, tanpa menambahkan delay/chord window tersembunyi.
- Perubahan jenis aksi adalah bagian dari draft dan Apply, bukan perubahan hot pada detektor yang sedang berjalan. Pose Back ditahan maupun hilang/reacquire tidak boleh berulang hingga keluar dari aplikasi secara tidak sengaja.

### 6.3 Interaksi dengan HYBRID dan NONE

- POSE hanya mengeluarkan event pose aktif; NONE tidak mengeksekusi apa pun.
- HYBRID: pose dengan binding bukan NONE mengambil kepemilikan sejak kandidat mulai dikonfirmasi; continuous berbasis gerakan tangan dan flick disupresi selama kandidat/pose itu ditahan, termasuk setelah aksi sekali per penampilan. Aksi continuous berbasis pose memiliki jalur output tunggal selama hold dan tidak boleh bertumpuk dengan continuous lama. Ini mencegah tangan terbuka selesai tap lalu tiba-tiba men-scroll.
- Pose dengan binding NONE tidak mengunci ownership/cooldown; flick dan continuous tetap diperbolehkan pada HYBRID. Jelaskan bahwa NONE menonaktifkan aksi pose, bukan membekukan semua kontrol gerak.
- Pertahankan prioritas flick yang sudah memiliki gesture; tidak menyelipkan aksi pose kedua dalam satu gerakan. Setelah ownership dilepas, reset riwayat gerakan agar tidak terbentuk lonjakan palsu.
- Pose keluarga profil lain tidak menjadi kandidat. Thumbs-up diperiksa sebelum kepalan atau kombinasi jari. INDEX_MIDDLE tidak pernah membocorkan event jari tunggal, termasuk ketika binding kombinasi adalah NONE: jangan fallback ke INDEX_ONLY/MIDDLE_ONLY. Semua perubahan arbitration diberi native test terarah.

## 7. Model data, penyimpanan dan integrasi

### 7.1 Pembagian kepemilikan

| Lokasi | Perubahan yang direncanakan |
| --- | --- |
| `gesture/.../Models.kt`, classifier dan detector | Identitas pose baru, profil, bentuk canonical, stabilitas dan trigger policy; seluruhnya pure Kotlin dengan timestamp eksplisit. |
| `gesture/.../GestureCoordinator.kt` | Hanya binding aktif yang memiliki gesture; tidak mengaktifkan pose pada FLICK/CONTINUOUS. Terima konfigurasi enabled/trigger sederhana dan terbitkan hold/freshness/invalidation eksplisit, tanpa mengenal Android. |
| `app/.../settings/NativeSettings.kt` | Snapshot konfigurasi kustom dan grup reset tersendiri. Koleksi binding disalin/immutable agar perubahan draft tidak memutasi saved settings. |
| `app/.../settings/SettingsStore.kt` | Decode/validate/encode preference tambahan; fallback per field tanpa mereset settings lama. |
| `app/.../scrolling` | Resolver pure Kotlin bersama untuk pose ke aksi final, termasuk no-op, Back dan held velocity. Reuse action coordinator sebagai pemilik tunggal dispatch. |
| `app/.../accessibility/AccessibilityScrollAdapter.kt`, `GestureAccessibilityService.kt` | Tambahkan eksekusi Back melalui API accessibility global action; share readiness, target guard dan kepemilikan dengan touch tanpa menambah screen-content access. |
| `app/.../session/SessionRecognition.kt`, `GestureCameraService.kt` | Pasok konfigurasi pada cabang aktual Scroll, snapshot per sesi, freshness/watchdog dan terminasi hold pada target/lifecycle changes. |
| `app/.../practice/PracticeFeedback.kt`, `PracticeController.kt` | Gunakan resolver dan policy bersama; simulasi continuous dan navigasi Back hanya pada sampel lokal. |
| `app/.../presentation/SettingsPanel.kt`, `SettingsDraft.kt`, `HandScrollScreen.kt`, `PracticeScreen.kt` | Editor mapping, profil, panduan jenis aksi dinamis dan feedback lokal. |
| `app/.../MainActivity.kt`, `session/SessionStore.kt` bila diperlukan | Apply/pause dan callback tetap di entry point; state diterbitkan immutable. Jangan memberi composable akses ke preferences/service. |

Nama tipe baru bersifat usulan: `PoseRecognitionProfile`, `PoseBinding`, `PoseAction`, `PoseActionResolver`. Letakkan di modul sesuai tanggung jawab; trigger policy diturunkan dari action, tidak perlu menambahkan modul, repository, framework DI atau generic command bus.

### 7.2 Format preference dan kompatibilitas

- Gunakan SharedPreferences `gesture_settings` yang sama. Tambahkan key bernamespace seperti `poseActions.version`, `poseActions.enabled`, `poseActions.profile`, dan `poseActions.binding.<stable-id>.action`. Tidak ada key repeat karena semantik sekali/hold sudah ditentukan action.
- Jangan menyimpan ordinal enum atau label UI. Pakai ID stabil eksplisit; NEUTRAL lama tidak otomatis berarti tangan terbuka dengan lima jari ketat pada jalur kompatibilitas. INDEX_ONLY, MIDDLE_ONLY dan INDEX_MIDDLE disimpan sebagai tiga key berbeda.
- Instalasi lama dan fresh install memulai dengan custom enabled=false. Semantik arah, reverse, open-hand tap, thumbs-up dan timing lama tetap sama sampai pengguna mengaktifkan konfigurasi kustom melalui Apply.
- Mengaktifkan custom adalah perubahan perilaku yang disengaja: bentuk lima jari menjadi eksplisit, pilih profil dan berikan instruksi Practice. Tidak mengklaim classifier lama sudah memenuhi definisi baru.
- Key baru yang hilang memakai default per binding; nilai action/profile salah diperbaiki per field. Versi format lebih baru yang tidak didukung menonaktifkan custom secara aman, mempertahankan key mentah dan settings lama; jangan menimpa data hanya karena dibaca.
- Decode key baru harus diisolasi dari `runCatching` seluruh `NativeSettings` saat ini. Satu mapping rusak tidak boleh memicu `getOrDefault(NativeSettings())` dan mereset tuning pengguna.
- Validasi action dan profil pada load serta update. Key asing diabaikan; tidak dipetakan ke aksi lain secara spekulatif. Pose umum memakai satu ID per pose, bukan per profil. Tidak ada migrasi produksi dari hitungan 1-5 jari karena perubahan sebelumnya baru berupa rencana; jangan menambahkan key hitungan jari pada implementasi awal.
- Simpan seluruh perubahan mapping dengan satu editor transaction dan publikasikan satu snapshot, mengikuti pola persistence existing. Membuka pemilih, mengganti tab, dan Discard tidak menulis preference.
- Persetujuan accessibility, preference Pointer, posisi overlay dan semua default lama tetap dipertahankan.

### 7.3 Jalur eksekusi dan keselamatan

Resolver menghasilkan salah satu: no-op, request discrete (swipe/tap/Back), atau desired velocity dari pose held beserta timestamp/status validitasnya. Jangan memakai `manual()` untuk event pose karena dapat melewati semantik timestamp pengenalan. Continuous pose bukan rangkaian `PoseEvent` cooldown; invalidasinya harus mencapai coordinator walaupun tidak ada event baru.

Untuk seluruh aksi, pertahankan freshness, session-start fence, duplicate/out-of-order rejection, target policy, lock, overlay, accessibility readiness, generation token dan satu operasi tanpa backlog pada coordinator/adapter existing. Back, tap, scroll discrete dan continuous berbagi pemilik dispatch; Back tidak menyela stroke berjalan. Ketika sibuk, tolak tanpa retry/antrean dan konsumsi penampilan discrete itu; pengguna melepas lalu mengonfirmasi ulang untuk mencoba lagi.

Back dijalankan melalui API publik `AccessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)` di adapter yang sama, setelah readiness dan target policy tervalidasi tepat sebelum panggilan. Periksa kontrak API terhadap compile/min SDK saat implementasi. Back diblokir pada Scroll Vision, layar sistem/izin, lock dan target terlarang, walaupun API global itu sendiri dapat menjangkau sistem. Jangan mensimulasikannya dengan tap koordinat atau membaca konten layar.

Hasil boolean global action hanya menandakan perintah diterima/ditolak, bukan bukti layar sudah berubah. Bedakan hasil diterima (misalnya ACCEPTED) dari touch callback COMPLETED pada diagnostics dan exhaustive enum consumers; jangan membuat callback completion palsu untuk Back. Kegagalan tidak memicu retry otomatis. Setelah Back diterima, pertahankan latch sampai pelepasan pose yang sah; menahan pose tidak boleh menutup banyak layar/aplikasi. Perubahan target membuang kandidat dan memblokir rearm sampai pose dilepas, bukan mereset latch menjadi siap begitu saja.

Pause/Stop existing tetap menghentikan desired velocity dan melepaskan resource dengan teardown kamera terserialisasi; Start/Resume membutuhkan konfirmasi pose baru. Stroke yang sudah disubmit dapat selesai sesuai keterbatasan Android. Tidak ada izin baru, pembacaan konten layar, penyimpanan/upload frame, analytics atau jaringan.

## 8. Tahapan implementasi

| Tahap | Pekerjaan | Syarat selesai |
| --- | --- | --- |
| P1: Kontrak dan settings | Model binding/profile/action, default, resolver, persistence defensif dan reset | Tes default lama, round-trip tiga binding kombinasi, corrupt key, unknown version, shared common poses dan inversion lulus. Belum ada aksi baru dari kamera. |
| P2: Pengenalan native | Empat arah, INDEX_ONLY/MIDDLE_ONLY/INDEX_MIDDLE, tangan terbuka, kepalan, konflik thumbs-up, hysteresis, trigger policy dan ownership | Synthetic native cases mencakup pose, batas/ambigu, waktu dan profil. Kombinasi tidak mengeluarkan aksi jari tunggal. Golden lama tetap identik. |
| P3: Editor dan Practice | UI Settings, Apply/Discard, instruksi saved settings, feedback dan lifecycle Practice | Seluruh mapping dapat dipilih; Practice memakai resolver bersama dan tidak menghasilkan aksi eksternal. |
| P4: Sesi eksternal | Wiring service aktual, mapped screen requests, global Back, continuous pose hold dan guard | Unit/instrumentation membuktikan satu Back per penampilan, hold berhenti tepat, serta penolakan stale/busy/unsafe tanpa antrean. |
| P5: Validasi dan dokumentasi | Build/lint, emulator UI, uji tangan/perangkat dan dokumen perilaku | Bukti dipisahkan menurut lapisan; kegagalan dan pengujian yang belum dilakukan tercatat. |

Setiap tahap menjaga aplikasi lama tetap berfungsi ketika custom off. Jangan membuka fitur untuk penggunaan sehari-hari sebelum seluruh wiring dan pengujian keselamatan selesai. Dokumen ini tidak menginstruksikan commit, push atau publish otomatis.

## 9. Strategi pengujian

### 9.1 Unit test Kotlin murni

- Arah atas/bawah/kiri/kanan, diagonal, batas sektor, rotasi metadata upright, rasio aspek, mirror sekali, dan tangan kiri/kanan.
- Bentuk INDEX_ONLY, MIDDLE_ONLY, INDEX_MIDDLE, tangan terbuka, kepalan, thumbs-up, jari setengah terlipat, landmark degenerat, tangan sebagian keluar frame dan susunan jari tidak didukung. Dua jari yang bukan pasangan telunjuk + tengah tidak cocok.
- INDEX_MIDDLE menghasilkan hanya satu identitas, tidak dua event tunggal; tangan terbuka tidak cocok dengan ketiga kombinasi. Tidak ada fallback binding kombinasi NONE ke aksi tunggal, atau profil arah ke telunjuk saja. Pergantian profil mereset bukti.
- Peralihan tunggal ke gabungan dan sebaliknya: continuous lama berhenti, kandidat tujuan dikonfirmasi dari awal, sampel transisi di bawah confirmation tidak memicu aksi sela; pose tunggal yang benar-benar stabil diperlakukan sesuai binding sendiri.
- Confirmation/cooldown/release dengan timestamp eksplisit, minimal observations, duplicate/out-of-order, gap panjang, tracking loss/reacquire dan pose transisi.
- Sekali per penampilan tidak terpicu lagi akibat UNKNOWN/no-hand singkat; hold status segera invalid saat pose hilang/berubah, tidak menunggu cooldown dan tidak mengejar waktu yang lewat.
- HYBRID ownership untuk enabled binding, NONE tidak mengunci, flick ownership tetap dihormati dan motion history direset.
- Jalur custom off dan fixture `tapGesturesEnabled=false` tetap kompatibel. Jangan mengganti `gesture-goldens.json`; tambah native cases terpisah untuk perilaku baru.

### 9.2 Unit test aplikasi

- Semua kombinasi pose dan action, seluruh NONE, aksi sama pada banyak pose, serta konfigurasi profil tidak aktif. Ubah action INDEX_ONLY tanpa mengubah MIDDLE_ONLY/INDEX_MIDDLE; ulangi untuk ketiganya. Ketika kedua jari terbuka hanya action gabungan yang di-resolve, termasuk Back dan continuous.
- NEXT/PREVIOUS dan continuous pose kustom tidak dibalik `inverted`; jalur lama, continuous berbasis gerak dan flick dibalik sekali. Tap dan Back tidak pernah dibalik.
- Practice dan sesi eksternal menyelesaikan event menjadi aksi yang sama, tetapi Practice hanya memutasi sampel lokal.
- Back ditahan, ditolak, diterima lalu berganti aplikasi, atau tracking loss/reacquire: maksimal satu panggilan per penampilan, tidak pernah menjadi swipe/tap atau retry. Hasil ACCEPTED tidak diklaim sebagai navigasi berhasil.
- Continuous hold: kecepatan tidak bergantung FPS, tidak ada burst saat frame lambat, tidak submit baru pada BUSY, dan tidak submit setelah kehilangan pose/watchdog/target change/Apply/Pause/Stop. Maksimal satu segmen berjalan.
- Callback dan tick generasi lama tidak memulai ulang scroll. Pose baru wajib confirmation baru; input target baru tidak mewarisi hold. Pause/Stop tetap melepas kamera dan tidak auto-resume.
- Freshness/session/duplicate dan blocked-target guards juga berlaku untuk Back; busy tidak menambah backlog; NONE tidak mencatat dispatch sukses. Back Practice tidak mengirim global action atau keluar dari Practice.
- Immutability draft, reset kelompok, Apply changed-only dan status mapping pada control/recognition mode berbeda.

### 9.3 Instrumentasi dan inspeksi emulator

- Settings round-trip dan restart process mempertahankan mapping tersimpan; draft belum Apply tidak tersimpan. Preference lama, consent dan tuning tetap utuh ketika key baru salah.
- Editor tiga baris kombinasi yang independen dan pose umum tanpa duplikasi; tidak ada baris 1-5 jari. Uji profil, jenis aksi discrete/continuous/Back, reset kelompok, Apply/Discard, tab switching, rotasi, serta kembali dari Practice.
- Light/dark, teks 200%, portrait/landscape, pilihan panjang dapat dibaca, target sentuh minimal 48dp, label accessibility jelas dan sheet dapat di-scroll.
- Saved mapping pada Gestures/Practice sesuai; draft tidak bocor ke sesi. Kamera/accessibility/overlay denial tetap memakai alur existing.
- Mapping custom ke single/double tap, navigasi dan Back diverifikasi melalui target uji eksternal. Back memakai daftar/detail target uji pada UID berbeda; verifikasi perubahan layar dan tidak memanggil Back kedua ketika pose tetap ditahan. Back tidak bekerja pada target terlarang.
- Continuous menggerakkan permukaan uji selama hold, berhenti mengirim segmen baru ketika pose diinvalidasi/target berubah, dan tidak melanjutkan saat callback macet. Catat segmen terakhir yang masih menyelesaikan gerakan. Practice menguji perilaku serupa hanya secara lokal.
- Mode Pointer tetap lulus regresi tap/hold/drag dan kamera handoff. Jangan membuat pose kustom aktif bersamaan dengan pinch.

Gunakan Gradle Wrapper dari root PowerShell. Sesuaikan nama kelas dengan implementasi akhir; selama pengembangan jalankan kelas yang terdampak saja. Sebelum commit implementasi:

```powershell
.\gradlew.bat :gesture:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
git diff --check
```

Eksekusi instrumentasi dilakukan terpisah pada emulator/perangkat uji yang dipilih eksplisit melalui `ANDROID_SERIAL`, sesuai `docs/development.md`. Kompilasi APK instrumentasi bukan hasil eksekusi. Jangan mengubah izin perangkat pribadi tanpa persetujuan.

### 9.4 Penerimaan fisik yang diusulkan

Ambang berikut merupakan target awal, bukan hasil atau jaminan akurasi:

1. Matriks pose: 20 penampilan per pose per tangan untuk seluruh pose canonical dan empat arah, dicatat pada profil yang tepat. Target minimal 18/20 menghasilkan tepat satu identitas/event yang diharapkan untuk konfigurasi sekali per penampilan.
2. Catat confusion matrix, false trigger, kegagalan deteksi dan latency. Catat kondisi cahaya, jarak kamera dan orientasi; jangan menyatukan semua kondisi menjadi satu angka tanpa rinciannya.
3. Uji 20 perpindahan per pasangan rawan: kepalan ke tangan terbuka, telunjuk saja ke keduanya, tengah saja ke keduanya, telunjuk saja ke tengah saja, dan arah kebalikannya. Catat durasi transisi: pose sela di bawah confirmation tidak boleh memicu aksi; pose tunggal yang ditahan cukup lama sah memicu bindingnya. Continuous lama harus berhenti sebelum pose tujuan dikonfirmasi; tidak boleh ada dua aksi berjalan bersamaan.
4. Tahan pose discrete selama 10 detik: tepat satu aksi, khususnya Back tidak menutup beberapa layar. Tahan pose continuous 10 detik: scroll berlanjut tanpa tangan perlu bergerak; pose dilepas/berubah mengakhiri submission baru. Ukur latency penghentian, segmen tersisa dan freshness/watchdog. Tracking loss/reacquire wajib confirmation baru dan tidak menghasilkan aksi stale.
5. Idle dengan tangan santai selama 60 detik dan tracking loss selama 30 detik: tidak ada aksi eksternal tak disengaja. Uji profil lain/NONE sesuai semantik HYBRID, bukan menganggap NONE membekukan detektor gerak.
6. Verifikasi hasil yang benar-benar terlihat di minimal dua aplikasi target, termasuk scroll sekali, scroll selama hold, tap dan Back. `dispatchGesture` COMPLETED maupun global action ACCEPTED tidak cukup untuk menyatakan navigasi atau playback berhasil.
7. Pause/Stop diuji saat idle, saat request sedang berjalan dan setelah tracking loss: kamera benar-benar dilepas, tidak muncul aksi baru, tidak auto-resume. Catat stroke yang telah disubmit sebelum Pause/Stop.
8. Uji penggunaan 15 menit, baterai/panas, latency dan kelelahan, serta cakupan OEM/API mengikuti `docs/device-validation.md`. Kondisi yang tidak tersedia dicatat SKIPPED.

Jika bentuk jari tertentu tidak mencapai target, jangan menyembunyikan kegagalan dengan mengubah ekspektasi test. Perbaiki detector atau nyatakan batas dukungan pada instruksi sebelum fitur dianggap selesai.

## 10. Dokumentasi dan definisi selesai

- Perbarui README, `docs/architecture.md`, `docs/development.md`, `docs/tap-gestures.md` dan `docs/device-validation.md` untuk mapping, profil, Back, continuous pose, inversion dan lifecycle yang berubah.
- Tambahkan receipt implementasi berisi commit bila ada, unit/build/lint, instrumentasi yang benar-benar dieksekusi, visual QA, hasil fisik dan kondisi yang belum diuji. Jangan menyalin hasil historis sebagai hasil perubahan ini.
- Ketiga kombinasi telunjuk/tengah memiliki pilihan aksi masing-masing, pose gabungan tidak menjalankan aksi tunggal, dan hitungan umum 1-5 jari sudah dihapus dari rancangan UI/model. Pose umum memiliki satu binding per pose; instruksi memperlihatkan bentuk yang didukung.
- Default instalasi lama tetap berjalan tanpa reset data; custom off memulihkan jalur lama tanpa membuang mapping tersimpan.
- Practice dan sesi eksternal memakai semantik yang sama, dengan pemilik resource dan efek yang berbeda secara eksplisit.
- Tidak ada double dispatch, stale action, backlog, perubahan izin diam-diam, regresi Pointer atau perubahan golden untuk menutupi masalah.

## 11. Di luar lingkup versi pertama

Aktivasi arah dan kombinasi jari secara bersamaan untuk bentuk yang identik; hitungan umum 1-5 jari; kombinasi jari lain; dua tangan; pose buatan pengguna melalui training; profil per aplikasi; macro/rangkaian aksi; gesture Resume; swipe horizontal; kontrol PC; penyimpanan frame; model baru; tuning slider per pose.

Profil eksklusif, tiga kombinasi telunjuk/tengah dengan aksi independen dan katalog scroll/tap/Back/continuous mengikuti keputusan pengguna. Bentuk canonical, default custom sekali-per-penampilan dan ambang penerimaan merupakan usulan desain yang konkret. Jika kelak diperlukan profil arah dan kombinasi aktif bersamaan, definisikan input pembeda yang terukur terlebih dahulu, misalnya pose tambahan atau mode eksplisit, bukan menambahkan prioritas tersembunyi pada Settings.


> Subsequent design: [unified pose recognition plan](2026-11-01-unified-pose-recognition.md) supersedes the earlier custom-off legacy-path assumptions.
