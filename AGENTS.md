# Panduan Agent

## Toolchain dan Package Manager
- Gunakan Gradle Wrapper yang tersedia; versi dependency mengikuti file Gradle.
- Setup JDK 17 dan Android SDK mengikuti [README.md](README.md); simpan lokasi SDK hanya di `local.properties` yang diabaikan Git.
- Project aktif adalah Android Kotlin dengan modul `:gesture` dan `:app`; tidak memerlukan Flutter/Dart.

## Perintah Terarah
- Jalankan dari root project di PowerShell; ganti `<TestClass>` dengan nama kelas pengujian yang terdampak.

```powershell
# Satu kelas pengujian logika gesture
.\gradlew.bat :gesture:test --tests 'dev.autoscroll.nativeapp.gesture.<TestClass>'
# Satu kelas unit test Android
.\gradlew.bat :app:testDebugUnitTest --tests 'dev.autoscroll.nativeapp.<package>.<TestClass>'
# Lint modul Android
.\gradlew.bat :app:lintDebug
```

- Untuk pengujian instrumentasi, pilih perangkat uji secara eksplisit; jangan mengubah izin perangkat pribadi tanpa persetujuan.

```powershell
$env:ANDROID_SERIAL = '<test-device-serial>'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=dev.autoscroll.nativeapp.<package>.<TestClass>'
```

- Sebelum commit, jalankan rangkaian validasi pada [docs/development.md](docs/development.md) dan `git diff --check`; CI acuannya adalah [.github/workflows/android.yml](.github/workflows/android.yml).
- Bedakan unit test, kompilasi instrumentasi, eksekusi di emulator, dan uji perangkat fisik; laporkan pengujian yang dilewati.

## Batas Modul dan Konvensi
- Ikuti pemetaan kepemilikan di [docs/development.md](docs/development.md) dan lifecycle di [docs/architecture.md](docs/architecture.md).
- `gesture/src/main/kotlin` tetap Kotlin murni dengan timestamp eksplisit; jangan memasukkan tipe Android, Compose, CameraX, atau MediaPipe.
- `app/src/main/java` memiliki adapter platform; service memiliki sesi eksternal, Activity memiliki practice lokal, dan SessionStore mempublikasikan state immutable.
- UI presentation menerima data dan callback; izin, persistence, dan perintah service tetap di entry point/pemiliknya.
- Gunakan constructor injection dan pola yang sudah ada; tambahkan dependency atau lapisan baru hanya jika kebutuhan perubahan menuntutnya.
- Pertahankan application ID, nama preference, default, dan validasi settings; jangan mereset data pengguna untuk memudahkan perubahan.
- Perubahan UI mengikuti checklist di [docs/development.md](docs/development.md), termasuk teks besar, light/dark, draft, rotasi, dan alur penolakan izin.

## Kamera, Aksi, dan Privasi
- Tutup setiap ImageProxy dalam `finally`; pertahankan satu worker terbatas, frame terbaru, teardown terserialisasi, dan penolakan callback lama lewat generation token.
- Pertahankan satu aksi berjalan tanpa backlog serta guard freshness, sesi, lock, target aplikasi, overlay, dan izin.
- Pause melepas kamera; Stop membersihkan resource. Resume memerlukan tindakan eksplisit; process restart tetap stopped.
- Practice hanya memengaruhi sample lokal, tidak membangun adapter accessibility atau mengirim swipe/tap eksternal; pertahankan guard handoff kamera antar pemilik.
- Pertahankan persetujuan accessibility dan larangan membaca konten layar; jangan menambahkan perekaman/upload frame, analytics, backend, atau izin jaringan tanpa permintaan eksplisit.
- Model produksi dan lisensinya ada di `app/src/main/assets/models`; gambar uji hanya di source set instrumentasi.
- Jangan commit lokasi SDK, signing credentials, output build, atau log lokal.

## Regresi dan Dokumentasi
- Pertahankan [parity-fixtures/gesture-goldens.json](parity-fixtures/gesture-goldens.json) saat refactor; jangan mengubah ekspektasi untuk menyembunyikan regresi.
- Untuk perubahan perilaku yang disengaja, tambahkan kasus native terarah; ikuti [parity-fixtures/README.md](parity-fixtures/README.md).
- Perbarui dokumentasi perilaku yang terdampak; bedakan rencana dari implementasi dan bukti pengujian.
- Akurasi kamera, respons feed, dan performa berkelanjutan memerlukan [docs/device-validation.md](docs/device-validation.md); build sukses bukan bukti penerimaan fisik.

## Atribusi Commit
- Commit yang dibuat AI wajib menyertakan trailer `Co-Authored-By: <nama model dan byline atribusi resminya>` sesuai identitas agent yang digunakan; jangan mengarang identitas atau alamat email.
