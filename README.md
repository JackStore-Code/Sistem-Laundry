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

## Cara Menjalankan Program

Aplikasi ini tidak memerlukan instalasi web server (seperti XAMPP). Cukup pastikan Java sudah terpasang di komputer.

Jalankan perintah berikut di terminal:

```bash
java LaundryApp.java
```
