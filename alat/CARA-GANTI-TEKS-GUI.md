# Cara Cepat Ganti Kata di GUI — Bening Laundry

Project ini **Java Swing**, semua kode ada di **1 berkas: `LaundryApp.java`** (3.111 baris).
Karena cuma 1 berkas, sebenarnya tidak seseram "ribuan baris" — kuncinya tahu cara mencarinya.

---

## 1. Alat bantu yang sudah disiapkan (folder `alat/`)

Semua dijalankan dari folder project (`C:\Users\Hanif\Downloads\laudry ipan`).

### a. Lihat semua teks yang tampil di layar
```bat
python alat/cari_teks_gui.py
```
Hasil: daftar teks + nomor barisnya. Ini "peta" teks GUI kamu.
Cari teks tertentu saja:
```bat
python alat/cari_teks_gui.py "Hapus"
```

### b. Cek apakah teks itu aman diganti
```bat
python alat/cek_risiko.py "Kiloan"
```
Menjawab: teks ini cuma tampilan, atau dipakai logika/database?

### c. Ganti teks otomatis (backup + cek kompilasi)
```bat
python alat/ganti_teks.py "Bening Laundry" "Laundry Ipan"
```
Masih **dry-run** (belum mengubah apa pun). Kalau sudah yakin:
```bat
python alat/ganti_teks.py "Bening Laundry" "Laundry Ipan" --terapkan
```
Otomatis: backup → ganti → kompilasi. Kalau kompilasi gagal, otomatis dikembalikan.

---

## 2. Kalau dosen minta ganti 1 kata dan kamu harus cari manual

**Jangan scroll.** Tekan `Ctrl+F` di VS Code / Notepad++, ketik teks yang tampil di layar
**persis** seperti yang kamu lihat, lalu Enter. Itu 90% jawabannya.

Tips pencarian:
- Cari **tanpa tanda kutip** dulu. Kalau hasilnya kebanyakan, baru pakai tanda kutip: `"Kirim Struk"`
- Teks di layar kadang terpotong sambungan, contoh `"Menampilkan " + tampil + " dari " + semua`
  → cari potongan yang paling khas: `Menampilkan`
- Huruf besar-kecil berpengaruh. Kalau tidak ketemu, coba versi lain (`Hapus` vs `hapus`)

---

## 3. Peta lokasi: teks GUI ada di bagian mana

Berdasarkan struktur `LaundryApp.java`:

| Baris | Isi |
|---|---|
| 300–384 | `class Pesanan` — data pesanan, status, jenis |
| 385–446 | `ModelPesanan` — **nama kolom tabel** (`KOLOM`, baris 386) |
| 539–617 | `class Tema` — warna & font |
| 750–827 | `class Tombol` — komponen tombol |
| 1188–1243 | `PanelHeader` — **nama brand "Bening Laundry"** (baris 1205) |
| 1244–1272 | `KartuStatistik` — kartu ringkasan di atas |
| 1600–1652 | `Toast` — notifikasi muncul di layar |
| 1653–1794 | `DialogHapus` — dialog konfirmasi hapus |
| **1798–2265** | **`LaundryApp` — deklarasi semua komponen + susunan tampilan** ← paling sering diedit |
| 2266–2448 | aksi tombol |
| 2713–2923 | form input |
| 2924–2961 | pencarian & saringan |
| 2962–3029 | notifikasi & dialog |

**Bagian paling penting: baris 1805–1845.** Di situ semua teks komponen dikumpulkan
dalam satu blok deklarasi, jadi gampang dibaca:

```java
private final JLabel lblJudulForm = new JLabel("Pesanan baru");        // 1815
private final Tombol btnStruk    = new Tombol("Kirim Struk", ...);     // 1833
private final KartuStatistik kartuTotal = new KartuStatistik("Total Pesanan", ...); // 1835
```

Judul jendela aplikasi ada di **baris 1857**:
```java
super("Bening Laundry \u2014 Kasir & Manajemen Pesanan");
```

---

## 4. Jebakan: teks yang kelihatan biasa tapi dipakai logika

Ini yang bikin mahasiswa gagal saat ujian. Contoh nyata di project ini:

| Teks | Status | Kenapa |
|---|---|---|
| `"Bening Laundry"` | **AMAN** | cuma `JLabel` |
| `"Kirim Struk"` | **AMAN** | cuma label tombol |
| `"Kiloan"` | **HATI-HATI** | 7 dari 10 tempat dipakai `.equals()` untuk hitung tarif |
| `"Belum Diproses"` / `"Proses"` / `"Selesai"` | **HATI-HATI** | disimpan ke database, dipakai filter & warna |
| `"Total Pesanan"` (kartu) | **AMAN** | label statistik |

Aturan praktisnya:
- Kalau teksnya **hanya muncul 1×** → hampir pasti aman (tampilan).
- Kalau muncul **banyak kali** → jalankan `cek_risiko.py` dulu.

Kalau yang diminta ganti teks "HATI-HATI", ganti **semua** kemunculannya sekaligus
(pakai `ganti_teks.py` yang mengganti global), jangan satu-satu.

---

## 5. Alur aman saat ujian (hafalkan 5 langkah ini)

```
1. Ctrl+F  → cari teks persis seperti di layar
2. Baca   → teksnya cuma label, atau dipakai .equals()/SQL?
3. Backup → copy LaundryApp.java  (atau: git stash / commit dulu)
4. Ganti  → edit, lalu:  javac -encoding UTF-8 LaundryApp.java
5. Cek    → jalankan aplikasinya, lihat teksnya sudah berubah
```

Kompilasi manual:
```bat
cd "C:\Users\Hanif\Downloads\laudry ipan"
javac -encoding UTF-8 LaundryApp.java
```
Kalau tidak ada pesan error = berhasil. Lalu buka aplikasi lewat `nyalakan.bat`
(atau `javaw -cp ".;sqlite-jdbc-3.54.1.0.jar" LaundryApp`).

**Kalau rusak / gagal kompilasi:**
```bat
copy LaundryApp.java.bak LaundryApp.java
```
atau kalau sudah commit sebelumnya:
```bat
git checkout LaundryApp.java
```

---

## 6. Kalimat yang bisa kamu ucapkan ke dosen

> "Saya pakai pencarian `Ctrl+F` dengan teks persis yang tampil di layar, karena di Swing
> semua teks UI adalah string literal di dalam `setText()` atau konstruktor komponen.
> Setelah ketemu, saya cek dulu apakah string itu hanya untuk tampilan atau juga dipakai
> sebagai nilai logika seperti status pesanan, karena kalau dipakai logika harus diganti
> serentak di semua tempat dan disesuaikan dengan data di database."

Itu menunjukkan kamu paham **kenapa**, bukan cuma bisa ngedit.

---

## 7. Catatan teknis kecil

- Berkas `sistem laudry` (12 MB) itu sebenarnya **file ZIP** (berisi `sqlite-jdbc` + lain-lain), bukan kode.
- `*.class` di folder utama adalah hasil kompilasi — jangan diedit, akan tertimpa saat compile.
- Folder `.build/`, `.build_v2/`, `.smoke/`, `.uji_db/` hanya artefak build/uji, bukan kode sumber.
- Satu-satunya kode sumber: **`LaundryApp.java`**.
