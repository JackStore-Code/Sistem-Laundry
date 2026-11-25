import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/* =========================================================================
 * 1. PENERAPAN 4 KONSEP OOP (PBO)
 * ========================================================================= */

// [A] ABSTRACTION: Kelas abstrak sebagai cetak biru (blueprint) layanan
abstract class LayananLaundry {
    // [D] ENCAPSULATION: atribut dibuat private. Kelas lain tidak dapat
    // mengubah nilainya secara langsung; satu-satunya jalan adalah lewat
    // getJumlah() / setJumlah(), sehingga validasi di bawah tidak bisa dilewati.
    private double jumlah;

    public LayananLaundry(double jumlah) {
        setJumlah(jumlah);
    }

    public double getJumlah() {
        return jumlah;
    }

    public void setJumlah(double jumlah) {
        this.jumlah = Math.max(0, jumlah); // nilai negatif otomatis ditolak
    }

    // Method abstrak yang wajib diisi oleh kelas anak
    public abstract double tarifSatuan();

    // [C] POLYMORPHISM: setiap jenis layanan punya estimasi hari berbeda.
    // Nilai ini hanya SARAN AWAL untuk kasir; angka final tetap bisa diubah
    // manual di form karena kasir yang tahu beban kerja hari itu.
    public abstract int estimasiHariDefault();

    // Perhitungan umum total harga
    public double hitungTotal() {
        return getJumlah() * tarifSatuan();
    }
}

// [B] INHERITANCE: Kelas anak mewarisi LayananLaundry
class LaundryKiloan extends LayananLaundry {
    public static final double TARIF_PER_KG = 7000;

    public LaundryKiloan(double beratKg) {
        super(beratKg);
    }

    // [C] POLYMORPHISM: tarif kiloan dihitung per kilogram
    @Override
    public double tarifSatuan() {
        return TARIF_PER_KG;
    }

    // Cucian kiloan selesai dalam 1 hari kerja.
    @Override
    public int estimasiHariDefault() {
        return 1;
    }
}

class LaundrySatuan extends LayananLaundry {
    private final String item;

    public LaundrySatuan(double jumlahPcs, String item) {
        super(jumlahPcs);
        this.item = item;
    }

    public String getItem() {
        return item;
    }

    // [C] POLYMORPHISM: tarif satuan berbeda-beda sesuai jenis item
    @Override
    public double tarifSatuan() {
        if ("Sepatu".equals(item)) return 25000;
        if ("Jaket".equals(item))  return 15000;
        if ("Karpet".equals(item)) return 20000;
        return 10000;
    }

    // [C] POLYMORPHISM: estimasi hari bergantung tingkat kesulitan item.
    // Karpet butuh penjemuran lebih lama, sepatu/jaket butuh pengeringan.
    @Override
    public int estimasiHariDefault() {
        if ("Karpet".equals(item)) return 3;
        if ("Sepatu".equals(item)) return 2;
        if ("Jaket".equals(item))  return 2;
        return 2; // pakaian satuan
    }
}

/* =========================================================================
 * 2. KONEKSI DATABASE (SQLite)
 * ========================================================================= */
class DB {
    private static java.sql.Driver driver;
    private static String berkasDb;

    // Folder tempat berkas .class aplikasi ini berada (acuan yang stabil,
    // tidak berubah-ubah seperti folder kerja / current directory)
    private static File folderKelas() {
        try {
            java.security.CodeSource cs = DB.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                File f = new File(cs.getLocation().toURI());
                return (f.isFile() ? f.getParentFile() : f).getAbsoluteFile();
            }
        } catch (Exception diabaikan) { }
        return new File(".").getAbsoluteFile();
    }

    // Daftar folder yang dicari: folder .class, folder kerja, lalu folder induknya.
    private static java.util.List<File> folderKandidat() {
        java.util.LinkedHashSet<File> hasil = new java.util.LinkedHashSet<>();
        for (File dasar : new File[]{folderKelas(), new File(".").getAbsoluteFile()}) {
            File naik = dasar;
            for (int i = 0; i < 5 && naik != null; i++) {
                hasil.add(naik);
                naik = naik.getParentFile();
            }
        }
        return new java.util.ArrayList<>(hasil);
    }

    private static boolean adaJarSqlite(File folder) {
        File[] jar = folder.listFiles((d, nama) ->
                nama.toLowerCase().endsWith(".jar") && nama.toLowerCase().contains("sqlite"));
        return jar != null && jar.length > 0;
    }

    // Mencari driver SQLite: lewat classpath dulu, kalau tidak ada baru cari file .jar
    private static synchronized java.sql.Driver ambilDriver() throws Exception {
        if (driver != null) return driver;

        try {
            driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
            return driver;
        } catch (ClassNotFoundException | NoClassDefFoundError tidakAdaDiClasspath) { }

        for (File folder : folderKandidat()) {
            File[] jar = folder.listFiles((d, nama) ->
                    nama.toLowerCase().endsWith(".jar") && nama.toLowerCase().contains("sqlite"));
            if (jar == null || jar.length == 0) continue;
            try {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar[0].toURI().toURL()}, DB.class.getClassLoader());
                driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC", true, loader).getDeclaredConstructor().newInstance();
                return driver;
            } catch (Exception gagalMuatJar) { /* coba folder berikutnya */ }
        }

        throw new SQLException("Driver SQLite tidak ditemukan. Letakkan sqlite-jdbc-3.54.1.0.jar "
                + "di folder proyek, atau jalankan dengan: java -cp \".;sqlite-jdbc-3.54.1.0.jar\" LaundryApp");
    }

    // Menentukan letak berkas db_laundry.db (sekali saja, lalu diingat)
    private static synchronized String lokasiDatabase() {
        if (berkasDb != null) return berkasDb;

        java.util.List<File> folders = folderKandidat();
        File hasil = null;

        for (File f : folders) {
            File db = new File(f, "db_laundry.db");
            if (adaJarSqlite(f) && db.isFile()) { hasil = db; break; }
        }
        if (hasil == null) {
            for (File f : folders) {
                File db = new File(f, "db_laundry.db");
                if (db.isFile()) { hasil = db; break; }
            }
        }
        if (hasil == null) {
            for (File f : folders) {
                if (adaJarSqlite(f)) { hasil = new File(f, "db_laundry.db"); break; }
            }
        }
        if (hasil == null) hasil = new File(folderKelas(), "db_laundry.db");

        berkasDb = hasil.getAbsolutePath();
        return berkasDb;
    }

    static String infoLokasi() {
        return lokasiDatabase();
    }

    static Connection getKoneksi() throws Exception {
        java.sql.Driver d = ambilDriver();
        String url = "jdbc:sqlite:" + lokasiDatabase().replace('\\', '/');
        Connection c = d.connect(url, new Properties());
        if (c == null) throw new SQLException("Koneksi database ditolak: " + url);
        return c;
    }

    // Menyiapkan tabel pesanan (sekaligus merapikan database versi lama)
    static void buatTabel() throws Exception {
        try (Connection c = getKoneksi(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS pesanan (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, nama TEXT, telepon TEXT, " +
                    "jenis TEXT, item TEXT, jumlah REAL, tarif REAL, total REAL, " +
                    "status TEXT DEFAULT 'Belum Diproses', tanggal TEXT, " +
                    "estimasi_hari INTEGER DEFAULT 0, tanggal_selesai TEXT)");
            tambahKolomYangHilang(s);
            isiEstimasiLama();
            s.executeUpdate("UPDATE pesanan SET status = 'Proses' WHERE status IN ('Cuci', 'Setrika')");
            s.executeUpdate("UPDATE pesanan SET status = 'Belum Diproses' WHERE status IS NULL OR TRIM(status) = ''");
            s.executeUpdate("UPDATE pesanan SET tanggal = date('now') WHERE tanggal IS NULL OR TRIM(tanggal) = ''");
        }
    }

    /**
     * Pesanan yang dibuat sebelum fitur estimasi ada belum punya estimasi_hari
     * maupun tanggal_selesai. Method ini menghitungnya sekali dari jenis, item,
     * dan tanggal pesanan, supaya data lama tetap tampil lengkap di tabel.
     */
    private static void isiEstimasiLama() throws Exception {
        java.util.List<int[]> perlu = new java.util.ArrayList<>();
        java.util.List<String> jenis = new java.util.ArrayList<>();
        java.util.List<String> item = new java.util.ArrayList<>();
        java.util.List<String> tanggal = new java.util.ArrayList<>();

        try (Connection c = getKoneksi();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                     "SELECT id, jenis, item, tanggal FROM pesanan "
                     + "WHERE estimasi_hari IS NULL OR estimasi_hari <= 0 "
                     + "OR tanggal_selesai IS NULL OR TRIM(tanggal_selesai) = ''")) {
            while (r.next()) {
                perlu.add(new int[]{r.getInt("id")});
                jenis.add(r.getString("jenis"));
                item.add(r.getString("item"));
                tanggal.add(r.getString("tanggal"));
            }
        }
        if (perlu.isEmpty()) return;

        try (Connection c = getKoneksi();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE pesanan SET estimasi_hari = ?, tanggal_selesai = ? WHERE id = ?")) {
            for (int i = 0; i < perlu.size(); i++) {
                int id = perlu.get(i)[0];
                LayananLaundry layanan = "Kiloan".equals(jenis.get(i))
                        ? new LaundryKiloan(1)
                        : new LaundrySatuan(1, item.get(i));
                int hari = layanan.estimasiHariDefault();
                String dasar = tanggal.get(i);
                if (Tanggal.baca(dasar) == null) dasar = Tanggal.hariIni();
                p.setInt(1, hari);
                p.setString(2, Tanggal.hitungSelesai(dasar, hari));
                p.setInt(3, id);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private static void tambahKolomYangHilang(Statement s) throws Exception {
        java.util.Set<String> kolom = new java.util.HashSet<>();
        try (ResultSet r = s.executeQuery("PRAGMA table_info(pesanan)")) {
            while (r.next()) kolom.add(r.getString("name").toLowerCase());
        }
        if (!kolom.contains("tarif"))   s.executeUpdate("ALTER TABLE pesanan ADD COLUMN tarif REAL DEFAULT 0");
        if (!kolom.contains("status"))  s.executeUpdate("ALTER TABLE pesanan ADD COLUMN status TEXT DEFAULT 'Belum Diproses'");
        if (!kolom.contains("tanggal")) s.executeUpdate("ALTER TABLE pesanan ADD COLUMN tanggal TEXT");
        if (!kolom.contains("estimasi_hari"))   s.executeUpdate("ALTER TABLE pesanan ADD COLUMN estimasi_hari INTEGER DEFAULT 0");
        if (!kolom.contains("tanggal_selesai")) s.executeUpdate("ALTER TABLE pesanan ADD COLUMN tanggal_selesai TEXT");
    }
}

/* =========================================================================
 * 3. DATA PESANAN
 * ========================================================================= */
class Pesanan {
    // [D] ENCAPSULATION: seluruh atribut private. Data hanya dapat dibaca
    // dan diubah melalui getter/setter di bawah, sehingga tidak ada kelas
    // lain yang bisa menyisipkan nilai sembarangan secara langsung.
    private int id;
    private String nama = "";
    private String telepon = "";
    private String jenis = "Kiloan";
    private String item = "Pakaian";
    private double jumlah;
    private double tarif;
    private double total;
    private String status = "Belum Diproses";
    private String tanggal = "";
    private int estimasiHari;
    private String tanggalSelesai = "";

    // ---------- getter ----------
    public int getId()               { return id; }
    public String getNama()          { return nama; }
    public String getTelepon()       { return telepon; }
    public String getJenis()         { return jenis; }
    public String getItem()          { return item; }
    public double getJumlah()        { return jumlah; }
    public double getTarif()         { return tarif; }
    public double getTotal()         { return total; }
    public String getStatus()        { return status; }
    public String getTanggal()       { return tanggal; }
    public int getEstimasiHari()     { return estimasiHari; }
    public String getTanggalSelesai() { return tanggalSelesai; }

    // ---------- setter (dengan validasi) ----------
    public void setId(int id)        { this.id = id; }

    public void setNama(String nama) {
        this.nama = (nama == null) ? "" : nama.trim();
    }

    public void setTelepon(String telepon) {
        this.telepon = (telepon == null) ? "" : telepon.trim();
    }

    public void setJenis(String jenis) {
        this.jenis = ("Satuan".equals(jenis)) ? "Satuan" : "Kiloan";
    }

    public void setItem(String item) {
        this.item = (item == null || item.trim().isEmpty()) ? "Pakaian" : item.trim();
    }

    public void setJumlah(double jumlah) {
        this.jumlah = Math.max(0, jumlah); // nilai negatif ditolak
    }

    public void setTarif(double tarif) {
        this.tarif = Math.max(0, tarif);
    }

    public void setTotal(double total) {
        this.total = Math.max(0, total);
    }

    public void setStatus(String status) {
        this.status = (status == null || status.trim().isEmpty())
                ? "Belum Diproses" : status.trim();
    }

    public void setTanggal(String tanggal) {
        this.tanggal = (tanggal == null) ? "" : tanggal.trim();
    }

    public void setEstimasiHari(int estimasiHari) {
        this.estimasiHari = Math.max(0, estimasiHari); // negatif ditolak
    }

    public void setTanggalSelesai(String tanggalSelesai) {
        this.tanggalSelesai = (tanggalSelesai == null) ? "" : tanggalSelesai.trim();
    }

    String kodeId() {
        return String.format("LND-%03d", getId());
    }
}

// Model tabel: seluruh data ditampung di ArrayList (Java Collection Framework)
class ModelPesanan extends AbstractTableModel {
    static final String[] KOLOM = {"ID", "Nama", "Telepon", "Jenis", "Item",
            "Jumlah", "Tarif", "Total", "Status", "Tanggal", "Est. Selesai", ""};
    private final List<Pesanan> data = new ArrayList<>();

    void setData(List<Pesanan> baru) {
        data.clear();
        data.addAll(baru);
        fireTableDataChanged();
    }

    Pesanan ambil(int baris) {
        return data.get(baris);
    }

    List<Pesanan> semua() {
        return data;
    }

    @Override public int getRowCount() { return data.size(); }
    @Override public int getColumnCount() { return KOLOM.length; }
    @Override public String getColumnName(int c) { return KOLOM[c]; }
    @Override public boolean isCellEditable(int r, int c) { return false; }
    @Override public Class<?> getColumnClass(int c) { return String.class; }

    @Override
    public Object getValueAt(int baris, int kolom) {
        Pesanan p = data.get(baris);
        switch (kolom) {
            case 0:  return p.kodeId();
            case 1:  return p.getNama();
            case 2:  return p.getTelepon();
            case 3:  return p.getJenis();
            case 4:  return p.getItem();
            case 5:  return angkaJumlah(p.getJumlah()) + " " + satuan(p.getJenis());
            case 6:  return Tema.rupiah(Math.round(p.getTarif()));
            case 7:  return Tema.rupiah(Math.round(p.getTotal()));
            case 8:  return p.getStatus();
            case 9:  return tanggalTampil(p.getTanggal());
            case 10: return tanggalTampil(p.getTanggalSelesai());
            default: return "";
        }
    }

    static String satuan(String jenis) {
        return "Kiloan".equals(jenis) ? "kg" : "pcs";
    }

    static String angkaJumlah(double n) {
        if (n == Math.rint(n)) return String.valueOf((long) n);
        return new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.US)).format(n).replace('.', ',');
    }

    static String tanggalTampil(String t) {
        if (t != null && t.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] b = t.split("-");
            return b[2] + "-" + b[1] + "-" + b[0];
        }
        return t == null ? "" : t;
    }
}

/* =========================================================================
 * 4. UTILITAS TANGGAL (perhitungan estimasi waktu selesai)
 * ========================================================================= */
final class Tanggal {

    private Tanggal() { } // kelas utilitas, tidak untuk diinstansiasi

    static final java.time.format.DateTimeFormatter FORMAT_DB =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Hari kerja laundry: Senin-Sabtu. Minggu libur, jadi tidak dihitung.
    static boolean hariKerja(java.time.LocalDate t) {
        return t.getDayOfWeek() != java.time.DayOfWeek.SUNDAY;
    }

    /**
     * Menjumlahkan sejumlah hari KERJA ke sebuah tanggal, dengan melewati
     * hari Minggu. Contoh: Jumat + 2 hari kerja = Senin (Sabtu dihitung,
     * Minggu dilewati).
     */
    static java.time.LocalDate tambahHariKerja(java.time.LocalDate mulai, int hari) {
        if (mulai == null) return null;
        java.time.LocalDate hasil = mulai;
        int sisa = Math.max(0, hari);
        while (sisa > 0) {
            hasil = hasil.plusDays(1);
            if (hariKerja(hasil)) sisa--;
        }
        return hasil;
    }

    /** Tanggal hari ini menurut jam sistem, dalam format database yyyy-MM-dd. */
    static String hariIni() {
        return java.time.LocalDate.now().format(FORMAT_DB);
    }

    /**
     * Menghitung tanggal selesai dari tanggal pesanan + estimasi hari kerja.
     * Mengembalikan string kosong bila tanggal tidak valid.
     */
    static String hitungSelesai(String tanggalPesanan, int estimasiHari) {
        java.time.LocalDate mulai = baca(tanggalPesanan);
        if (mulai == null) return "";
        return tambahHariKerja(mulai, estimasiHari).format(FORMAT_DB);
    }

    /** Membaca tanggal format database; null bila kosong/tidak valid. */
    static java.time.LocalDate baca(String t) {
        if (t == null || !t.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        try {
            return java.time.LocalDate.parse(t, FORMAT_DB);
        } catch (Exception e) {
            return null;
        }
    }

    /** Nama hari dalam bahasa Indonesia, mis. "Kamis". */
    static String namaHari(String tanggalDb) {
        java.time.LocalDate t = baca(tanggalDb);
        if (t == null) return "";
        switch (t.getDayOfWeek()) {
            case MONDAY:    return "Senin";
            case TUESDAY:   return "Selasa";
            case WEDNESDAY: return "Rabu";
            case THURSDAY:  return "Kamis";
            case FRIDAY:    return "Jumat";
            case SATURDAY:  return "Sabtu";
            default:        return "Minggu";
        }
    }

    /** Nama bulan dalam bahasa Indonesia, mis. "November". */
    static String namaBulan(String tanggalDb) {
        java.time.LocalDate t = baca(tanggalDb);
        if (t == null) return "";
        String[] bulan = {"Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
        return bulan[t.getMonthValue() - 1];
    }

    /** Tampilan ramah: "Kamis, 27 November 2026". */
    static String tampilPanjang(String tanggalDb) {
        java.time.LocalDate t = baca(tanggalDb);
        if (t == null) return tanggalDb == null ? "" : tanggalDb;
        return namaHari(tanggalDb) + ", " + t.getDayOfMonth() + " "
                + namaBulan(tanggalDb) + " " + t.getYear();
    }
}

/* =========================================================================
 * 5. TEMA (warna, font, format) - mengikuti mockup
 * ========================================================================= */
final class Tema {
    static final Color LATAR        = new Color(0xF4F7F8);
    static final Color KARTU        = Color.WHITE;
    static final Color GARIS        = new Color(0xDFE6EA);
    static final Color GARIS_HALUS  = new Color(0xE9EEF0);
    static final Color LATAR_LEMBUT = new Color(0xF9FBFC);
    static final Color LATAR_ABU    = new Color(0xF1F5F6);
    static final Color ZEBRA        = new Color(0xF9FBFC);

    static final Color TEAL         = new Color(0x087F75);
    static final Color TEAL_HOVER   = new Color(0x0A8F84);
    static final Color TEAL_TUA     = new Color(0x214F42);
    static final Color TEAL_LEMBUT  = new Color(0xE8F5F1);

    static final Color TEKS         = new Color(0x172B36);
    static final Color TEKS_ABU     = new Color(0x516068);
    static final Color TEKS_LEMAH   = new Color(0x707E7B);
    static final Color TEKS_SAMAR   = new Color(0x99A2A0);

    static final Color BIRU_BG      = new Color(0xE4EFFC);
    static final Color BIRU_TEKS    = new Color(0x2A5998);
    static final Color HIJAU_BG     = new Color(0xE3F5EC);
    static final Color HIJAU_TEKS   = new Color(0x1B6B48);
    static final Color AMBER_BG     = new Color(0xFDF1DD);
    static final Color AMBER_TEKS   = new Color(0x8A5A00);

    static final Color MERAH        = new Color(0xB63731);
    static final Color MERAH_HOVER  = new Color(0xC24A44);
    static final Color MERAH_BG     = new Color(0xFFF0EF);

    static Font f(float ukuran, boolean tebal) {
        return new Font("Segoe UI", tebal ? Font.BOLD : Font.PLAIN, 12).deriveFont(ukuran);
    }

    static final Font JUDUL   = f(19f, true);
    static final Font SUB     = f(12f, false);
    static final Font LABEL   = f(12f, true);
    static final Font ISI     = f(13f, false);
    static final Font NILAI   = f(25f, true);
    static final Font CAP     = f(11f, false);
    static final Font TOMBOL  = f(13f, true);
    static final Font TABEL   = f(12f, false);
    static final Font TABEL_TEBAL = f(12f, true);
    static final Font KEPALA  = f(10.5f, true);
    static final Font LENCANA = f(10.5f, true);

    static Color warnaStatus(String status) {
        if ("Selesai".equals(status)) return HIJAU_TEKS;
        if ("Proses".equals(status))  return BIRU_TEKS;
        return AMBER_TEKS;
    }

    static Color latarStatus(String status) {
        if ("Selesai".equals(status)) return HIJAU_BG;
        if ("Proses".equals(status))  return BIRU_BG;
        return AMBER_BG;
    }

    static String rupiah(long nilai) {
        DecimalFormatSymbols simbol = new DecimalFormatSymbols();
        simbol.setGroupingSeparator('.');
        return "Rp " + new DecimalFormat("#,##0", simbol).format(nilai);
    }

    static String tanggalPanjang() {
        String[] hari = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
        String[] bulan = {"Januari", "Februari", "Maret", "April", "Mei", "Juni",
                "Juli", "Agustus", "September", "Oktober", "November", "Desember"};
        Calendar k = Calendar.getInstance();
        return hari[k.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + k.get(Calendar.DAY_OF_MONTH) + " "
                + bulan[k.get(Calendar.MONTH)] + " "
                + k.get(Calendar.YEAR);
    }

    private Tema() { }
}

// Utilitas menggambar bentuk membulat dan ikon sederhana
final class Gambar {
    static Graphics2D siap(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static void isi(Graphics2D g, double x, double y, double w, double h, double r, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
    }

    static void garis(Graphics2D g, double x, double y, double w, double h, double r, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, r * 2, r * 2));
    }

    static void panahBawah(Graphics2D g, double cx, double cy, double lebar, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (cx - lebar / 2), (int) (cy - 2), (int) cx, (int) (cy + 2));
        g.drawLine((int) cx, (int) (cy + 2), (int) (cx + lebar / 2), (int) (cy - 2));
    }

    static void cari(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - 5, cy - 5, 9, 9));
        g.drawLine((int) (cx + 3), (int) (cy + 3), (int) (cx + 7), (int) (cy + 7));
    }

    static void gembok(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new Arc2D.Double(cx - 4, cy - 7, 8, 9, 0, 180, Arc2D.OPEN));
        g.fill(new RoundRectangle2D.Double(cx - 5, cy - 2, 10, 8, 2, 2));
    }

    static void centang(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (cx - 5), (int) cy, (int) (cx - 1), (int) (cy + 4));
        g.drawLine((int) (cx - 1), (int) (cy + 4), (int) (cx + 6), (int) (cy - 5));
    }

    static void silang(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
        g.drawLine((int) (cx + r), (int) (cy - r), (int) (cx - r), (int) (cy + r));
    }

    static void panahUlang(Graphics2D g, double cx, double cy, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(cx - 6, cy - 6, 12, 12, 40, 280, Arc2D.OPEN));
        g.drawLine((int) (cx + 5), (int) (cy - 6), (int) (cx + 1), (int) (cy - 6));
        g.drawLine((int) (cx + 5), (int) (cy - 6), (int) (cx + 5), (int) (cy - 2));
    }

    // Ikon panah "Enter" (seperti tombol ↵) digambar manual supaya tidak jadi kotak
    static void panahEnter(Graphics2D g, double x, double y, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (x + 11), (int) (y - 5), (int) (x + 11), (int) y);
        g.drawLine((int) (x + 11), (int) y, (int) (x + 4), (int) y);
        g.drawLine((int) (x + 4), (int) y, (int) (x + 7), (int) (y - 3));
        g.drawLine((int) (x + 4), (int) y, (int) (x + 7), (int) (y + 3));
    }

    private Gambar() { }
}

/* =========================================================================
 * 5. KOMPONEN DASAR
 * ========================================================================= */

// Scrollbar tipis bergaya aplikasi (bukan gaya bawaan Nimbus)
class GulirTipis extends javax.swing.plaf.basic.BasicScrollBarUI {
    @Override protected JButton createDecreaseButton(int orientasi) { return tombolKosong(); }
    @Override protected JButton createIncreaseButton(int orientasi) { return tombolKosong(); }

    private JButton tombolKosong() {
        JButton b = new JButton();
        Dimension nol = new Dimension(0, 0);
        b.setPreferredSize(nol);
        b.setMinimumSize(nol);
        b.setMaximumSize(nol);
        return b;
    }

    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        g2.setColor(new Color(0xDFE6EA));
        g2.fill(new RoundRectangle2D.Double(r.x + 2, r.y + 2, r.width - 4, Math.max(20, r.height - 4), 8, 8));
        g2.dispose();
    }
}

// Kartu putih bersudut membulat dengan garis tepi tipis
class Kartu extends JPanel {
    int radius = 12;
    Color isi = Tema.KARTU;
    Color tepi = Tema.GARIS;

    Kartu(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        Gambar.isi(g2, 0, 0, getWidth(), getHeight(), radius, isi);
        Gambar.garis(g2, 0, 0, getWidth(), getHeight(), radius, tepi);
        g2.dispose();
    }
}

class PanelLatar extends JPanel {
    PanelLatar(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        setBackground(Tema.LATAR);
    }
}

// Tombol dengan beberapa varian warna
class Tombol extends JButton {
    enum Jenis { UTAMA, GARIS, LEMBUT_MERAH, MERAH }

    private final Jenis jenis;
    private boolean adaPetunjukEnter;

    Tombol(String teks, Jenis jenis) {
        super(teks);
        this.jenis = jenis;
        setContentAreaFilled(false);
        setOpaque(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Tema.TOMBOL);
        setBorder(new EmptyBorder(0, 16, 0, 16));
    }

    void pakaiPetunjukEnter() {
        adaPetunjukEnter = true;
        setBorder(new EmptyBorder(0, 16, 0, 40));
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, 38);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        boolean hover = getModel().isRollover() && isEnabled();
        int w = getWidth(), h = getHeight();
        Color latar, teks;

        switch (jenis) {
            case UTAMA:
                latar = hover ? Tema.TEAL_HOVER : Tema.TEAL;
                teks = Color.WHITE;
                break;
            case MERAH:
                latar = hover ? Tema.MERAH_HOVER : Tema.MERAH;
                teks = Color.WHITE;
                break;
            case LEMBUT_MERAH:
                latar = hover ? new Color(0xFFE3E1) : Tema.MERAH_BG;
                teks = Tema.MERAH;
                break;
            default:
                latar = hover ? new Color(0xF7FAFA) : Color.WHITE;
                teks = Tema.TEKS;
        }

        Gambar.isi(g2, 0, 0, w, h, 9, latar);
        if (jenis == Jenis.GARIS) Gambar.garis(g2, 0, 0, w, h, 9, Tema.GARIS);

        // Teks di tengah area isi (di dalam padding), bukan di tengah seluruh lebar,
        // supaya ikon di kiri tidak menimpa tulisan.
        Insets in = getInsets();
        int areaKiri = in.left;
        int areaLebar = w - in.left - in.right;
        FontMetrics fm = g2.getFontMetrics(getFont());
        g2.setFont(getFont());
        g2.setColor(teks);
        int teksX = areaKiri + Math.max(0, (areaLebar - fm.stringWidth(getText())) / 2);
        int teksY = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(getText(), teksX, teksY);

        if (adaPetunjukEnter) {
            Gambar.panahEnter(g2, w - 30, teksY - 5, Color.WHITE);
        }
        g2.dispose();
    }
}

// Kolom teks membulat dengan placeholder, ikon, dan satuan di kanan
class Kolom extends JTextField {
    private final String placeholder;
    private String satuan = "";
    private boolean ikonCari;
    private boolean galat;

    Kolom(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(false);
        setBorder(new EmptyBorder(0, 13, 0, 13));
        setFont(Tema.ISI);
        setForeground(Tema.TEKS);
        setCaretColor(Tema.TEKS);
        setSelectionColor(Tema.TEAL_LEMBUT);
        setSelectedTextColor(Tema.TEKS);
        setPreferredSize(new Dimension(0, 38));
    }

    void setSatuan(String s) {
        satuan = s == null ? "" : s;
        setBorder(new EmptyBorder(0, 13, 0, satuan.isEmpty() ? 13 : 40));
        repaint();
    }

    void pakaiIkonCari() {
        ikonCari = true;
        setBorder(new EmptyBorder(0, 36, 0, 13));
        repaint();
    }

    void setGalat(boolean g) {
        galat = g;
        repaint();
    }

    boolean isGalat() {
        return galat;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        int w = getWidth(), h = getHeight();
        Gambar.isi(g2, 0, 0, w, h, 8, galat ? Tema.MERAH_BG : Color.WHITE);
        Gambar.garis(g2, 0, 0, w, h, 8, galat ? Tema.MERAH : (isFocusOwner() ? Tema.TEAL : Tema.GARIS));

        if (ikonCari) Gambar.cari(g2, 20, h / 2.0, Tema.TEKS_LEMAH);

        if (satuan != null && !satuan.isEmpty()) {
            Font f = Tema.f(12f, false);
            FontMetrics fm = g2.getFontMetrics(f);
            g2.setFont(f);
            g2.setColor(Tema.TEKS_LEMAH);
            g2.drawString(satuan, w - 14 - fm.stringWidth(satuan),
                    (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();

        super.paintComponent(g);

        if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            Graphics2D g3 = Gambar.siap((Graphics2D) g.create());
            Insets in = getInsets();
            g3.setFont(getFont());
            g3.setColor(Tema.TEKS_SAMAR);
            FontMetrics fm = g3.getFontMetrics();
            g3.drawString(placeholder, in.left,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g3.dispose();
        }
    }
}

// Label kecil (badge/pil) untuk ID pesanan dan jumlah data
class Lencana extends JComponent {
    private String teks;
    private final Color latar;
    private final Color warnaTeks;

    Lencana(String teks) {
        this(teks, Tema.LATAR_ABU, Tema.TEKS_ABU);
    }

    Lencana(String teks, Color latar, Color warnaTeks) {
        this.teks = teks;
        this.latar = latar;
        this.warnaTeks = warnaTeks;
        setFont(Tema.LENCANA);
    }

    void setTeks(String t) {
        teks = t;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Tema.LENCANA);
        return new Dimension(fm.stringWidth(teks) + 22, 24);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 7, latar);
        FontMetrics fm = g2.getFontMetrics(Tema.LENCANA);
        g2.setFont(Tema.LENCANA);
        g2.setColor(warnaTeks);
        g2.drawString(teks, (getWidth() - fm.stringWidth(teks)) / 2,
                (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}

// Pilihan bergaya segmented (Kiloan / Satuan)
class Segmen extends JPanel {
    private final JToggleButton[] tombol;
    private Runnable aksiUbah;

    Segmen(String... opsi) {
        setLayout(new GridLayout(1, opsi.length));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 4, 4, 4));
        ButtonGroup grup = new ButtonGroup();
        tombol = new JToggleButton[opsi.length];
        for (int i = 0; i < opsi.length; i++) {
            JToggleButton t = new JToggleButton(opsi[i]) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                    if (isSelected()) {
                        Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 7, Color.WHITE);
                        Gambar.garis(g2, 0, 0, getWidth(), getHeight(), 7, Tema.TEAL);
                    }
                    Font f = Tema.f(12.5f, true);
                    FontMetrics fm = g2.getFontMetrics(f);
                    g2.setFont(f);
                    g2.setColor(isSelected() ? Tema.TEAL : Tema.TEKS_ABU);
                    g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    g2.dispose();
                }
            };
            t.setOpaque(false);
            t.setContentAreaFilled(false);
            t.setBorderPainted(false);
            t.setFocusPainted(false);
            t.setBorder(new EmptyBorder(6, 4, 6, 4));
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            grup.add(t);
            add(t);
            tombol[i] = t;
        }
        tombol[0].setSelected(true);
    }

    void setPilihan(String teks) {
        for (JToggleButton t : tombol) {
            if (t.getText().equals(teks) && !t.isSelected()) {
                t.setSelected(true);
                repaint();
                if (aksiUbah != null) aksiUbah.run();
            }
        }
        repaint();
    }

    String getPilihan() {
        for (JToggleButton t : tombol) {
            if (t.isSelected()) return t.getText();
        }
        return tombol[0].getText();
    }

    void saatUbah(Runnable aksi) {
        aksiUbah = aksi;
        for (JToggleButton t : tombol) {
            t.addActionListener(e -> {
                repaint();
                if (aksiUbah != null) aksiUbah.run();
            });
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 38);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 9, Tema.LATAR_ABU);
        g2.dispose();
    }
}

// Kotak pilihan (dropdown) dengan tampilan sendiri
class Pilih extends JComponent {
    private final List<String> opsi = new ArrayList<>();
    private int aktif;
    private boolean terkunci;
    private Runnable saatUbah;

    Pilih(String... daftar) {
        for (String s : daftar) opsi.add(s);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Tema.ISI);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!terkunci) bukaDaftar();
            }
        });
    }

    void setOpsi(List<String> baru) {
        opsi.clear();
        opsi.addAll(baru);
        aktif = 0;
        repaint();
    }

    void setPilihan(String teks) {
        int i = opsi.indexOf(teks);
        if (i >= 0 && i != aktif) {
            aktif = i;
            repaint();
            if (saatUbah != null) saatUbah.run();
        }
    }

    String getPilihan() {
        return opsi.isEmpty() ? "" : opsi.get(aktif);
    }

    void setTerkunci(boolean k) {
        terkunci = k;
        setCursor(Cursor.getPredefinedCursor(k ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        repaint();
    }

    void saatUbah(Runnable aksi) {
        saatUbah = aksi;
    }

    private void bukaDaftar() {
        final JPopupMenu pop = new JPopupMenu();
        pop.setOpaque(false);
        pop.setBorder(new EmptyBorder(0, 0, 0, 0));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 6, 6, 6));

        for (int i = 0; i < opsi.size(); i++) {
            final int idx = i;
            final JPanel baris = new JPanel(new BorderLayout());
            baris.setOpaque(false);
            baris.setBorder(new EmptyBorder(7, 10, 7, 10));
            baris.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel label = new JLabel(opsi.get(i));
            label.setFont(Tema.f(12.5f, idx == aktif));
            label.setForeground(idx == aktif ? Tema.TEAL : Tema.TEKS);
            baris.add(label, BorderLayout.CENTER);
            baris.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            baris.setPreferredSize(new Dimension(Math.max(getWidth(), 150), 34));
            baris.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    baris.setOpaque(true);
                    baris.setBackground(Tema.LATAR_ABU);
                    baris.repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    baris.setOpaque(false);
                    baris.repaint();
                }
                @Override public void mousePressed(MouseEvent e) {
                    aktif = idx;
                    pop.setVisible(false);
                    repaint();
                    if (saatUbah != null) saatUbah.run();
                }
            });
            panel.add(baris);
        }

        JPanel bingkai = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 9, Color.WHITE);
                Gambar.garis(g2, 0, 0, getWidth(), getHeight(), 9, Tema.GARIS);
                g2.dispose();
            }
        };
        bingkai.setOpaque(false);
        bingkai.setBorder(new EmptyBorder(2, 2, 2, 2));
        bingkai.add(panel, BorderLayout.CENTER);
        pop.add(bingkai);

        pop.show(this, 0, getHeight() + 4);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 40);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        int w = getWidth(), h = getHeight();
        Gambar.isi(g2, 0, 0, w, h, 8, terkunci ? Tema.LATAR_ABU : Color.WHITE);
        Gambar.garis(g2, 0, 0, w, h, 8, terkunci ? Tema.LATAR_ABU : Tema.GARIS);

        FontMetrics fm = g2.getFontMetrics(Tema.ISI);
        g2.setFont(Tema.ISI);
        g2.setColor(terkunci ? Tema.TEKS_ABU : Tema.TEKS);
        g2.drawString(getPilihan(), 13, (h - fm.getHeight()) / 2 + fm.getAscent());

        if (terkunci) Gambar.gembok(g2, w - 20, h / 2.0, Tema.TEKS_LEMAH);
        else Gambar.panahBawah(g2, w - 20, h / 2.0, 8, Tema.TEKS_LEMAH);
        g2.dispose();
    }
}

/* =========================================================================
 * 6. HEADER, KARTU STATISTIK, TABEL, TOAST, DIALOG
 * ========================================================================= */

class IkonLogo extends JComponent {
    IkonLogo() {
        setPreferredSize(new Dimension(40, 40));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        Gambar.isi(g2, 0, 0, 40, 40, 11, Tema.TEAL);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(new RoundRectangle2D.Double(9, 9, 22, 22, 6, 6));
        g2.draw(new Ellipse2D.Double(14, 15, 12, 12));
        g2.fill(new Ellipse2D.Double(19, 20, 2.4, 2.4));
        g2.drawLine(13, 12, 17, 12);
        g2.dispose();
    }
}

class PanelHeader extends JPanel {
    private final JLabel tanggal = new JLabel();

    PanelHeader(Runnable muatUlang) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(8, 24, 8, 24));
        setPreferredSize(new Dimension(0, 60));

        JPanel kiri = new JPanel(new BorderLayout(12, 0));
        kiri.setOpaque(false);
        kiri.add(new IkonLogo(), BorderLayout.WEST);

        JPanel judul = new JPanel();
        judul.setOpaque(false);
        judul.setLayout(new BoxLayout(judul, BoxLayout.Y_AXIS));
        JLabel nama = new JLabel("Bening Laundry");
        nama.setFont(Tema.JUDUL);
        nama.setForeground(Tema.TEKS);
        judul.add(nama);
        kiri.add(judul, BorderLayout.CENTER);

        JPanel kanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        kanan.setOpaque(false);
        tanggal.setFont(Tema.SUB);
        tanggal.setForeground(Tema.TEKS_LEMAH);
        tanggal.setText(Tema.tanggalPanjang());

        Tombol muat = new Tombol("Muat Ulang", Tombol.Jenis.GARIS) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.panahUlang(g2, 18, getHeight() / 2.0, Tema.TEKS_ABU);
                g2.dispose();
            }
        };
        muat.setBorder(new EmptyBorder(0, 30, 0, 14));
        muat.addActionListener(e -> muatUlang.run());

        kanan.add(tanggal);
        kanan.add(muat);

        add(kiri, BorderLayout.WEST);
        add(kanan, BorderLayout.EAST);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Tema.GARIS);
        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
    }
}

class KartuStatistik extends Kartu {
    private final JLabel nilai = new JLabel("0");

    KartuStatistik(String label, String caption, boolean utama) {
        super(new BorderLayout());
        setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel isi = new JPanel();
        isi.setOpaque(false);
        isi.setLayout(new BoxLayout(isi, BoxLayout.Y_AXIS));

        JLabel atas = new JLabel(label);
        atas.setFont(Tema.CAP);
        atas.setForeground(Tema.TEKS_LEMAH);

        nilai.setFont(Tema.NILAI);
        nilai.setForeground(utama ? Tema.TEAL : Tema.TEKS);

        isi.add(atas);
        isi.add(Box.createVerticalStrut(6));
        isi.add(nilai);
        add(isi, BorderLayout.CENTER);
    }

    void setNilai(String v) {
        nilai.setText(v);
    }
}

class TabelPesanan extends JTable {
    // Indeks MODEL: dipakai saat memasang renderer, sebelum kolom dibuang.
    static final int KOL_STATUS = 8;

    // Indeks TAMPILAN: dipakai sesuaikanLebar() yang berjalan SETELAH kolom
    // "Jenis", "Tarif", dan kolom aksi dibuang. Setelah pembuangan, urutan
    // kolom yang terlihat adalah:
    //   0 ID | 1 Nama | 2 Telepon | 3 Item | 4 Jumlah | 5 Total |
    //   6 Status | 7 Tanggal | 8 Est. Selesai
    static final int LIHAT_TOTAL = 5;
    static final int LIHAT_TANGGAL = 7;
    static final int LIHAT_EST_SELESAI = 8;

    TabelPesanan(ModelPesanan model) {
        super(model);
        setRowHeight(54);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setBackground(Color.WHITE);
        setForeground(Tema.TEKS);
        setFont(Tema.TABEL);
        setSelectionBackground(Tema.TEAL_LEMBUT);
        setSelectionForeground(Tema.TEKS);
        setFocusable(false);
        setRowSelectionAllowed(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setColumnSelectionAllowed(false);
        setAutoCreateRowSorter(false);
        // AUTO_RESIZE_LAST_COLUMN: lebar pref/larangan di bawah dihormati apa adanya
        // (JTable hanya membagi sisa ruang ke kolom terakhir). Sebelumnya memakai
        // lebar TETAP, sehingga saat jendela diperkecil (1120 px) total kolom (819)
        // melebihi tabel (706) dan kolom Tanggal terdorong keluar tanpa scrollbar.
        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        // Kolom yang sempit (mis. saat jendela diperkecil) tetap bisa dibaca lewat
        // tooltip berisi teks utuh sel tersebut.
        ToolTipManager.sharedInstance().registerComponent(this);
        setBorder(new EmptyBorder(0, 0, 0, 0));

        getTableHeader().setReorderingAllowed(false);
        getTableHeader().setPreferredSize(new Dimension(0, 44));
        getTableHeader().setDefaultRenderer(new KepalaTabel());

        // Renderer dipasang memakai indeks model, jadi harus sebelum kolom dibuang.
        TableCellRenderer biasa = new SelTabel(Tema.TEKS, Tema.TABEL, 18);
        TableCellRenderer redup = new SelTabel(Tema.TEKS_ABU, Tema.TABEL, 18);
        TableCellRenderer tebal = new SelTabel(Tema.TEKS, Tema.TABEL_TEBAL, 18);
        TableCellRenderer kode = new SelTabel(Tema.TEAL, Tema.TABEL_TEBAL, 18);
        setDefaultRenderer(String.class, biasa);
        getColumnModel().getColumn(0).setCellRenderer(kode);
        getColumnModel().getColumn(2).setCellRenderer(redup);
        getColumnModel().getColumn(5).setCellRenderer(redup);
        getColumnModel().getColumn(7).setCellRenderer(tebal);
        getColumnModel().getColumn(9).setCellRenderer(redup);
        getColumnModel().getColumn(10).setCellRenderer(redup);
        getColumnModel().getColumn(KOL_STATUS).setCellRenderer(new SelStatus());

        // Kolom yang dibuang dari tampilan karena sudah terwakili kolom lain:
        //  - "Jenis" (Kiloan/Satuan) terbaca dari satuan di kolom Jumlah ("9 kg" / "5 pcs")
        //  - "Tarif" bisa dihitung dari Total / Jumlah
        //  - kolom kosong terakhir (tombol aksi, dirender terpisah)
        // Menyisakan 8 kolom supaya semuanya muat di panel kanan tanpa terpotong.
        for (String judul : new String[]{"Jenis", "Tarif", ""}) {
            for (int i = getColumnModel().getColumnCount() - 1; i >= 0; i--) {
                if (judul.equals(String.valueOf(getColumnModel().getColumn(i).getHeaderValue()))) {
                    getColumnModel().removeColumn(getColumnModel().getColumn(i));
                }
            }
        }

        // Tinggi baris tabel ikut tinggi panel yang tersedia, jadi tidak ada
        // hamparan putih kosong di bawah baris terakhir saat data masih sedikit.
        pakaiTinggiPenuh();

        // Lebar kolom diukur dari isi tabel, lihat sesuaikanLebar().
        sesuaikanLebar();
    }

    private boolean tinggiPenuh = true;

    void setTinggiPenuh(boolean penuh) {
        tinggiPenuh = penuh;
        pakaiTinggiPenuh();
    }

    // Membagi tinggi viewport ke jumlah baris; hanya berlaku saat data sedikit
    // (bars < 12) dan lewat batas baris normal (54 px). Saat data banyak,
    // tinggi baris kembali normal dan scrollbar vertikal mengambil alih.
    private void pakaiTinggiPenuh() {
        int baris = getRowCount();
        boolean penuh = tinggiPenuh && baris > 0 && baris < 12;
        setFillsViewportHeight(penuh);
        if (!penuh) {
            setRowHeight(54);
            return;
        }
        // Dua kali ditunda: percobaan pertama kadang masih memakai tinggi viewport
        // layout lama (saat jendela baru dibuka atau datanya baru dimuat).
        SwingUtilities.invokeLater(() -> {
            hitungTinggiBaris();
            SwingUtilities.invokeLater(this::hitungTinggiBaris);
        });
    }

    private void hitungTinggiBaris() {
        int baris = getRowCount();
        if (!(tinggiPenuh && baris > 0 && baris < 12)) { setRowHeight(54); return; }
        int tinggiView = getParent() == null ? 0 : getParent().getHeight();
        if (tinggiView <= 0) return;
        setRowHeight(Math.max(54, Math.min(110, tinggiView / baris)));
    }

    // Kolom yang sempit (mis. saat jendela diperkecil) tetap bisa dibaca lewat tooltip.
    @Override
    public String getToolTipText(MouseEvent e) {
        int baris = rowAtPoint(e.getPoint());
        int kolom = columnAtPoint(e.getPoint());
        if (baris < 0 || kolom < 0) return null;
        Object nilai = getValueAt(baris, kolom);
        return nilai == null ? null : nilai.toString();
    }

    // Lebar kolom diambil dari renderer aslinya: lebar teks isi + padding sel,
    // sehingga saat data berubah (mis. ada nomor telepon lebih panjang) kolomnya
    // ikut melebar sendiri dan tidak pernah terpotong elipsis.
    // Nilai ini hanya preferensi: saat jendela mengecil JTable memampatkan kolom
    // sampai lebar minimum, dan saat melebar sisa ruang masuk ke kolom terakhir.
    void sesuaikanLebar() {
        // Satu entri per kolom yang TERLIHAT. Tanggal & Est. Selesai butuh 104 px
        // agar "25-11-2026" (10 karakter) terbaca utuh tanpa elipsis.
        int[] minimum = {84, 84, 88, 54, 44, 86, 104, 104, 104};
        List<Integer> perlu = new ArrayList<>();
        int totalMin = 0, totalPerlu = 0;
        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            int alami = lebarAlami(i);
            int min = Math.min(minimum[Math.min(i, minimum.length - 1)], alami);
            perlu.add(alami);
            totalMin += min;
            totalPerlu += alami;
        }

        // Sisakan ruang untuk scrollbar vertikal (10 px) saat tabel perlu di-scroll.
        int tersedia = getParent() == null ? getWidth() : getParent().getWidth();
        if (tersedia <= 0) tersedia = totalPerlu;
        int ruang = Math.max(0, tersedia - 10);

        // Bila total lebar minimum sudah melebihi ruang (mis. jendela diperkecil ke
        // minimum 1120 px), kolom dipampatkan proporsional. Tanpa ini kolom Tanggal
        // terdorong keluar layar karena horizontal scrollbar JScrollPane dimatikan.
        // Nominal (Total) tidak pernah dipampatkan karena angka uang harus terbaca utuh.
        if (totalMin > ruang) {
            int tetap = perlu.get(LIHAT_TOTAL);
            int sisa = Math.max(0, ruang - tetap);
            int sisaMin = 0;
            for (int i = 0; i < perlu.size(); i++) {
                if (i != LIHAT_TOTAL) sisaMin += Math.min(minimum[Math.min(i, minimum.length - 1)], perlu.get(i));
            }
            double skala = sisaMin == 0 ? 0 : (double) sisa / sisaMin;
            for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
                TableColumn kolom = getColumnModel().getColumn(i);
                int min = Math.min(minimum[Math.min(i, minimum.length - 1)], perlu.get(i));
                boolean tanggal = i == LIHAT_TANGGAL || i == LIHAT_EST_SELESAI;
                int batasBawah = tanggal ? 96 : 46;
                int hasil = i == LIHAT_TOTAL ? tetap : Math.max(batasBawah, (int) Math.floor(min * skala));
                kolom.setMinWidth(hasil);
                kolom.setPreferredWidth(hasil);
            }
            return;
        }

        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            TableColumn kolom = getColumnModel().getColumn(i);
            int min = Math.min(minimum[Math.min(i, minimum.length - 1)], perlu.get(i));
            kolom.setMinWidth(min);
            kolom.setPreferredWidth(perlu.get(i));
        }
    }

    // Lebar paling pas untuk sebuah kolom: lebar teks sel terpanjang (memakai
    // renderer aslinya) ditambah padding sel + 4 px cadangan.
    private int lebarAlami(int kolom) {
        TableCellRenderer renderer = getCellRenderer(0, kolom);
        if (renderer == null) return 96;
        int terlebar = lebarTeks(getTableHeader().getDefaultRenderer(), getColumnName(kolom));
        for (int baris = 0; baris < Math.min(getRowCount(), 200); baris++) {
            terlebar = Math.max(terlebar, lebarTeks(renderer, getValueAt(baris, kolom)));
        }
        return Math.min(terlebar + 4, 260);
    }

    private int lebarTeks(TableCellRenderer renderer, Object nilai) {
        if (renderer == null) return 0;
        Component c = renderer.getTableCellRendererComponent(
                this, nilai, false, false, 0, 0);
        if (c == null) return 0;
        // Lebar "preferred" JLabel tidak menghitung padding border kiri-kanan,
        // padahal isi sel dibuat & diukur dengan border tersebut. Bila border ikut
        // dimasukkan, pil status (border 18 kiri + 10 kanan + pil) bisa terpotong.
        Insets inset = c instanceof JComponent ? ((JComponent) c).getInsets() : new Insets(0, 0, 0, 0);
        return c.getPreferredSize().width + inset.left + inset.right;
    }

}

class KepalaTabel extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable tabel, Object nilai, boolean dipilih,
                                                   boolean fokus, int baris, int kolom) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(tabel, nilai, dipilih, fokus, baris, kolom);
        l.setText(nilai == null ? "" : nilai.toString().toUpperCase());
        l.setFont(Tema.KEPALA);
        l.setForeground(Tema.TEKS_LEMAH);
        l.setBackground(Tema.LATAR_LEMBUT);
        l.setOpaque(true);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setBorder(new EmptyBorder(0, 18, 0, 10));
        l.setIcon(null);
        return l;
    }
}

class SelTabel extends DefaultTableCellRenderer {
    private final Color warna;
    private final Font font;
    private final int padding;

    SelTabel(Color warna, Font font, int padding) {
        this.warna = warna;
        this.font = font;
        this.padding = padding;
        setHorizontalAlignment(SwingConstants.LEFT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabel, Object nilai, boolean dipilih,
                                                   boolean fokus, int baris, int kolom) {
        super.getTableCellRendererComponent(tabel, nilai, dipilih, fokus, baris, kolom);
        setText(nilai == null ? "" : nilai.toString());
        setFont(font);
        setForeground(warna);
        setBackground(dipilih ? Tema.TEAL_LEMBUT : (baris % 2 == 0 ? Color.WHITE : Tema.ZEBRA));
        setBorder(new EmptyBorder(0, padding, 0, 10));
        return this;
    }
}

class SelStatus extends JPanel implements TableCellRenderer {
    private String status = "";
    private boolean dipilih;
    private int baris;

    SelStatus() {
        setOpaque(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabel, Object nilai, boolean dipilih,
                                                   boolean fokus, int baris, int kolom) {
        this.status = nilai == null ? "" : nilai.toString();
        this.dipilih = dipilih;
        this.baris = baris;
        return this;
    }

    // Lencana memakai padding yang menyesuaikan lebar kolom: saat kolom sempit
    // (jendela diperkecil) padding menyusut supaya teks status tidak terpotong.
    static final int PAD_PIL = 22;
    static final int PAD_KIRI = 18;
    static final int TIINGGI_PIL = 24;

    @Override
    public Dimension getPreferredSize() {
        Font f = Tema.f(11f, true);
        FontMetrics fm = getFontMetrics(f);
        return new Dimension(fm.stringWidth(status) + PAD_PIL + PAD_KIRI + 8, 24);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        g2.setColor(dipilih ? Tema.TEAL_LEMBUT : (baris % 2 == 0 ? Color.WHITE : Tema.ZEBRA));
        g2.fillRect(0, 0, getWidth(), getHeight());

        Font f = Tema.f(11f, true);
        FontMetrics fm = g2.getFontMetrics(f);
        int teks = fm.stringWidth(status);
        // Padding kiri menyusut lebih dulu (min 8 px), lebar pil tetap utuh.
        int sisa = Math.max(0, getWidth() - teks - PAD_PIL);
        int x = Math.max(8, Math.min(PAD_KIRI, sisa));
        int lebar = Math.min(teks + PAD_PIL, Math.max(teks + 6, getWidth() - x));
        int y = (getHeight() - TIINGGI_PIL) / 2;
        Gambar.isi(g2, x, y, lebar, TIINGGI_PIL, 7, Tema.latarStatus(status));
        g2.setFont(f);
        g2.setColor(Tema.warnaStatus(status));
        g2.drawString(status, x + 11, y + (TIINGGI_PIL - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}

class SelAksi extends JPanel implements TableCellRenderer {
    private boolean dipilih;
    private int baris;

    SelAksi() {
        setOpaque(false);
        setToolTipText("Ganti status");
    }

    @Override
    public Component getTableCellRendererComponent(JTable tabel, Object nilai, boolean dipilih,
                                                   boolean fokus, int baris, int kolom) {
        this.dipilih = dipilih;
        this.baris = baris;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        g2.setColor(dipilih ? Tema.TEAL_LEMBUT : (baris % 2 == 0 ? Color.WHITE : Tema.ZEBRA));
        g2.fillRect(0, 0, getWidth(), getHeight());
        Gambar.panahBawah(g2, getWidth() / 2.0, getHeight() / 2.0, 9, Tema.TEKS_LEMAH);
        g2.dispose();
    }
}

// Notifikasi mengambang di kanan bawah
class Toast extends JPanel {
    Toast(String pesan, Runnable tutup) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(12, 16, 12, 12));

        JComponent ikon = new JComponent() {
            { setPreferredSize(new Dimension(18, 18)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.centang(g2, 9, 9, Color.WHITE);
                g2.dispose();
            }
        };
        JLabel teks = new JLabel(pesan);
        teks.setFont(Tema.f(12.5f, false));
        teks.setForeground(Color.WHITE);

        JLabel silang = new JLabel() {
            {
                setPreferredSize(new Dimension(18, 18));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.silang(g2, 9, 9, 4, Color.WHITE);
                g2.dispose();
            }
        };
        silang.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { tutup.run(); }
        });

        add(ikon, BorderLayout.WEST);
        add(teks, BorderLayout.CENTER);
        add(silang, BorderLayout.EAST);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, 48);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 9, Tema.TEAL_TUA);
        g2.dispose();
    }
}

// Dialog konfirmasi hapus (overlay di dalam jendela)
class DialogHapus extends JPanel {
    private final JPanel kartu = new JPanel(new BorderLayout());

    DialogHapus(String kode, String nama, Runnable saatBatal, Runnable saatHapus) {
        super(null);
        setOpaque(false);

        JPanel isi = new JPanel();
        isi.setOpaque(false);
        isi.setLayout(new BoxLayout(isi, BoxLayout.Y_AXIS));
        isi.setBorder(new EmptyBorder(20, 22, 18, 22));

        JPanel barisJudul = new JPanel(new BorderLayout(12, 0));
        barisJudul.setOpaque(false);
        JComponent ikon = new JComponent() {
            { setPreferredSize(new Dimension(34, 34)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.isi(g2, 0, 0, 34, 34, 9, Tema.MERAH_BG);
                g2.setFont(Tema.f(16f, true));
                g2.setColor(Tema.MERAH);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("!", (34 - fm.stringWidth("!")) / 2, 24);
                g2.dispose();
            }
        };
        JLabel judul = new JLabel("Hapus pesanan ini?");
        judul.setFont(Tema.f(15f, true));
        judul.setForeground(Tema.TEKS);

        JLabel tutup = new JLabel() {
            {
                setPreferredSize(new Dimension(20, 20));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.silang(g2, 10, 10, 4.5, Tema.TEKS_LEMAH);
                g2.dispose();
            }
        };
        tutup.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { saatBatal.run(); }
        });

        JPanel kiriJudul = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        kiriJudul.setOpaque(false);
        kiriJudul.add(ikon);
        JPanel tengah = new JPanel(new BorderLayout());
        tengah.setOpaque(false);
        tengah.setBorder(new EmptyBorder(6, 0, 0, 0));
        tengah.add(judul, BorderLayout.CENTER);
        kiriJudul.add(tengah);

        barisJudul.add(kiriJudul, BorderLayout.WEST);
        barisJudul.add(tutup, BorderLayout.EAST);

        JTextArea pesan = new JTextArea("Pesanan " + kode + " atas nama " + nama
                + " akan dihapus permanen. Tindakan ini tidak dapat dibatalkan.");
        pesan.setFont(Tema.f(12.5f, false));
        pesan.setForeground(Tema.TEKS_ABU);
        pesan.setOpaque(false);
        pesan.setEditable(false);
        pesan.setFocusable(false);
        pesan.setLineWrap(true);
        pesan.setWrapStyleWord(true);
        pesan.setBorder(new EmptyBorder(0, 0, 0, 0));
        pesan.setAlignmentX(LEFT_ALIGNMENT);
        pesan.setRows(2);
        pesan.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel petunjuk = new JLabel("Fokus awal: Batal \u2022 Enter untuk membatalkan");
        petunjuk.setFont(Tema.f(11f, false));
        petunjuk.setForeground(Tema.TEKS_SAMAR);

        JPanel barisTombol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        barisTombol.setOpaque(false);
        Tombol batal = new Tombol("Batal", Tombol.Jenis.GARIS);
        batal.setPreferredSize(new Dimension(92, 38));
        Tombol hapus = new Tombol("Hapus Pesanan", Tombol.Jenis.MERAH);
        hapus.setPreferredSize(new Dimension(150, 38));
        batal.addActionListener(e -> saatBatal.run());
        hapus.addActionListener(e -> saatHapus.run());
        barisTombol.add(batal);
        barisTombol.add(hapus);

        isi.add(barisJudul);
        isi.add(Box.createVerticalStrut(14));
        isi.add(pesan);
        isi.add(Box.createVerticalStrut(10));
        isi.add(petunjuk);
        isi.add(Box.createVerticalStrut(16));
        JPanel garis = new JPanel();
        garis.setBackground(Tema.GARIS_HALUS);
        garis.setPreferredSize(new Dimension(0, 1));
        garis.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        isi.add(garis);
        isi.add(Box.createVerticalStrut(14));
        isi.add(barisTombol);

        for (Component komponen : isi.getComponents()) {
            if (komponen instanceof JComponent) {
                ((JComponent) komponen).setAlignmentX(LEFT_ALIGNMENT);
            }
        }
        barisTombol.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        kartu.setOpaque(false);
        kartu.add(isi, BorderLayout.CENTER);
        add(kartu);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "batal");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ENTER"), "batal");
        getActionMap().put("batal", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { saatBatal.run(); }
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!kartu.getBounds().contains(e.getPoint())) saatBatal.run();
            }
        });
    }

    @Override
    public void doLayout() {
        int w = Math.min(460, getWidth() - 40);
        int h = Math.min(268, getHeight() - 40);
        kartu.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
        g2.setColor(new Color(23, 43, 54, 90));
        g2.fillRect(0, 0, getWidth(), getHeight());
        Gambar.isi(g2, kartu.getX(), kartu.getY(), kartu.getWidth(), kartu.getHeight(), 12, Color.WHITE);
        Gambar.garis(g2, kartu.getX(), kartu.getY(), kartu.getWidth(), kartu.getHeight(), 12, Tema.GARIS);
        g2.dispose();
    }
}

/* =========================================================================
 * 7. JENDELA UTAMA
 * ========================================================================= */
public class LaundryApp extends JFrame {

    private final ModelPesanan model = new ModelPesanan();
    private final TabelPesanan tabel = new TabelPesanan(model);
    private final TableRowSorter<ModelPesanan> saringan = new TableRowSorter<>(model);
    private final JPopupMenu menuStatus = new JPopupMenu();

    private final Kolom txtNama = new Kolom("Masukkan nama pelanggan");
    private final Kolom txtTelepon = new Kolom("Contoh: 081234567890");
    private final Kolom txtJumlah = new Kolom("0");
    private final Kolom txtEstimasi = new Kolom("0");
    private final Segmen segJenis = new Segmen("Kiloan", "Satuan");
    private final Pilih pilihItem = new Pilih("Pakaian");
    private final Kolom txtCari = new Kolom("Cari nama pelanggan atau ID...");
    private final Pilih pilihStatus = new Pilih("Status: Semua", "Status: Belum Diproses",
            "Status: Proses", "Status: Selesai");

    private final JLabel lblJudulForm = new JLabel("Pesanan baru");
    private final JLabel lblSubForm = new JLabel("Isi data cucian pelanggan.");
    private final JLabel lblLabelJumlah = new JLabel("Berat (kg)");
    private final JLabel lblHarga = new JLabel("Rp 7.000 / kg");
    private final JLabel lblTotal = new JLabel("\u2014");
    private final JLabel lblBantuanItem = new JLabel("Terkunci untuk layanan kiloan");
    private final JLabel lblEstimasiSelesai = new JLabel(" ");
    private final JLabel lblGalatEstimasi = new JLabel(" ");
    private final JLabel lblGalatNama = new JLabel(" ");
    private final JLabel lblGalatTelepon = new JLabel(" ");
    private final JLabel lblGalatJumlah = new JLabel(" ");
    private final Lencana lencanaId = new Lencana("LND-000");
    private final Lencana lencanaData = new Lencana("0 data");
    private final JLabel lblHitung = new JLabel("Menampilkan 0 dari 0 pesanan");
    private final JLabel lblInfoTabel = new JLabel("");
    private final Tombol btnUtama = new Tombol("Tambah Pesanan", Tombol.Jenis.UTAMA);
    private final Tombol btnKedua = new Tombol("Bersihkan Form", Tombol.Jenis.GARIS);
    private final Tombol btnHapus = new Tombol("Hapus", Tombol.Jenis.LEMBUT_MERAH);
    private final Tombol btnStruk = new Tombol("Kirim Struk", Tombol.Jenis.GARIS);

    private final KartuStatistik kartuTotal = new KartuStatistik("Total Pesanan", "Semua pesanan tersimpan", false);
    private final KartuStatistik kartuProses = new KartuStatistik("Sedang Proses", "Pesanan sedang dikerjakan", false);
    private final KartuStatistik kartuSelesai = new KartuStatistik("Selesai", "Pesanan berstatus Selesai", false);
    private final KartuStatistik kartuPendapatan = new KartuStatistik("Pendapatan (Selesai)", "Hanya dari pesanan selesai", true);

    private final CardLayout kartuTabel = new CardLayout();
    private final JPanel isiTabel = new JPanel(kartuTabel);
    private final JPanel panelKosong = new JPanel(new GridBagLayout());
    private final JLabel lblKosongJudul = new JLabel("Tidak ada hasil");
    private final JLabel lblKosongPesan = new JLabel("Belum ada pesanan yang cocok.");
    private final Tombol btnHapusCari = new Tombol("Hapus pencarian", Tombol.Jenis.GARIS);

    private int idEdit = -1;
    // Menandai kasir sudah mengubah estimasi secara manual. Selama masih false,
    // sistem terus memperbarui angkanya mengikuti jenis layanan yang dipilih.
    private boolean estimasiManual = false;
    private String kataKunci = "";
    private String filterStatus = null;
    private Toast toastAktif;
    private DialogHapus dialogAktif;

    public LaundryApp() {
        super("Bening Laundry \u2014 Kasir & Manajemen Pesanan");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(buatIkonAplikasi());

        JPanel akar = new PanelLatar(new BorderLayout());

        JPanel tengah = new JPanel(new BorderLayout(0, 14));
        tengah.setOpaque(false);
        tengah.setBorder(new EmptyBorder(14, 24, 12, 24));
        tengah.add(buatBarisStatistik(), BorderLayout.NORTH);
        tengah.add(buatIsiUtama(), BorderLayout.CENTER);
        akar.add(tengah, BorderLayout.CENTER);
        akar.add(buatFooterHalaman(), BorderLayout.SOUTH);

        setContentPane(akar);
        pasangAksi();
        perbaruiItem();
        hitungTotal();
        saranEstimasi();
        muatData();

        Dimension layar = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1400, layar.width - 40);
        int h = Math.min(950, layar.height - 40);
        setSize(w, h);
        setMinimumSize(new Dimension(1120, 620));
        setLocationRelativeTo(null);

        // Saat jendela di-resize, bagi ulang tinggi baris tabel.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                tabel.setTinggiPenuh(true);
            }
        });
    }

    /* ---------------- susunan tampilan ---------------- */

    private JPanel buatBarisStatistik() {
        JPanel baris = new JPanel(new GridLayout(1, 4, 22, 0));
        baris.setOpaque(false);
        baris.setPreferredSize(new Dimension(0, 86));
        baris.add(kartuTotal);
        baris.add(kartuProses);
        baris.add(kartuSelesai);
        baris.add(kartuPendapatan);
        return baris;
    }

    private JPanel buatIsiUtama() {
        JPanel isi = new JPanel(new BorderLayout(22, 0));
        isi.setOpaque(false);

        JPanel kiri = buatPanelForm();
        kiri.setPreferredSize(new Dimension(330, 0));
        isi.add(kiri, BorderLayout.WEST);
        isi.add(buatPanelTabel(), BorderLayout.CENTER);
        return isi;
    }

    private Kartu buatPanelForm() {
        Kartu kartu = new Kartu(new BorderLayout());
        kartu.setBorder(new EmptyBorder(12, 20, 10, 20));

        JPanel isi = new JPanel();
        isi.setOpaque(false);
        isi.setLayout(new BoxLayout(isi, BoxLayout.Y_AXIS));

        JPanel barisJudul = new JPanel(new BorderLayout());
        barisJudul.setOpaque(false);
        lblJudulForm.setFont(Tema.f(16f, true));
        lblJudulForm.setForeground(Tema.TEKS);
        barisJudul.add(lblJudulForm, BorderLayout.WEST);
        barisJudul.add(lencanaId, BorderLayout.EAST);
        barisJudul.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        lblSubForm.setVisible(false);
        lblSubForm.setFont(Tema.f(11.5f, false));
        lblSubForm.setForeground(Tema.TEKS_LEMAH);
        lblSubForm.setAlignmentX(LEFT_ALIGNMENT);

        isi.add(barisJudul);
        isi.add(Box.createVerticalStrut(8));

        isi.add(labelForm("Nama Pelanggan"));
        isi.add(Box.createVerticalStrut(3));
        isi.add(txtNama);
        isi.add(pesanGalat(lblGalatNama));
        isi.add(Box.createVerticalStrut(2));

        isi.add(labelForm("No. Telepon"));
        isi.add(Box.createVerticalStrut(3));
        isi.add(txtTelepon);
        isi.add(pesanGalat(lblGalatTelepon));
        isi.add(Box.createVerticalStrut(2));

        JPanel barisJenis = new JPanel(new BorderLayout(12, 0));
        barisJenis.setOpaque(false);
        barisJenis.add(labelForm("Jenis Layanan"), BorderLayout.WEST);
        segJenis.setPreferredSize(new Dimension(164, 34));
        barisJenis.add(segJenis, BorderLayout.EAST);
        barisJenis.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        isi.add(barisJenis);
        isi.add(Box.createVerticalStrut(2));

        isi.add(labelForm("Pilihan Item"));
        isi.add(Box.createVerticalStrut(3));
        isi.add(pilihItem);

        isi.add(lblLabelJumlah);
        lblLabelJumlah.setFont(Tema.LABEL);
        lblLabelJumlah.setForeground(Tema.TEKS_ABU);
        lblLabelJumlah.setAlignmentX(LEFT_ALIGNMENT);
        isi.add(Box.createVerticalStrut(3));
        isi.add(txtJumlah);
        isi.add(pesanGalat(lblGalatJumlah));

        isi.add(Box.createVerticalStrut(4));

        // Estimasi waktu selesai: nilai awal diisi sistem, tetap bisa diubah kasir.
        isi.add(labelForm("Estimasi Selesai (hari kerja)"));
        isi.add(Box.createVerticalStrut(3));
        isi.add(txtEstimasi);
        isi.add(pesanGalat(lblGalatEstimasi));
        lblEstimasiSelesai.setFont(Tema.f(11f, false));
        lblEstimasiSelesai.setForeground(Tema.TEAL);
        lblEstimasiSelesai.setAlignmentX(LEFT_ALIGNMENT);
        isi.add(lblEstimasiSelesai);

        isi.add(Box.createVerticalStrut(6));

        JPanel barisHarga = new JPanel(new BorderLayout());
        barisHarga.setOpaque(false);
        JLabel kiriHarga = new JLabel("Harga Satuan");
        kiriHarga.setFont(Tema.f(11.5f, false));
        kiriHarga.setForeground(Tema.TEKS_LEMAH);
        lblHarga.setFont(Tema.f(12f, true));
        lblHarga.setForeground(Tema.TEKS);
        barisHarga.add(kiriHarga, BorderLayout.WEST);
        barisHarga.add(lblHarga, BorderLayout.EAST);
        barisHarga.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        isi.add(barisHarga);
        isi.add(Box.createVerticalStrut(6));

        JPanel kotakTotal = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 8, Tema.TEAL_LEMBUT);
                g2.dispose();
            }
        };
        kotakTotal.setOpaque(false);
        kotakTotal.setBorder(new EmptyBorder(10, 13, 10, 13));
        JLabel labelTotal = new JLabel("Total Estimasi");
        labelTotal.setFont(Tema.f(11.5f, false));
        labelTotal.setForeground(Tema.TEAL);
        lblTotal.setFont(Tema.f(14f, true));
        lblTotal.setForeground(Tema.TEAL_TUA);
        kotakTotal.add(labelTotal, BorderLayout.WEST);
        kotakTotal.add(lblTotal, BorderLayout.EAST);
        kotakTotal.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        isi.add(kotakTotal);

        isi.add(Box.createVerticalStrut(5));
        btnUtama.pakaiPetunjukEnter();
        btnUtama.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        isi.add(btnUtama);
        isi.add(Box.createVerticalStrut(5));
        btnKedua.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        isi.add(btnKedua);

        JPanel bungkus = new JPanel(new BorderLayout());
        bungkus.setOpaque(false);
        bungkus.setBorder(new EmptyBorder(0, 0, 0, 2));

        // Isi form mengisi tinggi kartu: label & input di atas, tombol menempel di
        // dasar kartu supaya tidak ada ruang kosong menggantung di bawah tombol.
        JPanel atasForm = new JPanel();
        atasForm.setOpaque(false);
        atasForm.setLayout(new BoxLayout(atasForm, BoxLayout.Y_AXIS));

        isi.remove(btnUtama);
        isi.remove(btnKedua);
        // Pindahkan semua isi form (kecuali strut 5 px terakhir sebelum tombol) ke
        // atasForm yang menempel di atas; tombol dipasang di kaki kartu.
        while (isi.getComponentCount() > 0) {
            Component c = isi.getComponent(0);
            if (c instanceof Box.Filler && isi.getComponentCount() <= 1) break;
            isi.remove(0);
            atasForm.add(c);
        }
        for (Component komponen : atasForm.getComponents()) {
            if (komponen instanceof JComponent) ((JComponent) komponen).setAlignmentX(LEFT_ALIGNMENT);
        }

        JPanel kakiForm = new JPanel();
        kakiForm.setOpaque(false);
        kakiForm.setLayout(new BoxLayout(kakiForm, BoxLayout.Y_AXIS));
        kakiForm.add(btnUtama);
        kakiForm.add(Box.createVerticalStrut(6));
        kakiForm.add(btnKedua);
        for (Component komponen : kakiForm.getComponents()) {
            if (komponen instanceof JComponent) ((JComponent) komponen).setAlignmentX(LEFT_ALIGNMENT);
        }

        bungkus.add(atasForm, BorderLayout.CENTER);

        // Semua isi form rata kiri (BoxLayout butuh alignmentX yang sama)
        for (Component komponen : isi.getComponents()) {
            if (komponen instanceof JComponent) {
                ((JComponent) komponen).setAlignmentX(LEFT_ALIGNMENT);
            }
        }

        JScrollPane gulung = new JScrollPane(bungkus);
        gulung.setBorder(new EmptyBorder(0, 0, 0, 0));
        gulung.setOpaque(false);
        gulung.getViewport().setOpaque(false);
        gulung.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        gulung.getVerticalScrollBar().setUnitIncrement(16);
        gulung.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        gulung.getVerticalScrollBar().setUI(new GulirTipis());
        gulung.getVerticalScrollBar().setOpaque(false);

        // Tombol sengaja diletakkan DI LUAR area scroll: hanya isian form yang
        // bisa digulir, tombol Tambah/Bersihkan selalu terlihat di dasar kartu
        // walau layar pendek (mis. laptop 768 px) atau form bertambah panjang.
        JPanel kakiKartu = new JPanel();
        kakiKartu.setOpaque(false);
        kakiKartu.setLayout(new BoxLayout(kakiKartu, BoxLayout.Y_AXIS));
        kakiKartu.setBorder(new EmptyBorder(8, 0, 0, 2));
        kakiKartu.add(kakiForm);

        kartu.add(gulung, BorderLayout.CENTER);
        kartu.add(kakiKartu, BorderLayout.SOUTH);
        return kartu;
    }

    private JLabel labelForm(String teks) {
        JLabel l = new JLabel(teks);
        l.setFont(Tema.LABEL);
        l.setForeground(Tema.TEKS_ABU);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel pesanGalat(JLabel label) {
        label.setFont(Tema.f(10.5f, false));
        label.setForeground(Tema.MERAH);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setVisible(false);
        return label;
    }

    private Kartu buatPanelTabel() {
        Kartu kartu = new Kartu(new BorderLayout());
        kartu.setBorder(new EmptyBorder(16, 0, 0, 0));

        JPanel kepala = new JPanel(new BorderLayout());
        kepala.setOpaque(false);
        kepala.setBorder(new EmptyBorder(0, 20, 12, 20));

        JPanel kiri = new JPanel();
        kiri.setOpaque(false);
        kiri.setLayout(new BoxLayout(kiri, BoxLayout.Y_AXIS));
        JLabel judul = new JLabel("Daftar pesanan");
        judul.setFont(Tema.f(15.5f, true));
        judul.setForeground(Tema.TEKS);
        kiri.add(judul);
        judul.setAlignmentX(LEFT_ALIGNMENT);
        kepala.add(kiri, BorderLayout.WEST);

        JPanel kanan = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        kanan.setOpaque(false);
        kanan.add(lencanaData);
        kanan.add(buatTombolMuatUlang());
        kepala.add(kanan, BorderLayout.EAST);

        JPanel cari = new JPanel(new BorderLayout(10, 0));
        cari.setOpaque(false);
        cari.setBorder(new EmptyBorder(0, 20, 14, 20));
        txtCari.pakaiIkonCari();
        pilihStatus.setPreferredSize(new Dimension(190, 40));
        cari.add(txtCari, BorderLayout.CENTER);
        cari.add(pilihStatus, BorderLayout.EAST);

        JPanel atas = new JPanel();
        atas.setOpaque(false);
        atas.setLayout(new BoxLayout(atas, BoxLayout.Y_AXIS));
        atas.add(kepala);
        atas.add(cari);
        kartu.add(atas, BorderLayout.NORTH);

        tabel.setRowSorter(saringan);
        JScrollPane gulung = new JScrollPane(tabel);
        gulung.setBorder(new EmptyBorder(0, 0, 0, 0));
        gulung.setOpaque(false);
        gulung.getViewport().setOpaque(false);
        gulung.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        gulung.getVerticalScrollBar().setUnitIncrement(18);
        gulung.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        gulung.getVerticalScrollBar().setUI(new GulirTipis());
        gulung.getVerticalScrollBar().setOpaque(false);

        panelKosong.setOpaque(false);
        JPanel isiKosong = new JPanel();
        isiKosong.setOpaque(false);
        isiKosong.setLayout(new BoxLayout(isiKosong, BoxLayout.Y_AXIS));
        lblKosongJudul.setFont(Tema.f(14f, true));
        lblKosongJudul.setForeground(Tema.TEKS);
        lblKosongJudul.setAlignmentX(CENTER_ALIGNMENT);
        lblKosongPesan.setFont(Tema.f(12f, false));
        lblKosongPesan.setForeground(Tema.TEKS_LEMAH);
        lblKosongPesan.setAlignmentX(CENTER_ALIGNMENT);
        btnHapusCari.setAlignmentX(CENTER_ALIGNMENT);
        btnHapusCari.setMaximumSize(new Dimension(170, 38));
        btnHapusCari.setPreferredSize(new Dimension(170, 38));
        isiKosong.add(lblKosongJudul);
        isiKosong.add(Box.createVerticalStrut(6));
        isiKosong.add(lblKosongPesan);
        isiKosong.add(Box.createVerticalStrut(14));
        isiKosong.add(btnHapusCari);
        panelKosong.add(isiKosong);

        isiTabel.setOpaque(false);
        isiTabel.add(gulung, "tabel");
        isiTabel.add(panelKosong, "kosong");
        kartu.add(isiTabel, BorderLayout.CENTER);

        JPanel kaki = new JPanel(new BorderLayout());
        kaki.setOpaque(false);
        kaki.setBorder(new EmptyBorder(10, 20, 0, 20));

        lblHitung.setFont(Tema.f(11.5f, false));
        lblHitung.setForeground(Tema.TEKS_LEMAH);

        JPanel barisKaki = new JPanel(new BorderLayout());
        barisKaki.setOpaque(false);
        barisKaki.add(lblHitung, BorderLayout.WEST);
        JPanel kananKaki = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        kananKaki.setOpaque(false);
        btnHapus.setPreferredSize(new Dimension(96, 34));
        btnHapus.setVisible(false);
        btnStruk.setPreferredSize(new Dimension(116, 34));
        btnStruk.setVisible(false);
        kananKaki.add(btnStruk);
        kananKaki.add(javax.swing.Box.createHorizontalStrut(8));
        kananKaki.add(btnHapus);
        barisKaki.add(kananKaki, BorderLayout.EAST);

        kaki.add(barisKaki, BorderLayout.NORTH);
        kartu.add(kaki, BorderLayout.SOUTH);
        return kartu;
    }

    private JPanel buatFooterHalaman() {
        JPanel kaki = new JPanel(new BorderLayout());
        kaki.setOpaque(false);
        kaki.setBorder(new EmptyBorder(4, 26, 12, 26));
        return kaki;
    }

    // Tombol muat ulang ringkas: hanya ikon panah melingkar, tanpa teks.
    private JComponent buatTombolMuatUlang() {
        JComponent tombol = new JComponent() {
            private boolean disorot;
            {
                setToolTipText("Muat ulang data dari database");
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setPreferredSize(new Dimension(32, 32));
                setMinimumSize(new Dimension(32, 32));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { disorot = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { disorot = false; repaint(); }
                    @Override public void mouseReleased(MouseEvent e) {
                        if (!contains(e.getPoint()) || !javax.swing.SwingUtilities.isLeftMouseButton(e)) return;
                        muatData();
                        // Ikon berputar satu kali supaya kliknya terasa.
                        putaran = 0;
                        if (!pemutarIkon.isRunning()) pemutarIkon.start();
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                Gambar.isi(g2, 0, 0, getWidth(), getHeight(), 9,
                        disorot ? Tema.TEAL_LEMBUT : Color.WHITE);
                Gambar.garis(g2, 0.5, 0.5, getWidth() - 1, getHeight() - 1, 9, Tema.GARIS);
                g2.rotate(Math.toRadians(pemutarIkon.isRunning() ? putaran : 0),
                        getWidth() / 2.0, getHeight() / 2.0);
                Gambar.panahUlang(g2, getWidth() / 2.0, getHeight() / 2.0,
                        disorot ? Tema.TEAL : Tema.TEKS_ABU);
                g2.dispose();
            }
        };
        return tombol;
    }

    // Animasi putaran ikon muat ulang: 0 -> 360 derajat sekali klik.
    private int putaran = 0;
    private final javax.swing.Timer pemutarIkon = new javax.swing.Timer(16, e -> {
        putaran = (putaran + 24) % 360;
        if (putaran == 0) ((javax.swing.Timer) e.getSource()).stop();
        repaint();
    });

    /* ---------------- aksi ---------------- */

    private void pasangAksi() {
        txtCari.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { cariBerubah(); }
            @Override public void removeUpdate(DocumentEvent e) { cariBerubah(); }
            @Override public void changedUpdate(DocumentEvent e) { cariBerubah(); }
        });
        pilihStatus.saatUbah(() -> {
            String p = pilihStatus.getPilihan();
            filterStatus = p.equals("Status: Semua") ? null : p.replace("Status: ", "");
            terapkanSaringan();
        });

        segJenis.saatUbah(() -> {
            perbaruiItem();
            hitungTotal();
            saranEstimasi();
        });
        pilihItem.saatUbah(() -> {
            hitungTotal();
            saranEstimasi();
        });
        txtJumlah.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { hitungTotal(); }
            @Override public void removeUpdate(DocumentEvent e) { hitungTotal(); }
            @Override public void changedUpdate(DocumentEvent e) { hitungTotal(); }
        });
        // Begitu kasir mengetik sendiri di kolom estimasi, sistem berhenti
        // menimpa angkanya dan hanya memperbarui perkiraan tanggal selesai.
        txtEstimasi.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { estimasiDiketik(); }
            @Override public void removeUpdate(DocumentEvent e) { estimasiDiketik(); }
            @Override public void changedUpdate(DocumentEvent e) { estimasiDiketik(); }
        });
        // Nama yang sedang diketik tidak boleh memaksa kolom tabel melebar, jadi
        // lebar kolom selalu diukur ulang dari data yang benar-benar tersimpan.
        txtNama.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { tabel.sesuaikanLebar(); }
            @Override public void removeUpdate(DocumentEvent e) { tabel.sesuaikanLebar(); }
            @Override public void changedUpdate(DocumentEvent e) { tabel.sesuaikanLebar(); }
        });

        btnUtama.addActionListener(e -> {
            if (idEdit >= 0) simpanPerubahan();
            else tambahData();
        });
        btnKedua.addActionListener(e -> {
            if (idEdit >= 0) {
                keluarModeEdit();
            } else {
                bersihkanForm();
                txtNama.requestFocusInWindow();
            }
        });
        btnHapus.addActionListener(e -> {
            Pesanan p = cariPesanan(idEdit);
            if (p != null) konfirmasiHapus(p);
        });
        btnStruk.addActionListener(e -> {
            Pesanan p = cariPesanan(idEdit);
            if (p != null) kirimWa(p, "struk", "Struk");
        });
        btnHapusCari.addActionListener(e -> {
            txtCari.setText("");
            pilihStatus.setPilihan("Status: Semua");
            filterStatus = null;
            terapkanSaringan();
        });

        txtNama.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (!txtNama.getText().trim().isEmpty()) {
                    txtNama.setGalat(false);
                    lblGalatNama.setText("");
                    lblGalatNama.setVisible(false);
                }
            }
        });
        txtTelepon.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                if (!txtTelepon.getText().trim().isEmpty()) {
                    txtTelepon.setGalat(false);
                    lblGalatTelepon.setText("");
                    lblGalatTelepon.setVisible(false);
                }
            }
        });

        tabel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int baris = tabel.rowAtPoint(e.getPoint());
                int kolom = tabel.columnAtPoint(e.getPoint());
                if (baris < 0 || kolom < 0) return;
                int modelBaris = tabel.convertRowIndexToModel(baris);
                // Bandingkan indeks MODEL, bukan indeks view: setelah beberapa kolom
                // disembunyikan, posisi kolom Status di view bergeser.
                int modelKolom = tabel.convertColumnIndexToModel(kolom);
                Pesanan p = model.ambil(modelBaris);
                if (modelKolom == TabelPesanan.KOL_STATUS) {
                    tampilkanMenuStatus(p, tabel, e.getX(), e.getY());
                } else {
                    tabel.setRowSelectionInterval(baris, baris);
                    masukModeEdit(p);
                }
            }
        });

        getRootPane().setDefaultButton(btnUtama);
    }

    private void tampilkanMenuStatus(Pesanan p, Component anchor, int x, int y) {
        if (p == null) return;
        menuStatus.removeAll();
        menuStatus.setBorder(BorderFactory.createLineBorder(Tema.GARIS));
        for (String status : new String[]{"Belum Diproses", "Proses", "Selesai"}) {
            menuStatus.add(buatItemStatus(p, status));
        }

        // Menu digeser ke kiri bila akan menembus tepi kanan panel tabel.
        Component induk = anchor.getParent();
        int lebar = menuStatus.getPreferredSize().width;
        int batas = induk == null ? x + lebar : induk.getWidth() - 4;
        int xAman = Math.max(-lebar + 30, Math.min(x, batas - lebar));
        menuStatus.show(anchor, xAman, y);
    }

    // Setiap pilihan memakai warna statusnya sendiri (sama seperti pil di tabel):
    // latar pil lembut dengan teks berwarna senada.
    private JMenuItem buatItemStatus(Pesanan pesanan, String status) {
        JMenuItem item = new JMenuItem(status) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gambar.siap((Graphics2D) g.create());
                boolean disorot = getModel().isArmed() || getModel().isRollover();
                Color latar = Tema.latarStatus(status);
                Color warna = Tema.warnaStatus(status);

                g2.setColor(disorot ? lebihGelap(latar) : Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Pil kecil berisi teks status, meniru tampilan lencana di tabel.
                Font f = Tema.f(11f, true);
                FontMetrics fm = g2.getFontMetrics(f);
                int lebarPil = Math.min(fm.stringWidth(status) + 24, getWidth() - 20);
                int tinggiPil = 22;
                int yPil = (getHeight() - tinggiPil) / 2;
                Gambar.isi(g2, (getWidth() - lebarPil) / 2, yPil, lebarPil, tinggiPil, 7, latar);

                g2.setFont(f);
                g2.setColor(warna);
                int teksX = (getWidth() - fm.stringWidth(status)) / 2;
                g2.drawString(status, teksX, yPil + (tinggiPil - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        item.setOpaque(false);
        item.setBorder(new EmptyBorder(4, 4, 4, 4));
        item.setPreferredSize(new Dimension(190, 34));
        item.addActionListener(ev -> simpanStatus(pesanan, status));
        return item;
    }

    private static Color lebihGelap(Color c) {
        return new Color(Math.max(0, c.getRed() - 14), Math.max(0, c.getGreen() - 14), Math.max(0, c.getBlue() - 14));
    }

    private void simpanStatus(Pesanan p, String statusBaru) {
        try (Connection c = DB.getKoneksi(); PreparedStatement ps = c.prepareStatement("UPDATE pesanan SET status=? WHERE id=?")) {
            ps.setString(1, statusBaru);
            ps.setInt(2, p.getId());
            ps.executeUpdate();
            muatData();
            tampilkanToast("Status pesanan diubah menjadi " + statusBaru);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gagal mengubah status.\n" + ex.getMessage(), "Error Database", JOptionPane.ERROR_MESSAGE);
        }
        // Baru berubah ke Selesai -> kabari pelanggan lewat WhatsApp (di latar belakang).
        if ("Selesai".equals(statusBaru) && !"Selesai".equals(p.getStatus())) {
            kirimWa(p, "selesai", "Notifikasi selesai");
        }
    }

    /* ---------------- kirim struk / notifikasi WhatsApp ---------------- */

    // Alamat webhook n8n dan bisa diubah tanpa compile ulang lewat berkas
    // config.properties di folder aplikasi.
    private static final String URL_WEBHOOK = bacaKonfigurasi("webhook.wa",
            "http://localhost:5678/webhook/laundry-struk");

    private static String bacaKonfigurasi(String kunci, String bawaan) {
        java.io.File f = new java.io.File("config.properties");
        if (!f.isFile()) return bawaan;
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty(kunci);
            return v == null || v.trim().isEmpty() ? bawaan : v.trim();
        } catch (Exception e) {
            return bawaan;
        }
    }

    // Kirim data pesanan ke workflow n8n; jalan di thread terpisah supaya UI tidak
    // membeku. Hasilnya dilaporkan lewat toast.
    private void kirimWa(Pesanan p, String mode, String label) {
        final String json = buatJsonWa(p, mode);
        final String kode = p.kodeId();
        new Thread(() -> {
            String pesan;
            try {
                // n8n menggantung bila diminta HTTP/2, jadi paksa HTTP/1.1.
                java.net.http.HttpClient klien = java.net.http.HttpClient.newBuilder()
                        .version(java.net.http.HttpClient.Version.HTTP_1_1)
                        .connectTimeout(java.time.Duration.ofSeconds(8)).build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(URL_WEBHOOK))
                        .timeout(java.time.Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .build();
                java.net.http.HttpResponse<String> res = klien.send(req,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                String badan = res.body() == null ? "" : res.body();
                if (res.statusCode() == 200 && badan.contains("\"ok\":true")) {
                    pesan = label + " terkirim ke WA pelanggan (" + kode + ")";
                } else {
                    pesan = label + " gagal: " + ringkasPesan(badan);
                }
            } catch (java.net.http.HttpTimeoutException batas) {
                pesan = label + " gagal: server WA tidak merespons (timeout)";
            } catch (java.net.ConnectException mati) {
                pesan = label + " gagal: server WA tidak aktif";
            } catch (Exception ex) {
                pesan = label + " gagal: " + ex.getClass().getSimpleName();
            }
            final String teks = pesan;
            SwingUtilities.invokeLater(() -> tampilkanToast(teks));
        }, "wa-" + mode).start();
    }

    private static String ringkasPesan(String badan) {
        if (badan.contains("tidak valid")) return "nomor telepon tidak valid";
        if (badan.contains("ok\":false")) return "ditolak server";
        return badan.isEmpty() ? "tidak ada balasan" : badan.trim().substring(0, Math.min(60, badan.trim().length()));
    }

    // JSON disusun manual supaya tidak perlu library tambahan.
    private static String buatJsonWa(Pesanan p, String mode) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"mode\":\"").append(mode).append("\",");
        sb.append("\"kode\":\"").append(kodeJson(p.kodeId())).append("\",");
        sb.append("\"nama\":\"").append(kodeJson(p.getNama())).append("\",");
        sb.append("\"telepon\":\"").append(kodeJson(p.getTelepon())).append("\",");
        sb.append("\"jenis\":\"").append(kodeJson(p.getJenis())).append("\",");
        sb.append("\"item\":\"").append(kodeJson(p.getItem())).append("\",");
        sb.append("\"jumlah\":\"").append(ModelPesanan.angkaJumlah(p.getJumlah())).append("\",");
        sb.append("\"tarif\":").append(Math.round(p.getTarif())).append(",");
        sb.append("\"total\":").append(Math.round(p.getTotal())).append(",");
        sb.append("\"status\":\"").append(kodeJson(p.getStatus())).append("\",");
        sb.append("\"tanggal\":\"").append(kodeJson(p.getTanggal())).append("\",");
        sb.append("\"estimasi_hari\":").append(p.getEstimasiHari()).append(",");
        sb.append("\"tanggal_selesai\":\"").append(kodeJson(p.getTanggalSelesai())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String kodeJson(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == 92) b.append((char) 92).append((char) 92);
            else if (c == 34) b.append((char) 92).append((char) 34);
            else if (c == 10 || c == 13) b.append((char) 32);
            else b.append(c);
        }
        return b.toString();
    }

    private void cariBerubah() {
        kataKunci = txtCari.getText().trim().toLowerCase();
        terapkanSaringan();
    }

    /* ---------------- database ---------------- */

    private void muatData() {
        List<Pesanan> data = new ArrayList<>();
        try (Connection c = DB.getKoneksi(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM pesanan ORDER BY id DESC")) {
            while (r.next()) {
                Pesanan p = new Pesanan();
                p.setId(r.getInt("id"));
                p.setNama(teks(r.getString("nama")));
                p.setTelepon(teks(r.getString("telepon")));
                p.setJenis(teks(r.getString("jenis")));
                p.setItem(teks(r.getString("item")));
                p.setJumlah(r.getDouble("jumlah"));
                p.setTarif(r.getDouble("tarif"));
                p.setTotal(r.getDouble("total"));
                p.setStatus(teks(r.getString("status")));
                p.setTanggal(teks(r.getString("tanggal")));
                p.setEstimasiHari(r.getInt("estimasi_hari"));
                p.setTanggalSelesai(teks(r.getString("tanggal_selesai")));
                data.add(p);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data.\n" + e.getMessage()
                    + "\n\nLokasi database: " + DB.infoLokasi(), "Error Database", JOptionPane.ERROR_MESSAGE);
        }
        model.setData(data);
        tabel.sesuaikanLebar();
        tabel.setTinggiPenuh(true);
        perbaruiStatistik();
        terapkanSaringan();
        lencanaId.setTeks(idBerikutnya());
        sinkronkanPilihanEdit();
    }

    // muatData() dipanggil setiap kali status diubah, jadi tanpa ini form edit
    // kembali menampilkan data lama bila pesanan yang diedit tidak lagi cocok
    // dengan saringan aktif (barisnya hilang dari tabel).
    private void sinkronkanPilihanEdit() {
        if (idEdit < 0) return;
        Pesanan terkini = cariPesanan(idEdit);
        if (terkini == null) {
            keluarModeEdit();
            return;
        }
        segJenis.setPilihan(terkini.getJenis());
        perbaruiItem();
        pilihItem.setPilihan(terkini.getItem());
        hitungTotal();
    }

    private void perbaruiStatistik() {
        int total = 0, proses = 0, selesai = 0;
        long pendapatan = 0;
        for (Pesanan p : model.semua()) {
            total++;
            if ("Proses".equals(p.getStatus())) proses++;
            if ("Selesai".equals(p.getStatus())) {
                selesai++;
                pendapatan += Math.round(p.getTotal());
            }
        }
        kartuTotal.setNilai(String.valueOf(total));
        kartuProses.setNilai(String.valueOf(proses));
        kartuSelesai.setNilai(String.valueOf(selesai));
        kartuPendapatan.setNilai(Tema.rupiah(pendapatan));
        lencanaData.setTeks(total + " data");
    }

    private void tambahData() {
        if (!validasi()) return;

        LayananLaundry layanan = buatLayanan();
        int estimasi = estimasiAngka();
        String tanggalPesanan = Tanggal.hariIni();
        String tanggalSelesai = Tanggal.hitungSelesai(tanggalPesanan, estimasi);
        String sql = "INSERT INTO pesanan (nama, telepon, jenis, item, jumlah, tarif, total, "
                + "status, tanggal, estimasi_hari, tanggal_selesai) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Belum Diproses', ?, ?, ?)";
        try (Connection c = DB.getKoneksi(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, txtNama.getText().trim());
            p.setString(2, txtTelepon.getText().trim());
            p.setString(3, segJenis.getPilihan());
            p.setString(4, pilihItem.getPilihan());
            p.setDouble(5, layanan.getJumlah());
            p.setLong(6, Math.round(layanan.tarifSatuan()));
            p.setLong(7, Math.round(layanan.hitungTotal()));
            p.setString(8, tanggalPesanan);
            p.setInt(9, estimasi);
            p.setString(10, tanggalSelesai);
            p.executeUpdate();

            String kode = idBerikutnya();
            bersihkanForm();
            muatData();
            tampilkanToast("Pesanan " + kode + " ditambahkan");
        } catch (Exception e) {
            tampilkanToast("Gagal menambah data: " + e.getMessage());
        }
    }

    private void simpanPerubahan() {
        if (idEdit < 0) return;
        if (!validasi()) return;

        LayananLaundry layanan = buatLayanan();
        int estimasi = estimasiAngka();
        Pesanan lama = cariPesanan(idEdit);
        String tanggalPesanan = (lama != null && !lama.getTanggal().isEmpty())
                ? lama.getTanggal() : Tanggal.hariIni();
        String tanggalSelesai = Tanggal.hitungSelesai(tanggalPesanan, estimasi);
        String sql = "UPDATE pesanan SET nama = ?, telepon = ?, jenis = ?, item = ?, jumlah = ?, "
                + "tarif = ?, total = ?, estimasi_hari = ?, tanggal_selesai = ? WHERE id = ?";
        try (Connection c = DB.getKoneksi(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, txtNama.getText().trim());
            p.setString(2, txtTelepon.getText().trim());
            p.setString(3, segJenis.getPilihan());
            p.setString(4, pilihItem.getPilihan());
            p.setDouble(5, layanan.getJumlah());
            p.setLong(6, Math.round(layanan.tarifSatuan()));
            p.setLong(7, Math.round(layanan.hitungTotal()));
            p.setInt(8, estimasi);
            p.setString(9, tanggalSelesai);
            p.setInt(10, idEdit);
            p.executeUpdate();

            String kode = String.format("LND-%03d", idEdit);
            keluarModeEdit();
            muatData();
            tampilkanToast("Pesanan " + kode + " diperbarui");
        } catch (Exception e) {
            tampilkanToast("Gagal menyimpan perubahan: " + e.getMessage());
        }
    }

    private void gantiStatus(Pesanan p) {
        String baru = "Belum Diproses".equals(p.getStatus()) ? "Proses"
                : "Proses".equals(p.getStatus()) ? "Selesai" : "Belum Diproses";
        try (Connection c = DB.getKoneksi();
             PreparedStatement s = c.prepareStatement("UPDATE pesanan SET status = ? WHERE id = ?")) {
            s.setString(1, baru);
            s.setInt(2, p.getId());
            s.executeUpdate();
            muatData();
            tampilkanToast("Status " + p.kodeId() + " menjadi " + baru);
        } catch (Exception e) {
            tampilkanToast("Gagal mengubah status: " + e.getMessage());
        }
    }

    private void hapusPesanan(Pesanan p) {
        try (Connection c = DB.getKoneksi();
             PreparedStatement s = c.prepareStatement("DELETE FROM pesanan WHERE id = ?")) {
            s.setInt(1, p.getId());
            s.executeUpdate();
            keluarModeEdit();
            muatData();
            tampilkanToast("Pesanan dihapus");
        } catch (Exception e) {
            tampilkanToast("Gagal menghapus: " + e.getMessage());
        }
    }

    /* ---------------- form ---------------- */

    private boolean validasi() {
        boolean ok = true;
        boolean namaKosong = txtNama.getText().trim().isEmpty();
        txtNama.setGalat(namaKosong);
        lblGalatNama.setText(namaKosong ? "Nama pelanggan wajib diisi." : "");
        lblGalatNama.setVisible(namaKosong);
        if (namaKosong) ok = false;

        boolean teleponKosong = txtTelepon.getText().trim().isEmpty();
        txtTelepon.setGalat(teleponKosong);
        lblGalatTelepon.setText(teleponKosong ? "No. telepon wajib diisi." : "");
        lblGalatTelepon.setVisible(teleponKosong);
        if (teleponKosong) ok = false;

        boolean jumlahSalah = jumlahAngka() <= 0;
        txtJumlah.setGalat(jumlahSalah);
        lblGalatJumlah.setText(jumlahSalah ? "Jumlah harus lebih dari 0." : "");
        lblGalatJumlah.setVisible(jumlahSalah);
        if (jumlahSalah) ok = false;

        boolean estimasiSalah = estimasiAngka() <= 0;
        txtEstimasi.setGalat(estimasiSalah);
        lblGalatEstimasi.setText(estimasiSalah
                ? "Estimasi minimal 1 hari kerja." : "");
        lblGalatEstimasi.setVisible(estimasiSalah);
        if (estimasiSalah) ok = false;

        revalidate();
        return ok;
    }

    private double jumlahAngka() {
        String t = txtJumlah.getText().trim().replace(',', '.');
        if (t.isEmpty()) return 0;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LayananLaundry buatLayanan() {
        double jumlah = jumlahAngka();
        if ("Kiloan".equals(segJenis.getPilihan())) return new LaundryKiloan(jumlah);
        return new LaundrySatuan(jumlah, pilihItem.getPilihan());
    }

    private void hitungTotal() {
        LayananLaundry layanan = buatLayanan();
        long tarif = Math.round(layanan.tarifSatuan());
        String satuan = "Kiloan".equals(segJenis.getPilihan()) ? "kg" : "pcs";
        lblHarga.setText(Tema.rupiah(tarif) + " / " + satuan);
        lblLabelJumlah.setText("Kiloan".equals(segJenis.getPilihan()) ? "Berat (kg)" : "Jumlah (pcs)");
        txtJumlah.setSatuan(satuan);

        if (jumlahAngka() > 0) {
            lblTotal.setText(Tema.rupiah(Math.round(layanan.hitungTotal())));
            if (txtJumlah.isGalat()) {
                txtJumlah.setGalat(false);
                lblGalatJumlah.setText("");
                lblGalatJumlah.setVisible(false);
            }
        } else {
            lblTotal.setText("\u2014");
        }
    }

    /** Dipanggil setiap kali isi kolom estimasi berubah karena diketik kasir. */
    private void estimasiDiketik() {
        if (!estimasiManual && !txtEstimasi.getText().trim().isEmpty()) {
            estimasiManual = true;
        }
        perbaruiLabelEstimasi();
    }

    /** Angka estimasi yang sedang terisi di form (0 bila kosong/tidak valid). */
    private int estimasiAngka() {
        String t = txtEstimasi.getText().trim();
        if (t.isEmpty()) return 0;
        try {
            return Math.max(0, Integer.parseInt(t));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Mengisi ulang estimasi dengan SARAN dari sistem, berdasarkan jenis
     * layanan yang dipilih. Hanya berjalan selama kasir belum mengubah
     * angkanya sendiri (estimasiManual == false).
     */
    private void saranEstimasi() {
        if (estimasiManual) {
            perbaruiLabelEstimasi();
            return;
        }
        int saran = buatLayanan().estimasiHariDefault();
        txtEstimasi.setText(String.valueOf(saran));
        perbaruiLabelEstimasi();
    }

    /**
     * Menghitung dan menampilkan tanggal selesai dari estimasi yang sedang
     * terisi, supaya kasir langsung melihat dampaknya sebelum menyimpan.
     */
    private void perbaruiLabelEstimasi() {
        int hari = estimasiAngka();
        if (hari <= 0) {
            lblEstimasiSelesai.setText("Isi minimal 1 hari kerja.");
            lblEstimasiSelesai.setForeground(Tema.MERAH);
            return;
        }
        String dasar = Tanggal.hariIni();
        if (idEdit >= 0) {
            Pesanan p = cariPesanan(idEdit);
            if (p != null && !p.getTanggal().isEmpty()) dasar = p.getTanggal();
        }
        String selesai = Tanggal.hitungSelesai(dasar, hari);
        lblEstimasiSelesai.setForeground(Tema.TEAL);
        lblEstimasiSelesai.setText("Perkiraan selesai: " + Tanggal.tampilPanjang(selesai));
    }

    private void perbaruiItem() {
        boolean kiloan = "Kiloan".equals(segJenis.getPilihan());
        List<String> opsi = new ArrayList<>();
        opsi.add("Pakaian");
        if (!kiloan) {
            opsi.add("Jaket");
            opsi.add("Sepatu");
            opsi.add("Karpet");
        }
        pilihItem.setOpsi(opsi);
        pilihItem.setTerkunci(kiloan);
        lblBantuanItem.setText("");
    }

    private void masukModeEdit(Pesanan p) {
        if (p == null) return;
        idEdit = p.getId();
        lblJudulForm.setText("Edit pesanan");
        lblSubForm.setText("Ubah data pesanan yang dipilih.");
        lencanaId.setTeks(p.kodeId());
        txtNama.setText(p.getNama());
        txtTelepon.setText(p.getTelepon());
        segJenis.setPilihan(p.getJenis());
        perbaruiItem();
        pilihItem.setPilihan(p.getItem());
        txtJumlah.setText(ModelPesanan.angkaJumlah(p.getJumlah()));
        // Saat mengedit, estimasi yang tersimpan dianggap keputusan kasir,
        // jadi sistem tidak menimpanya lagi secara otomatis.
        estimasiManual = true;
        txtEstimasi.setText(String.valueOf(p.getEstimasiHari()));
        perbaruiLabelEstimasi();
        btnUtama.setText("Simpan Perubahan");
        btnKedua.setText("Batal Edit");
        btnHapus.setVisible(true);
        btnStruk.setVisible(true);
        lblInfoTabel.setText("Ikon \u2304: ganti status \u2022 " + p.kodeId()
                + " dipilih \u2022 Klik Batal Edit untuk kembali menambah pesanan.");
        hitungTotal();
    }

    private void keluarModeEdit() {
        idEdit = -1;
        lblJudulForm.setText("Pesanan baru");
        lblSubForm.setText("Isi data cucian pelanggan.");
        btnUtama.setText("Tambah Pesanan");
        btnKedua.setText("Bersihkan Form");
        btnHapus.setVisible(false);
        btnStruk.setVisible(false);
        lblInfoTabel.setText("Ikon \u2304: ganti status \u2022 Belum Diproses \u2192 Proses \u2192 Selesai \u2192 Belum Diproses");
        tabel.clearSelection();
        bersihkanForm();
        lencanaId.setTeks(idBerikutnya());
    }

    private void bersihkanForm() {
        txtNama.setText("");
        txtTelepon.setText("");
        txtJumlah.setText("");
        estimasiManual = false;
        segJenis.setPilihan("Kiloan");
        perbaruiItem();
        pilihItem.setPilihan("Pakaian");
        txtNama.setGalat(false);
        txtTelepon.setGalat(false);
        txtJumlah.setGalat(false);
        lblGalatNama.setText("");
        lblGalatTelepon.setText("");
        lblGalatJumlah.setText("");
        lblGalatNama.setVisible(false);
        lblGalatTelepon.setVisible(false);
        lblGalatJumlah.setVisible(false);
        hitungTotal();
    }

    private String idBerikutnya() {
        int maks = 0;
        for (Pesanan p : model.semua()) maks = Math.max(maks, p.getId());
        return String.format("LND-%03d", maks + 1);
    }

    private Pesanan cariPesanan(int id) {
        for (Pesanan p : model.semua()) {
            if (p.getId() == id) return p;
        }
        return null;
    }

    /* ---------------- pencarian & saringan ---------------- */

    private void terapkanSaringan() {
        saringan.setRowFilter(new javax.swing.RowFilter<ModelPesanan, Integer>() {
            @Override
            public boolean include(Entry<? extends ModelPesanan, ? extends Integer> entry) {
                Pesanan p = entry.getModel().ambil(entry.getIdentifier());
                boolean cocokKata = kataKunci.isEmpty()
                        || p.getNama().toLowerCase().contains(kataKunci)
                        || p.kodeId().toLowerCase().contains(kataKunci)
                        || p.getTelepon().toLowerCase().contains(kataKunci);
                boolean cocokStatus = filterStatus == null || filterStatus.equals(p.getStatus());
                return cocokKata && cocokStatus;
            }
        });

        int tampil = tabel.getRowCount();
        int semua = model.getRowCount();
        lblHitung.setText("Menampilkan " + tampil + " dari " + semua + " pesanan");

        if (tampil == 0) {
            boolean adaSaringan = !kataKunci.isEmpty() || filterStatus != null;
            lblKosongJudul.setText(adaSaringan ? "Tidak ada hasil" : "Belum ada pesanan");
            if (!kataKunci.isEmpty()) {
                lblKosongPesan.setText("Tidak ada nama pelanggan atau ID yang cocok dengan \""
                        + txtCari.getText().trim() + "\".");
            } else if (filterStatus != null) {
                lblKosongPesan.setText("Tidak ada pesanan dengan status " + filterStatus + ".");
            } else {
                lblKosongPesan.setText("Tambahkan pesanan pertama lewat form di sebelah kiri.");
            }
            btnHapusCari.setVisible(adaSaringan);
            kartuTabel.show(isiTabel, "kosong");
        } else {
            kartuTabel.show(isiTabel, "tabel");
        }
    }

    /* ---------------- notifikasi & dialog ---------------- */

    private void tampilkanToast(String pesan) {
        final JLayeredPane lapisan = getLayeredPane();
        if (toastAktif != null) {
            lapisan.remove(toastAktif);
            toastAktif = null;
        }
        Toast toast = new Toast(pesan, () -> {
            lapisan.remove(toastAktif);
            lapisan.repaint();
            toastAktif = null;
        });
        toastAktif = toast;
        Dimension d = toast.getPreferredSize();
        int lebar = Math.max(220, d.width + 8);
        toast.setBounds(lapisan.getWidth() - lebar - 24, lapisan.getHeight() - 48 - 24, lebar, 48);
        lapisan.add(toast, JLayeredPane.PALETTE_LAYER);
        lapisan.repaint();

        Timer t = new Timer(3200, e -> {
            if (toastAktif == toast) {
                lapisan.remove(toast);
                lapisan.repaint();
                toastAktif = null;
            }
        });
        t.setRepeats(false);
        t.start();
    }

    private void konfirmasiHapus(Pesanan p) {
        final JLayeredPane lapisan = getLayeredPane();
        DialogHapus dialog = new DialogHapus(p.kodeId(), p.getNama(),
                () -> {
                    lapisan.remove(dialogAktif);
                    lapisan.repaint();
                    dialogAktif = null;
                },
                () -> {
                    lapisan.remove(dialogAktif);
                    lapisan.repaint();
                    dialogAktif = null;
                    hapusPesanan(p);
                });
        dialogAktif = dialog;
        dialog.setBounds(0, 0, lapisan.getWidth(), lapisan.getHeight());
        lapisan.add(dialog, JLayeredPane.MODAL_LAYER);
        lapisan.repaint();
        dialog.requestFocusInWindow();
    }

    private static String teks(String s) {
        return s == null ? "" : s;
    }

    private Image buatIkonAplikasi() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = Gambar.siap(img.createGraphics());
        Gambar.isi(g2, 2, 2, 60, 60, 16, Tema.TEAL);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3f));
        g2.draw(new RoundRectangle2D.Double(16, 16, 32, 32, 9, 9));
        g2.draw(new Ellipse2D.Double(23, 23, 18, 18));
        g2.dispose();
        return img;
    }

    /* ---------------- diagnosa tanpa tampilan (--cek) ---------------- */

    // Dipakai untuk memverifikasi logika (database, tarif, statistik, saringan)
    // dari terminal tanpa membuka jendela Swing.
    private static int cekMandiri(String[] args) {
        Locale.setDefault(new Locale("id", "ID"));
        final int[] gagal = {0};

        try {
            DB.buatTabel();
            List<Pesanan> baris = new ArrayList<>();
            try (Connection c = DB.getKoneksi(); Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT * FROM pesanan ORDER BY id DESC")) {
                while (r.next()) {
                    Pesanan p = new Pesanan();
                    p.setId(r.getInt("id"));
                    p.setNama(teks(r.getString("nama")));
                    p.setTelepon(teks(r.getString("telepon")));
                    p.setJenis(teks(r.getString("jenis")));
                    p.setItem(teks(r.getString("item")));
                    p.setJumlah(r.getDouble("jumlah"));
                    p.setTarif(r.getDouble("tarif"));
                    p.setTotal(r.getDouble("total"));
                    p.setStatus(teks(r.getString("status")));
                    p.setTanggal(teks(r.getString("tanggal")));
                    p.setEstimasiHari(r.getInt("estimasi_hari"));
                    p.setTanggalSelesai(teks(r.getString("tanggal_selesai")));
                    baris.add(p);
                }
            }

            int proses = 0, selesai = 0;
            long pendapatan = 0;
            for (Pesanan p : baris) {
                if ("Proses".equals(p.getStatus())) proses++;
                if ("Selesai".equals(p.getStatus())) {
                    selesai++;
                    pendapatan += Math.round(p.getTotal());
                }
            }
            System.out.println("Database   : " + DB.infoLokasi());
            System.out.println("Total      : " + baris.size());
            System.out.println("Proses     : " + proses);
            System.out.println("Selesai    : " + selesai);
            System.out.println("Pendapatan : " + Tema.rupiah(pendapatan));

            LayananLaundry kiloan = new LaundryKiloan(3);
            System.out.println("Tarif kg   : " + Tema.rupiah(Math.round(kiloan.tarifSatuan())));
            System.out.println("Total 3 kg : " + Tema.rupiah(Math.round(kiloan.hitungTotal())));
            LayananLaundry satuan = new LaundrySatuan(2, "Sepatu");
            System.out.println("Total 2 pcs Sepatu : " + Tema.rupiah(Math.round(satuan.hitungTotal())));
            System.out.println("Tanggal    : " + ModelPesanan.tanggalTampil("2026-10-29"));
        } catch (Exception e) {
            gagal[0]++;
            System.out.println("GAGAL: " + e);
        }

        System.out.println(gagal[0] == 0 ? "HASIL: OK" : "HASIL: ADA ERROR");
        return gagal[0] == 0 ? 0 : 1;
    }

    public static void main(String[] args) {
        if (args.length > 0 && ("--cek".equals(args[0]) || "--check".equals(args[0]))) {
            System.exit(cekMandiri(args));
        }

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                DB.buatTabel();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Database gagal disiapkan.\n\n" + e.getMessage()
                                + "\n\nLokasi database: " + DB.infoLokasi(),
                        "Error Database", JOptionPane.ERROR_MESSAGE);
            }
            new LaundryApp().setVisible(true);
        });
    }
}
