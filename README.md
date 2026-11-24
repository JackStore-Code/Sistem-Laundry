# Sistem Pengelolaan Laundry (UAS Pemrograman Berorientasi Objek)

Aplikasi desktop kasir dan manajemen pesanan laundry sederhana berbasis Java Swing dan database SQLite. Dibuat dengan struktur kode yang ringkas, bersih, dan mudah dipahami oleh pemula.

---

## Penerapan 4 Pilar Konsep PBO (OOP)

Program ini menerapkan 4 pilar utama Object-Oriented Programming secara terstruktur:

1. **Abstraction (Abstraksi)**
   * Diterapkan pada kelas abstrak `abstract class LayananLaundry`.
   * Berfungsi sebagai *blueprint* (cetak biru) umum yang mewajibkan setiap jenis layanan menentukan tarifnya sendiri melalui method abstrak `public abstract double tarifSatuan()`.

2. **Inheritance (Pewarisan)**
   * Diterapkan pada kelas anak `LaundryKiloan` dan `LaundrySatuan` yang mewarisi sifat dari kelas induk:
     ```java
     class LaundryKiloan extends LayananLaundry
     class LaundrySatuan extends LayananLaundry
     ```

3. **Polymorphism (Polimorfisme)**
   * **Method Overriding:** Kelas `LaundryKiloan` dan `LaundrySatuan` mengimplementasikan logika penentuan tarif yang berbeda melalui `@Override public double tarifSatuan()`.
   * **Dynamic Binding:** Objek layanan dipanggil secara polimorfis melalui tipe referensi induk `LayananLaundry` untuk menghitung total biaya secara otomatis.

4. **Encapsulation (Enkapsulasi)**
   * Variabel data dilindungi menggunakan *access modifier* `private` dan `protected` agar tidak dapat dimodifikasi sembarangan dari luar kelas.

---

## Fitur Aplikasi

* **Pencatatan Pesanan:** Input nama pelanggan, nomor telepon, jenis layanan (Kiloan / Satuan), pilihan item, dan berat/jumlah.
* **Perhitungan Otomatis:** Menghitung tarif satuan dan total estimasi harga secara *real-time*.
* **Format ID Rapi:** ID pesanan otomatis berformat formal (`LND-001`, `LND-002`, dst.).
* **Format Nominal Bersih:** Angka tarif dan total menggunakan bilangan bulat murni (tanpa angka desimal/koma yang mengganggu).
* **Update Status Langsung di Tabel:** Status pesanan (*Belum Diproses*, *Proses*, *Selesai*) dapat diubah langsung hanya dengan 1 klik pada tabel data dan langsung tersimpan ke database SQLite.
* **Hapus & Reset Form:** Fitur menghapus data pesanan dan membersihkan form input.

---

## Tampilan Antarmuka (UI Modern)

Tampilan diperbarui dengan gaya *flat design* modern **tanpa library tambahan** (murni Swing + tema Nimbus):

* **Header Gradien:** Banner biru-teal dengan judul aplikasi dan dekorasi gelembung sabun.
* **Kartu Statistik (Dashboard):** 4 kartu di bawah header yang ter-update otomatis dari database: *Total Pesanan*, *Sedang Proses*, *Selesai*, dan *Pendapatan (Selesai)*.
* **Tombol Modern:** Tombol bersudut membulat dengan warna berbeda sesuai fungsi (tambah = teal, hapus = kuning, dll.) serta efek *hover* dan *klik*. Tombol `Enter` pada keyboard juga berfungsi sebagai tombol "Tambah Pesanan".
* **Tabel Zebra:** Baris data berselang-seling putih/abu-abu muda, header tabel berwarna teal gelap, dan baris terpilih berwarna teal muda.
* **Lencana Status Berwarna:** Kolom status tampil sebagai *badge* pil berwarna (kuning = *Belum Diproses*, biru = *Proses*, hijau = *Selesai*).
* **Format Rupiah Rapi:** Nominal dengan pemisah ribuan, contoh: `Rp 7.000`.
* **Format Tanggal:** Kolom tanggal ditampilkan dalam format `dd-MM-yyyy`.
* **Tabel Lebar Menyesuaikan Isi:** Lebar tiap kolom diukur dari teks terpanjang yang benar-benar ada (memakai renderer aslinya), lalu dipampatkan otomatis saat jendela diperkecil sehingga kolom *Tanggal* tidak pernah terdorong keluar layar. Teks yang belum muat bisa dibaca lewat *tooltip*.
* **Font Segoe UI:** Tampilan teks lebih modern dan mudah dibaca.

---

## Integrasi WhatsApp (opsional)

Aplikasi bisa mengirim pesan ke pelanggan lewat nomor WhatsApp toko yang terpisah
dari nomor pribadi. Dua pemicu:

1. **Notifikasi selesai** — otomatis saat status pesanan diubah menjadi *Selesai*.
   Pelanggan menerima teks: cucian sudah selesai dan siap diambil.
2. **Kirim Struk** — tombol yang muncul saat satu pesanan dipilih. Pelanggan
   menerima invoice PDF bernama `Invoice Laundry - <Nama Pelanggan>.pdf`.

Nomor pelanggan diisi dengan format `08xx`; sistem mengubahnya ke `628xx`
secara otomatis. Nomor yang tidak valid tidak akan dikirim.

Alamat server pengirim diatur di `config.properties`:

```
webhook.wa=http://localhost:5678/webhook/laundry-struk
```

Bila server pengirim tidak aktif, aplikasi tetap berjalan normal dan hanya
menampilkan pemberitahuan singkat. Nomor pengirim tidak pernah membalas chat
masuk, sehingga tidak mengganggu dan aman dari pembatasan WhatsApp.

---

## Cara Menjalankan

Pastikan Java (JDK 8 ke atas) sudah terpasang, lalu jalankan di folder proyek:

```
javac -encoding UTF-8 LaundryApp.java
java LaundryApp
```

Tanpa argumen, aplikasi membuka jendela kasir seperti biasa. Untuk memeriksa
logika tanpa membuka jendela (koneksi database, tarif, total, format tanggal):

```
java -cp ".;sqlite-jdbc-3.54.1.0.jar" LaundryApp --cek
```

Pemeriksaan itu akan mencetak lokasi database, jumlah pesanan per status,
pendapatan dari pesanan *Selesai*, lalu `HASIL: OK`.


### Menyalakan sistem sekali klik

Setelah laptop dinyalakan ulang, jalankan skrip pembantu (tidak disertakan dalam
repositori karena memuat pengaturan khusus mesin). Skrip itu:

1. menyalakan Docker Desktop bila belum jalan dan menunggu sampai siap,
2. menyalakan seluruh layanan pendukung (`evolution_db`, `evolution_redis`,
   `evolution_api`, `doc_generator`, `n8n`),
3. memeriksa apakah WhatsApp toko masih terhubung,
4. membuka aplikasi kasir.

Bila WhatsApp ditandai belum terhubung, aplikasi tetap jalan tetapi notifikasi
dan invoice tidak terkirim sampai sesi WhatsApp ditautkan ulang.
