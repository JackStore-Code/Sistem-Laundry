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
* **Font Segoe UI:** Tampilan teks lebih modern dan mudah dibaca.

---

## Cara Menjalankan

Pastikan Java (JDK 8 ke atas) sudah terpasang, lalu jalankan di folder proyek:

```
javac -encoding UTF-8 LaundryApp.java
java LaundryApp
```

