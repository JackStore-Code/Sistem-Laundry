# Daftar Teks yang WAJIB Diganti Serentak

> Hasil analisis otomatis `LaundryApp.java` (3.111 baris) dengan `alat/analisis_teks.py`.
> Diverifikasi manual terhadap kode + isi `db_laundry.db`.
> Jalankan ulang kapan saja: `python alat/analisis_teks.py`

**Kesimpulan singkat:** dari 478 string literal, hanya **22 teks** yang berbahaya
kalau diganti sepihak. Sisanya (~56 teks GUI) aman diganti di satu tempat saja.

---

## TINGKAT 1 — PALING BERBAHAYA (data tersimpan di database)

Kalau teks ini diganti tapi data lama tidak di-update, aplikasi akan menampilkan
status kosong / warna salah / filter tidak jalan. **Ini penyebab bug paling umum.**

### 1. Status pesanan — 3 nilai, 20 baris total

| Nilai | Baris | Disimpan di DB? |
|---|---|---|
| `"Belum Diproses"` | 312, 364, 2381, 2686, 2687 | ✅ ya (DEFAULT kolom) |
| `"Proses"` | 587, 593, 2381, 2606, 2686, 2687, 3064 | ✅ ya |
| `"Selesai"` | 586, 592, 1837, 2381, 2444, 2607, 2687, 3065 | ✅ ya |

**Kenapa berbahaya:** nilai ini bukan cuma ditampilkan, tapi:
- disimpan ke kolom `status` di SQLite (baris 2566, 3053)
- dipakai `.equals()` untuk menentukan **warna lencana** (baris 586–593)
- dipakai untuk **filter** dropdown (baris 2381)
- dipakai menentukan **apakah WhatsApp dikirim** (baris 2444)
- dipakai menghitung **statistik "Sedang Proses"** (baris 2606)

⚠️ **JEBAKAN TERSEMBUNYI:** nilai status juga ada di dalam string SQL pakai
**kutip tunggal**, yang TIDAK akan tertangkap kalau kamu cari pakai tanda kutip dua:

```java
// baris 228
"status TEXT DEFAULT 'Belum Diproses', tanggal TEXT, " +
// baris 232
s.executeUpdate("UPDATE pesanan SET status = 'Proses' WHERE status IN ('Cuci', 'Setrika')");
// baris 233
s.executeUpdate("UPDATE pesanan SET status = 'Belum Diproses' WHERE status IS NULL ...");
// baris 290
s.executeUpdate("ALTER TABLE pesanan ADD COLUMN status TEXT DEFAULT 'Belum Diproses'");
```

**Data di database kamu sekarang** (sudah saya cek `db_laundry.db`):

```
status : Proses = 3 baris, Selesai = 3 baris
jenis  : Kiloan = 4 baris, Satuan = 2 baris
item   : Pakaian = 4, Jaket = 1, Sepatu = 1
```

Kalau kamu ganti `"Proses"` → `"Sedang Dicuci"` di kode tapi tidak di database,
6 baris data lama itu akan jadi **status tak dikenal**: lencana jadi abu-abu,
filter tidak menemukannya, statistik jadi 0.

**Cara aman:** ganti kode, lalu update database:
```bat
python alat/ganti_teks.py "Proses" "Sedang Dicuci" --terapkan
python -c "import sqlite3;c=sqlite3.connect('db_laundry.db');c.execute(\"UPDATE pesanan SET status='Sedang Dicuci' WHERE status='Proses'\");c.commit()"
```

---

### 2. Jenis layanan — `"Kiloan"` & `"Satuan"`

| Nilai | Baris |
|---|---|
| `"Kiloan"` | 269, 307, 343, 430, 1809, 2758, 2765, 2767, 2838, 2896 |
| `"Satuan"` | 343, 1809 |

**Dipakai untuk:** menentukan tarif (Rp 7.000/kg vs per pcs), memilih kelas
`LaundryKiloan`/`LaundrySatuan`, menentukan satuan `kg`/`pcs`, mengunci pilihan item.

⚠️ Perhatikan baris 343 — nilai di-**hardcode ulang** di dalam setter:
```java
this.jenis = ("Satuan".equals(jenis)) ? "Satuan" : "Kiloan";
```
Jadi ganti di 1 tempat saja pasti rusak.

---

### 3. Item layanan — `"Pakaian"`, `"Jaket"`, `"Sepatu"`, `"Karpet"`

| Nilai | Baris | Dipakai untuk |
|---|---|---|
| `"Pakaian"` | 308, 347, 1810, 2840, 2898 | nilai default, isi dropdown |
| `"Jaket"` | 103, 114, 2842 | tarif Rp 15.000, estimasi 2 hari |
| `"Sepatu"` | 102, 113, 2843 | tarif Rp 25.000, estimasi 2 hari |
| `"Karpet"` | 104, 112, 2844 | tarif Rp 20.000, estimasi 3 hari |

**Kenapa berbahaya:** harga ditentukan dari `.equals()` nama item (baris 101–105).
Kalau kamu ganti `"Sepatu"` → `"Sepatu Kulit"` di dropdown tapi tidak di baris 102,
tarifnya **otomatis jatuh ke Rp 10.000** (nilai default `return 10000`).
Uang pelanggan jadi salah hitung — dan tidak ada error yang muncul.

---

## TINGKAT 2 — BERBAHAYA (logika tampilan)

### 4. Nama kolom tabel — `"Jenis"`, `"Tarif"`

Baris: **386** (definisi), **1278** (komentar), **1331–1335** (pemakaian)

```java
// baris 386-387 — daftar kolom
static final String[] KOLOM = {"ID", "Nama", "Telepon", "Jenis", "Item",
        "Jumlah", "Tarif", "Total", "Status", "Tanggal", "Est. Selesai", ""};

// baris 1335 — kolom "Jenis" & "Tarif" DISEMBUNYIKAN dengan mencocokkan NAMA
for (String judul : new String[]{"Jenis", "Tarif", ""}) {
    ... if (judul.equals(...getHeaderValue())) removeColumn(...)
```

**Kenapa berbahaya:** kalau kamu ganti judul kolom `"Jenis"` → `"Layanan"` di baris 386
saja, kolom itu **tidak akan tersembunyi lagi**, dan tabel jadi terpotong / berantakan.
Nama kolom dipakai sebagai *kunci logika*, bukan cuma label.

**Aman:** kolom `"ID"`, `"Nama"`, `"Telepon"`, `"Item"`, `"Jumlah"`, `"Total"`,
`"Status"`, `"Tanggal"`, `"Est. Selesai"` → bebas diganti (hanya tampil).

### 5. Label filter status — `"Status: Semua"` dan `"Status: "`

Baris: **1812, 2276, 2331**

```java
// baris 1812 — isi dropdown
new Pilih("Status: Semua", "Status: Belum Diproses", "Status: Proses", "Status: Selesai")

// baris 2276 — PREFIX DIPOTONG untuk dapat nilai aslinya
filterStatus = p.equals("Status: Semua") ? null : p.replace("Status: ", "");
```

**Kenapa berbahaya:** kode memotong awalan `"Status: "` untuk mendapatkan nilai
status asli. Kalau kamu ganti label jadi `"Filter: Semua"`, potongannya gagal →
`filterStatus` jadi `"Filter: Semua"` → filter tidak menemukan apa pun (tabel kosong).

⚠️ Perhatikan `"Status: "` (pakai spasi di akhir) juga harus ikut diganti.

---

## TINGKAT 3 — PERLU HATI-HATI (tapi bukan teks GUI)

### 6. Nama hari — 7 nilai

`"Senin"` (508, 604), `"Selasa"` (509, 604), `"Rabu"` (510, 604), `"Kamis"` (511, 604),
`"Jumat"` (512, 604), `"Sabtu"` (513, 604), `"Minggu"` (514, 604)

Muncul **2× per hari** — sekali di `switch` (baris 504–516), sekali di array
`tanggalPanjang()` (baris 604). Ini tampil di header aplikasi.
Kalau mau ganti (misal ke bahasa Inggris), ganti **kedua tempat**, kalau tidak
hasilnya campur: "Monday, 25 November".

> Nama bulan (Januari…Desember) juga di baris 605–606 — **aman** diganti, cuma 1 tempat.

### 7. Mode WhatsApp — `"struk"` & `"selesai"`

Baris: **2327, 2445** (dan dikirim ke n8n lewat baris 2516)

```java
kirimWa(p, "struk", "Struk");      // baris 2327
kirimWa(p, "selesai", "Notifikasi selesai");  // baris 2445
```

Nilai `"struk"`/`"selesai"` dikirim sebagai field `mode` ke workflow n8n di
`http://localhost:5678/webhook/laundry-struk`. Kalau diganti, **workflow n8n kamu
harus ikut diubah** — kalau tidak, pesan WA tidak akan terkirim.

### 8. Label hasil WA — `"nomor telepon tidak valid"`, `"ditolak server"`

Baris: **2508, 2509**

```java
if (badan.contains("tidak valid")) return "nomor telepon tidak valid";
if (badan.contains("ok\":false"))  return "ditolak server";
```

Ini **dicocokkan** dengan balasan server pakai `.contains()`.
Teks `"tidak valid"` adalah kunci deteksi — kalau diganti, deteksi error gagal
dan pesan yang muncul malah isi mentah JSON dari server.

---

## RINGKASAN: mana yang AMAN diganti 1 tempat

Semua ini **aman**, karena murni tampilan:

| Teks | Baris |
|---|---|
| `"Bening Laundry"` (brand di header) | 1205 |
| `"Bening Laundry — Kasir & Manajemen Pesanan"` (judul jendela) | 1857 |
| `"Tambah Pesanan"`, `"Bersihkan Form"`, `"Kirim Struk"`, `"Hapus"` | 1830–1833 |
| `"Pesanan baru"`, `"Isi data cucian pelanggan."` | 1815–1816 |
| `"Total Pesanan"`, `"Sedang Proses"`, `"Pendapatan (Selesai)"` | 1835–1838 |
| `"Harga Satuan"`, `"Total Estimasi"` | 1990, 2010 |
| `"Tidak ada hasil"`, `"Belum ada pesanan yang cocok."` | 1843–1844 |
| `"Muat Ulang"`, `"Hapus pencarian"` | 1217, 1845 |
| `"Hapus pesanan ini?"`, `"Batal"`, `"Hapus Pesanan"` | 1679, 1730, 1732 |
| `"Berat (kg)"`, `"Jumlah (pcs)"` | 1817, 2767 |
| `"Rp 7.000 / kg"` | 1818 |
| `"Menampilkan ... pesanan"` | 2942 |
| Nama bulan (Januari…Desember) | 605–606 |

> `"Rp 7.000 / kg"` di baris 1818 itu **label awal saja** — angka sebenarnya
> dihitung dari `TARIF_PER_KG = 7000` di **baris 68**. Kalau dosen minta ubah
> tarif, ubah baris 68 (bukan 1818), nanti label ter-update otomatis.

---

## Cara pakai alat untuk cek sendiri

```bat
python alat/analisis_teks.py                    :: daftar lengkap (22 teks)
python alat/analisis_teks.py --detail "Selesai" :: lihat semua kemunculan + perannya
```

Contoh output `--detail`:
```
baris 586
   peran : dibanding .equals()
   kode  : if ("Selesai".equals(status)) return HIJAU_TEKS;
```

---

## Aturan praktis (hafalkan)

1. **Teks muncul 1×** → aman, itu tampilan.
2. **Teks muncul >1×** → cek `analisis_teks.py` dulu.
3. **Teks ada di dalam `'...'` (kutip tunggal) di SQL** → itu data database,
   harus ikut di-update di `db_laundry.db`.
4. **Teks dipakai `.equals()`, `.replace()`, `.contains()`** → itu kunci logika,
   ganti serentak di SEMUA tempat.
5. Kalau ragu → jalankan `python alat/cek_risiko.py "Teksnya"`.

---

## BUKTI EKSPERIMEN (sudah diuji, bukan teori)

Semua di bawah ini diuji di salinan project, `javac` **berhasil tanpa error**,
tapi aplikasinya **rusak secara diam-diam**. Ini yang harus kamu tunjukkan ke dosen
kalau ditanya "kenapa nggak boleh ganti satu tempat saja?".

### Bukti 1 — Item `"Sepatu"` → `"Sepatu Kulit"` di dropdown saja

Output nyata dari `LaundrySatuan.tarifSatuan()`:

```
Sepatu         -> tarif Rp 25.000 | estimasi 2 hari
Sepatu Kulit   -> tarif Rp 10.000 | estimasi 2 hari   <-- turun Rp 15.000
Jaket          -> tarif Rp 15.000 | estimasi 2 hari
Karpet         -> tarif Rp 20.000 | estimasi 3 hari
Pakaian        -> tarif Rp 10.000 | estimasi 2 hari
```

Harga pelanggan salah Rp 15.000 per sepatu. **Tidak ada error, tidak ada warning.**

### Bukti 2 — Judul kolom `"Jenis"` → `"Layanan"` di baris 386 saja

```
Jumlah kolom awal : 12
Setelah dibuang   : 10      <-- seharusnya 9
Kolom tersisa     : [ID] [Nama] [Telepon] [Layanan] [Item] [Jumlah]
                    [Total] [Status] [Tanggal] [Est. Selesai]
```

Kolom `Layanan` tidak ikut terbuang karena baris 1335 masih mencari `"Jenis"`.
Tabel kelebihan 1 kolom → terpotong di panel kanan.

### Bukti 3 — Label `"Status: ..."` → `"Filter: ..."` di baris 1812 saja

```
== Label ASLI ==
  label "Status: Semua"    -> filterStatus = null      (benar)
  label "Status: Proses"   -> filterStatus = Proses    (benar)

== Setelah diganti "Filter: ..." ==
  label "Filter: Semua"    -> filterStatus = Filter: Semua   <-- SALAH
  label "Filter: Proses"   -> filterStatus = Filter: Proses  <-- SALAH
```

Filter mencari status `"Filter: Proses"` padahal database berisi `"Proses"`
→ **0 hasil, tabel tampak kosong** padahal ada 6 pesanan.

### Bukti 4 — Status diganti di kode, database tidak

Kode baris 2381 diganti jadi `"Sedang Dicuci"`, tapi isi `db_laundry.db`:

```
status di DB : ['Proses', 'Selesai']
jumlah baris status='Sedang Dicuci' : 0   <-- menu dropdown kosong
```

### Cara kamu memverifikasi sendiri (tanpa buka GUI)

Aplikasi ini punya mode diagnosa bawaan:

```bat
java -cp ".;sqlite-jdbc-3.54.1.0.jar" LaundryApp --cek
```

Outputnya:
```
Database   : C:\...\db_laundry.db
Total      : 6
Proses     : 3
Selesai    : 3
Pendapatan : Rp 112.000
Tarif kg   : Rp 7.000
Total 3 kg : Rp 21.000
Total 2 pcs Sepatu : Rp 50.000
Tanggal    : 29-10-2026
HASIL: OK
```

Ini cara cepat membuktikan perhitungan masih benar setelah kamu ganti teks —
tanpa perlu klik-klik GUI saat presentasi.

